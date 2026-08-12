package com.freenet.mobile.mesh.ble

import android.bluetooth.BluetoothAdapter
import android.bluetooth.le.AdvertiseCallback
import android.bluetooth.le.AdvertiseData
import android.bluetooth.le.AdvertiseSettings
import android.bluetooth.le.BluetoothLeAdvertiser
import android.os.ParcelUuid
import com.freenet.mobile.mesh.protocol.FreeNetProtocol
import java.util.UUID

/**
 * Advertises the FreeNet GATT service UUID so nearby phones running BLE
 * scans can find this device without needing to already know its address.
 *
 * Every node acts as both a BLE peripheral (this class + FreeNetGattServer)
 * and a BLE central (BleTransport scanning + FreeNetGattClient), so any two
 * phones can discover and connect to each other regardless of which one
 * "found" the other first.
 */
class FreeNetAdvertiser {
    private var advertiser: BluetoothLeAdvertiser? = null
    private var callback: AdvertiseCallback? = null

    fun start(): Boolean {
        val adapter = BluetoothAdapter.getDefaultAdapter() ?: return false
        if (!adapter.isEnabled) return false
        val bleAdvertiser = adapter.bluetoothLeAdvertiser ?: return false
        advertiser = bleAdvertiser

        val settings = AdvertiseSettings.Builder()
            .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_BALANCED)
            .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_MEDIUM)
            .setConnectable(true)
            .build()

        val data = AdvertiseData.Builder()
            .addServiceUuid(ParcelUuid(UUID.fromString(FreeNetProtocol.SERVICE_UUID)))
            .setIncludeDeviceName(false)
            .build()

        callback = object : AdvertiseCallback() {
            override fun onStartFailure(errorCode: Int) {
                // Common causes: too many concurrent advertisers on this chipset,
                // or Bluetooth off. Mesh keeps working via other transports.
            }
        }

        return try {
            bleAdvertiser.startAdvertising(settings, data, callback)
            true
        } catch (_: Exception) {
            false
        }
    }

    fun stop() {
        try {
            callback?.let { advertiser?.stopAdvertising(it) }
        } catch (_: Exception) {}
        advertiser = null
        callback = null
    }
}

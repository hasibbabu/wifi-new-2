package com.freenet.mobile.mesh.ble

import android.bluetooth.*
import android.content.Context
import com.freenet.mobile.mesh.protocol.FreeNetProtocol
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * BLE peripheral role: accepts writes from centrals (other phones' GATT
 * clients), reassembles fragmented frames per-device, and can notify a
 * connected central back on the TX characteristic.
 */
class FreeNetGattServer(
    private val context: Context,
    private val onFrame: (deviceAddress: String, frame: ByteArray) -> Unit
) {
    private val manager =
        context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
    private var server: BluetoothGattServer? = null

    private val serviceUuid = UUID.fromString(FreeNetProtocol.SERVICE_UUID)
    private val rxUuid = UUID.fromString(FreeNetProtocol.RX_UUID)
    private val txUuid = UUID.fromString(FreeNetProtocol.TX_UUID)

    private val reassemblers = ConcurrentHashMap<String, BleReassembler>()
    private val connectedDevices = ConcurrentHashMap<String, BluetoothDevice>()

    fun start(): Boolean {
        val adapter = manager.adapter ?: return false
        server = manager.openGattServer(context, callback) ?: return false

        val service = BluetoothGattService(
            serviceUuid,
            BluetoothGattService.SERVICE_TYPE_PRIMARY
        )

        service.addCharacteristic(
            BluetoothGattCharacteristic(
                rxUuid,
                BluetoothGattCharacteristic.PROPERTY_WRITE or
                    BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE,
                BluetoothGattCharacteristic.PERMISSION_WRITE
            )
        )

        service.addCharacteristic(
            BluetoothGattCharacteristic(
                txUuid,
                BluetoothGattCharacteristic.PROPERTY_NOTIFY or
                    BluetoothGattCharacteristic.PROPERTY_READ,
                BluetoothGattCharacteristic.PERMISSION_READ
            )
        )

        server!!.addService(service)
        return true
    }

    /** Sends [frame] (already fragmented into a single BLE-sized piece) to a connected central. */
    fun notify(deviceAddress: String, frame: ByteArray): Boolean {
        val device = connectedDevices[deviceAddress] ?: return false
        val characteristic =
            server?.getService(serviceUuid)?.getCharacteristic(txUuid) ?: return false
        characteristic.value = frame
        return server?.notifyCharacteristicChanged(device, characteristic, false) ?: false
    }

    fun stop() {
        server?.close()
        server = null
        reassemblers.clear()
        connectedDevices.clear()
    }

    private val callback = object : BluetoothGattServerCallback() {
        override fun onConnectionStateChange(device: BluetoothDevice, status: Int, newState: Int) {
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                connectedDevices[device.address] = device
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                connectedDevices.remove(device.address)
                reassemblers.remove(device.address)
            }
        }

        override fun onCharacteristicWriteRequest(
            device: BluetoothDevice,
            requestId: Int,
            characteristic: BluetoothGattCharacteristic,
            preparedWrite: Boolean,
            responseNeeded: Boolean,
            offset: Int,
            value: ByteArray
        ) {
            if (characteristic.uuid == rxUuid) {
                val fragment = BleFragmenter.parse(value)
                if (fragment != null) {
                    val reassembler = reassemblers.computeIfAbsent(device.address) { BleReassembler() }
                    reassembler.accept(fragment)?.let { complete -> onFrame(device.address, complete) }
                }
            }
            if (responseNeeded) {
                server?.sendResponse(
                    device, requestId, BluetoothGatt.GATT_SUCCESS, 0, null
                )
            }
        }
    }
}

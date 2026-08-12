package com.freenet.mobile.mesh.ble

import android.bluetooth.*
import android.content.Context
import android.os.Build
import com.freenet.mobile.mesh.protocol.FreeNetProtocol
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * BLE central role: connects out to a peripheral's GATT server, writes
 * fragments to its RX characteristic, and subscribes to its TX
 * characteristic to receive fragments flowing back.
 */
class FreeNetGattClient(private val context: Context) {
    private val serviceUuid = UUID.fromString(FreeNetProtocol.SERVICE_UUID)
    private val rxUuid = UUID.fromString(FreeNetProtocol.RX_UUID)
    private val txUuid = UUID.fromString(FreeNetProtocol.TX_UUID)
    private val cccdUuid = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")

    private var gatt: BluetoothGatt? = null
    private var onFrame: ((ByteArray) -> Unit)? = null
    private val reassembler = BleReassembler()
    private var nextMessageId = 0

    fun connect(
        device: BluetoothDevice,
        onFrame: (ByteArray) -> Unit,
        onReady: (Boolean) -> Unit
    ) {
        this.onFrame = onFrame
        gatt = device.connectGatt(context, false, object : BluetoothGattCallback() {
            override fun onConnectionStateChange(g: BluetoothGatt, status: Int, newState: Int) {
                if (newState == BluetoothProfile.STATE_CONNECTED) {
                    g.discoverServices()
                } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                    onReady(false)
                }
            }

            override fun onServicesDiscovered(g: BluetoothGatt, status: Int) {
                if (status != BluetoothGatt.GATT_SUCCESS) {
                    onReady(false)
                    return
                }
                val characteristic = g.getService(serviceUuid)?.getCharacteristic(txUuid)
                if (characteristic != null) {
                    g.setCharacteristicNotification(characteristic, true)
                    val descriptor = characteristic.getDescriptor(cccdUuid)
                    if (descriptor != null) {
                        descriptor.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                        g.writeDescriptor(descriptor)
                    }
                }
                onReady(true)
            }

            @Suppress("DEPRECATION")
            override fun onCharacteristicChanged(g: BluetoothGatt, characteristic: BluetoothGattCharacteristic) {
                if (characteristic.uuid != txUuid) return
                val fragment = BleFragmenter.parse(characteristic.value) ?: return
                reassembler.accept(fragment)?.let { this@FreeNetGattClient.onFrame?.invoke(it) }
            }
        })
    }

    /**
     * Splits [frame] into MTU-sized fragments and writes each to the RX characteristic in order.
     *
     * Known limitation: Android BLE requires each GATT write to complete
     * (via onCharacteristicWrite) before the next one is issued on the same
     * connection; this fires them back-to-back without waiting, which works
     * on many stacks in practice but can drop fragments on stricter ones.
     * A production version should queue fragments and advance the queue
     * from onCharacteristicWrite instead of writing all of them here.
     */
    fun send(frame: ByteArray): Boolean {
        val g = gatt ?: return false
        val service = g.getService(serviceUuid) ?: return false
        val characteristic = service.getCharacteristic(rxUuid) ?: return false
        val messageId = synchronized(this) { nextMessageId++ }
        val fragments = BleFragmenter.split(messageId, frame)
        var ok = true
        for (fragment in fragments) {
            characteristic.value = fragment
            val writeType = if (Build.VERSION.SDK_INT >= 33) {
                BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
            } else {
                BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE
            }
            characteristic.writeType = writeType
            ok = ok && g.writeCharacteristic(characteristic)
        }
        return ok
    }

    fun close() {
        gatt?.close()
        gatt = null
    }
}

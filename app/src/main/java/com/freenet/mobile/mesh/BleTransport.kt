package com.freenet.mobile.mesh

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.le.BluetoothLeScanner
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.os.ParcelUuid
import com.freenet.mobile.mesh.ble.FreeNetAdvertiser
import com.freenet.mobile.mesh.ble.FreeNetGattClient
import com.freenet.mobile.mesh.ble.FreeNetGattServer
import com.freenet.mobile.mesh.protocol.FreeNetProtocol
import com.freenet.mobile.mesh.protocol.LinkFrame
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * BLE transport: the lowest-power, lowest-bandwidth link but usually the
 * one with the longest effective range for "is anyone nearby at all"
 * discovery. Every node advertises + scans + runs a GATT server + opens
 * GATT client connections simultaneously (central and peripheral role at
 * once), since there's no fixed hub in this mesh.
 *
 * Bandwidth note: BLE tops out around 1-4 KB/s of real throughput after
 * fragmentation/ack overhead. Fine for text and the file-chunk store-and-
 * forward path; not usable for live audio/video (see call/ package).
 */
class BleTransport(
    private val context: Context,
    private val nodeId: () -> String,
    private val onPeer: (String, String) -> Unit = { _, _ -> },
    private val onEnvelope: (ByteArray) -> Unit = {}
) {
    private val adapter = BluetoothAdapter.getDefaultAdapter()
    private val scanner: BluetoothLeScanner? get() = adapter?.bluetoothLeScanner
    private var scanCallback: ScanCallback? = null

    private val advertiser = FreeNetAdvertiser()
    private val gattServer = FreeNetGattServer(context) { address, frame -> handleFrame(address, frame) }

    /** address -> client connection, once connectGatt succeeds */
    private val clients = ConcurrentHashMap<String, FreeNetGattClient>()
    private val addressToNodeId = ConcurrentHashMap<String, String>()
    private val nodeIdToAddress = ConcurrentHashMap<String, String>()
    private val connecting = ConcurrentHashMap<String, Boolean>()

    fun start() {
        gattServer.start()
        advertiser.start()
    }

    fun discover() {
        val s = scanner ?: return
        if (scanCallback != null) return
        val filter = ScanFilter.Builder()
            .setServiceUuid(ParcelUuid(UUID.fromString(FreeNetProtocol.SERVICE_UUID)))
            .build()
        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .build()
        scanCallback = object : ScanCallback() {
            override fun onScanResult(callbackType: Int, result: ScanResult) {
                connectIfNew(result.device)
            }
        }
        s.startScan(listOf(filter), settings, scanCallback)
    }

    private fun connectIfNew(device: BluetoothDevice) {
        val address = device.address ?: return
        if (addressToNodeId.containsKey(address)) return
        if (connecting.putIfAbsent(address, true) == true) return

        val client = FreeNetGattClient(context)
        clients[address] = client
        client.connect(
            device,
            onFrame = { frame -> handleFrame(address, frame) },
            onReady = { ready ->
                if (ready) {
                    client.send(LinkFrame.encodeHello(nodeId()))
                } else {
                    connecting.remove(address)
                    clients.remove(address)
                }
            }
        )
    }

    private fun handleFrame(address: String, raw: ByteArray) {
        val frame = LinkFrame.decode(raw) ?: return
        when (frame.type) {
            LinkFrame.TYPE_HELLO -> {
                val peerNodeId = String(frame.payload, Charsets.UTF_8)
                addressToNodeId[address] = peerNodeId
                nodeIdToAddress[peerNodeId] = address
                connecting.remove(address)
                // Reply over whichever direction we have: GATT server notifies
                // the central back, our own client (if we're the central) has
                // already sent its hello above.
                gattServer.notify(address, LinkFrame.encodeHello(nodeId()))
                onPeer(peerNodeId, address)
            }
            LinkFrame.TYPE_ENVELOPE -> onEnvelope(frame.payload)
        }
    }

    fun send(envelopeBytes: ByteArray, peerId: String): SendResult {
        val address = nodeIdToAddress[peerId]
            ?: return SendResult(false, "BLE: no link established with $peerId")

        val frame = LinkFrame.encodeEnvelope(envelopeBytes)

        // Prefer an outgoing client connection; fall back to notifying over
        // an inbound connection this device accepted as a peripheral.
        val viaClient = clients[address]?.send(frame) ?: false
        if (viaClient) return SendResult(true, "BLE GATT (client) sent")

        val viaServer = gattServer.notify(address, frame)
        return if (viaServer) SendResult(true, "BLE GATT (server notify) sent")
        else SendResult(false, "BLE GATT data channel not connected")
    }

    fun stop() {
        scanCallback?.let { scanner?.stopScan(it) }
        scanCallback = null
        advertiser.stop()
        gattServer.stop()
        clients.values.forEach { it.close() }
        clients.clear()
        addressToNodeId.clear()
        nodeIdToAddress.clear()
        connecting.clear()
    }
}

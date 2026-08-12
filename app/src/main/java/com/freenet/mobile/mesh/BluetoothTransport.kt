package com.freenet.mobile.mesh

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.content.Context
import android.content.IntentFilter
import com.freenet.mobile.mesh.bluetooth.RfcommServer
import com.freenet.mobile.mesh.bluetooth.RfcommTransport
import com.freenet.mobile.mesh.protocol.LinkFrame
import java.util.concurrent.ConcurrentHashMap

/**
 * Bluetooth Classic (RFCOMM) transport. Discovery finds nearby devices;
 * once connected, each side sends a HELLO carrying its FreeNet node id so
 * routing can address peers by node id instead of a raw MAC address.
 *
 * @param onPeer called once a link's HELLO handshake completes: (nodeId, macAddress)
 * @param onEnvelope called for every reassembled MeshEnvelope received on any link
 */
class BluetoothTransport(
    private val context: Context,
    private val nodeId: () -> String,
    private val onPeer: (String, String) -> Unit = { _, _ -> },
    private val onEnvelope: (ByteArray) -> Unit = {}
) {
    private val adapter = BluetoothAdapter.getDefaultAdapter()
    private val server = RfcommServer()
    private val client = RfcommTransport()

    /** address -> nodeId, once handshake completes */
    private val addressToNodeId = ConcurrentHashMap<String, String>()
    /** nodeId -> address, once handshake completes */
    private val nodeIdToAddress = ConcurrentHashMap<String, String>()
    private val connecting = ConcurrentHashMap<String, Boolean>()

    private var receiver: BluetoothDiscoveryReceiver? = null

    fun start() {
        server.start { address, raw -> handleFrame(address, raw) }
        receiver = BluetoothDiscoveryReceiver { device -> onDeviceFound(device) }
        context.registerReceiver(receiver, IntentFilter(BluetoothDevice.ACTION_FOUND))
    }

    fun discover() {
        adapter?.let { if (it.isEnabled) { it.cancelDiscovery(); it.startDiscovery() } }
    }

    private fun onDeviceFound(device: BluetoothDevice) {
        val address = device.address ?: return
        if (addressToNodeId.containsKey(address)) return // already linked
        if (connecting.putIfAbsent(address, true) == true) return // already trying

        val ok = client.connect(device) { fromAddress, raw -> handleFrame(fromAddress, raw) }
        if (ok) {
            client.send(address, LinkFrame.encodeHello(nodeId()))
        } else {
            connecting.remove(address)
        }
    }

    private fun handleFrame(address: String, raw: ByteArray) {
        val frame = LinkFrame.decode(raw) ?: return
        when (frame.type) {
            LinkFrame.TYPE_HELLO -> {
                val peerNodeId = String(frame.payload, Charsets.UTF_8)
                addressToNodeId[address] = peerNodeId
                nodeIdToAddress[peerNodeId] = address
                connecting.remove(address)
                // Reply with our own hello so the connecting side also learns who we are.
                client.send(address, LinkFrame.encodeHello(nodeId()))
                onPeer(peerNodeId, address)
            }
            LinkFrame.TYPE_ENVELOPE -> onEnvelope(frame.payload)
        }
    }

    fun send(envelopeBytes: ByteArray, peerId: String): SendResult {
        val address = nodeIdToAddress[peerId]
            ?: return SendResult(false, "Bluetooth: no link established with $peerId")
        val ok = client.send(address, LinkFrame.encodeEnvelope(envelopeBytes))
        return if (ok) SendResult(true, "Bluetooth RFCOMM sent")
        else SendResult(false, "Bluetooth RFCOMM write failed")
    }

    fun stop() {
        adapter?.cancelDiscovery()
        receiver?.let { try { context.unregisterReceiver(it) } catch (_: Exception) {} }
        receiver = null
        server.stop()
        client.stop()
        addressToNodeId.clear()
        nodeIdToAddress.clear()
        connecting.clear()
    }
}

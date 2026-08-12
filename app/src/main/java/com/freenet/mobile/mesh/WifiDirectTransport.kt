package com.freenet.mobile.mesh

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.wifi.p2p.WifiP2pConfig
import android.net.wifi.p2p.WifiP2pDevice
import android.net.wifi.p2p.WifiP2pManager
import com.freenet.mobile.mesh.protocol.FreeNetProtocol
import com.freenet.mobile.mesh.wifidirect.WifiDirectEndpoint
import java.util.concurrent.ConcurrentHashMap

/**
 * Wi-Fi Direct's only job in this stack is establishing a local IP link
 * between two phones that aren't already sharing a Wi-Fi network. Once a
 * P2P group forms, both phones have IP addresses on the same virtual
 * interface, and the existing UDP discovery (LanDiscovery) + TCP transport
 * (WifiLanTransport, whose ServerSocket listens on all interfaces) take
 * over automatically to exchange node identity and mesh packets — there is
 * no separate wire format to maintain here.
 *
 * `send()` below is therefore only a fallback for the brief window after a
 * group forms but before LanDiscovery has announced a node id for the new
 * IP; ordinary traffic flows over the "wifi_lan" route once that happens.
 */
class WifiDirectTransport(private val context: Context) {
    private val manager = context.getSystemService(Context.WIFI_P2P_SERVICE) as? WifiP2pManager
    private var channel: WifiP2pManager.Channel? = null
    private var receiver: BroadcastReceiver? = null
    private val endpoint = WifiDirectEndpoint()

    /** peerId (device address, pre-handshake) -> last known group-owner IP */
    private val knownEndpoints = ConcurrentHashMap<String, String>()
    private val attemptedConnections = ConcurrentHashMap<String, Boolean>()

    fun start() {
        val m = manager ?: return
        channel = m.initialize(context, context.mainLooper, null)
        receiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context?, intent: Intent?) {
                when (intent?.action) {
                    WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION -> onPeersChanged()
                    WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION -> onConnectionChanged()
                }
            }
        }
        context.registerReceiver(receiver, IntentFilter().apply {
            addAction(WifiP2pManager.WIFI_P2P_STATE_CHANGED_ACTION)
            addAction(WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION)
            addAction(WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION)
            addAction(WifiP2pManager.WIFI_P2P_THIS_DEVICE_CHANGED_ACTION)
        }, Context.RECEIVER_NOT_EXPORTED)
    }

    fun discover() {
        val m = manager ?: return
        val c = channel ?: return
        m.discoverPeers(c, object : WifiP2pManager.ActionListener {
            override fun onSuccess() {}
            override fun onFailure(reason: Int) {}
        })
    }

    private fun onPeersChanged() {
        val m = manager ?: return
        val c = channel ?: return
        m.requestPeers(c) { peers ->
            // Auto-connect to any newly seen peer. There's no cheap way to
            // pre-filter "is this a FreeNet node" before a WPS-style
            // negotiation, so this mirrors the codebase's existing BLE/BT
            // discovery approach: connect and let the app-level HELLO
            // handshake (over the resulting LAN link) decide if it's useful.
            peers.deviceList.forEach { device: WifiP2pDevice ->
                val address = device.deviceAddress ?: return@forEach
                if (attemptedConnections.putIfAbsent(address, true) == true) return@forEach
                val config = WifiP2pConfig().apply { deviceAddress = address }
                m.connect(c, config, object : WifiP2pManager.ActionListener {
                    override fun onSuccess() {}
                    override fun onFailure(reason: Int) { attemptedConnections.remove(address) }
                })
            }
        }
    }

    private fun onConnectionChanged() {
        val m = manager ?: return
        val c = channel ?: return
        m.requestConnectionInfo(c) { info ->
            if (info.groupFormed && info.groupOwnerAddress != null) {
                val host = info.groupOwnerAddress.hostAddress ?: return@requestConnectionInfo
                // LanDiscovery's UDP broadcast + WifiLanTransport's TCP server
                // (already running, bound on all interfaces) take it from here.
                knownEndpoints["group_owner"] = host
            }
        }
    }

    /** Fallback direct send while node-id routing hasn't been learned yet for this link. */
    fun send(envelopeBytes: ByteArray, peerId: String): SendResult {
        val host = knownEndpoints[peerId] ?: knownEndpoints["group_owner"]
            ?: return SendResult(false, "Wi-Fi Direct endpoint not established")
        val ok = endpoint.send(host, FreeNetProtocol.PACKET_PORT, envelopeBytes)
        return if (ok) SendResult(true, "Wi-Fi Direct sent")
        else SendResult(false, "Wi-Fi Direct send failed")
    }

    fun stop() {
        receiver?.let { try { context.unregisterReceiver(it) } catch (_: Exception) {} }
        receiver = null
        channel = null
        knownEndpoints.clear()
        attemptedConnections.clear()
    }
}

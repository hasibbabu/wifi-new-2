package com.freenet.mobile.mesh.discovery

import android.content.Context
import com.freenet.mobile.mesh.protocol.FreeNetProtocol
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.util.concurrent.Executors

class LanDiscovery(
    private val context: Context,
    private val nodeId: () -> String,
    private val onPeer: (String, String) -> Unit
) {
    private val executor = Executors.newSingleThreadExecutor()
    private var socket: DatagramSocket? = null

    fun start() {
        executor.execute {
            try {
                socket = DatagramSocket(FreeNetProtocol.DISCOVERY_PORT)
                socket!!.broadcast = true
                val hello = "FREENET|${nodeId()}|${FreeNetProtocol.PACKET_PORT}".toByteArray()
                val packet = DatagramPacket(
                    hello, hello.size,
                    InetAddress.getByName("255.255.255.255"),
                    FreeNetProtocol.DISCOVERY_PORT
                )
                socket!!.send(packet)

                val buf = ByteArray(2048)
                while (!socket!!.isClosed) {
                    val incoming = DatagramPacket(buf, buf.size)
                    socket!!.receive(incoming)
                    val msg = String(incoming.data, 0, incoming.length)
                    val parts = msg.split("|")
                    if (parts.size == 3 && parts[0] == "FREENET" && parts[1] != nodeId()) {
                        onPeer(parts[1], incoming.address.hostAddress ?: "")
                    }
                }
            } catch (_: Exception) {}
        }
    }

    fun stop() {
        try { socket?.close() } catch (_: Exception) {}
        executor.shutdownNow()
    }
}

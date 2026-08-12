package com.freenet.mobile.mesh

import android.content.Context
import com.freenet.mobile.mesh.protocol.FrameCodec
import com.freenet.mobile.mesh.protocol.FreeNetProtocol
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.Executors

/**
 * Plain TCP transport for phones already sharing a Wi-Fi LAN (e.g. the same
 * hotspot). Wire format is length-prefixed MeshEnvelope bytes (FrameCodec),
 * the same framing used by the Bluetooth/Wi-Fi Direct socket transports, so
 * every stream-based transport speaks one format.
 */
class WifiLanTransport(private val context: Context) {
    companion object { const val PORT = FreeNetProtocol.PACKET_PORT }
    private val executor = Executors.newCachedThreadPool()
    private var server: ServerSocket? = null

    /** Set by FreeNetBridge before start(); receives raw MeshEnvelope-encoded bytes. */
    var onPacketBytes: ((ByteArray) -> Unit)? = null

    fun start() {
        if (server != null) return
        executor.execute {
            try {
                server = ServerSocket(PORT)
                while (!server!!.isClosed) {
                    val socket = server!!.accept()
                    executor.execute { receive(socket) }
                }
            } catch (_: Exception) {}
        }
    }

    fun send(envelopeBytes: ByteArray, peerAddress: String): SendResult = try {
        Socket().use { socket ->
            socket.connect(InetSocketAddress(peerAddress, PORT), 3000)
            socket.getOutputStream().write(FrameCodec.encode(envelopeBytes))
            socket.getOutputStream().flush()
        }
        SendResult(true, "Wi-Fi LAN sent")
    } catch (e: Exception) {
        SendResult(false, "Wi-Fi LAN failed: ${e.javaClass.simpleName}")
    }

    private fun receive(socket: Socket) {
        socket.use {
            it.soTimeout = 8000
            val frame = FrameCodec.readFrame(it.getInputStream()) ?: return
            onPacketBytes?.invoke(frame)
        }
    }

    fun stop() {
        try { server?.close() } catch (_: Exception) {}
        server = null
        executor.shutdownNow()
    }
}

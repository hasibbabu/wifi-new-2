package com.freenet.mobile.mesh.wifidirect

import android.content.Context
import android.net.wifi.p2p.WifiP2pInfo
import com.freenet.mobile.mesh.protocol.FrameCodec
import java.net.InetSocketAddress
import java.net.Socket

class WifiDirectEndpoint {
    fun endpoint(info: WifiP2pInfo): String? =
        info.groupOwnerAddress?.hostAddress

    fun send(host: String, port: Int, payload: ByteArray): Boolean {
        return try {
            Socket().use { socket ->
                socket.connect(InetSocketAddress(host, port), 3000)
                socket.outputStream.write(FrameCodec.encode(payload))
                socket.outputStream.flush()
            }
            true
        } catch (_: Exception) {
            false
        }
    }
}

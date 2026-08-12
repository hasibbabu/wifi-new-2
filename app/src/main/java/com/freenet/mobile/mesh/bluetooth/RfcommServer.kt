package com.freenet.mobile.mesh.bluetooth

import android.bluetooth.BluetoothAdapter
import com.freenet.mobile.mesh.protocol.FrameCodec
import java.util.concurrent.Executors

class RfcommServer {
    private val adapter = BluetoothAdapter.getDefaultAdapter()
    private val executor = Executors.newSingleThreadExecutor()
    private var server: android.bluetooth.BluetoothServerSocket? = null
    private var running = false

    fun start(onFrame: (String, ByteArray) -> Unit): Boolean {
        if (running) return true
        running = true
        return try {
            server = adapter?.listenUsingRfcommWithServiceRecord(
                "FreeNet",
                RfcommTransport.SERVICE_UUID
            )
            executor.execute {
                while (running) {
                    try {
                        val socket = server?.accept() ?: break
                        executor.execute {
                            try {
                                while (running && !socket.isClosed) {
                                    val frame = FrameCodec.readFrame(socket.inputStream) ?: break
                                    onFrame(socket.remoteDevice.address, frame)
                                }
                            } catch (_: Exception) {}
                            try { socket.close() } catch (_: Exception) {}
                        }
                    } catch (_: Exception) {
                        if (running) continue
                    }
                }
            }
            true
        } catch (_: Exception) {
            false
        }
    }

    fun stop() {
        running = false
        try { server?.close() } catch (_: Exception) {}
        executor.shutdownNow()
    }
}

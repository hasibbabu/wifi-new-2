package com.freenet.mobile.mesh.bluetooth

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import com.freenet.mobile.mesh.protocol.FrameCodec
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors

class RfcommTransport {
    companion object {
        val SERVICE_UUID: UUID =
            UUID.fromString("7d9e1001-5a5a-4f4e-8d01-465245454e45")
    }

    private val adapter = BluetoothAdapter.getDefaultAdapter()
    private val executor = Executors.newCachedThreadPool()
    private val sockets = ConcurrentHashMap<String, android.bluetooth.BluetoothSocket>()
    private var running = true

    fun connect(
        device: BluetoothDevice,
        onFrame: (String, ByteArray) -> Unit
    ): Boolean {
        if (!running) return false
        return try {
            val socket = device.createRfcommSocketToServiceRecord(SERVICE_UUID)
            adapter?.cancelDiscovery()
            socket.connect()
            sockets[device.address] = socket

            executor.execute {
                try {
                    val input = socket.inputStream
                    while (running && !socket.isClosed) {
                        val frame = FrameCodec.readFrame(input) ?: break
                        onFrame(device.address, frame)
                    }
                } catch (_: Exception) {
                    close(device.address)
                }
            }
            true
        } catch (_: Exception) {
            false
        }
    }

    fun send(peerAddress: String, payload: ByteArray): Boolean {
        val socket = sockets[peerAddress] ?: return false
        return try {
            socket.outputStream.write(FrameCodec.encode(payload))
            socket.outputStream.flush()
            true
        } catch (_: Exception) {
            close(peerAddress)
            false
        }
    }

    fun close(peerAddress: String) {
        try { sockets.remove(peerAddress)?.close() } catch (_: Exception) {}
    }

    fun stop() {
        running = false
        sockets.keys.toList().forEach(::close)
        executor.shutdownNow()
    }
}

package com.freenet.mobile.mesh.protocol

import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Length-prefixed framing for stream transports.
 * [4-byte big-endian length][payload]
 */
object FrameCodec {
    const val HEADER_BYTES = 4
    const val MAX_FRAME_BYTES = FreeNetProtocol.MAX_PACKET_BYTES

    fun encode(payload: ByteArray): ByteArray {
        require(payload.size <= MAX_FRAME_BYTES) { "frame too large" }
        return ByteBuffer.allocate(HEADER_BYTES + payload.size)
            .order(ByteOrder.BIG_ENDIAN)
            .putInt(payload.size)
            .put(payload)
            .array()
    }

    fun readFrame(input: java.io.InputStream): ByteArray? {
        val header = ByteArray(HEADER_BYTES)
        if (!readFully(input, header)) return null
        val size = ByteBuffer.wrap(header).order(ByteOrder.BIG_ENDIAN).int
        if (size !in 1..MAX_FRAME_BYTES) return null
        val payload = ByteArray(size)
        if (!readFully(input, payload)) return null
        return payload
    }

    private fun readFully(input: java.io.InputStream, buffer: ByteArray): Boolean {
        var offset = 0
        while (offset < buffer.size) {
            val n = input.read(buffer, offset, buffer.size - offset)
            if (n < 0) return false
            offset += n
        }
        return true
    }
}

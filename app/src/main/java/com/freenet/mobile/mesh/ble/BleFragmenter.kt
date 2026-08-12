package com.freenet.mobile.mesh.ble

import java.nio.ByteBuffer

object BleFragmenter {
    const val HEADER = 12

    fun split(messageId: Int, payload: ByteArray, mtu: Int = 180): List<ByteArray> {
        require(mtu > HEADER + 1)
        val chunkSize = mtu - HEADER
        val count = (payload.size + chunkSize - 1) / chunkSize
        return (0 until count).map { index ->
            val start = index * chunkSize
            val end = minOf(start + chunkSize, payload.size)
            ByteBuffer.allocate(HEADER + end - start)
                .putInt(messageId)
                .putInt(index)
                .putInt(count)
                .put(payload, start, end - start)
                .array()
        }
    }

    data class Fragment(
        val messageId: Int,
        val index: Int,
        val count: Int,
        val data: ByteArray
    )

    fun parse(frame: ByteArray): Fragment? {
        if (frame.size < HEADER) return null
        val b = ByteBuffer.wrap(frame)
        val id = b.int
        val index = b.int
        val count = b.int
        if (count <= 0 || index !in 0 until count) return null
        val data = ByteArray(b.remaining())
        b.get(data)
        return Fragment(id, index, count, data)
    }
}

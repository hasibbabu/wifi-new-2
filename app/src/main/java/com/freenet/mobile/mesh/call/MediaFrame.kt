package com.freenet.mobile.mesh.call

import java.nio.ByteBuffer

/**
 * Wire format for live call media. Deliberately UDP/best-effort: a dropped
 * audio frame or video frame should just be skipped, never retried — by the
 * time a retry would arrive, real-time audio/video has moved on. This is
 * the same design real-time voice/video protocols (RTP included) use, kept
 * minimal here since this only ever needs to survive one direct radio hop
 * (see DirectCallManager).
 *
 * Header layout (10 bytes) + payload:
 *   [0]      type            1 = audio, 2 = video
 *   [1..4]   frameId  (u32)  increments per audio chunk / per video frame
 *   [5..6]   fragIndex (u16) which fragment of this frame (video only; 0 for audio)
 *   [7..8]   fragCount (u16) total fragments for this frame (1 for audio)
 *   [9]      reserved
 */
object MediaFrame {
    const val TYPE_AUDIO: Byte = 1
    const val TYPE_VIDEO: Byte = 2
    const val HEADER_BYTES = 10

    /** Safe payload size to stay under typical Wi-Fi/BLE-tethered UDP MTUs without IP fragmentation. */
    const val MAX_FRAGMENT_PAYLOAD = 1200

    data class Fragment(
        val type: Byte,
        val frameId: Int,
        val fragIndex: Int,
        val fragCount: Int,
        val payload: ByteArray
    )

    fun encode(type: Byte, frameId: Int, fragIndex: Int, fragCount: Int, payload: ByteArray): ByteArray =
        ByteBuffer.allocate(HEADER_BYTES + payload.size)
            .put(type)
            .putInt(frameId)
            .putShort(fragIndex.toShort())
            .putShort(fragCount.toShort())
            .put(0) // reserved
            .put(payload)
            .array()

    fun decode(raw: ByteArray): Fragment? {
        if (raw.size < HEADER_BYTES) return null
        val buf = ByteBuffer.wrap(raw)
        val type = buf.get()
        val frameId = buf.int
        val fragIndex = buf.short.toInt()
        val fragCount = buf.short.toInt()
        buf.get() // reserved
        val payload = ByteArray(buf.remaining())
        buf.get(payload)
        if (fragCount <= 0 || fragIndex !in 0 until fragCount) return null
        return Fragment(type, frameId, fragIndex, fragCount, payload)
    }

    /** Splits an audio chunk (always 1 fragment) or a video JPEG (possibly many) into wire fragments. */
    fun fragment(type: Byte, frameId: Int, payload: ByteArray): List<ByteArray> {
        if (payload.size <= MAX_FRAGMENT_PAYLOAD) {
            return listOf(encode(type, frameId, 0, 1, payload))
        }
        val count = (payload.size + MAX_FRAGMENT_PAYLOAD - 1) / MAX_FRAGMENT_PAYLOAD
        return (0 until count).map { index ->
            val start = index * MAX_FRAGMENT_PAYLOAD
            val end = minOf(start + MAX_FRAGMENT_PAYLOAD, payload.size)
            encode(type, frameId, index, count, payload.copyOfRange(start, end))
        }
    }
}

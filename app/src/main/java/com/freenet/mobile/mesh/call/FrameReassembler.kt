package com.freenet.mobile.mesh.call

import java.util.concurrent.ConcurrentHashMap

/**
 * Reassembles fragmented [MediaFrame]s (video JPEGs split across several
 * UDP datagrams). Unlike the store-and-forward [com.freenet.mobile.mesh.media.MediaReassembler],
 * this never blocks waiting for a missing fragment: a newer frameId simply
 * evicts any older, still-incomplete frame, since displaying a stale video
 * frame late is worse than skipping it.
 */
class FrameReassembler {
    private data class Partial(val fragCount: Int, val parts: MutableMap<Int, ByteArray> = mutableMapOf())

    private val partials = ConcurrentHashMap<Int, Partial>()
    @Volatile private var highestSeenFrameId = -1

    fun accept(fragment: MediaFrame.Fragment): ByteArray? {
        if (fragment.frameId < highestSeenFrameId - 2) return null // too stale, drop

        if (fragment.fragCount == 1) {
            if (fragment.frameId > highestSeenFrameId) highestSeenFrameId = fragment.frameId
            return fragment.payload
        }

        val partial = partials.computeIfAbsent(fragment.frameId) { Partial(fragment.fragCount) }
        partial.parts[fragment.fragIndex] = fragment.payload

        if (partial.parts.size < partial.fragCount) {
            // Drop any older partial frames still hanging around; a newer
            // frame is already arriving, so completing the old one is moot.
            partials.keys.removeAll { it < fragment.frameId }
            return null
        }

        partials.remove(fragment.frameId)
        if (fragment.frameId > highestSeenFrameId) highestSeenFrameId = fragment.frameId

        val out = java.io.ByteArrayOutputStream()
        for (i in 0 until partial.fragCount) {
            out.write(partial.parts[i] ?: return null)
        }
        return out.toByteArray()
    }
}

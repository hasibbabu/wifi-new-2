package com.freenet.mobile.mesh.ble

import java.io.ByteArrayOutputStream
import java.util.concurrent.ConcurrentHashMap

class BleReassembler {
    private data class State(
        val count: Int,
        val parts: MutableMap<Int, ByteArray> = mutableMapOf()
    )

    private val states = ConcurrentHashMap<Int, State>()

    fun accept(fragment: BleFragmenter.Fragment): ByteArray? {
        val state = states.computeIfAbsent(fragment.messageId) { State(fragment.count) }
        if (state.count != fragment.count) {
            states.remove(fragment.messageId)
            return null
        }
        state.parts[fragment.index] = fragment.data
        if (state.parts.size != state.count) return null

        val out = ByteArrayOutputStream()
        for (i in 0 until state.count) {
            out.write(state.parts[i] ?: return null)
        }
        states.remove(fragment.messageId)
        return out.toByteArray()
    }

    fun clear() = states.clear()
}

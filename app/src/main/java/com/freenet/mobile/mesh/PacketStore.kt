package com.freenet.mobile.mesh

import java.util.concurrent.ConcurrentHashMap

class PacketStore(private val maxEntries: Int = 10000) {
    private val seen = ConcurrentHashMap<String, Long>()

    fun seen(id: String) = seen.containsKey(id)

    fun remember(id: String) {
        if (seen.size >= maxEntries) seen.keys.firstOrNull()?.let { seen.remove(it) }
        seen[id] = System.currentTimeMillis()
    }
}

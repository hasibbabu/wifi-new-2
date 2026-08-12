package com.freenet.mobile.mesh.routing

import java.util.concurrent.ConcurrentHashMap

class RouteTable {
    private val table = ConcurrentHashMap<String, List<RouteHop>>()

    fun update(destination: String, hops: List<RouteHop>) {
        table[destination] = hops.sortedByDescending { it.score }.take(4)
    }

    fun next(destination: String): RouteHop? =
        table[destination]?.firstOrNull()

    fun remove(destination: String) {
        table.remove(destination)
    }

    fun removePeer(peerId: String) {
        table.entries.forEach { (key, value) ->
            val filtered = value.filterNot { it.peerId == peerId }
            if (filtered.isEmpty()) table.remove(key) else table[key] = filtered
        }
    }
}

data class RouteHop(
    val peerId: String,
    val transport: String,
    val endpoint: String?,
    val score: Int
)

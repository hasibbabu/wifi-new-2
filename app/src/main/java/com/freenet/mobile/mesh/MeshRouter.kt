package com.freenet.mobile.mesh

import java.util.concurrent.ConcurrentHashMap

class MeshRouter {
    private val routes = ConcurrentHashMap<String, MutableList<RouteHop>>()

    fun updateRoute(destination: String, hop: RouteHop) {
        routes.computeIfAbsent(destination) { mutableListOf() }.apply {
            removeAll { it.peerId == hop.peerId && it.transport == hop.transport }
            add(hop)
            sortByDescending { it.score }
        }
    }

    fun removePeer(peerId: String) {
        routes.values.forEach { it.removeAll { hop -> hop.peerId == peerId } }
    }

    fun nextHop(destination: String?) = destination?.let { routes[it]?.firstOrNull() }
}

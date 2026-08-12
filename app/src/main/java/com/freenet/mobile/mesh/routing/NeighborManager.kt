package com.freenet.mobile.mesh.routing

import java.util.concurrent.ConcurrentHashMap

class NeighborManager {
    private val neighbors = ConcurrentHashMap<String, Neighbor>()

    fun upsert(neighbor: Neighbor) {
        neighbors[neighbor.nodeId] = neighbor
    }

    fun remove(nodeId: String) {
        neighbors.remove(nodeId)
    }

    fun active(): List<Neighbor> =
        neighbors.values.filter { it.alive() }.sortedByDescending { it.score }
}

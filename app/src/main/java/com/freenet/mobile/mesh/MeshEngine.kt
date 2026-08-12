package com.freenet.mobile.mesh

import android.content.Context
import com.freenet.mobile.mesh.protocol.MeshEnvelope
import com.freenet.mobile.mesh.routing.Neighbor
import com.freenet.mobile.mesh.routing.NeighborManager
import com.freenet.mobile.mesh.routing.RouteTable
import com.freenet.mobile.mesh.routing.RouteHop
import com.freenet.mobile.mesh.security.NodeIdentity
import com.freenet.mobile.mesh.storage.PacketQueue

class MeshEngine(private val context: Context) {

    private val identity = NodeIdentity()
    private val neighbors = NeighborManager()
    private val routes = RouteTable()
    private val queue = PacketQueue(context)
    private val seenPackets = PacketStore()

    fun nodeId(): String = identity.nodeId()
    fun publicKey(): String = identity.publicKeyBase64()

    fun registerNeighbor(
        nodeId: String,
        endpoint: String?,
        transports: Set<String>,
        score: Int
    ) {
        neighbors.upsert(Neighbor(nodeId, endpoint, transports, score))
        val hops = transports.map {
            RouteHop(nodeId, it, endpoint, score)
        }
        routes.update(nodeId, hops)
    }

    fun createPacket(destination: String?, body: String, ttl: Int = 10): MeshEnvelope =
        MeshEnvelope(
            packetId = java.util.UUID.randomUUID().toString(),
            sourceNodeId = nodeId(),
            destinationNodeId = destination,
            ttl = ttl.coerceIn(1, 64),
            hopCount = 0,
            createdAt = System.currentTimeMillis(),
            body = body
        )

    /**
     * True once per unique packetId. A flooding mesh will see the same
     * packet arrive from several neighbors; without this dedupe check the
     * network would rebroadcast every packet forever.
     */
    fun shouldProcess(packetId: String): Boolean {
        if (seenPackets.seen(packetId)) return false
        seenPackets.remember(packetId)
        return true
    }

    fun isForMe(packet: MeshEnvelope): Boolean = packet.destinationNodeId == nodeId()

    fun isBroadcast(packet: MeshEnvelope): Boolean = packet.destinationNodeId == null

    fun accept(packet: MeshEnvelope): Boolean {
        if (packet.expired()) return false
        if (packet.destinationNodeId == nodeId()) return true
        return false
    }

    fun queue(packet: MeshEnvelope) {
        if (!packet.expired()) queue.enqueue(packet)
    }

    fun pendingCount() = queue.size()

    /** Drains everything sitting in the offline store-and-forward queue. */
    fun drainQueue() = queue.drain()

    fun nextHop(destination: String?): RouteHop? =
        destination?.let { routes.next(it) }

    fun forwardPacket(packet: MeshEnvelope): MeshEnvelope? {
        if (packet.expired()) return null
        return packet.decremented().takeIf { !it.expired() }
    }
}

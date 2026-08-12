package com.freenet.mobile.mesh.protocol

import org.json.JSONObject

data class MeshEnvelope(
    val packetId: String,
    val sourceNodeId: String,
    val destinationNodeId: String?,
    val ttl: Int,
    val hopCount: Int,
    val createdAt: Long,
    val body: String,
    val signature: String? = null
) {
    fun encode(): ByteArray = JSONObject().apply {
        put("v", FreeNetProtocol.PROTOCOL_VERSION)
        put("packet_id", packetId)
        put("source", sourceNodeId)
        put("destination", destinationNodeId)
        put("ttl", ttl)
        put("hop_count", hopCount)
        put("created_at", createdAt)
        put("body", body)
        put("signature", signature)
    }.toString().toByteArray(Charsets.UTF_8)

    fun decremented(): MeshEnvelope = copy(ttl = ttl - 1, hopCount = hopCount + 1)

    fun expired(): Boolean = ttl <= 0

    companion object {
        fun decode(bytes: ByteArray): MeshEnvelope {
            require(bytes.size <= FreeNetProtocol.MAX_PACKET_BYTES) { "packet too large" }
            val j = JSONObject(String(bytes, Charsets.UTF_8))
            require(j.optInt("v", 0) == FreeNetProtocol.PROTOCOL_VERSION) { "unsupported protocol" }
            return MeshEnvelope(
                packetId = j.getString("packet_id"),
                sourceNodeId = j.getString("source"),
                destinationNodeId = if (j.isNull("destination")) null else j.getString("destination"),
                ttl = j.getInt("ttl"),
                hopCount = j.getInt("hop_count"),
                createdAt = j.getLong("created_at"),
                body = j.getString("body"),
                signature = if (j.isNull("signature")) null else j.getString("signature")
            )
        }
    }
}

package com.freenet.mobile.mesh.routing

import com.freenet.mobile.mesh.protocol.FreeNetProtocol

data class Neighbor(
    val nodeId: String,
    val endpoint: String?,
    val transports: Set<String>,
    val score: Int,
    val lastSeen: Long = System.currentTimeMillis()
) {
    fun alive(now: Long = System.currentTimeMillis()) =
        now - lastSeen < 30_000L
}

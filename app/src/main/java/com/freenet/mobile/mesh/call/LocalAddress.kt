package com.freenet.mobile.mesh.call

import java.net.Inet4Address
import java.net.NetworkInterface

/**
 * Direct (1-hop) calling only works when both phones already have IP
 * reachability to each other — same Wi-Fi network, same hotspot, or a
 * formed Wi-Fi Direct group. This just finds *a* non-loopback IPv4 address
 * to hand to the other side during call signaling; it does not try to guess
 * which interface the peer is actually reachable on when several are up.
 */
object LocalAddress {
    fun ipv4(): String? {
        return try {
            NetworkInterface.getNetworkInterfaces().asSequence()
                .filter { it.isUp && !it.isLoopback }
                .flatMap { it.inetAddresses.asSequence() }
                .filterIsInstance<Inet4Address>()
                .map { it.hostAddress }
                .firstOrNull { it != null && !it.startsWith("127.") }
        } catch (_: Exception) {
            null
        }
    }
}

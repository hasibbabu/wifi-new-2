package com.freenet.mobile.mesh.protocol

/**
 * Every point-to-point link (Bluetooth RFCOMM socket, BLE GATT
 * characteristic, Wi-Fi Direct socket) needs to learn the *mesh* node id of
 * whatever device it just connected to before routing can register it as a
 * neighbor — a Bluetooth MAC address or GATT device handle is not a stable
 * FreeNet node id.
 *
 * LinkFrame prefixes a single byte in front of whatever is written to the
 * link so the two purposes never get confused: a HELLO announcing "this is
 * my node id" the moment a link comes up, and ENVELOPE carrying an actual
 * (possibly fragmented) MeshEnvelope.
 */
object LinkFrame {
    const val TYPE_HELLO: Byte = 0x01
    const val TYPE_ENVELOPE: Byte = 0x02

    data class Frame(val type: Byte, val payload: ByteArray)

    fun encodeHello(nodeId: String): ByteArray =
        byteArrayOf(TYPE_HELLO) + nodeId.toByteArray(Charsets.UTF_8)

    fun encodeEnvelope(bytes: ByteArray): ByteArray =
        byteArrayOf(TYPE_ENVELOPE) + bytes

    fun decode(raw: ByteArray): Frame? {
        if (raw.isEmpty()) return null
        return Frame(raw[0], raw.copyOfRange(1, raw.size))
    }
}

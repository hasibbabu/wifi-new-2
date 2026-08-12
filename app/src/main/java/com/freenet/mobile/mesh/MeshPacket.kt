package com.freenet.mobile.mesh

/**
 * Result of a single transport-level send attempt.
 *
 * (Earlier revisions of this file also declared a duplicate `MeshPacket`
 * wire format alongside `protocol.MeshEnvelope`. Encoding a packet twice,
 * with two different JSON schemas, meant the receiving side's
 * `MeshEnvelope.decode()` would reject every packet that arrived over a
 * transport that used the old `MeshPacket.toJson()` format. Transports now
 * send/receive `MeshEnvelope.encode()`/`decode()` bytes directly — see
 * FreeNetBridge.sendPacket().)
 */
data class SendResult(val success: Boolean, val detail: String)

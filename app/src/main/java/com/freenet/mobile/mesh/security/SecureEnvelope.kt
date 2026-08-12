package com.freenet.mobile.mesh.security

import android.util.Base64
import com.freenet.mobile.mesh.protocol.MeshEnvelope
import org.json.JSONObject

object SecureEnvelope {
    fun wrap(packet: MeshEnvelope, crypto: SessionCrypto, key: ByteArray): String {
        val aad = packet.packetId.toByteArray()
        val encrypted = crypto.encrypt(key, packet.body.toByteArray(), aad)
        return JSONObject().apply {
            put("packet", String(packet.encode(), Charsets.UTF_8))
            put("encrypted_body", Base64.encodeToString(encrypted, Base64.NO_WRAP))
        }.toString()
    }

    fun unwrap(
        value: String,
        crypto: SessionCrypto,
        key: ByteArray
    ): MeshEnvelope {
        val obj = JSONObject(value)
        val packet = MeshEnvelope.decode(obj.getString("packet").toByteArray())
        val encrypted = Base64.decode(obj.getString("encrypted_body"), Base64.DEFAULT)
        val body = crypto.decrypt(key, encrypted, packet.packetId.toByteArray())
        return packet.copy(body = String(body, Charsets.UTF_8))
    }
}

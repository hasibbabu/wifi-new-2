package com.freenet.mobile.mesh.security

import android.util.Base64
import java.security.KeyFactory
import java.security.Signature
import java.security.spec.X509EncodedKeySpec

object PeerVerifier {
    fun verify(
        publicKeyBase64: String,
        payload: ByteArray,
        signatureBase64: String
    ): Boolean = try {
        val keyBytes = Base64.decode(publicKeyBase64, Base64.DEFAULT)
        val key = KeyFactory.getInstance("EC").generatePublic(X509EncodedKeySpec(keyBytes))
        val sig = Signature.getInstance("SHA256withECDSA")
        sig.initVerify(key)
        sig.update(payload)
        sig.verify(Base64.decode(signatureBase64, Base64.DEFAULT))
    } catch (_: Exception) {
        false
    }
}

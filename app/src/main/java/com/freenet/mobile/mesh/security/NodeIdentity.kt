package com.freenet.mobile.mesh.security

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.Signature
import java.util.UUID

class NodeIdentity {

    private val alias = "freenet-node-signing-key"

    init {
        ensureKey()
    }

    private fun ensureKey() {
        val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        if (ks.containsAlias(alias)) return

        val generator = KeyPairGenerator.getInstance(
            KeyProperties.KEY_ALGORITHM_EC,
            "AndroidKeyStore"
        )
        generator.initialize(
            KeyGenParameterSpec.Builder(
                alias,
                KeyProperties.PURPOSE_SIGN or KeyProperties.PURPOSE_VERIFY
            )
                .setDigests(KeyProperties.DIGEST_SHA256)
                .build()
        )
        generator.generateKeyPair()
    }

    fun nodeId(): String {
        val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        val cert = ks.getCertificate(alias)
        return cert.publicKey.encoded.joinToString("") { "%02x".format(it) }.take(32)
    }

    fun publicKeyBase64(): String {
        val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        return android.util.Base64.encodeToString(
            ks.getCertificate(alias).publicKey.encoded,
            android.util.Base64.NO_WRAP
        )
    }

    fun sign(data: ByteArray): ByteArray {
        val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        val privateKey = ks.getKey(alias, null)
        val signature = Signature.getInstance("SHA256withECDSA")
        signature.initSign(privateKey as java.security.PrivateKey)
        signature.update(data)
        return signature.sign()
    }
}

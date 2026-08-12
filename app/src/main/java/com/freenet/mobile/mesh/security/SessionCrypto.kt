package com.freenet.mobile.mesh.security

import android.util.Base64
import java.nio.ByteBuffer
import java.security.KeyFactory
import java.security.KeyPairGenerator
import java.security.PrivateKey
import java.security.PublicKey
import java.security.spec.X509EncodedKeySpec
import javax.crypto.Cipher
import javax.crypto.KeyAgreement
import javax.crypto.Mac
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

class SessionCrypto {

    data class Session(
        val peerNodeId: String,
        val key: ByteArray
    )

    fun generateEphemeralPublicKey(): Pair<PrivateKey, String> {
        val gen = KeyPairGenerator.getInstance("EC")
        gen.initialize(256)
        val pair = gen.generateKeyPair()
        return pair.private to Base64.encodeToString(
            pair.public.encoded, Base64.NO_WRAP
        )
    }

    fun derive(privateKey: PrivateKey, peerPublicKeyBase64: String): ByteArray {
        val peerBytes = Base64.decode(peerPublicKeyBase64, Base64.DEFAULT)
        val peerKey: PublicKey = KeyFactory.getInstance("EC")
            .generatePublic(X509EncodedKeySpec(peerBytes))

        val agreement = KeyAgreement.getInstance("ECDH")
        agreement.init(privateKey)
        agreement.doPhase(peerKey, true)
        val shared = agreement.generateSecret()

        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(ByteArray(32), "HmacSHA256"))
        return mac.doFinal(shared).copyOf(32)
    }

    fun encrypt(key: ByteArray, plaintext: ByteArray, aad: ByteArray? = null): ByteArray {
        val iv = ByteArray(12)
        java.security.SecureRandom().nextBytes(iv)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(
            Cipher.ENCRYPT_MODE,
            SecretKeySpec(key.copyOf(32), "AES"),
            GCMParameterSpec(128, iv)
        )
        if (aad != null) cipher.updateAAD(aad)
        val ciphertext = cipher.doFinal(plaintext)
        return ByteBuffer.allocate(4 + iv.size + ciphertext.size)
            .putInt(iv.size)
            .put(iv)
            .put(ciphertext)
            .array()
    }

    fun decrypt(key: ByteArray, blob: ByteArray, aad: ByteArray? = null): ByteArray {
        val buffer = ByteBuffer.wrap(blob)
        val ivSize = buffer.int
        require(ivSize == 12)
        val iv = ByteArray(ivSize)
        buffer.get(iv)
        val ciphertext = ByteArray(buffer.remaining())
        buffer.get(ciphertext)

        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(
            Cipher.DECRYPT_MODE,
            SecretKeySpec(key.copyOf(32), "AES"),
            GCMParameterSpec(128, iv)
        )
        if (aad != null) cipher.updateAAD(aad)
        return cipher.doFinal(ciphertext)
    }
}

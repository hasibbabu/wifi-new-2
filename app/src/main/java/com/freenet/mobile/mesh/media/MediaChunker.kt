package com.freenet.mobile.mesh.media

import android.util.Base64
import com.freenet.mobile.mesh.protocol.MessageBody
import java.security.MessageDigest
import java.util.UUID

/**
 * Splits a photo/voice-note/file into a [MessageBody.FileMeta] announcement
 * followed by a sequence of [MessageBody.FileChunk] pieces, each one small
 * enough to fit inside a single MeshEnvelope (well under
 * FreeNetProtocol.MAX_PACKET_BYTES, leaving headroom for JSON/base64/BLE
 * fragmentation overhead further down the stack).
 */
object MediaChunker {

    /** Raw bytes per chunk before base64 (base64 inflates by ~4/3). */
    const val CHUNK_RAW_BYTES = 48 * 1024

    data class ChunkedFile(
        val fileId: String,
        val meta: MessageBody.FileMeta,
        val chunks: List<MessageBody.FileChunk>
    )

    fun chunk(
        bytes: ByteArray,
        fileName: String,
        mimeType: String,
        mediaKind: String
    ): ChunkedFile {
        val fileId = UUID.randomUUID().toString()
        val totalChunks = if (bytes.isEmpty()) 1 else (bytes.size + CHUNK_RAW_BYTES - 1) / CHUNK_RAW_BYTES
        val checksum = sha256(bytes)

        val chunks = (0 until totalChunks).map { index ->
            val start = index * CHUNK_RAW_BYTES
            val end = minOf(start + CHUNK_RAW_BYTES, bytes.size)
            val slice = if (end > start) bytes.copyOfRange(start, end) else ByteArray(0)
            MessageBody.FileChunk(
                fileId = fileId,
                chunkIndex = index,
                totalChunks = totalChunks,
                dataBase64 = Base64.encodeToString(slice, Base64.NO_WRAP)
            )
        }

        val meta = MessageBody.FileMeta(
            fileId = fileId,
            fileName = fileName,
            mimeType = mimeType,
            mediaKind = mediaKind,
            totalBytes = bytes.size,
            totalChunks = totalChunks,
            checksum = checksum
        )

        return ChunkedFile(fileId, meta, chunks)
    }

    fun sha256(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(bytes)
            .joinToString("") { "%02x".format(it) }
}

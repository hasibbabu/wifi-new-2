package com.freenet.mobile.mesh.media

import android.util.Base64
import com.freenet.mobile.mesh.protocol.MessageBody
import java.util.concurrent.ConcurrentHashMap

/**
 * Reassembles [MessageBody.FileMeta] + [MessageBody.FileChunk] sequences
 * arriving out of order (mesh hops can reorder/duplicate) back into a
 * complete file. One instance is shared per [com.freenet.mobile.mesh.FreeNetBridge].
 */
class MediaReassembler {

    data class CompletedFile(
        val fileId: String,
        val fileName: String,
        val mimeType: String,
        val mediaKind: String,
        val bytes: ByteArray,
        val checksumOk: Boolean
    )

    private data class Transfer(
        val meta: MessageBody.FileMeta,
        val parts: MutableMap<Int, ByteArray> = ConcurrentHashMap(),
        val startedAt: Long = System.currentTimeMillis()
    )

    private val inflight = ConcurrentHashMap<String, Transfer>()

    /** Call when a FILE_META envelope arrives. Safe to call more than once for the same fileId. */
    fun onMeta(meta: MessageBody.FileMeta) {
        inflight.computeIfAbsent(meta.fileId) { Transfer(meta) }
    }

    /**
     * Call when a FILE_CHUNK envelope arrives. Returns the completed file once
     * every chunk has been seen, or null while the transfer is still in progress.
     * If a chunk arrives before its FileMeta (out-of-order hop delivery), a
     * placeholder transfer is created from the chunk's own total_chunks field.
     */
    fun onChunk(chunk: MessageBody.FileChunk): CompletedFile? {
        val transfer = inflight.computeIfAbsent(chunk.fileId) {
            Transfer(
                MessageBody.FileMeta(
                    fileId = chunk.fileId,
                    fileName = "received_${chunk.fileId.take(8)}",
                    mimeType = "application/octet-stream",
                    mediaKind = "file",
                    totalBytes = -1,
                    totalChunks = chunk.totalChunks,
                    checksum = ""
                )
            )
        }

        transfer.parts[chunk.chunkIndex] = Base64.decode(chunk.dataBase64, Base64.NO_WRAP)

        if (transfer.parts.size < transfer.meta.totalChunks) return null

        val out = java.io.ByteArrayOutputStream()
        for (i in 0 until transfer.meta.totalChunks) {
            val part = transfer.parts[i] ?: return null // still missing a piece
            out.write(part)
        }
        inflight.remove(chunk.fileId)

        val bytes = out.toByteArray()
        val checksumOk = transfer.meta.checksum.isEmpty() ||
            transfer.meta.checksum == MediaChunker.sha256(bytes)

        return CompletedFile(
            fileId = transfer.meta.fileId,
            fileName = transfer.meta.fileName,
            mimeType = transfer.meta.mimeType,
            mediaKind = transfer.meta.mediaKind,
            bytes = bytes,
            checksumOk = checksumOk
        )
    }

    fun progress(fileId: String): Pair<Int, Int>? {
        val t = inflight[fileId] ?: return null
        return t.parts.size to t.meta.totalChunks
    }

    /** Drops transfers that have been incomplete for longer than [maxAgeMs] (default 10 minutes). */
    fun sweepStale(maxAgeMs: Long = 10 * 60 * 1000L) {
        val now = System.currentTimeMillis()
        inflight.entries.removeAll { now - it.value.startedAt > maxAgeMs }
    }
}

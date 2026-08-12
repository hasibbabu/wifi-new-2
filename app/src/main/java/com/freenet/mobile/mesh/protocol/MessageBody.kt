package com.freenet.mobile.mesh.protocol

import org.json.JSONObject

/**
 * Application-level message types carried inside [MeshEnvelope.body].
 *
 * MeshEnvelope/transport layers only know about routing (ttl, hop count,
 * source/destination). Everything about *what* is being sent lives here,
 * one level up, so new content types can be added without touching routing.
 */
object MessageType {
    const val TEXT = "text"
    const val FILE_META = "file_meta"
    const val FILE_CHUNK = "file_chunk"
    const val FILE_ACK = "file_ack"
    const val CALL_INVITE = "call_invite"
    const val CALL_ACCEPT = "call_accept"
    const val CALL_REJECT = "call_reject"
    const val CALL_END = "call_end"
    const val CALL_ENDPOINT = "call_endpoint"
}

/** Media kind for a file transfer, used for UI + storage routing. */
object MediaKind {
    const val IMAGE = "image"
    const val AUDIO = "audio"
    const val VIDEO = "video"
    const val FILE = "file"
}

/**
 * Thin, explicit wrapper around the JSON stored in [MeshEnvelope.body].
 * Kept deliberately simple (no reflection/serialization library) to match
 * the rest of this codebase's style and to avoid adding new dependencies.
 */
sealed class MessageBody {
    abstract fun toJson(): JSONObject
    fun encode(): String = toJson().toString()

    data class Text(
        val text: String
    ) : MessageBody() {
        override fun toJson() = JSONObject().apply {
            put("type", MessageType.TEXT)
            put("text", text)
        }
    }

    /** Announces an incoming file before any chunk data is sent. */
    data class FileMeta(
        val fileId: String,
        val fileName: String,
        val mimeType: String,
        val mediaKind: String,
        val totalBytes: Int,
        val totalChunks: Int,
        val checksum: String
    ) : MessageBody() {
        override fun toJson() = JSONObject().apply {
            put("type", MessageType.FILE_META)
            put("file_id", fileId)
            put("file_name", fileName)
            put("mime_type", mimeType)
            put("media_kind", mediaKind)
            put("total_bytes", totalBytes)
            put("total_chunks", totalChunks)
            put("checksum", checksum)
        }
    }

    /** One base64-encoded slice of a file. Order is given by [chunkIndex]. */
    data class FileChunk(
        val fileId: String,
        val chunkIndex: Int,
        val totalChunks: Int,
        val dataBase64: String
    ) : MessageBody() {
        override fun toJson() = JSONObject().apply {
            put("type", MessageType.FILE_CHUNK)
            put("file_id", fileId)
            put("chunk_index", chunkIndex)
            put("total_chunks", totalChunks)
            put("data", dataBase64)
        }
    }

    data class FileAck(
        val fileId: String,
        val receivedChunks: Int
    ) : MessageBody() {
        override fun toJson() = JSONObject().apply {
            put("type", MessageType.FILE_ACK)
            put("file_id", fileId)
            put("received_chunks", receivedChunks)
        }
    }

    /** Call signaling: setup/teardown only. Media never flows through the mesh. */
    data class CallSignal(
        val kind: String, // one of CALL_INVITE / CALL_ACCEPT / CALL_REJECT / CALL_END / CALL_ENDPOINT
        val callId: String,
        val withVideo: Boolean = false,
        val host: String? = null,
        val port: Int? = null,
        val reason: String? = null
    ) : MessageBody() {
        override fun toJson() = JSONObject().apply {
            put("type", kind)
            put("call_id", callId)
            put("with_video", withVideo)
            put("host", host)
            put("port", port)
            put("reason", reason)
        }
    }

    companion object {
        fun decode(raw: String): MessageBody {
            val j = JSONObject(raw)
            return when (val type = j.getString("type")) {
                MessageType.TEXT -> Text(j.getString("text"))
                MessageType.FILE_META -> FileMeta(
                    fileId = j.getString("file_id"),
                    fileName = j.getString("file_name"),
                    mimeType = j.getString("mime_type"),
                    mediaKind = j.optString("media_kind", MediaKind.FILE),
                    totalBytes = j.getInt("total_bytes"),
                    totalChunks = j.getInt("total_chunks"),
                    checksum = j.optString("checksum", "")
                )
                MessageType.FILE_CHUNK -> FileChunk(
                    fileId = j.getString("file_id"),
                    chunkIndex = j.getInt("chunk_index"),
                    totalChunks = j.getInt("total_chunks"),
                    dataBase64 = j.getString("data")
                )
                MessageType.FILE_ACK -> FileAck(
                    fileId = j.getString("file_id"),
                    receivedChunks = j.getInt("received_chunks")
                )
                MessageType.CALL_INVITE, MessageType.CALL_ACCEPT,
                MessageType.CALL_REJECT, MessageType.CALL_END,
                MessageType.CALL_ENDPOINT -> CallSignal(
                    kind = type,
                    callId = j.getString("call_id"),
                    withVideo = j.optBoolean("with_video", false),
                    host = if (j.isNull("host")) null else j.optString("host"),
                    port = if (j.isNull("port")) null else j.optInt("port"),
                    reason = if (j.isNull("reason")) null else j.optString("reason")
                )
                else -> throw IllegalArgumentException("Unknown message type: $type")
            }
        }
    }
}

package com.freenet.mobile.mesh.media

import android.content.Context
import android.net.Uri
import com.freenet.mobile.mesh.protocol.MediaKind
import java.io.File

/**
 * Persists received mesh media into app-specific external storage
 * (no runtime storage permission needed on API 26+: these directories are
 * private to the app and cleaned up automatically on uninstall).
 *
 * /Android/data/com.freenet.mobile/files/FreeNet/{images,voice,files}/
 */
class MediaStore(private val context: Context) {

    private fun dirFor(mediaKind: String): File {
        val sub = when (mediaKind) {
            MediaKind.IMAGE -> "images"
            MediaKind.AUDIO -> "voice"
            MediaKind.VIDEO -> "video"
            else -> "files"
        }
        val dir = File(context.getExternalFilesDir(null), "FreeNet/$sub")
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    fun save(completed: MediaReassembler.CompletedFile): File {
        val safeName = completed.fileName.replace(Regex("[^A-Za-z0-9._-]"), "_")
        val target = File(dirFor(completed.mediaKind), "${completed.fileId.take(8)}_$safeName")
        target.writeBytes(completed.bytes)
        return target
    }

    /** Reads bytes from a content:// or file:// Uri picked by the user (e.g. gallery picker). */
    fun readBytes(uri: Uri): ByteArray? = try {
        context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
    } catch (_: Exception) {
        null
    }

    fun newRecordingFile(): File {
        val dir = dirFor(MediaKind.AUDIO)
        return File(dir, "outgoing_${System.currentTimeMillis()}.m4a")
    }
}

package com.captord.screenrecorder.core.gallery

import com.captord.screenrecorder.core.storage.PendingOutputStore
import java.io.File
import java.security.MessageDigest

class GalleryRepository {

    fun listRecordings(): List<RecordingItem> {
        val dir = PendingOutputStore.resolveOutputDirectory(null)
        if (!dir.exists()) {
            dir.mkdirs()
            return emptyList()
        }
        return dir.listFiles()
            ?.asSequence()
            ?.filter { it.isFile && it.extension.equals("mp4", ignoreCase = true) }
            ?.map { file ->
                RecordingItem(
                    id = pathId(file.absolutePath),
                    displayName = file.name,
                    filePath = file.absolutePath,
                    fileSizeBytes = file.length(),
                    modifiedAtMs = file.lastModified(),
                )
            }
            ?.sortedByDescending { it.modifiedAtMs }
            ?.toList()
            .orEmpty()
    }

    fun fileExists(path: String): Boolean = File(path).isFile

    companion object {
        fun pathId(path: String): String {
            val digest = MessageDigest.getInstance("SHA-256").digest(path.toByteArray())
            return digest.take(8).joinToString("") { "%02x".format(it) }
        }
    }
}

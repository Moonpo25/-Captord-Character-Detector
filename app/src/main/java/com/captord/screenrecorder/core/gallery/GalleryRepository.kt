package com.captord.screenrecorder.core.gallery

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import com.captord.screenrecorder.core.storage.PendingOutputStore
import java.io.File
import java.security.MessageDigest

class GalleryRepository(private val context: Context? = null) {

    @Volatile
    private var customSaveUri: String? = null

    fun setCustomSaveUri(uri: String?) {
        customSaveUri = uri
    }

    fun listRecordings(): List<RecordingItem> {
        val dir = PendingOutputStore.resolveOutputDirectory(null)
        if (!dir.exists()) {
            dir.mkdirs()
            return emptyList()
        }
        val local = dir.listFiles()
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
        return (local + listCustomRecordings()).distinctBy { it.filePath }
            .sortedByDescending { it.modifiedAtMs }
    }

    private fun listCustomRecordings(): List<RecordingItem> {
        val resolver = context?.contentResolver ?: return emptyList()
        val tree = customSaveUri?.let(Uri::parse) ?: return emptyList()
        return try {
            val children = DocumentsContract.buildChildDocumentsUriUsingTree(
                tree,
                DocumentsContract.getTreeDocumentId(tree),
            )
            resolver.query(
                children,
                arrayOf(
                    DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                    DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                    DocumentsContract.Document.COLUMN_LAST_MODIFIED,
                    DocumentsContract.Document.COLUMN_SIZE,
                    DocumentsContract.Document.COLUMN_MIME_TYPE,
                ),
                "${DocumentsContract.Document.COLUMN_MIME_TYPE} = ?",
                arrayOf("video/mp4"),
                null,
            )?.use { cursor ->
                val result = mutableListOf<RecordingItem>()
                while (cursor.moveToNext()) {
                    val id = cursor.getString(0)
                    val uri = DocumentsContract.buildDocumentUriUsingTree(tree, id)
                    result += RecordingItem(
                        id = pathId(uri.toString()),
                        displayName = cursor.getString(1),
                        filePath = uri.toString(),
                        fileSizeBytes = cursor.getLong(3),
                        modifiedAtMs = cursor.getLong(2),
                    )
                }
                result
            }.orEmpty()
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun fileExists(path: String): Boolean = File(path).isFile

    companion object {
        fun pathId(path: String): String {
            val digest = MessageDigest.getInstance("SHA-256").digest(path.toByteArray())
            return digest.take(8).joinToString("") { "%02x".format(it) }
        }
    }
}

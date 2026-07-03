package com.captord.screenrecorder.core.storage

import android.content.Context
import android.media.MediaScannerConnection
import android.os.Environment
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object PendingOutputStore {
    private const val PENDING_DIR_NAME = ".pending"
    private const val CAPTORD_DIR_NAME = "Captord"

    fun resolveOutputDirectory(savePath: String?): File {
        val dir = if (!savePath.isNullOrBlank()) {
            File(savePath)
        } else {
            File(
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES),
                CAPTORD_DIR_NAME,
            )
        }
        if (!dir.exists()) {
            dir.mkdirs()
        }
        return dir
    }

    fun buildFinalFile(savePath: String?, filePrefix: String): File {
        val dir = resolveOutputDirectory(savePath)
        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val prefix = FilenameUtils.sanitizePrefix(filePrefix)
        return File(dir, "${prefix}_$timestamp.mp4")
    }

    fun pendingFileFor(finalFile: File): File {
        val pendingDir = File(finalFile.parentFile, PENDING_DIR_NAME)
        if (!pendingDir.exists()) {
            pendingDir.mkdirs()
        }
        val marker = File(pendingDir, ".nomedia")
        if (!marker.exists()) {
            try {
                marker.createNewFile()
            } catch (_: Exception) {
            }
        }
        return File(pendingDir, finalFile.name)
    }

    fun finalizePending(pendingPath: String?, finalPath: String?): Boolean {
        if (pendingPath.isNullOrBlank() || finalPath.isNullOrBlank()) return false
        val pending = File(pendingPath)
        val finalFile = File(finalPath)
        if (!pending.exists()) return false
        finalFile.parentFile?.mkdirs()
        if (pending.renameTo(finalFile)) return true
        return try {
            pending.copyTo(finalFile, overwrite = true)
            pending.delete()
            finalFile.exists()
        } catch (_: Exception) {
            false
        }
    }

    fun scanMedia(context: Context, path: String) {
        MediaScannerConnection.scanFile(context, arrayOf(path), null, null)
    }
}

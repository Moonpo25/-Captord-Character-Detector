package com.captord.screenrecorder.core.gallery

import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.util.Log
import java.io.File
import java.io.FileOutputStream

object VideoMetadataHelper {
    private const val TAG = "VideoMetadataHelper"

    data class Metadata(
        val durationMs: Long,
        val width: Int,
        val height: Int,
        val thumbnailPath: String?,
    )

    fun extract(context: Context, filePath: String): Metadata? {
        val file = File(filePath)
        if (!file.isFile) return null
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(filePath)
            val duration = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                ?.toLongOrNull() ?: 0L
            val width = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)
                ?.toIntOrNull() ?: 0
            val height = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)
                ?.toIntOrNull() ?: 0
            val thumbPath = captureThumbnail(context, retriever, file)
            Metadata(durationMs = duration, width = width, height = height, thumbnailPath = thumbPath)
        } catch (e: Exception) {
            Log.w(TAG, "Metadata failed for $filePath", e)
            null
        } finally {
            try {
                retriever.release()
            } catch (_: Exception) {
            }
        }
    }

    private fun captureThumbnail(
        context: Context,
        retriever: MediaMetadataRetriever,
        source: File,
    ): String? {
        return try {
            val frame = retriever.getFrameAtTime(0, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                ?: return null
            val dir = File(context.cacheDir, "thumbnails")
            if (!dir.exists()) dir.mkdirs()
            val outFile = File(dir, "${GalleryRepository.pathId(source.absolutePath)}_${source.lastModified()}.jpg")
            if (!outFile.exists()) {
                FileOutputStream(outFile).use { fos ->
                    frame.compress(Bitmap.CompressFormat.JPEG, 80, fos)
                }
            }
            if (!frame.isRecycled) frame.recycle()
            outFile.absolutePath
        } catch (e: Exception) {
            Log.w(TAG, "Thumbnail failed", e)
            null
        }
    }
}

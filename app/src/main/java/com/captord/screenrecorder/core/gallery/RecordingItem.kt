package com.captord.screenrecorder.core.gallery

data class RecordingItem(
    val id: String,
    val displayName: String,
    val filePath: String,
    val fileSizeBytes: Long,
    val modifiedAtMs: Long,
    val durationMs: Long? = null,
    val width: Int? = null,
    val height: Int? = null,
    val thumbnailPath: String? = null,
)

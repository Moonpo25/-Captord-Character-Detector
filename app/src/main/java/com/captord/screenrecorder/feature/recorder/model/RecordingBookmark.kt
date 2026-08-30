package com.captord.screenrecorder.feature.recorder.model

/**
 * A single user-bookmarked moment inside a recording. Stored in the
 * bookmark DataStore keyed by recording path; rendered as chips in the
 * preview screen so the user can seek back to the moment.
 *
 * Fields mirror the planning docs (DATA-008: RecordingBookmark).
 */
data class RecordingBookmark(
    val id: String,
    val recordingPath: String,
    val timestampMs: Long,
    val label: String? = null,
    val createdAt: Long,
)
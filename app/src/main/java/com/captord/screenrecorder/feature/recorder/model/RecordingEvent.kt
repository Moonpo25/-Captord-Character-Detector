package com.captord.screenrecorder.feature.recorder.model

sealed class RecordingEvent {
    data class TimerTick(
        val durationMs: Long,
        val fileSizeBytes: Long,
    ) : RecordingEvent()

    data class StateChanged(
        val state: RecordingSessionState,
    ) : RecordingEvent()

    data class Error(
        val code: String,
        val message: String,
        val recoverable: Boolean,
    ) : RecordingEvent()

    data class FileSegmentCreated(
        val path: String,
        val segmentIndex: Int,
        val fileSizeBytes: Long,
    ) : RecordingEvent()

    data class AudioFallback(
        val reason: String,
    ) : RecordingEvent()
}

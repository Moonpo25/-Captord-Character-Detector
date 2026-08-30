package com.captord.screenrecorder.feature.recorder.model

data class RecordingUiState(
    val sessionState: RecordingSessionState = RecordingSessionState.IDLE,
    val durationMs: Long = 0L,
    val fileSizeBytes: Long = 0L,
    val lastErrorCode: String? = null,
    val lastErrorMessage: String? = null,
    val lastFinalPath: String? = null,
    /** Snapshot of the recording config at the moment the last engine error fired. */
    val lastErrorContext: RecordingErrorContext? = null,
) {
    val isRecording: Boolean
        get() = sessionState == RecordingSessionState.RECORDING

    val isPaused: Boolean
        get() = sessionState == RecordingSessionState.PAUSED

    val isActive: Boolean
        get() = sessionState == RecordingSessionState.RECORDING ||
            sessionState == RecordingSessionState.PAUSED ||
            sessionState == RecordingSessionState.STOPPING
}

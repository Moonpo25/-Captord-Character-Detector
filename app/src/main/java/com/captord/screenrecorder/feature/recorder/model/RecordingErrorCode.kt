package com.captord.screenrecorder.feature.recorder.model

object RecordingErrorCode {
    const val PERMISSION_DENIED = "permission_denied"
    const val INVALID_CONFIG = "invalid_config"
    const val ALREADY_RECORDING = "already_recording"
    const val NOT_RECORDING = "not_recording"
    const val NOT_PAUSED = "not_paused"
    const val SERVICE_START_FAILED = "service_start_failed"
    const val PROJECTION_FAILED = "projection_failed"
    const val NOT_SUPPORTED = "not_supported"
    const val RESUME_FAILED = "resume_failed"
    const val FINALIZE_FAILED = "finalize_failed"
    const val CAPABILITY_CHECK_FAILED = "capability_check_failed"
    const val INVALID_ARGS = "invalid_args"
    const val LOW_STORAGE = "low_storage"
    const val AUDIO_UNAVAILABLE = "audio_unavailable"
    const val ENGINE_ERROR = "engine_error"
    const val SPLIT_ERROR = "split_error"
}

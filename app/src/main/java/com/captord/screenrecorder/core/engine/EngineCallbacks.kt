package com.captord.screenrecorder.core.engine

import com.captord.screenrecorder.feature.recorder.model.RecordingSessionState

interface EngineCallbacks {
    fun onStateChanged(state: RecordingSessionState)
    fun onTimerTick(durationMs: Long, fileSizeBytes: Long)
    fun onError(code: String, message: String, recoverable: Boolean)
    fun onFileSegmentCreated(path: String, segmentIndex: Int, fileSizeBytes: Long)
    fun onAudioFallback(reason: String)
}

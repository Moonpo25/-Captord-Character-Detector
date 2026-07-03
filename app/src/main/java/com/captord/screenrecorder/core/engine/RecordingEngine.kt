package com.captord.screenrecorder.core.engine

import android.content.Intent
import com.captord.screenrecorder.feature.recorder.model.RecordingConfig

interface RecordingEngine {
    fun start(config: RecordingConfig, resultCode: Int, projectionData: Intent)
    fun stop()
    fun pause()
    fun resume()
    fun release()
    fun isRecording(): Boolean
    fun isPaused(): Boolean
}

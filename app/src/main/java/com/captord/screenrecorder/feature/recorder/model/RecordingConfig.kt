package com.captord.screenrecorder.feature.recorder.model

import java.io.Serializable

data class RecordingConfig(
    val engineType: Int = ENGINE_STANDARD,
    val frameRate: Int = 30,
    val videoBitrate: Int = 8_000_000,
    val videoCodec: String = "H264",
    val audioEnabled: Boolean = true,
    val audioSource: String = "mic",
    val audioBitrate: Int = 128_000,
    val sampleRate: Int = 44_100,
    val audioChannel: Int = 1,
    val countdownSec: Int = 0,
    val maxFileSizeMb: Int = 0,
    val filePrefix: String = "Captord",
    val savePath: String? = null,
    val showOverlay: Boolean = false,
    val manualResolutionHeight: Int = 0,
    val orientationMode: String = "auto",
    val showTouches: Boolean = false,
    val customSaveUri: String? = null,
) : Serializable {

    fun validate(): ValidationResult {
        if (frameRate !in ALLOWED_FRAME_RATES) {
            return ValidationResult.Invalid("Unsupported frame rate: $frameRate")
        }
        if (videoBitrate !in MIN_VIDEO_BITRATE..MAX_VIDEO_BITRATE) {
            return ValidationResult.Invalid("Video bitrate out of range: $videoBitrate")
        }
        if (videoCodec != "H264" && videoCodec != "H265") {
            return ValidationResult.Invalid("Unsupported video codec: $videoCodec")
        }
        if (audioBitrate !in ALLOWED_AUDIO_BITRATES) {
            return ValidationResult.Invalid("Unsupported audio bitrate: $audioBitrate")
        }
        if (sampleRate != 44_100 && sampleRate != 48_000) {
            return ValidationResult.Invalid("Unsupported sample rate: $sampleRate")
        }
        if (audioChannel != 1 && audioChannel != 2) {
            return ValidationResult.Invalid("Unsupported audio channel count: $audioChannel")
        }
        if (orientationMode !in setOf("auto", "portrait", "landscape")) {
            return ValidationResult.Invalid("Unsupported orientation mode: $orientationMode")
        }
        if (engineType != ENGINE_STANDARD && engineType != ENGINE_ADVANCED) {
            return ValidationResult.Invalid("Unsupported engine type: $engineType")
        }
        return ValidationResult.Valid
    }

    sealed class ValidationResult {
        data object Valid : ValidationResult()
        data class Invalid(val reason: String) : ValidationResult()
    }

    companion object {
        const val ENGINE_STANDARD = 1
        const val ENGINE_ADVANCED = 2
        val ALLOWED_FRAME_RATES = setOf(24, 25, 30, 48, 60, 90, 120)
        val ALLOWED_AUDIO_BITRATES = setOf(64_000, 128_000, 192_000, 320_000)
        const val MIN_VIDEO_BITRATE = 2_000_000
        const val MAX_VIDEO_BITRATE = 40_000_000

        fun phase1Default(): RecordingConfig = RecordingConfig()
    }
}

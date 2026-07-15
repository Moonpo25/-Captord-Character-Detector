package com.captord.screenrecorder.core.settings

import com.captord.screenrecorder.core.storage.FilenameUtils
import com.captord.screenrecorder.feature.recorder.model.RecordingConfig

data class AppSettings(
    val engineType: Int = RecordingConfig.ENGINE_STANDARD,
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
    val showOverlay: Boolean = false,
    val manualResolutionHeight: Int = 0,
    val orientationMode: String = "auto",
    val showTouches: Boolean = false,
    val themeMode: String = THEME_SYSTEM,
    val dynamicColorEnabled: Boolean = true,
    val customSaveUri: String? = null,
) {
    fun toRecordingConfig(): RecordingConfig = RecordingConfig(
        engineType = engineType,
        frameRate = frameRate,
        videoBitrate = videoBitrate,
        videoCodec = videoCodec,
        audioEnabled = audioEnabled,
        audioSource = audioSource,
        audioBitrate = audioBitrate,
        sampleRate = sampleRate,
        audioChannel = audioChannel,
        countdownSec = countdownSec,
        maxFileSizeMb = maxFileSizeMb,
        filePrefix = FilenameUtils.sanitizePrefix(filePrefix),
        savePath = null,
        showOverlay = showOverlay,
        manualResolutionHeight = manualResolutionHeight,
        orientationMode = orientationMode,
        showTouches = showTouches,
    )

    companion object {
        const val THEME_SYSTEM = "system"
        const val THEME_LIGHT = "light"
        const val THEME_DARK = "dark"
        const val SAVE_LOCATION_DISPLAY = "Movies/Captord"

        val COUNTDOWN_OPTIONS = listOf(0, 3, 5, 10)
        val RESOLUTION_OPTIONS = listOf(0, 480, 720, 1080)
        val MAX_FILE_SIZE_OPTIONS = listOf(0, 100, 500, 1000)
    }
}

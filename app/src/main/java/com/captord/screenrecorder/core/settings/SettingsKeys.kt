package com.captord.screenrecorder.core.settings

import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey

object SettingsKeys {
    val engineType = intPreferencesKey("engine_type")
    val frameRate = intPreferencesKey("frame_rate")
    val videoBitrate = intPreferencesKey("video_bitrate")
    val videoCodec = stringPreferencesKey("video_codec")
    val audioEnabled = booleanPreferencesKey("audio_enabled")
    val audioSource = stringPreferencesKey("audio_source")
    val audioBitrate = intPreferencesKey("audio_bitrate")
    val sampleRate = intPreferencesKey("sample_rate")
    val audioChannel = intPreferencesKey("audio_channel")
    val countdownSec = intPreferencesKey("countdown_sec")
    val maxFileSizeMb = intPreferencesKey("max_file_size_mb")
    val filePrefix = stringPreferencesKey("file_prefix")
    val showOverlay = booleanPreferencesKey("show_overlay")
    val manualResolutionHeight = intPreferencesKey("manual_resolution_height")
    val orientationMode = stringPreferencesKey("orientation_mode")
    val showTouches = booleanPreferencesKey("show_touches")
    val hideOverlayFromCapture = booleanPreferencesKey("hide_overlay_from_capture")
    val themeMode = stringPreferencesKey("theme_mode")
    val dynamicColorEnabled = booleanPreferencesKey("dynamic_color_enabled")
    val customSaveUri = stringPreferencesKey("custom_save_uri")
}

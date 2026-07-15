package com.captord.screenrecorder.core.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import com.captord.screenrecorder.core.storage.FilenameUtils
import com.captord.screenrecorder.feature.recorder.model.RecordingConfig
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.captordSettingsDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "captord_settings",
)

class SettingsRepository(private val dataStore: DataStore<Preferences>) {

    val settings: Flow<AppSettings> = dataStore.data.map { prefs -> prefs.toAppSettings() }

    suspend fun update(transform: (AppSettings) -> AppSettings) {
        dataStore.edit { prefs ->
            val current = prefs.toAppSettings()
            prefs.writeAppSettings(transform(current))
        }
    }

    suspend fun setEngineType(value: Int) = update { it.copy(engineType = value) }
    suspend fun setFrameRate(value: Int) = update { it.copy(frameRate = value) }
    suspend fun setVideoBitrate(value: Int) = update {
        it.copy(
            videoBitrate = value.coerceIn(
                RecordingConfig.MIN_VIDEO_BITRATE,
                RecordingConfig.MAX_VIDEO_BITRATE,
            ),
        )
    }
    suspend fun setVideoCodec(value: String) = update { it.copy(videoCodec = value) }
    suspend fun setAudioEnabled(value: Boolean) = update { it.copy(audioEnabled = value) }
    suspend fun setAudioSource(value: String) = update { it.copy(audioSource = value) }
    suspend fun setAudioBitrate(value: Int) = update { it.copy(audioBitrate = value) }
    suspend fun setSampleRate(value: Int) = update { it.copy(sampleRate = value) }
    suspend fun setAudioChannel(value: Int) = update { it.copy(audioChannel = value) }
    suspend fun setCountdownSec(value: Int) = update { it.copy(countdownSec = value) }
    suspend fun setMaxFileSizeMb(value: Int) = update { it.copy(maxFileSizeMb = value) }
    suspend fun setFilePrefix(value: String) = update {
        it.copy(filePrefix = FilenameUtils.sanitizePrefix(value))
    }
    suspend fun setShowOverlay(value: Boolean) = update { it.copy(showOverlay = value) }
    suspend fun setManualResolutionHeight(value: Int) = update {
        it.copy(manualResolutionHeight = value)
    }
    suspend fun setOrientationMode(value: String) = update { it.copy(orientationMode = value) }
    suspend fun setShowTouches(value: Boolean) = update { it.copy(showTouches = value) }
    suspend fun setThemeMode(value: String) = update { it.copy(themeMode = value) }
    suspend fun setDynamicColorEnabled(value: Boolean) = update {
        it.copy(dynamicColorEnabled = value)
    }
    suspend fun setCustomSaveUri(value: String?) = update { it.copy(customSaveUri = value) }

    companion object {
        @Volatile
        private var instance: SettingsRepository? = null

        fun get(context: Context): SettingsRepository {
            return instance ?: synchronized(this) {
                instance ?: SettingsRepository(context.applicationContext.captordSettingsDataStore)
                    .also { instance = it }
            }
        }
    }
}

private fun Preferences.toAppSettings(): AppSettings {
    val defaults = AppSettings()
    return AppSettings(
        engineType = this[SettingsKeys.engineType] ?: defaults.engineType,
        frameRate = this[SettingsKeys.frameRate] ?: defaults.frameRate,
        videoBitrate = this[SettingsKeys.videoBitrate] ?: defaults.videoBitrate,
        videoCodec = this[SettingsKeys.videoCodec] ?: defaults.videoCodec,
        audioEnabled = this[SettingsKeys.audioEnabled] ?: defaults.audioEnabled,
        audioSource = this[SettingsKeys.audioSource] ?: defaults.audioSource,
        audioBitrate = this[SettingsKeys.audioBitrate] ?: defaults.audioBitrate,
        sampleRate = this[SettingsKeys.sampleRate] ?: defaults.sampleRate,
        audioChannel = this[SettingsKeys.audioChannel] ?: defaults.audioChannel,
        countdownSec = this[SettingsKeys.countdownSec] ?: defaults.countdownSec,
        maxFileSizeMb = this[SettingsKeys.maxFileSizeMb] ?: defaults.maxFileSizeMb,
        filePrefix = this[SettingsKeys.filePrefix] ?: defaults.filePrefix,
        showOverlay = this[SettingsKeys.showOverlay] ?: defaults.showOverlay,
        manualResolutionHeight = this[SettingsKeys.manualResolutionHeight]
            ?: defaults.manualResolutionHeight,
        orientationMode = this[SettingsKeys.orientationMode] ?: defaults.orientationMode,
        showTouches = this[SettingsKeys.showTouches] ?: defaults.showTouches,
        themeMode = this[SettingsKeys.themeMode] ?: defaults.themeMode,
        dynamicColorEnabled = this[SettingsKeys.dynamicColorEnabled]
            ?: defaults.dynamicColorEnabled,
        customSaveUri = this[SettingsKeys.customSaveUri] ?: defaults.customSaveUri,
    )
}

private fun MutablePreferences.writeAppSettings(settings: AppSettings) {
    this[SettingsKeys.engineType] = settings.engineType
    this[SettingsKeys.frameRate] = settings.frameRate
    this[SettingsKeys.videoBitrate] = settings.videoBitrate
    this[SettingsKeys.videoCodec] = settings.videoCodec
    this[SettingsKeys.audioEnabled] = settings.audioEnabled
    this[SettingsKeys.audioSource] = settings.audioSource
    this[SettingsKeys.audioBitrate] = settings.audioBitrate
    this[SettingsKeys.sampleRate] = settings.sampleRate
    this[SettingsKeys.audioChannel] = settings.audioChannel
    this[SettingsKeys.countdownSec] = settings.countdownSec
    this[SettingsKeys.maxFileSizeMb] = settings.maxFileSizeMb
    this[SettingsKeys.filePrefix] = settings.filePrefix
    this[SettingsKeys.showOverlay] = settings.showOverlay
    this[SettingsKeys.manualResolutionHeight] = settings.manualResolutionHeight
    this[SettingsKeys.orientationMode] = settings.orientationMode
    this[SettingsKeys.showTouches] = settings.showTouches
    this[SettingsKeys.themeMode] = settings.themeMode
    this[SettingsKeys.dynamicColorEnabled] = settings.dynamicColorEnabled
    if (settings.customSaveUri == null) remove(SettingsKeys.customSaveUri)
    else this[SettingsKeys.customSaveUri] = settings.customSaveUri
}

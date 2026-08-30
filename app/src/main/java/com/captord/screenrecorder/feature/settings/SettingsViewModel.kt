package com.captord.screenrecorder.feature.settings

import android.app.Application
import android.content.Intent
import android.provider.Settings
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.captord.screenrecorder.core.capability.CapabilityChecker
import com.captord.screenrecorder.core.overlay.FloatingOverlayManager
import com.captord.screenrecorder.core.settings.AppSettings
import com.captord.screenrecorder.core.settings.SettingsRepository
import com.captord.screenrecorder.feature.recorder.RecordingRepository
import com.captord.screenrecorder.feature.recorder.model.CapabilityReport
import com.captord.screenrecorder.feature.recorder.model.RecordingUiState
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SettingsViewModel(application: Application) : AndroidViewModel(application) {

    private val settingsRepository = SettingsRepository.get(application)
    private val recordingRepository = RecordingRepository.get(application)

    val settings: StateFlow<AppSettings> = settingsRepository.settings
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AppSettings())

    val capabilities: StateFlow<CapabilityReport> = kotlinx.coroutines.flow.flow {
        emit(CapabilityChecker.probe(application))
    }.flowOn(kotlinx.coroutines.Dispatchers.IO)
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5_000),
            CapabilityReport.probeMinimal(),
        )

    val recordingUiState: StateFlow<RecordingUiState> = recordingRepository.uiState

    val qualityLocked: StateFlow<Boolean> = recordingRepository.uiState
        .map { it.isActive }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    private val _overlayPermissionEvents = MutableSharedFlow<Intent>(extraBufferCapacity = 1)
    val overlayPermissionEvents: SharedFlow<Intent> = _overlayPermissionEvents.asSharedFlow()

    fun canDrawOverlays(): Boolean =
        FloatingOverlayManager.canDrawOverlays(getApplication())

    fun setShowOverlay(value: Boolean) {
        if (value && !canDrawOverlays()) {
            viewModelScope.launch {
                settingsRepository.setShowOverlay(false)
                _overlayPermissionEvents.tryEmit(
                    FloatingOverlayManager.createOverlaySettingsIntent(getApplication()),
                )
            }
            return
        }
        launchUpdate { settingsRepository.setShowOverlay(value) }
    }

    fun setEngineType(value: Int) = launchQualityUpdate { settingsRepository.setEngineType(value) }
    fun setFrameRate(value: Int) = launchQualityUpdate { settingsRepository.setFrameRate(value) }
    fun setVideoBitrate(value: Int) = launchQualityUpdate { settingsRepository.setVideoBitrate(value) }
    fun setVideoCodec(value: String) = launchQualityUpdate { settingsRepository.setVideoCodec(value) }
    fun setAudioEnabled(value: Boolean) = launchQualityUpdate { settingsRepository.setAudioEnabled(value) }
    fun setAudioSource(value: String) = launchQualityUpdate { settingsRepository.setAudioSource(value) }
    fun setAudioBitrate(value: Int) = launchQualityUpdate { settingsRepository.setAudioBitrate(value) }
    fun setSampleRate(value: Int) = launchQualityUpdate { settingsRepository.setSampleRate(value) }
    fun setAudioChannel(value: Int) = launchQualityUpdate { settingsRepository.setAudioChannel(value) }
    fun setCountdownSec(value: Int) = launchQualityUpdate { settingsRepository.setCountdownSec(value) }
    fun setMaxFileSizeMb(value: Int) = launchQualityUpdate { settingsRepository.setMaxFileSizeMb(value) }
    fun setFilePrefix(value: String) = launchQualityUpdate { settingsRepository.setFilePrefix(value) }
    fun setManualResolutionHeight(value: Int) = launchQualityUpdate {
        settingsRepository.setManualResolutionHeight(value)
    }
    fun setOrientationMode(value: String) = launchQualityUpdate {
        settingsRepository.setOrientationMode(value)
    }
    fun setShowTouches(value: Boolean) = launchQualityUpdate { settingsRepository.setShowTouches(value) }
    fun setHideOverlayFromCapture(value: Boolean) = launchUpdate {
        settingsRepository.setHideOverlayFromCapture(value)
    }
    fun setThemeMode(value: String) = launchUpdate { settingsRepository.setThemeMode(value) }
    fun setDynamicColorEnabled(value: Boolean) = launchUpdate {
        settingsRepository.setDynamicColorEnabled(value)
    }
    fun setCustomSaveUri(value: String?) = launchQualityUpdate {
        settingsRepository.setCustomSaveUri(value)
    }

    private fun launchQualityUpdate(block: suspend () -> Unit) {
        viewModelScope.launch {
            if (!allowQualitySettingsWrite(recordingRepository.uiState.value.isActive)) return@launch
            block()
        }
    }

    private fun launchUpdate(block: suspend () -> Unit) {
        viewModelScope.launch { block() }
    }
}

/** Output-affecting settings must not change while a recording session is active. */
fun allowQualitySettingsWrite(sessionActive: Boolean): Boolean = !sessionActive

package com.captord.screenrecorder.feature.settings

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.captord.screenrecorder.core.capability.CapabilityChecker
import com.captord.screenrecorder.core.settings.AppSettings
import com.captord.screenrecorder.core.settings.SettingsRepository
import com.captord.screenrecorder.feature.recorder.RecordingRepository
import com.captord.screenrecorder.feature.recorder.model.CapabilityReport
import com.captord.screenrecorder.feature.recorder.model.RecordingUiState
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SettingsViewModel(application: Application) : AndroidViewModel(application) {

    private val settingsRepository = SettingsRepository.get(application)
    private val recordingRepository = RecordingRepository.get(application)

    val settings: StateFlow<AppSettings> = settingsRepository.settings
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AppSettings())

    val capabilities: StateFlow<CapabilityReport> = kotlinx.coroutines.flow.flow {
        emit(CapabilityChecker.probe(application))
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        CapabilityReport.probeMinimal(),
    )

    val recordingUiState: StateFlow<RecordingUiState> = recordingRepository.uiState

    val qualityLocked: StateFlow<Boolean> = recordingRepository.uiState
        .map { it.isActive }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    fun setEngineType(value: Int) = launchUpdate { settingsRepository.setEngineType(value) }
    fun setFrameRate(value: Int) = launchUpdate { settingsRepository.setFrameRate(value) }
    fun setVideoBitrate(value: Int) = launchUpdate { settingsRepository.setVideoBitrate(value) }
    fun setVideoCodec(value: String) = launchUpdate { settingsRepository.setVideoCodec(value) }
    fun setAudioEnabled(value: Boolean) = launchUpdate { settingsRepository.setAudioEnabled(value) }
    fun setAudioSource(value: String) = launchUpdate { settingsRepository.setAudioSource(value) }
    fun setAudioBitrate(value: Int) = launchUpdate { settingsRepository.setAudioBitrate(value) }
    fun setSampleRate(value: Int) = launchUpdate { settingsRepository.setSampleRate(value) }
    fun setAudioChannel(value: Int) = launchUpdate { settingsRepository.setAudioChannel(value) }
    fun setCountdownSec(value: Int) = launchUpdate { settingsRepository.setCountdownSec(value) }
    fun setMaxFileSizeMb(value: Int) = launchUpdate { settingsRepository.setMaxFileSizeMb(value) }
    fun setFilePrefix(value: String) = launchUpdate { settingsRepository.setFilePrefix(value) }
    fun setShowOverlay(value: Boolean) = launchUpdate { settingsRepository.setShowOverlay(value) }
    fun setManualResolutionHeight(value: Int) = launchUpdate {
        settingsRepository.setManualResolutionHeight(value)
    }
    fun setOrientationMode(value: String) = launchUpdate {
        settingsRepository.setOrientationMode(value)
    }
    fun setShowTouches(value: Boolean) = launchUpdate { settingsRepository.setShowTouches(value) }
    fun setThemeMode(value: String) = launchUpdate { settingsRepository.setThemeMode(value) }
    fun setDynamicColorEnabled(value: Boolean) = launchUpdate {
        settingsRepository.setDynamicColorEnabled(value)
    }

    private fun launchUpdate(block: suspend () -> Unit) {
        viewModelScope.launch { block() }
    }
}

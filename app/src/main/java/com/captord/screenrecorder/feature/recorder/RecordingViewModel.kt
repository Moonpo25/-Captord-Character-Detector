package com.captord.screenrecorder.feature.recorder

import android.app.Application
import android.content.Intent
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.captord.screenrecorder.core.capability.CapabilityChecker
import com.captord.screenrecorder.core.settings.AppSettings
import com.captord.screenrecorder.core.settings.SettingsRepository
import com.captord.screenrecorder.feature.recorder.model.RecordingConfig
import com.captord.screenrecorder.feature.recorder.model.RecordingEvent
import com.captord.screenrecorder.feature.recorder.model.RecordingSessionState
import com.captord.screenrecorder.feature.recorder.model.RecordingUiState
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class RecordingViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = RecordingRepository.get(application)
    private val settingsRepository = SettingsRepository.get(application)

    private val _uiState = MutableStateFlow(RecordingUiState())
    val uiState: StateFlow<RecordingUiState> = _uiState.asStateFlow()

    val events: SharedFlow<RecordingEvent> = repository.events

    private val _countdownRemaining = MutableStateFlow(0)
    val countdownRemaining: StateFlow<Int> = _countdownRemaining.asStateFlow()

    private var countdownJob: Job? = null
    private var pendingAfterCountdown: (() -> Unit)? = null

    init {
        repository.bind()
        repository.uiState
            .onEach { serviceState ->
                // Preserve local countdown overlay unless service is active.
                if (serviceState.sessionState != RecordingSessionState.IDLE ||
                    _uiState.value.sessionState != RecordingSessionState.COUNTDOWN
                ) {
                    _uiState.value = serviceState
                } else if (serviceState.lastErrorMessage != null) {
                    _uiState.update {
                        it.copy(
                            lastErrorCode = serviceState.lastErrorCode,
                            lastErrorMessage = serviceState.lastErrorMessage,
                        )
                    }
                }
            }
            .launchIn(viewModelScope)
        events
            .onEach { event ->
                if (event is RecordingEvent.AudioFallback) {
                    _uiState.update {
                        it.copy(
                            lastErrorCode = "audio_fallback",
                            lastErrorMessage = event.reason,
                        )
                    }
                }
            }
            .launchIn(viewModelScope)
    }

    fun startRecording(config: RecordingConfig, resultCode: Int, projectionData: Intent) {
        cancelCountdown()
        repository.startRecording(config, resultCode, projectionData)
    }

    fun stopRecording() {
        cancelCountdown()
        repository.stopRecording()
    }

    fun pauseRecording() = repository.pauseRecording()

    fun resumeRecording() = repository.resumeRecording()

    fun notifyProjectionDenied() {
        cancelCountdown()
        repository.notifyProjectionDenied()
        _uiState.update {
            it.copy(
                sessionState = RecordingSessionState.IDLE,
                lastErrorCode = "projection_failed",
                lastErrorMessage = "Screen capture consent was denied",
            )
        }
    }

    fun currentRecordingConfig(): RecordingConfig {
        // Synchronous best-effort: use last known via blocking first in coroutine callers.
        return AppSettings().toRecordingConfig()
    }

    suspend fun loadRecordingConfig(): RecordingConfig {
        val settings = settingsRepository.settings.first()
        val capability = CapabilityChecker.probe(getApplication())
        return CapabilityChecker.clampConfig(settings.toRecordingConfig(), capability)
    }

    /**
     * Runtime perms OK → optional countdown → invoke [onReadyForProjection].
     */
    fun beginStartSequence(onReadyForProjection: () -> Unit) {
        if (_uiState.value.isActive || _uiState.value.sessionState == RecordingSessionState.COUNTDOWN) {
            return
        }
        countdownJob?.cancel()
        viewModelScope.launch {
            val settings = settingsRepository.settings.first()
            val seconds = settings.countdownSec
            if (seconds <= 0) {
                onReadyForProjection()
                return@launch
            }
            _uiState.update { it.copy(sessionState = RecordingSessionState.COUNTDOWN) }
            _countdownRemaining.value = seconds
            pendingAfterCountdown = onReadyForProjection
            countdownJob = viewModelScope.launch {
                for (remaining in seconds downTo 1) {
                    _countdownRemaining.value = remaining
                    delay(1000)
                }
                _countdownRemaining.value = 0
                _uiState.update {
                    if (it.sessionState == RecordingSessionState.COUNTDOWN) {
                        it.copy(sessionState = RecordingSessionState.IDLE)
                    } else {
                        it
                    }
                }
                val action = pendingAfterCountdown
                pendingAfterCountdown = null
                action?.invoke()
            }
        }
    }

    fun cancelCountdown() {
        countdownJob?.cancel()
        countdownJob = null
        pendingAfterCountdown = null
        _countdownRemaining.value = 0
        if (_uiState.value.sessionState == RecordingSessionState.COUNTDOWN) {
            _uiState.update { it.copy(sessionState = RecordingSessionState.IDLE) }
        }
    }
}

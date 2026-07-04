package com.captord.screenrecorder.feature.recorder

import android.app.Application
import android.content.Intent
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.captord.screenrecorder.feature.recorder.model.RecordingConfig
import com.captord.screenrecorder.feature.recorder.model.RecordingEvent
import com.captord.screenrecorder.feature.recorder.model.RecordingUiState
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach

class RecordingViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = RecordingRepository.get(application)

    val uiState: StateFlow<RecordingUiState> = repository.uiState
    val events: SharedFlow<RecordingEvent> = repository.events

    init {
        repository.bind()
        events.onEach { /* consumers collect in UI */ }.launchIn(viewModelScope)
    }

    fun startRecording(config: RecordingConfig, resultCode: Int, projectionData: Intent) {
        repository.startRecording(config, resultCode, projectionData)
    }

    fun stopRecording() = repository.stopRecording()

    fun pauseRecording() = repository.pauseRecording()

    fun resumeRecording() = repository.resumeRecording()

    fun notifyProjectionDenied() = repository.notifyProjectionDenied()

    fun phase1Config(): RecordingConfig = RecordingConfig.phase1Default()
}

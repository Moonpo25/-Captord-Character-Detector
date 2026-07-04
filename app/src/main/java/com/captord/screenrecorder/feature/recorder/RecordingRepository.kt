package com.captord.screenrecorder.feature.recorder

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import com.captord.screenrecorder.core.service.ScreenRecorderService
import com.captord.screenrecorder.feature.recorder.model.CapabilityReport
import com.captord.screenrecorder.feature.recorder.model.RecordingConfig
import com.captord.screenrecorder.feature.recorder.model.RecordingErrorCode
import com.captord.screenrecorder.feature.recorder.model.RecordingEvent
import com.captord.screenrecorder.feature.recorder.model.RecordingSessionState
import com.captord.screenrecorder.feature.recorder.model.RecordingUiState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class RecordingRepository(private val appContext: Context) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val _uiState = MutableStateFlow(RecordingUiState())
    val uiState: StateFlow<RecordingUiState> = _uiState.asStateFlow()

    private val _events = MutableSharedFlow<RecordingEvent>(extraBufferCapacity = 64)
    val events: SharedFlow<RecordingEvent> = _events.asSharedFlow()

    private var boundService: ScreenRecorderService? = null
    private var stateCollectJob: Job? = null
    private var eventCollectJob: Job? = null

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            val binder = service as? ScreenRecorderService.LocalBinder ?: return
            val recorderService = binder.getService()
            boundService = recorderService
            stateCollectJob?.cancel()
            eventCollectJob?.cancel()
            stateCollectJob = scope.launch {
                recorderService.uiState.collect { _uiState.value = it }
            }
            eventCollectJob = scope.launch {
                recorderService.events.collect { _events.emit(it) }
            }
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            boundService = null
            stateCollectJob?.cancel()
            eventCollectJob?.cancel()
        }
    }

    fun bind() {
        val intent = Intent(appContext, ScreenRecorderService::class.java)
        appContext.bindService(intent, connection, Context.BIND_AUTO_CREATE)
    }

    fun unbind() {
        stateCollectJob?.cancel()
        eventCollectJob?.cancel()
        try {
            appContext.unbindService(connection)
        } catch (_: Exception) {
        }
        boundService = null
    }

    fun getRecordingState(): RecordingUiState = _uiState.value

    fun checkCapabilities(): CapabilityReport = CapabilityReport.probeMinimal()

    fun startRecording(config: RecordingConfig, resultCode: Int, projectionData: Intent) {
        when (val validation = config.validate()) {
            is RecordingConfig.ValidationResult.Invalid -> {
                emitLocalError(
                    RecordingErrorCode.INVALID_CONFIG,
                    validation.reason,
                    false,
                )
                return
            }
            RecordingConfig.ValidationResult.Valid -> Unit
        }

        val current = _uiState.value
        if (current.isActive) {
            emitLocalError(
                RecordingErrorCode.ALREADY_RECORDING,
                "A recording session is already active",
                true,
            )
            return
        }

        try {
            ScreenRecorderService.startRecording(appContext, config, resultCode, projectionData)
            ensureBound()
        } catch (e: Exception) {
            emitLocalError(
                RecordingErrorCode.SERVICE_START_FAILED,
                "Failed to start recording service: ${e.message}",
                false,
            )
        }
    }

    fun stopRecording() {
        if (!_uiState.value.isActive && boundService == null && ScreenRecorderService.instance == null) {
            emitLocalError(
                RecordingErrorCode.NOT_RECORDING,
                "No active recording to stop",
                true,
            )
            return
        }
        ScreenRecorderService.sendAction(appContext, ScreenRecorderService.ACTION_STOP)
    }

    fun pauseRecording() {
        if (!_uiState.value.isRecording) {
            emitLocalError(
                RecordingErrorCode.NOT_RECORDING,
                "No active recording to pause",
                true,
            )
            return
        }
        ScreenRecorderService.sendAction(appContext, ScreenRecorderService.ACTION_PAUSE)
    }

    fun resumeRecording() {
        if (!_uiState.value.isPaused) {
            emitLocalError(
                RecordingErrorCode.NOT_PAUSED,
                "Recording is not paused",
                true,
            )
            return
        }
        ScreenRecorderService.sendAction(appContext, ScreenRecorderService.ACTION_RESUME)
    }

    fun notifyProjectionDenied() {
        emitLocalError(
            RecordingErrorCode.PROJECTION_FAILED,
            "Screen capture consent was denied",
            true,
        )
        _uiState.value = _uiState.value.copy(sessionState = RecordingSessionState.IDLE)
    }

    private fun ensureBound() {
        if (boundService == null) {
            bind()
        }
    }

    private fun emitLocalError(code: String, message: String, recoverable: Boolean) {
        _uiState.value = _uiState.value.copy(
            lastErrorCode = code,
            lastErrorMessage = message,
        )
        _events.tryEmit(RecordingEvent.Error(code, message, recoverable))
    }

    companion object {
        @Volatile
        private var instance: RecordingRepository? = null

        fun get(context: Context): RecordingRepository {
            return instance ?: synchronized(this) {
                instance ?: RecordingRepository(context.applicationContext).also {
                    instance = it
                    it.bind()
                }
            }
        }
    }
}

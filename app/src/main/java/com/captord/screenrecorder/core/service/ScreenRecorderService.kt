package com.captord.screenrecorder.core.service

import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Binder
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import android.util.Log
import androidx.core.content.ContextCompat
import com.captord.screenrecorder.core.engine.EngineCallbacks
import com.captord.screenrecorder.core.engine.RecordingEngine
import com.captord.screenrecorder.core.engine.StandardRecorderEngine
import com.captord.screenrecorder.feature.recorder.model.RecordingConfig
import com.captord.screenrecorder.feature.recorder.model.RecordingErrorCode
import com.captord.screenrecorder.feature.recorder.model.RecordingEvent
import com.captord.screenrecorder.feature.recorder.model.RecordingSessionState
import com.captord.screenrecorder.feature.recorder.model.RecordingUiState
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

class ScreenRecorderService : Service() {

    private val binder = LocalBinder()
    private lateinit var notificationController: RecordingNotificationController
    private var engine: RecordingEngine? = null
    private var currentConfig: RecordingConfig? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private val mainHandler = Handler(Looper.getMainLooper())

    private val _uiState = MutableStateFlow(RecordingUiState())
    val uiState: StateFlow<RecordingUiState> = _uiState.asStateFlow()

    private val _events = MutableSharedFlow<RecordingEvent>(extraBufferCapacity = 64)
    val events: SharedFlow<RecordingEvent> = _events.asSharedFlow()

    inner class LocalBinder : Binder() {
        fun getService(): ScreenRecorderService = this@ScreenRecorderService
    }

    override fun onBind(intent: Intent?): IBinder = binder

    override fun onCreate() {
        super.onCreate()
        notificationController = RecordingNotificationController(this)
        instance = this
        Log.i(TAG, "Service created")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action
        // REQ-037: startForeground must run before getMediaProjection / engine start.
        notificationController.showRecordingNotification()

        when (action) {
            ACTION_START -> handleStart(intent)
            ACTION_STOP -> handleStop(showCompletion = true)
            ACTION_PAUSE -> {
                engine?.pause()
                notificationController.updateNotificationText("Recording paused", isPaused = true)
            }
            ACTION_RESUME -> {
                engine?.resume()
                notificationController.updateNotificationText("Recording in progress", isPaused = false)
            }
            else -> {
                Log.w(TAG, "Unknown action: $action")
                notificationController.stopNotification()
                stopSelf()
            }
        }
        return START_NOT_STICKY
    }

    private fun handleStart(intent: Intent?) {
        val projectionData = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent?.getParcelableExtra(EXTRA_PROJECTION_DATA, Intent::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent?.getParcelableExtra(EXTRA_PROJECTION_DATA)
        }
        val resultCode = intent?.getIntExtra(EXTRA_RESULT_CODE, 0) ?: 0
        val config = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent?.getSerializableExtra(EXTRA_CONFIG, RecordingConfig::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent?.getSerializableExtra(EXTRA_CONFIG) as? RecordingConfig
        } ?: RecordingConfig.phase1Default()

        if (projectionData == null || resultCode != android.app.Activity.RESULT_OK) {
            emitError(
                RecordingErrorCode.PROJECTION_FAILED,
                "Missing MediaProjection consent data",
                false,
            )
            cleanupAndStop(showCompletion = false)
            return
        }

        if (config.audioEnabled &&
            ContextCompat.checkSelfPermission(this, android.Manifest.permission.RECORD_AUDIO) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            emitError(
                RecordingErrorCode.PERMISSION_DENIED,
                "RECORD_AUDIO permission is required",
                true,
            )
            cleanupAndStop(showCompletion = false)
            return
        }

        currentConfig = config
        val callbacks = object : EngineCallbacks {
            override fun onStateChanged(state: RecordingSessionState) {
                mainHandler.post {
                    _uiState.update { it.copy(sessionState = state) }
                    emitEvent(RecordingEvent.StateChanged(state))
                    when (state) {
                        RecordingSessionState.RECORDING ->
                            notificationController.updateNotificationText(
                                "Recording in progress",
                                isPaused = false,
                            )
                        RecordingSessionState.PAUSED ->
                            notificationController.updateNotificationText(
                                "Recording paused",
                                isPaused = true,
                            )
                        RecordingSessionState.IDLE, RecordingSessionState.ERROR -> {
                            // stop handled by command path
                        }
                        else -> Unit
                    }
                }
            }

            override fun onTimerTick(durationMs: Long, fileSizeBytes: Long) {
                mainHandler.post {
                    _uiState.update {
                        it.copy(durationMs = durationMs, fileSizeBytes = fileSizeBytes)
                    }
                    emitEvent(RecordingEvent.TimerTick(durationMs, fileSizeBytes))
                    val text = formatDuration(durationMs)
                    notificationController.updateNotificationText(
                        "Recording: $text",
                        isPaused = false,
                    )
                }
            }

            override fun onError(code: String, message: String, recoverable: Boolean) {
                mainHandler.post {
                    _uiState.update {
                        it.copy(
                            lastErrorCode = code,
                            lastErrorMessage = message,
                            sessionState = if (!recoverable) {
                                RecordingSessionState.ERROR
                            } else {
                                it.sessionState
                            },
                        )
                    }
                    emitEvent(RecordingEvent.Error(code, message, recoverable))
                    if (!recoverable) {
                        cleanupAndStop(showCompletion = false)
                    }
                }
            }

            override fun onFileSegmentCreated(path: String, segmentIndex: Int, fileSizeBytes: Long) {
                mainHandler.post {
                    _uiState.update { it.copy(lastFinalPath = path, fileSizeBytes = fileSizeBytes) }
                    emitEvent(RecordingEvent.FileSegmentCreated(path, segmentIndex, fileSizeBytes))
                }
            }

            override fun onAudioFallback(reason: String) {
                mainHandler.post {
                    emitEvent(RecordingEvent.AudioFallback(reason))
                }
            }
        }

        if (engine == null) {
            engine = StandardRecorderEngine(this, callbacks)
        }
        acquireWakeLock()
        try {
            engine?.start(config, resultCode, projectionData)
        } catch (e: Exception) {
            Log.e(TAG, "Engine start failed", e)
            emitError(
                RecordingErrorCode.ENGINE_ERROR,
                "Engine start failed: ${e.message}",
                false,
            )
            cleanupAndStop(showCompletion = false)
        }
    }

    private fun handleStop(showCompletion: Boolean) {
        try {
            engine?.stop()
        } catch (e: Exception) {
            Log.e(TAG, "Stop failed", e)
        }
        try {
            engine?.release()
        } catch (_: Exception) {
        }
        engine = null
        releaseWakeLock()
        notificationController.stopNotification()
        if (showCompletion && !_uiState.value.lastFinalPath.isNullOrBlank()) {
            notificationController.showCompletionNotification()
        }
        _uiState.update {
            it.copy(
                sessionState = RecordingSessionState.IDLE,
                durationMs = 0L,
            )
        }
        emitEvent(RecordingEvent.StateChanged(RecordingSessionState.IDLE))
        stopSelf()
    }

    private fun cleanupAndStop(showCompletion: Boolean) {
        try {
            engine?.release()
        } catch (_: Exception) {
        }
        engine = null
        releaseWakeLock()
        notificationController.stopNotification()
        if (showCompletion) {
            notificationController.showCompletionNotification()
        }
        stopSelf()
    }

    private fun emitError(code: String, message: String, recoverable: Boolean) {
        _uiState.update {
            it.copy(lastErrorCode = code, lastErrorMessage = message)
        }
        emitEvent(RecordingEvent.Error(code, message, recoverable))
    }

    private fun emitEvent(event: RecordingEvent) {
        _events.tryEmit(event)
    }

    private fun acquireWakeLock() {
        if (wakeLock?.isHeld == true) return
        val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "Captord:RecordingWakeLock").apply {
            setReferenceCounted(false)
            acquire(10 * 60 * 60 * 1000L)
        }
    }

    private fun releaseWakeLock() {
        try {
            if (wakeLock?.isHeld == true) {
                wakeLock?.release()
            }
        } catch (_: Exception) {
        }
        wakeLock = null
    }

    override fun onDestroy() {
        releaseWakeLock()
        try {
            engine?.release()
        } catch (_: Exception) {
        }
        engine = null
        if (instance === this) {
            instance = null
        }
        super.onDestroy()
        Log.i(TAG, "Service destroyed")
    }

    companion object {
        private const val TAG = "ScreenRecorderService"
        const val ACTION_START = "com.captord.screenrecorder.ACTION_START"
        const val ACTION_STOP = "com.captord.screenrecorder.ACTION_STOP"
        const val ACTION_PAUSE = "com.captord.screenrecorder.ACTION_PAUSE"
        const val ACTION_RESUME = "com.captord.screenrecorder.ACTION_RESUME"
        const val EXTRA_CONFIG = "extra_config"
        const val EXTRA_PROJECTION_DATA = "extra_projection_data"
        const val EXTRA_RESULT_CODE = "extra_result_code"

        @Volatile
        var instance: ScreenRecorderService? = null
            private set

        fun startRecording(
            context: Context,
            config: RecordingConfig,
            resultCode: Int,
            projectionData: Intent,
        ) {
            val intent = Intent(context, ScreenRecorderService::class.java).apply {
                action = ACTION_START
                putExtra(EXTRA_CONFIG, config)
                putExtra(EXTRA_RESULT_CODE, resultCode)
                putExtra(EXTRA_PROJECTION_DATA, projectionData)
            }
            ContextCompat.startForegroundService(context, intent)
        }

        fun sendAction(context: Context, action: String) {
            val intent = Intent(context, ScreenRecorderService::class.java).apply {
                this.action = action
            }
            context.startService(intent)
        }

        private fun formatDuration(durationMs: Long): String {
            val totalSeconds = durationMs / 1000
            val seconds = totalSeconds % 60
            val minutes = (totalSeconds / 60) % 60
            val hours = totalSeconds / 3600
            return if (hours > 0) {
                String.format("%02d:%02d:%02d", hours, minutes, seconds)
            } else {
                String.format("%02d:%02d", minutes, seconds)
            }
        }
    }
}

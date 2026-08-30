package com.captord.screenrecorder.core.engine

import android.content.Context
import android.content.Intent
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.MediaRecorder
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.util.DisplayMetrics
import android.util.Log
import android.view.WindowManager
import com.captord.screenrecorder.core.storage.PendingOutputStore
import com.captord.screenrecorder.feature.recorder.model.RecordingConfig
import com.captord.screenrecorder.feature.recorder.model.RecordingErrorCode
import com.captord.screenrecorder.feature.recorder.model.RecordingSessionState
import java.io.File
import java.util.Locale

class StandardRecorderEngine(
    private val context: Context,
    private val callbacks: EngineCallbacks,
) : RecordingEngine {

    private var mediaRecorder: MediaRecorder? = null
    private var projection: MediaProjection? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var recordingState = RecordingSessionState.IDLE
    private var currentConfig: RecordingConfig? = null
    private var currentFilePath: String? = null
    private var currentFinalPath: String? = null
    private var startTimeMs: Long = 0
    private var pausedAccumulatedMs: Long = 0
    private var pauseStartedMs: Long = 0
    private var fileSizeBytes: Long = 0
    private var timerThread: Thread? = null
    @Volatile
    private var isTimerRunning = false
    private var segmentIndex = 0
    private var screenWidth = 0
    private var screenHeight = 0
    private var screenDpi = 0
    private var lastAudioSourceUsed: Int? = null
    private var projectionCallback: MediaProjection.Callback? = null
    @Volatile
    private var isStopping = false

    override fun start(config: RecordingConfig, resultCode: Int, projectionData: Intent) {
        if (recordingState == RecordingSessionState.RECORDING ||
            recordingState == RecordingSessionState.PAUSED
        ) {
            Log.w(TAG, "Already recording, ignoring start")
            return
        }

        currentConfig = config
        segmentIndex = 0
        pausedAccumulatedMs = 0
        pauseStartedMs = 0
        isStopping = false

        val wm = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val metrics = DisplayMetrics()
        @Suppress("DEPRECATION")
        wm.defaultDisplay.getRealMetrics(metrics)
        val nativeWidth = metrics.widthPixels
        val nativeHeight = metrics.heightPixels
        screenDpi = metrics.densityDpi

        val targetSize = resolveTargetSize(
            nativeWidth = nativeWidth,
            nativeHeight = nativeHeight,
            manualShortSide = config.manualResolutionHeight,
            orientationMode = config.orientationMode,
            alignment = 2,
        )
        screenWidth = targetSize.width
        screenHeight = targetSize.height

        try {
            val projectionManager =
                context.getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
            @Suppress("DEPRECATION")
            projection = projectionManager.getMediaProjection(resultCode, projectionData)
            if (projection == null) {
                callbacks.onError(
                    RecordingErrorCode.PROJECTION_FAILED,
                    "Failed to create MediaProjection",
                    false,
                )
                return
            }

            val callback = object : MediaProjection.Callback() {
                override fun onStop() {
                    Log.i(TAG, "MediaProjection stopped")
                    if (isStopping) return
                    if (recordingState == RecordingSessionState.RECORDING ||
                        recordingState == RecordingSessionState.PAUSED
                    ) {
                        stop()
                    } else {
                        releaseCaptureResources(stopProjection = false)
                        callbacks.onStateChanged(RecordingSessionState.IDLE)
                    }
                }
            }
            projectionCallback = callback
            projection?.registerCallback(callback, null)

            val recorder = buildAndPrepareRecorder(config)
            mediaRecorder = recorder
            val surface = recorder.surface

            virtualDisplay = projection?.createVirtualDisplay(
                "Captord",
                screenWidth,
                screenHeight,
                screenDpi,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                surface,
                null,
                null,
            )

            recorder.start()
            recordingState = RecordingSessionState.RECORDING
            startTimeMs = System.currentTimeMillis()
            callbacks.onStateChanged(RecordingSessionState.RECORDING)
            startTimer()
            Log.i(TAG, "Recording started: $currentFilePath (${screenWidth}x$screenHeight)")
        } catch (e: SecurityException) {
            Log.e(TAG, "SecurityException starting recorder", e)
            recordingState = RecordingSessionState.ERROR
            callbacks.onError(
                RecordingErrorCode.PERMISSION_DENIED,
                "RECORD_AUDIO permission is required for audio. Grant permission or disable audio.",
                true,
            )
            releaseCaptureResources(stopProjection = true)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start recording", e)
            recordingState = RecordingSessionState.ERROR
            callbacks.onError(
                RecordingErrorCode.ENGINE_ERROR,
                CodecErrorFormatter.format(e, "Failed to start recording"),
                false,
            )
            releaseCaptureResources(stopProjection = true)
        }
    }

    private fun buildAndPrepareRecorder(config: RecordingConfig): MediaRecorder {
        val outputFile = PendingOutputStore.buildFinalFile(config.savePath, config.filePrefix)
        currentFinalPath = outputFile.absolutePath
        val tempFile = PendingOutputStore.pendingFileFor(outputFile)
        currentFilePath = tempFile.absolutePath

        val recorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            MediaRecorder(context)
        } else {
            @Suppress("DEPRECATION")
            MediaRecorder()
        }

        recorder.apply {
            setVideoSource(MediaRecorder.VideoSource.SURFACE)
            if (config.audioEnabled) {
                val sourceUsed = applyAudioSourceWithFallback(this)
                if (sourceUsed == null) {
                    Log.w(TAG, "All audio sources failed — recording without audio")
                    callbacks.onAudioFallback("Audio not available — recording video only")
                    callbacks.onError(
                        RecordingErrorCode.AUDIO_UNAVAILABLE,
                        "Audio not available — recording video only",
                        true,
                    )
                }
                lastAudioSourceUsed = sourceUsed
            } else {
                lastAudioSourceUsed = null
            }
            setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            setVideoEncoder(
                if (config.videoCodec == "H265" && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    MediaRecorder.VideoEncoder.HEVC
                } else {
                    MediaRecorder.VideoEncoder.H264
                },
            )
            if (config.audioEnabled && lastAudioSourceUsed != null) {
                try {
                    setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                    setAudioSamplingRate(config.sampleRate)
                    setAudioEncodingBitRate(config.audioBitrate)
                    setAudioChannels(config.audioChannel)
                } catch (e: IllegalStateException) {
                    Log.w(TAG, "Cannot configure audio encoder", e)
                }
            }
            setVideoFrameRate(config.frameRate)
            setVideoSize(screenWidth, screenHeight)
            setVideoEncodingBitRate(config.videoBitrate)
            setOutputFile(tempFile.absolutePath)
            if (config.maxFileSizeMb > 0) {
                setMaxFileSize(config.maxFileSizeMb.toLong() * 1024 * 1024)
            }
            setOnInfoListener { _, what, _ ->
                if (what == MediaRecorder.MEDIA_RECORDER_INFO_MAX_FILESIZE_REACHED) {
                    splitSegment()
                }
            }
            prepare()
        }
        return recorder
    }

    private fun applyAudioSourceWithFallback(recorder: MediaRecorder): Int? {
        val candidates = listOf(
            MediaRecorder.AudioSource.MIC to "MIC",
            MediaRecorder.AudioSource.VOICE_COMMUNICATION to "VOICE_COMMUNICATION",
            MediaRecorder.AudioSource.CAMCORDER to "CAMCORDER",
        )
        for ((source, label) in candidates) {
            try {
                recorder.setAudioSource(source)
                Log.i(TAG, "Audio source set ($label)")
                return source
            } catch (e: Exception) {
                Log.w(TAG, "Audio source $label rejected", e)
            }
        }
        return null
    }

    @Synchronized
    private fun splitSegment() {
        val config = currentConfig ?: return
        if (recordingState != RecordingSessionState.RECORDING &&
            recordingState != RecordingSessionState.PAUSED
        ) {
            return
        }

        val wasPaused = recordingState == RecordingSessionState.PAUSED
        val prevTempPath = currentFilePath
        val prevFinalPath = currentFinalPath
        val completedIndex = segmentIndex

        val stoppedOk = try {
            mediaRecorder?.stop()
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to stop recorder for split", e)
            false
        }
        try {
            mediaRecorder?.release()
        } catch (_: Exception) {
        }
        mediaRecorder = null

        finalizeSegment(stoppedOk, prevTempPath, prevFinalPath, completedIndex)
        segmentIndex++

        try {
            val recorder = buildAndPrepareRecorder(config)
            mediaRecorder = recorder
            virtualDisplay?.surface = recorder.surface
            recorder.start()
            if (wasPaused && Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                recorder.pause()
            }
            recordingState = if (wasPaused) {
                RecordingSessionState.PAUSED
            } else {
                RecordingSessionState.RECORDING
            }
            startTimeMs = System.currentTimeMillis()
            fileSizeBytes = 0
            callbacks.onStateChanged(recordingState)
            Log.i(TAG, "Started new segment $segmentIndex: $currentFilePath")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start new segment", e)
            callbacks.onError(
                RecordingErrorCode.SPLIT_ERROR,
                CodecErrorFormatter.format(e, "Failed to start next recording segment"),
                false,
            )
            recordingState = RecordingSessionState.ERROR
            releaseCaptureResources(stopProjection = true)
        }
    }

    override fun stop() {
        if (isStopping) return
        if (recordingState != RecordingSessionState.RECORDING &&
            recordingState != RecordingSessionState.PAUSED
        ) {
            Log.w(TAG, "Not recording, ignoring stop")
            return
        }

        isStopping = true
        recordingState = RecordingSessionState.STOPPING
        callbacks.onStateChanged(RecordingSessionState.STOPPING)
        stopTimer()

        val tempPath = currentFilePath
        val finalPath = currentFinalPath
        val finishedIndex = segmentIndex

        // Order: stop encoder → release VirtualDisplay (surface consumer) → release MediaRecorder.
        val stoppedOk = try {
            mediaRecorder?.stop()
            true
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping recorder", e)
            false
        }

        releaseCaptureResources(stopProjection = true)

        recordingState = RecordingSessionState.IDLE
        isStopping = false
        val finalized = finalizeSegment(stoppedOk, tempPath, finalPath, finishedIndex)
        callbacks.onStateChanged(RecordingSessionState.IDLE)
        if (!finalized) {
            callbacks.onError(
                RecordingErrorCode.FINALIZE_FAILED,
                "Failed to finalize recording output",
                false,
            )
        }
    }

    override fun pause() {
        if (recordingState != RecordingSessionState.RECORDING) return
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                mediaRecorder?.pause()
            }
            recordingState = RecordingSessionState.PAUSED
            pauseStartedMs = System.currentTimeMillis()
            stopTimer()
            callbacks.onStateChanged(RecordingSessionState.PAUSED)
        } catch (e: Exception) {
            Log.e(TAG, "Error pausing recorder", e)
        }
    }

    override fun resume() {
        if (recordingState != RecordingSessionState.PAUSED) return
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                mediaRecorder?.resume()
            }
            if (pauseStartedMs > 0) {
                pausedAccumulatedMs += System.currentTimeMillis() - pauseStartedMs
                pauseStartedMs = 0
            }
            recordingState = RecordingSessionState.RECORDING
            startTimer()
            callbacks.onStateChanged(RecordingSessionState.RECORDING)
        } catch (e: Exception) {
            Log.e(TAG, "Error resuming recorder", e)
            callbacks.onError(
                RecordingErrorCode.RESUME_FAILED,
                "Failed to resume recording: ${e.message}",
                true,
            )
        }
    }

    override fun release() {
        isStopping = true
        stopTimer()
        releaseCaptureResources(stopProjection = true)
        recordingState = RecordingSessionState.IDLE
        isStopping = false
    }

    override fun isRecording() = recordingState == RecordingSessionState.RECORDING
    override fun isPaused() = recordingState == RecordingSessionState.PAUSED

    private fun finalizeSegment(
        stoppedOk: Boolean,
        tempPath: String?,
        finalPath: String?,
        index: Int,
    ): Boolean {
        if (!stoppedOk || tempPath == null || finalPath == null) {
            Log.e(TAG, "Segment not finalized (stoppedOk=$stoppedOk), temp retained: $tempPath")
            return false
        }
        val ok = PendingOutputStore.finalizePending(tempPath, finalPath)
        if (!ok) {
            Log.e(TAG, "Failed to rename temp file to final: $tempPath -> $finalPath")
            return false
        }
        val finalFile = File(finalPath)
        fileSizeBytes = finalFile.length()
        val publishedPath = PendingOutputStore.moveFinalToCustomFolder(
            context,
            finalFile,
            currentConfig?.customSaveUri,
        ) ?: return false
        callbacks.onFileSegmentCreated(publishedPath, index, fileSizeBytes)
        if (publishedPath == finalFile.absolutePath) {
            PendingOutputStore.scanMedia(context, publishedPath)
        }
        Log.i(TAG, "Recording segment finalized: $finalPath ($fileSizeBytes bytes)")
        return true
    }

    private fun startTimer() {
        stopTimer()
        isTimerRunning = true
        timerThread = Thread {
            while (isTimerRunning) {
                val elapsed = System.currentTimeMillis() - startTimeMs - pausedAccumulatedMs
                fileSizeBytes = File(currentFilePath ?: "").length()
                callbacks.onTimerTick(elapsed.coerceAtLeast(0), fileSizeBytes)
                try {
                    Thread.sleep(1000)
                } catch (_: InterruptedException) {
                    break
                }
            }
        }.apply {
            name = "recording-timer"
            isDaemon = true
            start()
        }
    }

    private fun stopTimer() {
        isTimerRunning = false
        timerThread?.interrupt()
        timerThread = null
    }

    /**
     * Releases VirtualDisplay before MediaRecorder so the encoder surface is not left dangling
     * (avoids "A resource failed to call Surface.release").
     */
    private fun releaseCaptureResources(stopProjection: Boolean) {
        val display = virtualDisplay
        virtualDisplay = null
        try {
            display?.release()
        } catch (e: Exception) {
            Log.w(TAG, "VirtualDisplay release failed", e)
        }

        val recorder = mediaRecorder
        mediaRecorder = null
        try {
            recorder?.reset()
        } catch (_: Exception) {
        }
        try {
            recorder?.release()
        } catch (e: Exception) {
            Log.w(TAG, "MediaRecorder release failed", e)
        }

        val proj = projection
        val callback = projectionCallback
        projectionCallback = null
        if (proj != null && callback != null) {
            try {
                proj.unregisterCallback(callback)
            } catch (_: Exception) {
            }
        }
        if (stopProjection) {
            projection = null
            try {
                proj?.stop()
            } catch (_: Exception) {
            }
        }
    }

    private data class TargetSize(val width: Int, val height: Int)

    private fun resolveTargetSize(
        nativeWidth: Int,
        nativeHeight: Int,
        manualShortSide: Int,
        orientationMode: String,
        alignment: Int,
    ): TargetSize {
        val nativeLong = maxOf(nativeWidth, nativeHeight)
        val nativeShort = minOf(nativeWidth, nativeHeight)
        val targetShort = if (manualShortSide > 0) {
            minOf(manualShortSide, nativeShort)
        } else {
            nativeShort
        }
        val targetLong = if (nativeShort > 0) {
            Math.round(nativeLong.toFloat() * targetShort.toFloat() / nativeShort.toFloat())
        } else {
            nativeLong
        }

        val portrait = when (orientationMode.lowercase(Locale.US)) {
            "portrait" -> true
            "landscape" -> false
            else -> nativeHeight >= nativeWidth
        }

        val rawWidth = if (portrait) targetShort else targetLong
        val rawHeight = if (portrait) targetLong else targetShort
        return TargetSize(
            width = alignDown(rawWidth, alignment),
            height = alignDown(rawHeight, alignment),
        )
    }

    private fun alignDown(value: Int, alignment: Int): Int {
        val aligned = (value / alignment) * alignment
        return maxOf(alignment, aligned)
    }

    companion object {
        private const val TAG = "StandardRecorderEngine"
    }
}

package com.captord.screenrecorder.core.engine

import android.content.Context
import android.content.Intent
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.media.MediaMuxer
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Bundle
import android.util.DisplayMetrics
import android.util.Log
import android.view.Surface
import android.view.WindowManager
import com.captord.screenrecorder.core.audio.AudioCaptureManager
import com.captord.screenrecorder.core.audio.AudioEncoder
import java.io.File
import java.nio.ByteBuffer
import java.util.Locale
import com.captord.screenrecorder.core.storage.PendingOutputStore
import com.captord.screenrecorder.feature.recorder.model.RecordingConfig
import com.captord.screenrecorder.feature.recorder.model.RecordingErrorCode
import com.captord.screenrecorder.feature.recorder.model.RecordingSessionState
import java.util.concurrent.atomic.AtomicBoolean

class AdvancedRecorderEngine(
    private val context: Context,
    private val callbacks: EngineCallbacks,
) : RecordingEngine {

    companion object {
        private const val TAG = "AdvancedRecorder"
        /**
         * Shift internal/mixed audio earlier in the file timeline to compensate
         * REMOTE_SUBMIX + AudioRecord content lag (~100–200ms subjective).
         * Only applied when audioSource is internal or both.
         */
        private const val INTERNAL_AUDIO_LATENCY_OFFSET_US = 120_000L
    }

    private var recording = false
    private var paused = false
    private val isRunning = AtomicBoolean(false)

    private var projection: MediaProjection? = null
    private var virtualDisplay: VirtualDisplay? = null

    private var videoEncoder: MediaCodec? = null
    private var inputSurface: Surface? = null
    private var mediaMuxer: MediaMuxer? = null
    private var videoTrackIndex = -1
    private var audioTrackIndex = -1
    private var muxerStarted = false

    private var videoThread: Thread? = null
    private val videoEosRequested = AtomicBoolean(false)
    private var currentFilePath: String? = null
    private var currentFinalPath: String? = null
    private var currentConfig: RecordingConfig? = null

    private var audioCaptureManager: AudioCaptureManager? = null
    private var audioEncoder: AudioEncoder? = null
    private var currentAudioFormat: MediaFormat? = null

    private var startTimeNs = -1L
    private var totalPauseTimeNs = 0L
    private var pauseStartNs = 0L
    private var segmentStartTimeUs = 0L
    private var segmentIndex = 0

    private var firstVideoTimestampUs = -1L
    private val timestampLock = Any()
    private var lastAudioPtsUs = -1L
    private var lastVideoPtsUs = -1L
    /** First audio PTS written to muxer; used to zero audio timeline at muxer start. */
    private var audioPtsBaseUs = -1L

    // A/V sync measurement (debug)
    private var muxerStartedWallNs = -1L
    private var firstAudioWriteWallNs = -1L
    private var firstVideoWriteWallNs = -1L
    private var lastSkewLogNs = 0L
    private var audioWriteCount = 0L
    private var videoWriteCount = 0L
    /** Subtracted from audio mux PTS for internal/both sources (content lag compensation). */
    private var audioLatencyOffsetUs = 0L

    private var timerThread: Thread? = null
    @Volatile
    private var isTimerRunning = false

    override fun start(config: RecordingConfig, resultCode: Int, projectionData: Intent) {
        if (recording) return
        currentConfig = config

        val wm = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val metrics = DisplayMetrics()
        @Suppress("DEPRECATION")
        wm.defaultDisplay.getRealMetrics(metrics)
        val screenWidth = metrics.widthPixels
        val screenHeight = metrics.heightPixels
        val screenDpi = metrics.densityDpi

        val targetSize = resolveTargetSize(
            nativeWidth = screenWidth,
            nativeHeight = screenHeight,
            manualShortSide = config.manualResolutionHeight,
            orientationMode = config.orientationMode,
            alignment = 16,
        )
        val alignedWidth = targetSize.width
        val alignedHeight = targetSize.height

        Log.i(TAG, "Recording size: ${alignedWidth}x${alignedHeight} (resolution=${config.manualResolutionHeight}, orientation=${config.orientationMode})")
        Log.i(TAG, "Video bitrate: ${config.videoBitrate} bps")

        val outputFile = createOutputFile(config)
        currentFinalPath = outputFile.absolutePath
        val tempOutputFile = tempFileFor(outputFile)
        currentFilePath = tempOutputFile.absolutePath

        startTimeNs = -1L  // Will be initialized on first media frame arrival
        totalPauseTimeNs = 0L
        pauseStartNs = 0L
        segmentStartTimeUs = 0L
        segmentIndex = 0
        currentAudioFormat = null

        try {
            val mime = if (config.videoCodec.equals("H265", ignoreCase = true)) "video/hevc" else "video/avc"
            val format = MediaFormat.createVideoFormat(mime, alignedWidth, alignedHeight).apply {
                setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface)
                setInteger(MediaFormat.KEY_BIT_RATE, config.videoBitrate)
                setInteger(MediaFormat.KEY_FRAME_RATE, config.frameRate)
                setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1)
                setInteger(MediaFormat.KEY_BITRATE_MODE, MediaCodecInfo.EncoderCapabilities.BITRATE_MODE_VBR)
            }

            videoEncoder = MediaCodec.createEncoderByType(mime)
            videoEncoder?.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
            inputSurface = videoEncoder?.createInputSurface()
            videoEncoder?.start()

            mediaMuxer = MediaMuxer(currentFilePath!!, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)

            val projectionManager = context.getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
            @Suppress("DEPRECATION")
            projection = projectionManager.getMediaProjection(resultCode, projectionData)
            if (projection == null) {
                callbacks.onError(RecordingErrorCode.PROJECTION_FAILED, "Failed to create MediaProjection", false)
                return
            }

            projection?.registerCallback(object : MediaProjection.Callback() {
                override fun onStop() {
                    Log.i(TAG, "MediaProjection stopped")
                    stop()
                }
            }, null)

            if (config.audioEnabled) {
                lastAudioPtsUs = -1L
                val src = config.audioSource
                audioLatencyOffsetUs =
                    if (src.equals("internal", ignoreCase = true) ||
                        src.equals("both", ignoreCase = true)
                    ) {
                        INTERNAL_AUDIO_LATENCY_OFFSET_US
                    } else {
                        0L
                    }
                if (audioLatencyOffsetUs > 0) {
                    Log.i(
                        TAG,
                        "Internal audio latency compensation: -${audioLatencyOffsetUs / 1000}ms",
                    )
                }

                val capture = AudioCaptureManager { pcmData, size, ptsUs ->
                    audioEncoder?.queueInputBuffer(pcmData, size, ptsUs)
                }
                audioCaptureManager = capture

                val preferred = capture.resolvePreferredFormat(
                    audioSource = config.audioSource,
                    requestedSampleRate = config.sampleRate,
                    requestedChannelCount = config.audioChannel,
                )
                Log.i(
                    TAG,
                    "Audio preferred: ${preferred.sampleRate}Hz/${preferred.channelCount}ch " +
                        "(requested ${config.sampleRate}Hz/${config.audioChannel}ch, " +
                        "source=${config.audioSource})",
                )

                // Phase 1: open AudioRecord and learn negotiated rate/channel (pitch fix).
                val actual = capture.prepare(
                    config.audioSource,
                    preferred.sampleRate,
                    preferred.channelConfig,
                    projection,
                )
                if (actual == null) {
                    callbacks.onError(
                        RecordingErrorCode.ENGINE_ERROR,
                        "Failed to prepare audio capture",
                        false,
                    )
                    cleanup()
                    return
                }
                if (actual.sampleRate != config.sampleRate ||
                    actual.channelCount != config.audioChannel
                ) {
                    callbacks.onAudioFallback(
                        "Audio using ${actual.sampleRate}Hz / ${actual.channelCount}ch " +
                            "(requested ${config.sampleRate}Hz / ${config.audioChannel}ch)",
                    )
                }

                // Phase 2: AAC must match capture format exactly.
                audioEncoder = AudioEncoder(
                    onOutputFormatChanged = { newFormat ->
                        currentAudioFormat = newFormat
                        audioTrackIndex = mediaMuxer?.addTrack(newFormat) ?: -1
                        checkAndStartMuxer()
                    },
                    onOutputBufferAvailable = { byteBuffer, bufferInfo ->
                        writeAudioSample(byteBuffer, bufferInfo)
                    },
                )
                if (audioEncoder?.start(
                        actual.sampleRate,
                        actual.channelCount,
                        config.audioBitrate,
                    ) != true
                ) {
                    callbacks.onError(
                        RecordingErrorCode.ENGINE_ERROR,
                        "Failed to start AAC encoder",
                        false,
                    )
                    cleanup()
                    return
                }

                Log.i(
                    TAG,
                    "Audio pipeline prepared: capture=${actual.sampleRate}Hz/${actual.channelCount}ch " +
                        "encoder=${audioEncoder?.configuredSampleRate}Hz/" +
                        "${audioEncoder?.configuredChannelCount}ch",
                )
            }

            // Reset muxer track state before any encoder output arrives.
            videoTrackIndex = -1
            audioTrackIndex = -1
            muxerStarted = false
            lastAudioPtsUs = -1L
            lastVideoPtsUs = -1L
            audioPtsBaseUs = -1L
            firstVideoTimestampUs = -1L
            muxerStartedWallNs = -1L
            firstAudioWriteWallNs = -1L
            firstVideoWriteWallNs = -1L
            lastSkewLogNs = 0L
            audioWriteCount = 0L
            videoWriteCount = 0L

            virtualDisplay = projection?.createVirtualDisplay(
                "Captord-Display",
                alignedWidth,
                alignedHeight,
                screenDpi,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                inputSurface,
                null,
                null,
            )

            recording = true
            paused = false
            isRunning.set(true)
            videoEosRequested.set(false)

            videoThread = Thread { drainVideoEncoder() }.apply { start() }

            // Start PCM workers after video surface is live; emission waits for first video PTS.
            audioCaptureManager?.startWorkers()

            startTimer()

            callbacks.onStateChanged(RecordingSessionState.RECORDING)
            Log.i(TAG, "Advanced Engine started recording successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Error starting Advanced Engine: ${e.message}", e)
            callbacks.onError(RecordingErrorCode.ENGINE_ERROR, "Failed to start advanced recording: ${e.message}", false)
            cleanup()
        }
    }

    override fun stop() {
        if (!recording) return
        Log.i(TAG, "Stopping Advanced Engine...")
        isRunning.set(false)
        stopTimer()

        stopAudioPipeline(flush = true)

        try {
            videoEosRequested.set(true)
            videoEncoder?.signalEndOfInputStream()
        } catch (e: Exception) {
            videoEosRequested.set(false)
            Log.e(TAG, "Failed to signal video end of stream", e)
        }

        try {
            videoThread?.join(3000)
        } catch (e: InterruptedException) {
            Log.e(TAG, "Thread join interrupted", e)
        }

        val muxerStoppedOk = try {
            mediaMuxer?.stop()
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to stop muxer: ${e.message}", e)
            false
        }

        val tempPath = currentFilePath
        val finalPath = currentFinalPath
        val finishedSegmentIndex = segmentIndex

        cleanup(stopMuxer = false)
        recording = false
        paused = false

        val finalized = finalizeSegment(muxerStoppedOk, tempPath, finalPath, finishedSegmentIndex)
        callbacks.onStateChanged(RecordingSessionState.IDLE)
        if (!finalized) {
            callbacks.onError(RecordingErrorCode.FINALIZE_FAILED, "Failed to finalize recording output", false)
        }
    }

    override fun pause() {
        if (recording && !paused) {
            paused = true
            pauseStartNs = System.nanoTime()
            audioCaptureManager?.setPaused(true)
            stopTimer()
            callbacks.onStateChanged(RecordingSessionState.PAUSED)
            Log.i(TAG, "Recording paused")
        }
    }

    override fun resume() {
        if (recording && paused) {
            paused = false
            totalPauseTimeNs += System.nanoTime() - pauseStartNs
            pauseStartNs = 0L
            audioCaptureManager?.setPaused(false)
            startTimer()
            callbacks.onStateChanged(RecordingSessionState.RECORDING)
            Log.i(TAG, "Recording resumed")
        }
    }

    override fun release() {
        stopTimer()
        cleanup()
    }

    override fun isRecording(): Boolean = recording
    override fun isPaused(): Boolean = paused

    private fun getPtsUs(): Long {
        if (startTimeNs < 0) return 0L
        val elapsedNs = System.nanoTime() - startTimeNs - totalPauseTimeNs
        return elapsedNs / 1000
    }

    /**
     * Get synchronized video PTS. Initializes startTimeNs on first video frame.
     * Video uses codec timestamp offset from first frame (hardware-accurate).
     */
    private fun getSynchronizedVideoPtsUs(codecPtsUs: Long): Long {
        synchronized(timestampLock) {
            if (firstVideoTimestampUs < 0) {
                firstVideoTimestampUs = codecPtsUs
                if (startTimeNs < 0) {
                    startTimeNs = System.nanoTime()
                }
                return 0L
            }
            val videoOffsetUs = codecPtsUs - firstVideoTimestampUs
            return videoOffsetUs - (totalPauseTimeNs / 1000)
        }
    }

    private fun startTimer() {
        stopTimer()
        isTimerRunning = true
        timerThread = Thread {
            while (isTimerRunning) {
                if (!paused) {
                    val elapsedMs = getPtsUs() / 1000
                    val fileSizeBytes = File(currentFilePath ?: "").length()
                    callbacks.onTimerTick(elapsedMs, fileSizeBytes)
                }
                try {
                    Thread.sleep(1000)
                } catch (_: InterruptedException) {
                    break
                }
            }
        }.apply {
            name = "advanced-recording-timer"
            isDaemon = true
            start()
        }
    }

    private fun stopTimer() {
        isTimerRunning = false
        timerThread?.interrupt()
        timerThread = null
    }

    @Synchronized
    private fun checkAndStartMuxer() {
        val muxer = mediaMuxer ?: return
        if (muxerStarted) return

        val isAudioRequired = currentConfig?.audioEnabled == true
        val hasVideo = videoTrackIndex >= 0
        val hasAudio = audioTrackIndex >= 0

        if (hasVideo && (!isAudioRequired || hasAudio)) {
            try {
                muxer.start()
                muxerStarted = true
                // Zero A/V timelines at the moment muxing begins.
                firstVideoTimestampUs = -1L
                lastVideoPtsUs = -1L
                lastAudioPtsUs = -1L
                audioPtsBaseUs = -1L
                muxerStartedWallNs = System.nanoTime()
                firstAudioWriteWallNs = -1L
                firstVideoWriteWallNs = -1L
                lastSkewLogNs = 0L
                audioWriteCount = 0L
                videoWriteCount = 0L
                audioCaptureManager?.resetSampleClock()
                Log.i(
                    TAG,
                    "MediaMuxer started successfully. videoTrack=$videoTrackIndex, audioTrack=$audioTrackIndex",
                )
            } catch (e: Exception) {
                Log.e(TAG, "Failed to start MediaMuxer: ${e.message}", e)
            }
        }
    }

    @Synchronized
    private fun checkAndSplitFile(): Boolean {
        val path = currentFilePath ?: return false
        val config = currentConfig ?: return false
        if (config.maxFileSizeMb <= 0) return false

        val file = File(path)
        if (file.exists() && file.length() >= config.maxFileSizeMb * 1024 * 1024) {
            val prevTempPath = currentFilePath
            val prevFinalPath = currentFinalPath
            val completedIndex = segmentIndex
            try {
                Log.i(TAG, "File size ${file.length()} bytes exceeds limit ${config.maxFileSizeMb} MB. Splitting...")

                val muxerStoppedOk = try {
                    mediaMuxer?.stop()
                    true
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to stop muxer for split: ${e.message}", e)
                    false
                }
                mediaMuxer?.release()
                mediaMuxer = null
                muxerStarted = false

                finalizeSegment(muxerStoppedOk, prevTempPath, prevFinalPath, completedIndex)
                segmentIndex++

                val nextFile = createOutputFile(config)
                currentFinalPath = nextFile.absolutePath
                val nextTempFile = tempFileFor(nextFile)
                currentFilePath = nextTempFile.absolutePath

                mediaMuxer = MediaMuxer(currentFilePath!!, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)

                videoEncoder?.outputFormat?.let {
                    videoTrackIndex = mediaMuxer!!.addTrack(it)
                }
                currentAudioFormat?.let {
                    audioTrackIndex = mediaMuxer!!.addTrack(it)
                }

                segmentStartTimeUs = 0L
                lastAudioPtsUs = -1L
                lastVideoPtsUs = -1L
                audioPtsBaseUs = -1L
                firstVideoTimestampUs = -1L
                audioCaptureManager?.resetSampleClock()
                checkAndStartMuxer()
                videoEncoder?.setParameters(
                    Bundle().apply {
                        putInt(MediaCodec.PARAMETER_KEY_REQUEST_SYNC_FRAME, 0)
                    },
                )
                return true
            } catch (e: Exception) {
                Log.e(TAG, "Error splitting file: ${e.message}", e)
                callbacks.onError(RecordingErrorCode.SPLIT_ERROR, "Failed to split recording: ${e.message}", false)
            }
        }
        return false
    }

    @Synchronized
    private fun writeAudioSample(encodedData: ByteBuffer, bufferInfo: MediaCodec.BufferInfo) {
        val muxer = mediaMuxer ?: return
        if (!muxerStarted || audioTrackIndex < 0) return

        // Keep encoder/sample-clock PTS (not wall-clock). Rebase + optional internal lag offset.
        val rawPts = bufferInfo.presentationTimeUs
        if (audioPtsBaseUs < 0) {
            audioPtsBaseUs = rawPts
        }
        // Pull audio earlier so content aligns with video (REMOTE_SUBMIX lag).
        var pts = rawPts - audioPtsBaseUs - segmentStartTimeUs - audioLatencyOffsetUs
        if (pts < 0) {
            // Drop early packets that would land before t=0 after compensation.
            if (audioWriteCount == 0L && audioLatencyOffsetUs > 0) {
                return
            }
            pts = 0L
        }
        if (lastAudioPtsUs >= 0 && pts < lastAudioPtsUs) {
            pts = lastAudioPtsUs + 1
        }
        bufferInfo.presentationTimeUs = pts
        lastAudioPtsUs = pts

        try {
            muxer.writeSampleData(audioTrackIndex, encodedData, bufferInfo)
            audioWriteCount++
            val nowNs = System.nanoTime()
            if (firstAudioWriteWallNs < 0) {
                firstAudioWriteWallNs = nowNs
                val sinceMuxerMs = if (muxerStartedWallNs > 0) {
                    (nowNs - muxerStartedWallNs) / 1_000_000
                } else {
                    -1L
                }
                Log.i(
                    TAG,
                    "A/V sync first audio write: muxPtsUs=$pts rawPtsUs=$rawPts " +
                        "baseUs=$audioPtsBaseUs offsetUs=$audioLatencyOffsetUs " +
                        "sinceMuxerMs=$sinceMuxerMs",
                )
            }
            maybeLogAvSkew(nowNs)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to write audio sample data: ${e.message}", e)
        }
    }

    private fun maybeLogAvSkew(nowNs: Long) {
        if (lastAudioPtsUs < 0 || lastVideoPtsUs < 0) return
        if (nowNs - lastSkewLogNs < 500_000_000L) return // every ~500ms
        lastSkewLogNs = nowNs
        val aMs = lastAudioPtsUs / 1000
        val vMs = lastVideoPtsUs / 1000
        val skewMs = aMs - vMs
        val firstA = if (firstAudioWriteWallNs > 0 && muxerStartedWallNs > 0) {
            (firstAudioWriteWallNs - muxerStartedWallNs) / 1_000_000
        } else {
            -1L
        }
        val firstV = if (firstVideoWriteWallNs > 0 && muxerStartedWallNs > 0) {
            (firstVideoWriteWallNs - muxerStartedWallNs) / 1_000_000
        } else {
            -1L
        }
        Log.i(
            TAG,
            "A/V sync: aPts=${aMs}ms vPts=${vMs}ms skewMs=$skewMs " +
                "(a-v; +audio ahead of video in file) offsetMs=${audioLatencyOffsetUs / 1000} " +
                "audioN=$audioWriteCount videoN=$videoWriteCount " +
                "firstAWriteMs=$firstA firstVWriteMs=$firstV",
        )
    }

    private fun drainVideoEncoder() {
        val bufferInfo = MediaCodec.BufferInfo()
        val encoder = videoEncoder ?: return
        var lastVideoWriteWallNs = System.nanoTime()
        var lastStallLogNs = 0L

        while (isRunning.get() || videoEosRequested.get()) {
            try {
                // Always dequeue/release even while paused — soft pause must not leak codec buffers.
                val timeoutUs = if (paused && !videoEosRequested.get()) 50_000L else 10_000L
                val outputBufferIndex = encoder.dequeueOutputBuffer(bufferInfo, timeoutUs)
                if (outputBufferIndex == MediaCodec.INFO_TRY_AGAIN_LATER) {
                    val nowNs = System.nanoTime()
                    if (isRunning.get() &&
                        !paused &&
                        videoWriteCount > 0 &&
                        nowNs - lastVideoWriteWallNs > 2_000_000_000L &&
                        nowNs - lastStallLogNs > 2_000_000_000L
                    ) {
                        lastStallLogNs = nowNs
                        Log.w(
                            TAG,
                            "Video encoder stall: no output for ${(nowNs - lastVideoWriteWallNs) / 1_000_000}ms " +
                                "videoN=$videoWriteCount vPts=${lastVideoPtsUs / 1000}ms " +
                                "audioN=$audioWriteCount aPts=${lastAudioPtsUs / 1000}ms",
                        )
                    }
                    continue
                } else if (outputBufferIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                    if (muxerStarted) {
                        Log.e(TAG, "Format changed twice, illegal state")
                        continue
                    }
                    val newFormat = encoder.outputFormat
                    videoTrackIndex = mediaMuxer?.addTrack(newFormat) ?: -1
                    checkAndStartMuxer()
                } else if (outputBufferIndex >= 0) {
                    try {
                        val encodedData = encoder.getOutputBuffer(outputBufferIndex)
                        if (encodedData == null) {
                            Log.w(TAG, "Null video output buffer index=$outputBufferIndex")
                        } else {
                            if (bufferInfo.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG != 0) {
                                bufferInfo.size = 0
                            }

                            val canWrite = bufferInfo.size != 0 && muxerStarted && !paused
                            if (canWrite) {
                                val codecPtsUs = bufferInfo.presentationTimeUs
                                var syncedPts =
                                    getSynchronizedVideoPtsUs(codecPtsUs) - segmentStartTimeUs
                                if (syncedPts < 0) syncedPts = 0L
                                if (syncedPts < lastVideoPtsUs) {
                                    syncedPts = lastVideoPtsUs + 1
                                }

                                if (checkAndSplitFile()) {
                                    // First sample in a new segment starts its own timeline.
                                    syncedPts = getSynchronizedVideoPtsUs(codecPtsUs)
                                }
                                lastVideoPtsUs = syncedPts
                                bufferInfo.presentationTimeUs = syncedPts

                                encodedData.position(bufferInfo.offset)
                                encodedData.limit(bufferInfo.offset + bufferInfo.size)
                                try {
                                    val muxer = mediaMuxer
                                        ?: throw IllegalStateException("No active muxer")
                                    if (videoTrackIndex < 0) {
                                        throw IllegalStateException("No video track")
                                    }
                                    muxer.writeSampleData(videoTrackIndex, encodedData, bufferInfo)
                                    videoWriteCount++
                                    lastVideoWriteWallNs = System.nanoTime()
                                    val nowNs = lastVideoWriteWallNs
                                    if (firstVideoWriteWallNs < 0) {
                                        firstVideoWriteWallNs = nowNs
                                        val sinceMuxerMs = if (muxerStartedWallNs > 0) {
                                            (nowNs - muxerStartedWallNs) / 1_000_000
                                        } else {
                                            -1L
                                        }
                                        Log.i(
                                            TAG,
                                            "A/V sync first video write: ptsUs=$syncedPts " +
                                                "sinceMuxerMs=$sinceMuxerMs",
                                        )
                                    }
                                    maybeLogAvSkew(nowNs)
                                } catch (e: Exception) {
                                    Log.e(
                                        TAG,
                                        "Failed to write sample data to muxer: ${e.message}",
                                        e,
                                    )
                                }
                            }
                        }
                    } finally {
                        // Always return the buffer — never continue without release.
                        try {
                            encoder.releaseOutputBuffer(outputBufferIndex, false)
                        } catch (e: Exception) {
                            Log.e(TAG, "Failed to release video buffer: ${e.message}", e)
                        }
                    }
                    if (bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) {
                        videoEosRequested.set(false)
                        Log.i(TAG, "Video encoder EOS after videoN=$videoWriteCount")
                        break
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Video drain error: ${e.message}", e)
                if (!isRunning.get() && !videoEosRequested.get()) break
                try {
                    Thread.sleep(10)
                } catch (_: InterruptedException) {
                    break
                }
            }
        }
    }

    private fun cleanup(stopMuxer: Boolean = true) {
        isRunning.set(false)
        stopTimer()

        stopAudioPipeline()

        firstVideoTimestampUs = -1L
        startTimeNs = -1L
        lastAudioPtsUs = -1L
        lastVideoPtsUs = -1L
        audioPtsBaseUs = -1L
        muxerStartedWallNs = -1L
        firstAudioWriteWallNs = -1L
        firstVideoWriteWallNs = -1L
        lastSkewLogNs = 0L
        audioWriteCount = 0L
        videoWriteCount = 0L
        audioLatencyOffsetUs = 0L

        try { videoEncoder?.stop() } catch (_: Exception) {}
        try { videoEncoder?.release() } catch (_: Exception) {}
        videoEncoder = null

        if (stopMuxer) {
            try { mediaMuxer?.stop() } catch (_: Exception) {}
        }
        try { mediaMuxer?.release() } catch (_: Exception) {}
        mediaMuxer = null

        inputSurface?.release()
        inputSurface = null

        virtualDisplay?.release()
        virtualDisplay = null

        projection?.stop()
        projection = null

        videoTrackIndex = -1
        audioTrackIndex = -1
        muxerStarted = false
        currentAudioFormat = null
    }

    private fun stopAudioPipeline(flush: Boolean = false) {
        try { audioCaptureManager?.stop() } catch (_: Exception) {}
        audioCaptureManager = null
        if (flush) {
            try { audioEncoder?.stopAndDrain() } catch (_: Exception) {}
        }
        try { audioEncoder?.stop() } catch (_: Exception) {}
        audioEncoder = null
    }

    private fun createOutputFile(config: RecordingConfig): File {
        return PendingOutputStore.buildFinalFile(config.savePath, config.filePrefix)
    }

    private fun tempFileFor(finalFile: File): File =
        PendingOutputStore.pendingFileFor(finalFile)

    private fun finalizeSegment(
        muxerStoppedOk: Boolean,
        tempPath: String?,
        finalPath: String?,
        index: Int,
    ): Boolean {
        if (!muxerStoppedOk || tempPath == null || finalPath == null) {
            Log.e(TAG, "Segment not finalized (muxerStoppedOk=$muxerStoppedOk), temp retained: $tempPath")
            return false
        }
        if (!PendingOutputStore.finalizePending(tempPath, finalPath)) {
            Log.e(TAG, "Failed to rename temp file to final: $tempPath -> $finalPath")
            return false
        }
        val finalFile = File(finalPath)
        val publishedPath = PendingOutputStore.moveFinalToCustomFolder(
            context,
            finalFile,
            currentConfig?.customSaveUri,
        ) ?: return false
        callbacks.onFileSegmentCreated(publishedPath, index, finalFile.length())
        if (publishedPath == finalFile.absolutePath) {
            PendingOutputStore.scanMedia(context, publishedPath)
        }
        Log.i(TAG, "Recording segment finalized: $finalPath (${finalFile.length()} bytes)")
        return true
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
}

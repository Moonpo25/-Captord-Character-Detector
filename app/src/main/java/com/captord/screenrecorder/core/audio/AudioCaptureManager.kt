package com.captord.screenrecorder.core.audio

import android.annotation.SuppressLint
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioPlaybackCaptureConfiguration
import android.media.AudioRecord
import android.media.AudioTimestamp
import android.media.MediaRecorder
import android.media.projection.MediaProjection
import android.os.Build
import android.util.Log
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

data class AudioCaptureFormat(
    val sampleRate: Int,
    val channelCount: Int,
    val channelConfig: Int,
)

private data class AudioChunk(val buffer: ShortArray, val frames: Int)

/**
 * Captures mic and/or internal (playback) audio as PCM 16-bit LE.
 * PTS is sample-clock based so A/V stays aligned without wall-clock rewrite.
 *
 * Two-phase start:
 * 1. [prepare] open AudioRecord(s), return negotiated format
 * 2. [startWorkers] after AAC is configured to that format
 */
class AudioCaptureManager(
    private val callback: (ByteArray, Int, Long) -> Unit,
) {
    companion object {
        private const val TAG = "AudioCaptureManager"
        private const val QUEUE_CAPACITY = 32
        /** Preferred AudioRecord read period (lower = less capture latency). */
        private const val TARGET_BUFFER_MS = 20
        private const val MAX_BUFFER_MS = 40
    }

    private val isRunning = AtomicBoolean(false)
    private val isPaused = AtomicBoolean(false)
    private var micRecord: AudioRecord? = null
    private var internalRecord: AudioRecord? = null
    private var micWorker: Thread? = null
    private var internalWorker: Thread? = null
    private var mixingThread: Thread? = null
    private val micQueue = LinkedBlockingQueue<AudioChunk>(QUEUE_CAPACITY)
    private val internalQueue = LinkedBlockingQueue<AudioChunk>(QUEUE_CAPACITY)
    private val bufferPool = ConcurrentLinkedQueue<ShortArray>()

    @Volatile
    private var actualSampleRate: Int = 48_000
    @Volatile
    private var actualChannelCount: Int = 2
    @Volatile
    private var actualChannelConfig: Int = AudioFormat.CHANNEL_IN_STEREO
    @Volatile
    private var shortsPerFrame: Int = 2

    private val presentedFrames = AtomicLong(0)
    private var framesPerRead: Int = 0
    private var shortsPerRead: Int = 0
    private var preparedSource: String? = null
    private var prepared = false
    private var timestampProbed = false

    fun getActualFormat(): AudioCaptureFormat = AudioCaptureFormat(
        sampleRate = actualSampleRate,
        channelCount = actualChannelCount,
        channelConfig = actualChannelConfig,
    )

    fun setPaused(paused: Boolean) {
        isPaused.set(paused)
    }

    /** Reset sample-clock PTS origin (e.g. when muxer starts). */
    fun resetSampleClock() {
        presentedFrames.set(0)
    }

    /**
     * Prefer device-native rates for internal capture (usually 48 kHz stereo).
     */
    fun resolvePreferredFormat(
        audioSource: String,
        requestedSampleRate: Int,
        requestedChannelCount: Int,
    ): AudioCaptureFormat {
        val isInternal =
            audioSource.equals("internal", ignoreCase = true) ||
                audioSource.equals("both", ignoreCase = true)
        val sampleRate = if (isInternal) {
            // Playback capture / REMOTE_SUBMIX is almost always 48 kHz on modern devices.
            if (requestedSampleRate == 44_100 || requestedSampleRate == 48_000) {
                48_000
            } else {
                requestedSampleRate
            }
        } else {
            requestedSampleRate
        }
        val channelCount = if (isInternal) {
            // Prefer stereo for internal; mono often gets upmixed incorrectly by OEM path.
            maxOf(requestedChannelCount, 2).coerceIn(1, 2)
        } else {
            requestedChannelCount.coerceIn(1, 2)
        }
        val channelConfig = if (channelCount == 2) {
            AudioFormat.CHANNEL_IN_STEREO
        } else {
            AudioFormat.CHANNEL_IN_MONO
        }
        return AudioCaptureFormat(sampleRate, channelCount, channelConfig)
    }

    /**
     * Open AudioRecord(s) and report negotiated format. Does not start capture threads yet.
     */
    @SuppressLint("MissingPermission", "NewApi")
    fun prepare(
        audioSource: String,
        sampleRate: Int,
        channelConfig: Int,
        mediaProjection: MediaProjection?,
    ): AudioCaptureFormat? {
        if (prepared || isRunning.get()) return getActualFormat()

        val audioEncoding = AudioFormat.ENCODING_PCM_16BIT
        val channelCount = channelCountFromMask(channelConfig)
        val minBuf = AudioRecord.getMinBufferSize(sampleRate, channelConfig, audioEncoding)
        if (minBuf <= 0) {
            Log.e(TAG, "Invalid min buffer size: $minBuf for $sampleRate/$channelConfig")
            return null
        }

        val bytesPerFrame = channelCount * 2
        val targetBytes = (sampleRate * bytesPerFrame * TARGET_BUFFER_MS) / 1000
        val maxBytes = (sampleRate * bytesPerFrame * MAX_BUFFER_MS) / 1000
        // Prefer short periods: use max(minBuf, target) then cap at ~40ms.
        // Avoid minBuf*2 which forced ~80ms on this device (log: periodMs=80).
        val bufferSize = maxOf(minBuf, targetBytes)
            .coerceAtMost(maxOf(minBuf, maxBytes))
            .let { ((it + bytesPerFrame - 1) / bytesPerFrame) * bytesPerFrame }

        // Read in smaller chunks than the hardware buffer when possible.
        val readBytes = minOf(bufferSize, maxOf(targetBytes, minBuf))
            .let { ((it + bytesPerFrame - 1) / bytesPerFrame) * bytesPerFrame }

        presentedFrames.set(0)
        isPaused.set(false)

        try {
            micQueue.clear()
            internalQueue.clear()
            bufferPool.clear()

            if (audioSource.equals("mic", ignoreCase = true) ||
                audioSource.equals("both", ignoreCase = true)
            ) {
                micRecord = AudioRecord(
                    MediaRecorder.AudioSource.MIC,
                    sampleRate,
                    channelConfig,
                    audioEncoding,
                    bufferSize,
                )
                if (micRecord?.state != AudioRecord.STATE_INITIALIZED) {
                    Log.e(TAG, "Failed to initialize MIC AudioRecord")
                    cleanup()
                    return null
                }
            }

            if ((audioSource.equals("internal", ignoreCase = true) ||
                    audioSource.equals("both", ignoreCase = true)) &&
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q &&
                mediaProjection != null
            ) {
                val captureConfig = AudioPlaybackCaptureConfiguration.Builder(mediaProjection)
                    .addMatchingUsage(AudioAttributes.USAGE_MEDIA)
                    .addMatchingUsage(AudioAttributes.USAGE_GAME)
                    .addMatchingUsage(AudioAttributes.USAGE_UNKNOWN)
                    .build()

                internalRecord = AudioRecord.Builder()
                    .setAudioFormat(
                        AudioFormat.Builder()
                            .setEncoding(audioEncoding)
                            .setSampleRate(sampleRate)
                            .setChannelMask(channelConfig)
                            .build(),
                    )
                    .setAudioPlaybackCaptureConfig(captureConfig)
                    .setBufferSizeInBytes(bufferSize)
                    .build()

                if (internalRecord?.state != AudioRecord.STATE_INITIALIZED) {
                    Log.e(TAG, "Failed to initialize Internal AudioRecord")
                    cleanup()
                    return null
                }
            }

            val primary = internalRecord ?: micRecord
            if (primary == null) {
                Log.e(TAG, "No AudioRecord created for source=$audioSource")
                cleanup()
                return null
            }

            actualSampleRate = primary.sampleRate.takeIf { it > 0 } ?: sampleRate
            actualChannelConfig = primary.channelConfiguration.takeIf {
                it == AudioFormat.CHANNEL_IN_MONO || it == AudioFormat.CHANNEL_IN_STEREO
            } ?: channelConfig
            actualChannelCount = channelCountFromMask(actualChannelConfig)
            shortsPerFrame = actualChannelCount
            // Chunk size for read()/PTS advance — keep near TARGET_BUFFER_MS.
            val effectiveReadBytes = minOf(readBytes, bufferSize).coerceAtLeast(bytesPerFrame)
            framesPerRead = effectiveReadBytes / (actualChannelCount * 2)
            shortsPerRead = framesPerRead * shortsPerFrame
            preparedSource = audioSource
            prepared = true

            if (actualSampleRate != sampleRate || actualChannelCount != channelCount) {
                Log.w(
                    TAG,
                    "AudioRecord negotiated format differs: requested=${sampleRate}Hz/${channelCount}ch " +
                        "actual=${actualSampleRate}Hz/${actualChannelCount}ch",
                )
            }

            val periodMs =
                if (actualSampleRate > 0) (framesPerRead * 1000L) / actualSampleRate else -1L
            Log.i(
                TAG,
                "AudioCaptureManager prepared source=$audioSource " +
                    "actual=${actualSampleRate}Hz ch=$actualChannelCount " +
                    "hwBufferBytes=$bufferSize readBytes=$effectiveReadBytes " +
                    "framesPerRead=$framesPerRead periodMs=$periodMs minBuf=$minBuf",
            )
            return getActualFormat()
        } catch (e: Exception) {
            Log.e(TAG, "Error preparing AudioCaptureManager: ${e.message}", e)
            cleanup()
            return null
        }
    }

    /** Start capture threads after AAC encoder matches [getActualFormat]. */
    fun startWorkers() {
        if (!prepared || isRunning.get()) return
        val audioSource = preparedSource ?: return

        isRunning.set(true)
        val isMixed = audioSource.equals("both", ignoreCase = true)

        if (micRecord != null) {
            micWorker = Thread {
                captureWorkerLoop(micRecord!!, micQueue, shortsPerRead, isMixed)
            }.apply {
                name = "Captord-MicWorker"
                priority = Thread.MAX_PRIORITY
                start()
            }
        }

        if (internalRecord != null) {
            internalWorker = Thread {
                captureWorkerLoop(internalRecord!!, internalQueue, shortsPerRead, isMixed)
            }.apply {
                name = "Captord-InternalWorker"
                priority = Thread.MAX_PRIORITY
                start()
            }
        }

        if (isMixed) {
            mixingThread = Thread {
                mixingLoop(shortsPerRead)
            }.apply {
                name = "Captord-MixingThread"
                priority = Thread.MAX_PRIORITY
                start()
            }
        }

        Log.i(TAG, "AudioCaptureManager workers started source=$audioSource")
    }

    fun stop() {
        isRunning.set(false)
        try {
            micWorker?.interrupt()
            internalWorker?.interrupt()
            mixingThread?.interrupt()
            micWorker?.join(500)
            internalWorker?.join(500)
            mixingThread?.join(500)
        } catch (_: Exception) {
        }
        cleanup()
        Log.i(TAG, "AudioCaptureManager stopped")
    }

    private fun nextPtsUs(frames: Int): Long {
        val start = presentedFrames.getAndAdd(frames.toLong())
        return (start * 1_000_000L) / actualSampleRate
    }

    /** One-shot probe: does this AudioRecord expose usable timestamps? (measurement only) */
    private fun probeAudioTimestamp(record: AudioRecord) {
        try {
            val ts = AudioTimestamp()
            val result = record.getTimestamp(ts, AudioTimestamp.TIMEBASE_MONOTONIC)
            if (result == AudioRecord.SUCCESS) {
                val nowNs = System.nanoTime()
                val lagMs = (nowNs - ts.nanoTime) / 1_000_000
                Log.i(
                    TAG,
                    "AudioTimestamp probe OK: framePosition=${ts.framePosition} " +
                        "lagVsNowMs=$lagMs (positive => capture behind wall clock)",
                )
            } else {
                Log.i(TAG, "AudioTimestamp probe failed: result=$result")
            }
        } catch (e: Exception) {
            Log.i(TAG, "AudioTimestamp probe unsupported: ${e.message}")
        }
    }

    private fun deliverPcm(pcm: ByteArray, byteSize: Int, frames: Int) {
        // Drain while paused without advancing sample clock.
        if (isPaused.get()) {
            return
        }
        val ptsUs = nextPtsUs(frames)
        callback(pcm, byteSize, ptsUs)
    }

    private fun captureWorkerLoop(
        record: AudioRecord,
        queue: LinkedBlockingQueue<AudioChunk>,
        shortsPerRead: Int,
        isMixed: Boolean,
    ) {
        try {
            record.startRecording()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start recording on worker: ${e.message}")
            return
        }

        while (isRunning.get() && !Thread.currentThread().isInterrupted) {
            val buffer = bufferPool.poll() ?: ShortArray(shortsPerRead)
            val read = record.read(buffer, 0, shortsPerRead)

            if (read > 0) {
                val frames = read / shortsPerFrame
                if (frames <= 0) {
                    bufferPool.offer(buffer)
                    continue
                }

                if (!timestampProbed) {
                    timestampProbed = true
                    probeAudioTimestamp(record)
                }

                if (isMixed) {
                    if (isPaused.get()) {
                        bufferPool.offer(buffer)
                        continue
                    }
                    if (queue.size >= QUEUE_CAPACITY) {
                        val dropped = queue.poll()
                        if (dropped != null) bufferPool.offer(dropped.buffer)
                    }
                    if (!queue.offer(AudioChunk(buffer, frames))) {
                        bufferPool.offer(buffer)
                    }
                } else {
                    val byteSize = read * 2
                    val outputBuffer = ByteArray(byteSize)
                    shortsToBytes(buffer, read, outputBuffer)
                    deliverPcm(outputBuffer, byteSize, frames)
                    bufferPool.offer(buffer)
                }
            } else {
                bufferPool.offer(buffer)
                if (read < 0) {
                    Log.e(TAG, "AudioRecord read error: $read")
                    break
                }
                try {
                    Thread.sleep(2)
                } catch (_: InterruptedException) {
                    break
                }
            }
        }

        try {
            record.stop()
        } catch (_: Exception) {
        }
    }

    /**
     * Period-paced Both mix: emit exactly one PCM period per capture period.
     * Pair mic+internal when both arrive; fill silence for a missing side.
     * Never emit unpaired chunks (that doubled sample-clock PTS and caused stutter).
     */
    private fun mixingLoop(shortsPerRead: Int) {
        val periodFrames = if (framesPerRead > 0) framesPerRead else (shortsPerRead / maxOf(1, shortsPerFrame))
        val periodShorts = periodFrames * shortsPerFrame
        val outputBuffer = ByteArray(periodShorts * 2)
        val periodMs = if (actualSampleRate > 0) {
            maxOf(5L, (periodFrames * 1000L) / actualSampleRate)
        } else {
            20L
        }
        // Wait up to ~1 period for the late peer before filling silence.
        val peerWaitMs = periodMs

        var paired = 0L
        var micOnly = 0L
        var internalOnly = 0L
        var dropped = 0L
        var lastStatsNs = System.nanoTime()

        while (isRunning.get() && !Thread.currentThread().isInterrupted) {
            if (isPaused.get()) {
                drainQueueToPool(micQueue)
                drainQueueToPool(internalQueue)
                try {
                    Thread.sleep(5)
                } catch (_: InterruptedException) {
                    break
                }
                continue
            }

            // Keep queues from growing unbounded (late peer / mixer lag).
            dropped += dropOldestIfOver(micQueue, QUEUE_CAPACITY / 2)
            dropped += dropOldestIfOver(internalQueue, QUEUE_CAPACITY / 2)

            val periodStartNs = System.nanoTime()
            var micChunk: AudioChunk? = null
            var internalChunk: AudioChunk? = null
            try {
                micChunk = micQueue.poll(peerWaitMs, TimeUnit.MILLISECONDS)
            } catch (_: InterruptedException) {
                break
            }
            try {
                val elapsedMs = (System.nanoTime() - periodStartNs) / 1_000_000L
                val remainMs = (peerWaitMs - elapsedMs).coerceAtLeast(0L)
                // Always give the peer the remaining period window to pair.
                internalChunk = internalQueue.poll(remainMs, TimeUnit.MILLISECONDS)
            } catch (_: InterruptedException) {
                break
            }

            if (micChunk == null && internalChunk == null) {
                // Neither side produced audio this period — advance clock with silence
                // so A/V timeline does not stall if both capture briefly starve.
                java.util.Arrays.fill(outputBuffer, 0.toByte())
                deliverPcm(outputBuffer, outputBuffer.size, periodFrames)
                continue
            }

            when {
                micChunk != null && internalChunk != null -> paired++
                micChunk != null -> micOnly++
                else -> internalOnly++
            }

            val micBuffer = micChunk?.buffer
            val internalBuffer = internalChunk?.buffer
            val micFrames = micChunk?.frames ?: 0
            val internalFrames = internalChunk?.frames ?: 0

            // Always output a fixed period so PTS advances one step only.
            for (i in 0 until periodShorts) {
                val micSample = if (micBuffer != null && i < micFrames * shortsPerFrame && i < micBuffer.size) {
                    micBuffer[i].toInt()
                } else {
                    0
                }
                val internalSample =
                    if (internalBuffer != null && i < internalFrames * shortsPerFrame && i < internalBuffer.size) {
                        internalBuffer[i].toInt()
                    } else {
                        0
                    }
                var mixed = micSample + internalSample
                if (mixed > 32767) mixed = 32767
                else if (mixed < -32768) mixed = -32768
                outputBuffer[i * 2] = (mixed and 0xFF).toByte()
                outputBuffer[i * 2 + 1] = ((mixed ushr 8) and 0xFF).toByte()
            }

            deliverPcm(outputBuffer, outputBuffer.size, periodFrames)

            micBuffer?.let { bufferPool.offer(it) }
            internalBuffer?.let { bufferPool.offer(it) }

            val nowNs = System.nanoTime()
            if (nowNs - lastStatsNs >= 1_000_000_000L) {
                lastStatsNs = nowNs
                Log.i(
                    TAG,
                    "Both mix stats: paired=$paired micOnly=$micOnly internalOnly=$internalOnly " +
                        "dropped=$dropped periodMs=$periodMs qMic=${micQueue.size} qInt=${internalQueue.size}",
                )
            }
        }
    }

    private fun drainQueueToPool(queue: LinkedBlockingQueue<AudioChunk>) {
        var item = queue.poll()
        while (item != null) {
            bufferPool.offer(item.buffer)
            item = queue.poll()
        }
    }

    private fun dropOldestIfOver(queue: LinkedBlockingQueue<AudioChunk>, maxKeep: Int): Long {
        var dropped = 0L
        while (queue.size > maxKeep) {
            val old = queue.poll() ?: break
            bufferPool.offer(old.buffer)
            dropped++
        }
        return dropped
    }

    private fun shortsToBytes(src: ShortArray, shortCount: Int, dst: ByteArray) {
        var di = 0
        for (i in 0 until shortCount) {
            val s = src[i].toInt()
            dst[di++] = (s and 0xFF).toByte()
            dst[di++] = ((s ushr 8) and 0xFF).toByte()
        }
    }

    private fun channelCountFromMask(mask: Int): Int = when (mask) {
        AudioFormat.CHANNEL_IN_STEREO,
        AudioFormat.CHANNEL_OUT_STEREO,
        -> 2
        else -> 1
    }

    private fun cleanup() {
        isRunning.set(false)
        isPaused.set(false)
        prepared = false
        preparedSource = null
        timestampProbed = false
        try {
            micRecord?.release()
        } catch (_: Exception) {
        }
        micRecord = null
        try {
            internalRecord?.release()
        } catch (_: Exception) {
        }
        internalRecord = null
        micWorker = null
        internalWorker = null
        mixingThread = null

        var item = micQueue.poll()
        while (item != null) {
            bufferPool.offer(item.buffer)
            item = micQueue.poll()
        }
        item = internalQueue.poll()
        while (item != null) {
            bufferPool.offer(item.buffer)
            item = internalQueue.poll()
        }
    }
}

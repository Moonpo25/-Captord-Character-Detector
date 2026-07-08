package com.captord.screenrecorder.core.audio

import android.media.AudioFormat
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.util.Log
import java.nio.ByteBuffer
import java.util.concurrent.atomic.AtomicBoolean

class AudioEncoder(
    private val onOutputFormatChanged: (MediaFormat) -> Unit,
    private val onOutputBufferAvailable: (ByteBuffer, MediaCodec.BufferInfo) -> Unit,
) {
    companion object {
        private const val TAG = "AudioEncoder"
        private const val INPUT_TIMEOUT_US = 2_000L
    }

    private var encoder: MediaCodec? = null
    private val running = AtomicBoolean(false)

    @Volatile
    var configuredSampleRate: Int = 0
        private set

    @Volatile
    var configuredChannelCount: Int = 0
        private set

    fun start(sampleRate: Int, channelCount: Int, bitrate: Int): Boolean {
        if (running.get()) return true

        return try {
            val ch = channelCount.coerceIn(1, 2)
            val rate = sampleRate
            val format = MediaFormat.createAudioFormat(
                MediaFormat.MIMETYPE_AUDIO_AAC,
                rate,
                ch,
            ).apply {
                setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC)
                setInteger(MediaFormat.KEY_BIT_RATE, bitrate.coerceAtLeast(64_000))
                setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, 16 * 1024)
                setInteger(MediaFormat.KEY_PCM_ENCODING, AudioFormat.ENCODING_PCM_16BIT)
            }

            val codec = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_AUDIO_AAC)
            codec.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
            codec.start()
            encoder = codec
            configuredSampleRate = rate
            configuredChannelCount = ch
            running.set(true)

            // Prefer actual negotiated output values when available.
            try {
                val out = codec.outputFormat
                configuredSampleRate = out.getInteger(MediaFormat.KEY_SAMPLE_RATE).takeIf { it > 0 } ?: rate
                configuredChannelCount =
                    out.getInteger(MediaFormat.KEY_CHANNEL_COUNT).takeIf { it > 0 } ?: ch
            } catch (_: Exception) {
            }

            Log.i(
                TAG,
                "AudioEncoder started rate=$configuredSampleRate ch=$configuredChannelCount bitrate=$bitrate",
            )
            true
        } catch (e: Exception) {
            Log.e(TAG, "Error starting AudioEncoder: ${e.message}", e)
            stop()
            false
        }
    }

    /**
     * Queue PCM and drain. Returns false if the frame could not be queued (buffer busy).
     * Caller should keep sample-clock PTS advancing even on drop to avoid pitch stretch.
     */
    fun queueInputBuffer(data: ByteArray, size: Int, ptsUs: Long): Boolean {
        val codec = encoder ?: return false
        if (!running.get() || size <= 0) return false

        var queued = false
        try {
            val inputBufferIndex = codec.dequeueInputBuffer(INPUT_TIMEOUT_US)
            if (inputBufferIndex >= 0) {
                val inputBuffer = codec.getInputBuffer(inputBufferIndex)
                if (inputBuffer != null) {
                    inputBuffer.clear()
                    val toWrite = minOf(size, inputBuffer.remaining(), data.size)
                    inputBuffer.put(data, 0, toWrite)
                    codec.queueInputBuffer(inputBufferIndex, 0, toWrite, ptsUs, 0)
                    queued = true
                }
            } else {
                Log.w(TAG, "No input buffer available, dropping audio frame pts=$ptsUs")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error queuing audio buffer: ${e.message}", e)
        }

        drain()
        return queued
    }

    fun drain() {
        val codec = encoder ?: return
        if (!running.get()) return
        val bufferInfo = MediaCodec.BufferInfo()

        try {
            while (running.get()) {
                when (val outputBufferIndex = codec.dequeueOutputBuffer(bufferInfo, 0)) {
                    MediaCodec.INFO_TRY_AGAIN_LATER -> break
                    MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                        val out = codec.outputFormat
                        try {
                            configuredSampleRate =
                                out.getInteger(MediaFormat.KEY_SAMPLE_RATE).takeIf { it > 0 }
                                    ?: configuredSampleRate
                            configuredChannelCount =
                                out.getInteger(MediaFormat.KEY_CHANNEL_COUNT).takeIf { it > 0 }
                                    ?: configuredChannelCount
                        } catch (_: Exception) {
                        }
                        Log.i(
                            TAG,
                            "AAC output format: rate=$configuredSampleRate ch=$configuredChannelCount $out",
                        )
                        onOutputFormatChanged(out)
                    }
                    else -> {
                        if (outputBufferIndex >= 0) {
                            val encodedData = codec.getOutputBuffer(outputBufferIndex)
                            if (encodedData != null) {
                                if (bufferInfo.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG != 0) {
                                    bufferInfo.size = 0
                                }
                                if (bufferInfo.size > 0) {
                                    encodedData.position(bufferInfo.offset)
                                    encodedData.limit(bufferInfo.offset + bufferInfo.size)
                                    onOutputBufferAvailable(encodedData, bufferInfo)
                                }
                            }
                            codec.releaseOutputBuffer(outputBufferIndex, false)
                            if (bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) {
                                break
                            }
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error draining audio encoder: ${e.message}", e)
        }
    }

    fun stop() {
        running.set(false)
        try {
            encoder?.stop()
        } catch (_: Exception) {
        }
        try {
            encoder?.release()
        } catch (_: Exception) {
        }
        encoder = null
    }
}

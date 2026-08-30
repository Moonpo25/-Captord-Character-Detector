package com.captord.screenrecorder.core.capability

import android.content.Context
import android.media.MediaCodecInfo
import android.media.MediaCodecList
import android.os.Build
import android.provider.Settings
import com.captord.screenrecorder.feature.recorder.model.CapabilityReport
import com.captord.screenrecorder.feature.recorder.model.RecordingConfig

object CapabilityChecker {

    @Volatile
    private var cachedReport: CapabilityReport? = null

    fun probe(context: Context): CapabilityReport {
        cachedReport?.let { return it }
        synchronized(this) {
            cachedReport?.let { return it }
            val report = doProbe(context)
            cachedReport = report
            return report
        }
    }

    private fun doProbe(context: Context): CapabilityReport {
        val codecList = MediaCodecList(MediaCodecList.ALL_CODECS)
        val encoders = codecList.codecInfos.filter { it.isEncoder }

        val supportsH265 = encoders.any { info ->
            info.supportedTypes.any { it.equals("video/hevc", ignoreCase = true) }
        }

        val maxFps = encoders
            .flatMap { info ->
                info.supportedTypes
                    .filter { it.startsWith("video/", ignoreCase = true) }
                    .mapNotNull { type -> maxFpsForType(info, type) }
            }
            .maxOrNull() ?: 30

        val supportedFrameRates = RecordingConfig.ALLOWED_FRAME_RATES
            .filter { it <= maxFps }
            .sorted()
            .ifEmpty { listOf(30) }

        // Take the minimum of the per-encoder max bitrate across all video MIME types.
        // AVC is the floor; if a device only supports HEVC at a lower rate, that still
        // wins because we also iterate video/hevc. Capping at MAX_VIDEO_BITRATE keeps
        // us within the documented wide bracket for 1080p+.
        val probedMaxBps = encoders
            .flatMap { info ->
                info.supportedTypes
                    .filter { it.startsWith("video/", ignoreCase = true) }
                    .mapNotNull { type -> maxBitrateForType(info, type) }
            }
            .minOrNull()
            ?.coerceAtMost(RecordingConfig.MAX_VIDEO_BITRATE)
            ?: RecordingConfig.MAX_VIDEO_BITRATE

        return CapabilityReport(
            supportsH265 = supportsH265,
            supportedFrameRates = supportedFrameRates,
            maxResolution = 1080,
            supportsInternalAudio = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q,
            supportsPause = Build.VERSION.SDK_INT >= Build.VERSION_CODES.N,
            androidApi = Build.VERSION.SDK_INT,
            supportsShowTouches = canProbeShowTouches(context),
            supportsDynamicColor = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S,
            maxVideoBitrateBps = probedMaxBps,
        )
    }

    fun clampConfig(
        config: RecordingConfig,
        capability: CapabilityReport,
    ): RecordingConfig {
        var frameRate = config.frameRate
        if (frameRate !in capability.supportedFrameRates) {
            frameRate = capability.supportedFrameRates
                .filter { it <= frameRate }
                .maxOrNull()
                ?: capability.supportedFrameRates.first()
        }

        val videoCodec = if (config.videoCodec == "H265" && !capability.supportsH265) {
            "H264"
        } else {
            config.videoCodec
        }

        val engineType = when (config.engineType) {
            RecordingConfig.ENGINE_ADVANCED -> RecordingConfig.ENGINE_ADVANCED
            else -> RecordingConfig.ENGINE_STANDARD
        }

        // Internal/Both only valid on Advanced + API support.
        val audioSource = when {
            engineType != RecordingConfig.ENGINE_ADVANCED -> "mic"
            config.audioSource.equals("internal", ignoreCase = true) &&
                !capability.supportsInternalAudio -> "mic"
            config.audioSource.equals("both", ignoreCase = true) &&
                !capability.supportsInternalAudio -> "mic"
            else -> config.audioSource
        }

        // Clamp the requested bitrate to the device's hardware-enforced ceiling.
        // A value above maxVideoBitrateBps would make MediaCodec.configure() throw
        // IllegalArgumentException on devices with stricter encoders.
        val videoBitrate = if (capability.maxVideoBitrateBps > 0) {
            config.videoBitrate.coerceAtMost(capability.maxVideoBitrateBps)
        } else {
            config.videoBitrate
        }

        return config.copy(
            engineType = engineType,
            frameRate = frameRate,
            videoCodec = videoCodec,
            audioSource = audioSource,
            videoBitrate = videoBitrate,
        )
    }

    private fun maxFpsForType(info: MediaCodecInfo, type: String): Int? {
        return try {
            val caps = info.getCapabilitiesForType(type)
            val videoCaps = caps.videoCapabilities ?: return null
            val ranges = videoCaps.supportedFrameRates
            ranges?.upper?.toInt()
        } catch (_: Exception) {
            null
        }
    }

    private fun maxBitrateForType(info: MediaCodecInfo, type: String): Int? {
        return try {
            val caps = info.getCapabilitiesForType(type)
            val videoCaps = caps.videoCapabilities ?: return null
            videoCaps.bitrateRange.upper.toInt()
        } catch (_: Exception) {
            null
        }
    }

    private fun canProbeShowTouches(context: Context): Boolean {
        return try {
            Settings.System.getInt(context.contentResolver, "show_touches", 0)
            // Write requires privileged capability; report false for normal apps.
            false
        } catch (_: Exception) {
            false
        }
    }
}

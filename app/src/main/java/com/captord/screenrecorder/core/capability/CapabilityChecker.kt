package com.captord.screenrecorder.core.capability

import android.content.Context
import android.media.MediaCodecInfo
import android.media.MediaCodecList
import android.os.Build
import android.provider.Settings
import com.captord.screenrecorder.feature.recorder.model.CapabilityReport
import com.captord.screenrecorder.feature.recorder.model.RecordingConfig

object CapabilityChecker {

    fun probe(context: Context): CapabilityReport {
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

        return CapabilityReport(
            supportsH265 = supportsH265,
            supportedFrameRates = supportedFrameRates,
            maxResolution = 1080,
            supportsInternalAudio = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q,
            supportsPause = Build.VERSION.SDK_INT >= Build.VERSION_CODES.N,
            androidApi = Build.VERSION.SDK_INT,
            supportsShowTouches = canProbeShowTouches(context),
            supportsDynamicColor = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S,
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

        return config.copy(
            engineType = engineType,
            frameRate = frameRate,
            videoCodec = videoCodec,
            audioSource = audioSource,
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

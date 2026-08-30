package com.captord.screenrecorder.core.capability

import com.captord.screenrecorder.feature.recorder.model.CapabilityReport
import com.captord.screenrecorder.feature.recorder.model.RecordingConfig
import org.junit.Assert.assertEquals
import org.junit.Test

class CapabilityCheckerTest {

    private val fullCapability = CapabilityReport(
        supportsH265 = true,
        supportedFrameRates = RecordingConfig.ALLOWED_FRAME_RATES.sorted(),
        maxResolution = 1080,
        supportsInternalAudio = true,
        supportsPause = true,
        androidApi = 36,
        supportsShowTouches = false,
        supportsDynamicColor = true,
        maxVideoBitrateBps = RecordingConfig.MAX_VIDEO_BITRATE,
    )

    private val restrictedCapability = fullCapability.copy(
        maxVideoBitrateBps = 12_000_000,
    )

    @Test
    fun advancedNative_keeps120fps() {
        val config = RecordingConfig(
            engineType = RecordingConfig.ENGINE_ADVANCED,
            frameRate = 120,
            videoBitrate = 38_000_000,
            manualResolutionHeight = 0,
            audioSource = "internal",
        )
        val clamped = CapabilityChecker.clampConfig(config, fullCapability)
        assertEquals(120, clamped.frameRate)
        assertEquals(38_000_000, clamped.videoBitrate)
    }

    @Test
    fun unsupportedFps_clampsToNearestSupported() {
        val limited = fullCapability.copy(supportedFrameRates = listOf(24, 30, 60))
        val config = RecordingConfig(
            engineType = RecordingConfig.ENGINE_ADVANCED,
            frameRate = 120,
        )
        val clamped = CapabilityChecker.clampConfig(config, limited)
        assertEquals(60, clamped.frameRate)
    }

    @Test
    fun standard_forcesMic() {
        val config = RecordingConfig(
            engineType = RecordingConfig.ENGINE_STANDARD,
            audioSource = "internal",
        )
        val clamped = CapabilityChecker.clampConfig(config, fullCapability)
        assertEquals("mic", clamped.audioSource)
    }

    @Test
    fun bitrateAboveDeviceCap_clampsDown() {
        val config = RecordingConfig(
            engineType = RecordingConfig.ENGINE_STANDARD,
            videoBitrate = 38_000_000,
        )
        val clamped = CapabilityChecker.clampConfig(config, restrictedCapability)
        assertEquals(12_000_000, clamped.videoBitrate)
    }

    @Test
    fun bitrateWithinDeviceCap_kept() {
        val config = RecordingConfig(
            engineType = RecordingConfig.ENGINE_STANDARD,
            videoBitrate = 8_000_000,
        )
        val clamped = CapabilityChecker.clampConfig(config, restrictedCapability)
        assertEquals(8_000_000, clamped.videoBitrate)
    }

    @Test
    fun bitrateCapZero_isIgnored() {
        // Zero means "unknown / unbounded" — should NOT clamp at zero.
        val capability = fullCapability.copy(maxVideoBitrateBps = 0)
        val config = RecordingConfig(
            engineType = RecordingConfig.ENGINE_STANDARD,
            videoBitrate = 8_000_000,
        )
        val clamped = CapabilityChecker.clampConfig(config, capability)
        assertEquals(8_000_000, clamped.videoBitrate)
    }
}

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
}

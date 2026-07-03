package com.captord.screenrecorder.feature.recorder.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RecordingConfigTest {

    @Test
    fun phase1Default_isValid() {
        val result = RecordingConfig.phase1Default().validate()
        assertTrue(result is RecordingConfig.ValidationResult.Valid)
    }

    @Test
    fun invalidFrameRate_isRejected() {
        val result = RecordingConfig(frameRate = 15).validate()
        assertTrue(result is RecordingConfig.ValidationResult.Invalid)
    }

    @Test
    fun invalidBitrate_isRejected() {
        val result = RecordingConfig(videoBitrate = 500_000).validate()
        assertTrue(result is RecordingConfig.ValidationResult.Invalid)
    }

    @Test
    fun errorCodes_areStable() {
        assertEquals("projection_failed", RecordingErrorCode.PROJECTION_FAILED)
        assertEquals("finalize_failed", RecordingErrorCode.FINALIZE_FAILED)
        assertEquals("audio_unavailable", RecordingErrorCode.AUDIO_UNAVAILABLE)
    }
}

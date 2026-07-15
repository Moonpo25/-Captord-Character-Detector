package com.captord.screenrecorder.core.settings

import com.captord.screenrecorder.feature.recorder.model.RecordingConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AppSettingsTest {

    @Test
    fun defaults_mapToValidRecordingConfig() {
        val config = AppSettings().toRecordingConfig()
        assertTrue(config.validate() is RecordingConfig.ValidationResult.Valid)
        assertEquals(30, config.frameRate)
        assertEquals(8_000_000, config.videoBitrate)
        assertEquals("H264", config.videoCodec)
    }

    @Test
    fun toRecordingConfig_sanitizesPrefix() {
        val config = AppSettings(filePrefix = "Cap/tord:?").toRecordingConfig()
        assertEquals("Captord", config.filePrefix)
    }

    @Test
    fun countdownOptions_includeCommonValues() {
        assertTrue(AppSettings.COUNTDOWN_OPTIONS.containsAll(listOf(0, 3, 5, 10)))
    }

    @Test
    fun customSaveUri_mapsToRecordingConfig() {
        val uri = "content://com.android.externalstorage.documents/tree/primary%3AMovies%2FLolok"

        assertEquals(uri, AppSettings(customSaveUri = uri).toRecordingConfig().customSaveUri)
    }
}

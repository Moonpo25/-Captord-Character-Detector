package com.captord.screenrecorder.feature.settings

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class QualitySettingsGuardTest {

    @Test
    fun allowQualitySettingsWrite_blocksWhenSessionActive() {
        assertFalse(allowQualitySettingsWrite(sessionActive = true))
    }

    @Test
    fun allowQualitySettingsWrite_allowsWhenIdle() {
        assertTrue(allowQualitySettingsWrite(sessionActive = false))
    }
}

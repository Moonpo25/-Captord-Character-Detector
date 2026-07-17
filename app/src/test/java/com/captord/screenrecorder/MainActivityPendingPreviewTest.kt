package com.captord.screenrecorder

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

class MainActivityPendingPreviewTest {

    @Before
    fun clearPending() {
        MainActivity.clearPendingPreviewPath()
    }

    @Test
    fun pendingPreviewPath_startsNull() {
        assertNull(MainActivity.pendingPreviewPath.value)
    }

    @Test
    fun offerAndClear_pendingPreviewPath() {
        MainActivity.offerPendingPreviewPath("/sdcard/Movies/Captord/clip.mp4")
        assertEquals("/sdcard/Movies/Captord/clip.mp4", MainActivity.pendingPreviewPath.value)

        MainActivity.clearPendingPreviewPath()
        assertNull(MainActivity.pendingPreviewPath.value)
    }
}

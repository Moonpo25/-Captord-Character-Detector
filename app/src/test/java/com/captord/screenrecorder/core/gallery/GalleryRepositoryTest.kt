package com.captord.screenrecorder.core.gallery

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class GalleryRepositoryTest {

    @Test
    fun pathId_isStableForSamePath() {
        val a = GalleryRepository.pathId("/sdcard/Movies/Captord/a.mp4")
        val b = GalleryRepository.pathId("/sdcard/Movies/Captord/a.mp4")
        assertEquals(a, b)
    }

    @Test
    fun pathId_differsForDifferentPaths() {
        val a = GalleryRepository.pathId("/sdcard/Movies/Captord/a.mp4")
        val b = GalleryRepository.pathId("/sdcard/Movies/Captord/b.mp4")
        assertNotEquals(a, b)
    }
}

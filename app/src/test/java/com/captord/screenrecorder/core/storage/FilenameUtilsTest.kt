package com.captord.screenrecorder.core.storage

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class FilenameUtilsTest {

    @Test
    fun sanitizePrefix_stripsIllegalChars() {
        assertEquals("Captordclip", FilenameUtils.sanitizePrefix("Captord/clip:?"))
    }

    @Test
    fun sanitizePrefix_fallsBackWhenEmpty() {
        assertEquals("Captord", FilenameUtils.sanitizePrefix("///"))
    }

    @Test
    fun pendingFileFor_usesHiddenPendingDir() {
        val base = File(System.getProperty("java.io.tmpdir"), "captord-test-movies")
        val finalFile = File(base, "Captord/Captord_20260101_120000.mp4")
        finalFile.parentFile?.mkdirs()
        val pending = PendingOutputStore.pendingFileFor(finalFile)
        assertTrue(pending.parentFile?.name == ".pending")
        assertEquals(finalFile.name, pending.name)
        assertTrue(File(pending.parentFile, ".nomedia").exists())
    }
}

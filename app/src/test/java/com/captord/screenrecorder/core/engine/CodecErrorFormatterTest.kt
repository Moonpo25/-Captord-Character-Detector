package com.captord.screenrecorder.core.engine

import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for [CodecErrorFormatter]. We deliberately avoid
 * `MediaCodec.CodecException` here: its constructor became package-private
 * and final on API 30+, so it can't be subclassed from a JVM unit test.
 * The CodecException branch is covered by integration testing on device
 * (issue #2 repro on Android 16 / crDroid 12.11).
 */
class CodecErrorFormatterTest {

    @Test
    fun format_IllegalArgumentException_includesContextAndMessage() {
        val ex = IllegalArgumentException("bitrate out of range")
        val result = CodecErrorFormatter.format(ex, "configure")
        assertTrue(result, result.contains("configure"))
        assertTrue(result, result.contains("IllegalArgumentException"))
        assertTrue(result, result.contains("bitrate out of range"))
        assertFalse(
            "Output must never end with a stray ':'",
            result.endsWith(":") || result.endsWith(" |"),
        )
    }

    @Test
    fun format_throwableWithNullMessage_doesNotProduceTrailingColon() {
        // Most reproduction cases from issue #2 surface as exceptions whose
        // .message is null. The formatter must still produce a useful
        // string and never end with ':' or ' |'.
        val ex = RuntimeException() // no-arg ctor sets message to null
        val result = CodecErrorFormatter.format(ex, "Failed to start recording")
        assertNotNull(result)
        assertTrue(result, result.contains("Failed to start recording"))
        assertTrue(result, result.contains("RuntimeException"))
        assertFalse(result.endsWith(":"))
        assertFalse(result.endsWith(" |"))
    }

    @Test
    fun format_nestedCause_walksChain() {
        val root = IllegalStateException("encoder rejected frame")
        val mid = RuntimeException("muxer overflow", root)
        val top = IllegalArgumentException("bad config", mid)
        val result = CodecErrorFormatter.format(top, "start")
        assertTrue(result, result.contains("start"))
        assertTrue(result, result.contains("bad config"))
        assertTrue(result, result.contains("muxer overflow"))
        assertTrue(result, result.contains("encoder rejected frame"))
    }

    @Test
    fun format_emptyContext_isOk() {
        val result = CodecErrorFormatter.format(RuntimeException("x"))
        assertNotNull(result)
        assertTrue(result.contains("x"))
        assertTrue(result.contains("RuntimeException"))
    }

    @Test
    fun format_blankContext_isOmitted() {
        val result = CodecErrorFormatter.format(RuntimeException("x"), context = "   ")
        assertFalse(result.startsWith(" |"))
        assertFalse(result.startsWith("|"))
    }
}
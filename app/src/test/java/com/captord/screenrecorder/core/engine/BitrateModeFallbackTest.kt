package com.captord.screenrecorder.core.engine

import org.junit.Assert.assertEquals
import org.junit.Test

class BitrateModeFallbackTest {

    @Test
    fun primarySucceeds_returnsPrimaryResult() {
        var fallbacks = 0
        var retries = 0
        val result = BitrateModeFallback.withCbrFallback(
            primary = { "primary" },
            fallback = { retries++; "fallback" },
            onFallback = { fallbacks++ },
        )
        assertEquals("primary", result)
        assertEquals(0, retries)
        assertEquals(0, fallbacks)
    }

    @Test
    fun primaryThrowsIllegalArgument_callsFallbackAndOnFallback() {
        var fallbacks = 0
        var retries = 0
        val result = BitrateModeFallback.withCbrFallback(
            primary = { throw IllegalArgumentException("VBR not supported") },
            fallback = { retries++; "fallback-ok" },
            onFallback = { reason ->
                fallbacks++
                assertEquals(
                    "Reason should mention CBR fallback",
                    true,
                    reason.contains("CBR"),
                )
            },
        )
        assertEquals("fallback-ok", result)
        assertEquals(1, retries)
        assertEquals(1, fallbacks)
    }

    @Test
    fun primaryThrowsIllegalState_propagatesWithoutFallback() {
        var fallbacks = 0
        var retries = 0
        try {
            BitrateModeFallback.withCbrFallback(
                primary = { throw IllegalStateException("encoder in wrong state") },
                fallback = { retries++; "should not run" },
                onFallback = { fallbacks++ },
            )
            org.junit.Assert.fail("Expected IllegalStateException")
        } catch (e: IllegalStateException) {
            assertEquals("encoder in wrong state", e.message)
        }
        assertEquals(0, retries)
        assertEquals(0, fallbacks)
    }

    @Test
    fun primaryThrowsRuntime_propagatesWithoutFallback() {
        var fallbacks = 0
        var retries = 0
        try {
            BitrateModeFallback.withCbrFallback(
                primary = { throw RuntimeException("muxer blew up") },
                fallback = { retries++; "should not run" },
                onFallback = { fallbacks++ },
            )
            org.junit.Assert.fail("Expected RuntimeException")
        } catch (e: RuntimeException) {
            assertEquals("muxer blew up", e.message)
        }
        assertEquals(0, retries)
        assertEquals(0, fallbacks)
    }

    @Test
    fun fallbackThrows_propagatesFallbackException() {
        var fallbacks = 0
        try {
            BitrateModeFallback.withCbrFallback(
                primary = { throw IllegalArgumentException("VBR") },
                fallback = { throw RuntimeException("CBR also failed") },
                onFallback = { fallbacks++ },
            )
            org.junit.Assert.fail("Expected RuntimeException from fallback")
        } catch (e: RuntimeException) {
            assertEquals("CBR also failed", e.message)
        }
        // onFallback should still fire even though fallback threw.
        assertEquals(1, fallbacks)
    }
}
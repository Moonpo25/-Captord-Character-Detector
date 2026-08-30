package com.captord.screenrecorder.core.engine

import android.media.MediaCodec.CodecException

/**
 * Render MediaCodec exceptions into a structured, human-readable string.
 *
 * Some OEM OMX stacks throw CodecException with a null message but carry
 * diagnostic data via [CodecException.getErrorCode] and
 * [CodecException.getDiagnosticInfo]. The previous implementation only
 * surfaced `t.message`, which produced strings like "Failed to start
 * recording: " — useless to the user and to issue triage.
 *
 * The formatter walks the cause chain and includes the codec error code
 * when available, so the UI error banner can show the actual failure mode
 * (e.g. "errorCode=0xffffec74, cause=IllegalArgumentException: ...").
 */
object CodecErrorFormatter {

    private const val MAX_CAUSE_DEPTH = 3

    fun format(t: Throwable, context: String = ""): String {
        val parts = mutableListOf<String>()
        if (context.isNotBlank()) parts.add(context)
        val message = t.message?.takeIf { it.isNotBlank() }
        when {
            t is CodecException -> {
                parts.add("CodecException")
                parts.add("errorCode=${t.errorCode}")
                t.diagnosticInfo?.takeIf { it.isNotBlank() }?.let { parts.add("diagnostic=\"$it\"") }
                message?.let { parts.add("message=\"$it\"") }
            }
            message != null -> {
                parts.add(t.javaClass.simpleName)
                parts.add("message=\"$message\"")
            }
            else -> {
                parts.add(t.javaClass.simpleName)
            }
        }
        var cause: Throwable? = t.cause
        var depth = 0
        while (cause != null && depth < MAX_CAUSE_DEPTH) {
            val causeMessage = cause.message?.takeIf { it.isNotBlank() }
            if (causeMessage != null) {
                parts.add("cause=${cause.javaClass.simpleName}: \"$causeMessage\"")
            } else {
                parts.add("cause=${cause.javaClass.simpleName}")
            }
            cause = cause.cause
            depth++
        }
        return parts.joinToString(" | ")
    }
}
package com.captord.screenrecorder.core.engine

/**
 * Retry helper for OEM encoder quirks.
 *
 * Some OEM MediaCodec encoders (notably a few Qualcomm OMX stacks on
 * Android 16 + crDroid 12.11, and some Exynos variants) reject
 * `MediaCodecInfo.EncoderCapabilities.BITRATE_MODE_VBR` with an
 * [IllegalArgumentException] during `MediaCodec.configure()`. The advanced
 * engine previously surfaced this as "Failed to start advanced recording:"
 * with the rest of the message empty — see GitHub issue #2.
 *
 * Strategy: try the encoder with whatever bitrate mode the caller put on
 * the format; if configure throws [IllegalArgumentException], the fallback
 * lambda runs. The fallback is expected to switch the format's
 * `KEY_BITRATE_MODE` to `BITRATE_MODE_CBR` before retrying. [onFallback]
 * fires once between the two attempts so the UI can surface that the
 * quality knob is now degraded.
 *
 * Any other exception type (IllegalStateException, MediaCodec.CodecException,
 * …) is not retried — those indicate a deeper encoder failure that CBR
 * won't fix. The fallback's own exception propagates unchanged.
 */
object BitrateModeFallback {

    fun <T> withCbrFallback(
        primary: () -> T,
        fallback: () -> T,
        onFallback: (String) -> Unit,
    ): T {
        return try {
            primary()
        } catch (e: IllegalArgumentException) {
            onFallback("Bitrate mode fell back to CBR (VBR not supported by this encoder)")
            fallback()
        }
    }
}
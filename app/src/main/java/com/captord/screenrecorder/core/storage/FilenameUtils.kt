package com.captord.screenrecorder.core.storage

object FilenameUtils {
    private val illegalChars = Regex("[\\\\/:*?\"<>|\\x00-\\x1F]")

    fun sanitizePrefix(rawPrefix: String, fallback: String = "Captord"): String {
        val sanitized = rawPrefix.replace(illegalChars, "").trim()
        return sanitized.ifEmpty { fallback }
    }
}

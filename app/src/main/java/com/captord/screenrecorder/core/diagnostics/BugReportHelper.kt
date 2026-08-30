package com.captord.screenrecorder.core.diagnostics

import android.content.Context
import android.content.Intent
import android.os.Build

/**
 * Builds an ACTION_SEND intent for bug reports. The body is a structured
 * prefill (app version, Android version, device model) that the user can
 * edit and extend before sending. The intent targets mail-style apps so
 * the report leaves the device through the user's chosen channel — no
 * network upload is initiated by Captord itself.
 */
object BugReportHelper {

    private const val MAILTO_FALLBACK = "mailto:"

    fun build(context: Context, packageName: String = context.packageName): Intent {
        val appVersion = readAppVersion(context, packageName)
        val deviceSummary = buildString {
            appendLine("Device: ${Build.MANUFACTURER} ${Build.MODEL}")
            appendLine("Android: ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
            appendLine("Build: ${Build.FINGERPRINT}")
        }
        val body = buildString {
            appendLine("Captord bug report")
            appendLine()
            appendLine("App version: $appVersion")
            appendLine(deviceSummary)
            appendLine()
            appendLine("Describe the issue:")
            appendLine("(paste logcat with: adb logcat -d -s MediaCodec:* AdvancedRecorder:* StandardRecorder:* ScreenRecorderService:*)")
        }
        return Intent(Intent.ACTION_SEND).apply {
            type = "message/rfc822"
            putExtra(Intent.EXTRA_SUBJECT, "Captord bug report")
            putExtra(Intent.EXTRA_TEXT, body)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    }

    private fun readAppVersion(context: Context, packageName: String): String {
        return try {
            val info = context.packageManager.getPackageInfo(packageName, 0)
            "${info.versionName} (${info.longVersionCodeCompat()})"
        } catch (_: Exception) {
            "unknown"
        }
    }

    private fun android.content.pm.PackageInfo.longVersionCodeCompat(): Long =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) longVersionCode else versionCode.toLong()
}
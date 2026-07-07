package com.captord.screenrecorder

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.captord.screenrecorder.core.capability.CapabilityChecker
import com.captord.screenrecorder.core.service.ScreenRecorderService
import com.captord.screenrecorder.core.settings.SettingsRepository
import com.captord.screenrecorder.feature.recorder.model.RecordingConfig
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Translucent activity for MediaProjection consent from QS Tile / App Shortcuts
 * without bringing the full Compose main UI to the foreground.
 */
class TransparentPermissionActivity : ComponentActivity() {

    private val mediaProjectionLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == Activity.RESULT_OK && result.data != null) {
                lifecycleScope.launch {
                    val config = loadConfig()
                    ScreenRecorderService.startRecording(
                        this@TransparentPermissionActivity,
                        config,
                        result.resultCode,
                        result.data!!,
                    )
                    finish()
                }
            } else {
                finish()
            }
        }

    private val permissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { grants ->
            val audioDenied = grants[Manifest.permission.RECORD_AUDIO] == false
            if (audioDenied) {
                finish()
            } else {
                launchCapture()
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        when (intent?.action) {
            ACTION_SHORTCUT_STOP, ACTION_STOP -> {
                if (ScreenRecorderService.isSessionActive()) {
                    ScreenRecorderService.sendAction(this, ScreenRecorderService.ACTION_STOP)
                }
                finish()
                return
            }
            else -> {
                // start path (tile / shortcut start)
                if (ScreenRecorderService.isSessionActive()) {
                    ScreenRecorderService.sendAction(this, ScreenRecorderService.ACTION_STOP)
                    finish()
                    return
                }
                requestRuntimeThenCapture()
            }
        }
    }

    private fun requestRuntimeThenCapture() {
        lifecycleScope.launch {
            val config = loadConfig()
            val needed = mutableListOf<String>()
            if (config.audioEnabled &&
                ContextCompat.checkSelfPermission(
                    this@TransparentPermissionActivity,
                    Manifest.permission.RECORD_AUDIO,
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                needed += Manifest.permission.RECORD_AUDIO
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                ContextCompat.checkSelfPermission(
                    this@TransparentPermissionActivity,
                    Manifest.permission.POST_NOTIFICATIONS,
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                needed += Manifest.permission.POST_NOTIFICATIONS
            }
            if (needed.isNotEmpty()) {
                permissionLauncher.launch(needed.toTypedArray())
            } else {
                launchCapture()
            }
        }
    }

    private fun launchCapture() {
        val pm = getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        mediaProjectionLauncher.launch(pm.createScreenCaptureIntent())
    }

    private suspend fun loadConfig(): RecordingConfig {
        val settings = SettingsRepository.get(this).settings.first()
        val capability = CapabilityChecker.probe(this)
        return CapabilityChecker.clampConfig(settings.toRecordingConfig(), capability)
    }

    companion object {
        const val ACTION_SHORTCUT_START = "com.captord.screenrecorder.ACTION_SHORTCUT_START"
        const val ACTION_SHORTCUT_STOP = "com.captord.screenrecorder.ACTION_SHORTCUT_STOP"
        const val ACTION_START = "com.captord.screenrecorder.ACTION_TRANSPARENT_START"
        const val ACTION_STOP = "com.captord.screenrecorder.ACTION_TRANSPARENT_STOP"
    }
}

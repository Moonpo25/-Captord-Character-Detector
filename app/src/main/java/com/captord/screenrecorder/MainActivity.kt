package com.captord.screenrecorder

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.captord.screenrecorder.core.settings.AppSettings
import com.captord.screenrecorder.feature.recorder.RecordingViewModel
import com.captord.screenrecorder.feature.settings.SettingsViewModel
import com.captord.screenrecorder.ui.navigation.CaptordNavHost
import com.captord.screenrecorder.ui.theme.CaptordTheme
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private val recordingViewModel: RecordingViewModel by viewModels()

    private val mediaProjectionLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == Activity.RESULT_OK && result.data != null) {
                lifecycleScope.launch {
                    val config = recordingViewModel.loadRecordingConfig()
                    recordingViewModel.startRecording(
                        config = config,
                        resultCode = result.resultCode,
                        projectionData = result.data!!,
                    )
                }
            } else {
                recordingViewModel.notifyProjectionDenied()
            }
        }

    private val permissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { grants ->
            val audioOk = grants[Manifest.permission.RECORD_AUDIO] != false
            if (audioOk) {
                recordingViewModel.beginStartSequence(::launchMediaProjectionConsent)
            } else {
                recordingViewModel.notifyProjectionDenied()
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val settingsViewModel: SettingsViewModel = viewModel()
            val settings by settingsViewModel.settings.collectAsStateWithLifecycle()
            val systemDark = isSystemInDarkTheme()
            val darkTheme = when (settings.themeMode) {
                AppSettings.THEME_LIGHT -> false
                AppSettings.THEME_DARK -> true
                else -> systemDark
            }
            CaptordTheme(
                darkTheme = darkTheme,
                dynamicColor = settings.dynamicColorEnabled,
            ) {
                CaptordNavHost(
                    onRequestStartRecording = ::requestStartRecording,
                    recordingViewModel = recordingViewModel,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
    }

    private fun requestStartRecording() {
        val needed = mutableListOf<String>()
        val audioNeeded = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.RECORD_AUDIO,
        ) != PackageManager.PERMISSION_GRANTED

        // Only request RECORD_AUDIO when settings want audio — always request for phase1 default true;
        // settings-driven: still request if currently granted not needed when audio off.
        lifecycleScope.launch {
            val config = recordingViewModel.loadRecordingConfig()
            if (config.audioEnabled && audioNeeded) {
                needed += Manifest.permission.RECORD_AUDIO
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                ContextCompat.checkSelfPermission(
                    this@MainActivity,
                    Manifest.permission.POST_NOTIFICATIONS,
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                needed += Manifest.permission.POST_NOTIFICATIONS
            }

            if (needed.isNotEmpty()) {
                permissionLauncher.launch(needed.toTypedArray())
            } else {
                recordingViewModel.beginStartSequence(::launchMediaProjectionConsent)
            }
        }
    }

    private fun launchMediaProjectionConsent() {
        val projectionManager =
            getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        val captureIntent: Intent = projectionManager.createScreenCaptureIntent()
        mediaProjectionLauncher.launch(captureIntent)
    }
}

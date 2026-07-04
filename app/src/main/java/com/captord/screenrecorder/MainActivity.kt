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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import com.captord.screenrecorder.feature.recorder.RecordingViewModel
import com.captord.screenrecorder.ui.navigation.CaptordNavHost
import com.captord.screenrecorder.ui.theme.CaptordTheme

class MainActivity : ComponentActivity() {

    private val recordingViewModel: RecordingViewModel by viewModels()

    private val mediaProjectionLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == Activity.RESULT_OK && result.data != null) {
                val config = recordingViewModel.phase1Config()
                recordingViewModel.startRecording(
                    config = config,
                    resultCode = result.resultCode,
                    projectionData = result.data!!,
                )
            } else {
                recordingViewModel.notifyProjectionDenied()
            }
        }

    private val permissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { grants ->
            val audioOk = grants[Manifest.permission.RECORD_AUDIO] != false
            val notifOk = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                grants[Manifest.permission.POST_NOTIFICATIONS] != false
            } else {
                true
            }
            if (audioOk) {
                launchMediaProjectionConsent()
            } else {
                recordingViewModel.notifyProjectionDenied()
            }
            // Notification denial is non-blocking; app controls remain available.
            if (!notifOk) {
                // proceed without notification visibility guarantee
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            CaptordTheme {
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
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            needed += Manifest.permission.RECORD_AUDIO
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            needed += Manifest.permission.POST_NOTIFICATIONS
        }

        if (needed.isNotEmpty()) {
            permissionLauncher.launch(needed.toTypedArray())
        } else {
            launchMediaProjectionConsent()
        }
    }

    private fun launchMediaProjectionConsent() {
        val projectionManager =
            getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        val captureIntent: Intent = projectionManager.createScreenCaptureIntent()
        mediaProjectionLauncher.launch(captureIntent)
    }
}

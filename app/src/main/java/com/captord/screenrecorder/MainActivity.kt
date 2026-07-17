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
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.captord.screenrecorder.core.settings.AppSettings
import com.captord.screenrecorder.feature.gallery.GalleryViewModel
import com.captord.screenrecorder.feature.recorder.RecordingViewModel
import com.captord.screenrecorder.feature.settings.SettingsViewModel
import com.captord.screenrecorder.ui.navigation.CaptordNavHost
import com.captord.screenrecorder.ui.theme.CaptordTheme
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private val recordingViewModel: RecordingViewModel by viewModels()
    private val galleryViewModel: GalleryViewModel by viewModels()

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
            if (audioOk || grants[Manifest.permission.RECORD_AUDIO] == null) {
                // If audio not requested, grant map may omit it
                if (grants.containsKey(Manifest.permission.RECORD_AUDIO) && !audioOk) {
                    recordingViewModel.notifyProjectionDenied()
                } else {
                    recordingViewModel.beginStartSequence(::launchMediaProjectionConsent)
                }
            } else {
                recordingViewModel.notifyProjectionDenied()
            }
        }

    private val deleteConfirmationLauncher =
        registerForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { result ->
            galleryViewModel.onDeleteConfirmationResult(result.resultCode)
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        galleryViewModel.onNeedDeleteConfirmation = { sender ->
            deleteConfirmationLauncher.launch(IntentSenderRequest.Builder(sender).build())
        }
        setContent {
            val settingsViewModel: SettingsViewModel = viewModel()
            val settings by settingsViewModel.settings.collectAsStateWithLifecycle()
            val systemDark = isSystemInDarkTheme()
            val darkTheme = when (settings.themeMode) {
                AppSettings.THEME_LIGHT -> false
                AppSettings.THEME_DARK -> true
                else -> systemDark
            }
            DisposableEffect(Unit) {
                onDispose {
                    galleryViewModel.onNeedDeleteConfirmation = null
                }
            }
            CaptordTheme(
                darkTheme = darkTheme,
                dynamicColor = settings.dynamicColorEnabled,
            ) {
                CaptordNavHost(
                    onRequestStartRecording = ::requestStartRecording,
                    recordingViewModel = recordingViewModel,
                    galleryViewModel = galleryViewModel,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
        openPreviewFromIntent(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        openPreviewFromIntent(intent)
    }

    private fun openPreviewFromIntent(intent: Intent?) {
        val path = intent?.getStringExtra(EXTRA_PREVIEW_PATH)?.takeIf { it.isNotBlank() } ?: return
        intent.removeExtra(EXTRA_PREVIEW_PATH)
        _pendingPreviewPath.value = path
    }

    companion object {
        const val EXTRA_PREVIEW_PATH = "extra_preview_path"

        private val _pendingPreviewPath = MutableStateFlow<String?>(null)
        val pendingPreviewPath: StateFlow<String?> = _pendingPreviewPath.asStateFlow()

        fun clearPendingPreviewPath() {
            _pendingPreviewPath.value = null
        }
    }

    private fun requestStartRecording() {
        val needed = mutableListOf<String>()
        val audioNeeded = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.RECORD_AUDIO,
        ) != PackageManager.PERMISSION_GRANTED

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
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                ContextCompat.checkSelfPermission(
                    this@MainActivity,
                    Manifest.permission.READ_MEDIA_VIDEO,
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                needed += Manifest.permission.READ_MEDIA_VIDEO
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

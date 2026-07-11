package com.captord.screenrecorder.ui.settings

import android.os.Build
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.AudioFile
import androidx.compose.material.icons.filled.ControlCamera
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.RadioButtonChecked
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.captord.screenrecorder.core.haptics.HapticFeedbackHelper
import com.captord.screenrecorder.core.haptics.rememberHapticFeedbackHelper
import com.captord.screenrecorder.core.overlay.FloatingOverlayManager
import com.captord.screenrecorder.core.settings.AppSettings
import com.captord.screenrecorder.feature.recorder.model.RecordingConfig
import com.captord.screenrecorder.feature.settings.SettingsViewModel
import com.captord.screenrecorder.ui.components.squishable
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel = viewModel(),
    modifier: Modifier = Modifier,
) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val capabilities by viewModel.capabilities.collectAsStateWithLifecycle()
    val qualityLocked by viewModel.qualityLocked.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val canOverlay = FloatingOverlayManager.canDrawOverlays(context)

    val haptic = rememberHapticFeedbackHelper()
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        viewModel.overlayPermissionEvents.collect { intent ->
            runCatching { context.startActivity(intent) }
        }
    }

    var sheet by remember { mutableStateOf<SettingsSheet?>(null) }
    var showPrefixDialog by remember { mutableStateOf(false) }
    var prefixDraft by remember { mutableStateOf(settings.filePrefix) }

    var showPrivacyDialog by remember { mutableStateOf(false) }

    var lastBitrateHapticValue by remember { mutableIntStateOf((settings.videoBitrate / 1_000_000).toInt()) }
    LaunchedEffect(settings.videoBitrate) {
        lastBitrateHapticValue = (settings.videoBitrate / 1_000_000).toInt()
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 16.dp)
            .padding(bottom = 96.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            "Settings",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Black,
            color = MaterialTheme.colorScheme.onSurface
        )

        if (qualityLocked) {
            Surface(
                color = MaterialTheme.colorScheme.errorContainer,
                shape = MaterialTheme.shapes.large,
                modifier = Modifier.fillMaxWidth(),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.3f))
            ) {
                Text(
                    text = "Configurations are locked during active recording sessions.",
                    modifier = Modifier.padding(16.dp),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                )
            }
        }

        // Section 1: Recording General
        ModernSectionCard(
            title = "Recording",
            icon = Icons.Filled.Tune
        ) {
            Text(
                text = "Engine",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(6.dp))
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                SegmentedButton(
                    selected = settings.engineType == RecordingConfig.ENGINE_STANDARD,
                    onClick = {
                        if (!qualityLocked) {
                            scope.launch { haptic.doubleClick() }
                            viewModel.setEngineType(RecordingConfig.ENGINE_STANDARD)
                            if (settings.audioSource != "mic") {
                                viewModel.setAudioSource("mic")
                            }
                        }
                    },
                    shape = SegmentedButtonDefaults.itemShape(0, 2),
                    enabled = !qualityLocked,
                ) { Text("Standard", fontWeight = FontWeight.Bold) }
                SegmentedButton(
                    selected = settings.engineType == RecordingConfig.ENGINE_ADVANCED,
                    onClick = {
                        if (!qualityLocked) {
                            scope.launch { haptic.doubleClick() }
                            viewModel.setEngineType(RecordingConfig.ENGINE_ADVANCED)
                        }
                    },
                    shape = SegmentedButtonDefaults.itemShape(1, 2),
                    enabled = !qualityLocked,
                ) { Text("Advanced", fontWeight = FontWeight.Bold) }
            }
            Text(
                text = if (settings.engineType == RecordingConfig.ENGINE_ADVANCED) {
                    "MediaCodec path: high FPS, internal audio, safer split."
                } else {
                    "Stable MediaRecorder path for screen + microphone."
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 6.dp, bottom = 12.dp),
            )

            ModernClickRow(
                title = "Countdown timer",
                summary = countdownLabel(settings.countdownSec),
                enabled = !qualityLocked,
                onClick = { sheet = SettingsSheet.Countdown }
            )
        }

        // Section 2: Video Configuration
        ModernSectionCard(
            title = "Video",
            icon = Icons.Filled.Videocam
        ) {
            ModernClickRow(
                title = "Frame rate",
                summary = "${settings.frameRate} fps",
                enabled = !qualityLocked,
                onClick = { sheet = SettingsSheet.FrameRate },
            )

            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Video codec",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(6.dp))
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                SegmentedButton(
                    selected = settings.videoCodec == "H264",
                    onClick = {
                        if (!qualityLocked) {
                            scope.launch { haptic.doubleClick() }
                            viewModel.setVideoCodec("H264")
                        }
                    },
                    shape = SegmentedButtonDefaults.itemShape(0, 2),
                    enabled = !qualityLocked,
                ) { Text("H.264", fontWeight = FontWeight.Bold) }
                SegmentedButton(
                    selected = settings.videoCodec == "H265",
                    onClick = {
                        if (!qualityLocked && capabilities.supportsH265) {
                            scope.launch { haptic.doubleClick() }
                            viewModel.setVideoCodec("H265")
                        }
                    },
                    shape = SegmentedButtonDefaults.itemShape(1, 2),
                    enabled = !qualityLocked && capabilities.supportsH265,
                ) { Text("H.265", fontWeight = FontWeight.Bold) }
            }
            if (!capabilities.supportsH265) {
                Text(
                    "H.265 (HEVC) is not supported by your hardware profile.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(top = 4.dp, bottom = 8.dp),
                )
            }

            Spacer(modifier = Modifier.height(14.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "Video bitrate",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Surface(
                    color = MaterialTheme.colorScheme.primaryContainer,
                    shape = MaterialTheme.shapes.small
                ) {
                    Text(
                        "${settings.videoBitrate / 1_000_000} Mbps",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
                    )
                }
            }
            Slider(
                value = settings.videoBitrate.toFloat(),
                onValueChange = {
                    if (!qualityLocked) {
                        val stepped = ((it / 1_000_000f).roundToInt() * 1_000_000)
                            .coerceIn(RecordingConfig.MIN_VIDEO_BITRATE, RecordingConfig.MAX_VIDEO_BITRATE)
                        
                        val mbps = stepped / 1_000_000
                        if (mbps != lastBitrateHapticValue) {
                            lastBitrateHapticValue = mbps
                            scope.launch { haptic.performHaptic(HapticFeedbackHelper.HapticIntensity.TEXTURE_TICK) }
                        }
                        viewModel.setVideoBitrate(stepped)
                    }
                },
                valueRange = RecordingConfig.MIN_VIDEO_BITRATE.toFloat()..RecordingConfig.MAX_VIDEO_BITRATE.toFloat(),
                steps = 37,
                enabled = !qualityLocked,
                modifier = Modifier.padding(top = 4.dp)
            )

            Spacer(modifier = Modifier.height(8.dp))
            ModernClickRow(
                title = "Resolution short-side",
                summary = resolutionLabel(settings.manualResolutionHeight),
                enabled = !qualityLocked,
                onClick = { sheet = SettingsSheet.Resolution },
            )
            ModernClickRow(
                title = "Orientation lock",
                summary = settings.orientationMode.replaceFirstChar { it.uppercase() },
                enabled = !qualityLocked,
                onClick = { sheet = SettingsSheet.Orientation },
            )
        }

        // Section 3: Audio Options
        ModernSectionCard(
            title = "Audio",
            icon = Icons.Filled.AudioFile
        ) {
            ModernSwitchRow(
                title = "Record audio track",
                checked = settings.audioEnabled,
                enabled = !qualityLocked,
                onCheckedChange = {
                    scope.launch { haptic.doubleClick() }
                    viewModel.setAudioEnabled(it)
                }
            )

            if (settings.audioEnabled) {
                val advanced = settings.engineType == RecordingConfig.ENGINE_ADVANCED
                val internalOk = advanced && capabilities.supportsInternalAudio

                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = "Audio input source",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(6.dp))
                SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                    SegmentedButton(
                        selected = settings.audioSource == "mic",
                        onClick = {
                            if (!qualityLocked) {
                                scope.launch { haptic.doubleClick() }
                                viewModel.setAudioSource("mic")
                            }
                        },
                        shape = SegmentedButtonDefaults.itemShape(0, 3),
                        enabled = !qualityLocked,
                    ) { Text("Mic", fontWeight = FontWeight.Bold) }
                    SegmentedButton(
                        selected = settings.audioSource == "internal",
                        onClick = {
                            if (!qualityLocked && internalOk) {
                                scope.launch { haptic.doubleClick() }
                                viewModel.setAudioSource("internal")
                            }
                        },
                        shape = SegmentedButtonDefaults.itemShape(1, 3),
                        enabled = !qualityLocked && internalOk,
                    ) { Text("System", fontWeight = FontWeight.Bold) }
                    SegmentedButton(
                        selected = settings.audioSource == "both",
                        onClick = {
                            if (!qualityLocked && internalOk) {
                                scope.launch { haptic.doubleClick() }
                                viewModel.setAudioSource("both")
                            }
                        },
                        shape = SegmentedButtonDefaults.itemShape(2, 3),
                        enabled = !qualityLocked && internalOk,
                    ) { Text("Both", fontWeight = FontWeight.Bold) }
                }
                Text(
                    text = when {
                        !advanced -> "System/Both options require Advanced engine."
                        !capabilities.supportsInternalAudio -> "System audio capture requires Android 10+."
                        else -> "Certain apps may block internal audio recording by policy."
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp, bottom = 12.dp),
                )

                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "Sample rate",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(6.dp))
                SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                    SegmentedButton(
                        selected = settings.sampleRate == 44_100,
                        onClick = {
                            if (!qualityLocked) {
                                scope.launch { haptic.doubleClick() }
                                viewModel.setSampleRate(44_100)
                            }
                        },
                        shape = SegmentedButtonDefaults.itemShape(0, 2),
                        enabled = !qualityLocked,
                    ) { Text("44.1 kHz", fontWeight = FontWeight.Bold) }
                    SegmentedButton(
                        selected = settings.sampleRate == 48_000,
                        onClick = {
                            if (!qualityLocked) {
                                scope.launch { haptic.doubleClick() }
                                viewModel.setSampleRate(48_000)
                            }
                        },
                        shape = SegmentedButtonDefaults.itemShape(1, 2),
                        enabled = !qualityLocked,
                    ) { Text("48 kHz", fontWeight = FontWeight.Bold) }
                }

                Spacer(modifier = Modifier.height(14.dp))
                Text(
                    text = "Channels",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(6.dp))
                SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                    SegmentedButton(
                        selected = settings.audioChannel == 1,
                        onClick = {
                            if (!qualityLocked) {
                                scope.launch { haptic.doubleClick() }
                                viewModel.setAudioChannel(1)
                            }
                        },
                        shape = SegmentedButtonDefaults.itemShape(0, 2),
                        enabled = !qualityLocked,
                    ) { Text("Mono", fontWeight = FontWeight.Bold) }
                    SegmentedButton(
                        selected = settings.audioChannel == 2,
                        onClick = {
                            if (!qualityLocked) {
                                scope.launch { haptic.doubleClick() }
                                viewModel.setAudioChannel(2)
                            }
                        },
                        shape = SegmentedButtonDefaults.itemShape(1, 2),
                        enabled = !qualityLocked,
                    ) { Text("Stereo", fontWeight = FontWeight.Bold) }
                }

                Spacer(modifier = Modifier.height(12.dp))
                ModernClickRow(
                    title = "Audio bitrate",
                    summary = "${settings.audioBitrate / 1000} Kbps",
                    enabled = !qualityLocked,
                    onClick = { sheet = SettingsSheet.AudioBitrate },
                )
            }
        }

        // Section 4: Controls
        ModernSectionCard(
            title = "Controls",
            icon = Icons.Filled.ControlCamera
        ) {
            ModernSwitchRow(
                title = "Floating overlay panel",
                checked = settings.showOverlay && canOverlay,
                enabled = !qualityLocked,
                onCheckedChange = {
                    scope.launch { haptic.doubleClick() }
                    viewModel.setShowOverlay(it)
                }
            )
            if (!canOverlay) {
                ModernClickRow(
                    title = "Overlay permission required",
                    summary = "Tap to grant display overlay capability",
                    enabled = true,
                    onClick = {
                        context.startActivity(
                            FloatingOverlayManager.createOverlaySettingsIntent(context),
                        )
                    },
                )
            }

            Spacer(modifier = Modifier.height(10.dp))
            ModernSwitchRow(
                title = "Show physical touches",
                checked = settings.showTouches,
                enabled = !qualityLocked && capabilities.supportsShowTouches,
                onCheckedChange = {
                    scope.launch { haptic.doubleClick() }
                    viewModel.setShowTouches(it)
                }
            )

            Spacer(modifier = Modifier.height(10.dp))
            ModernClickRow(
                title = "Maximum segment size",
                summary = if (settings.maxFileSizeMb == 0) "Unlimited" else "${settings.maxFileSizeMb} MB",
                enabled = !qualityLocked,
                onClick = { sheet = SettingsSheet.MaxFileSize },
            )
            ModernClickRow(
                title = "Output file prefix",
                summary = settings.filePrefix,
                enabled = !qualityLocked,
                onClick = {
                    prefixDraft = settings.filePrefix
                    showPrefixDialog = true
                },
            )
        }

        // Section 5: Files
        ModernSectionCard(
            title = "Files",
            icon = Icons.Filled.Folder
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        "Save path",
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        AppSettings.SAVE_LOCATION_DISPLAY,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        // Section 6: Appearance
        ModernSectionCard(
            title = "Appearance",
            icon = Icons.Filled.Palette
        ) {
            Text(
                text = "Color scheme theme",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(6.dp))
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                listOf(
                    AppSettings.THEME_SYSTEM to "System",
                    AppSettings.THEME_LIGHT to "Light",
                    AppSettings.THEME_DARK to "Dark",
                ).forEachIndexed { index, (value, label) ->
                    SegmentedButton(
                        selected = settings.themeMode == value,
                        onClick = {
                            scope.launch { haptic.doubleClick() }
                            viewModel.setThemeMode(value)
                        },
                        shape = SegmentedButtonDefaults.itemShape(index, 3),
                    ) { Text(label, fontWeight = FontWeight.Bold) }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))
            ModernSwitchRow(
                title = "Material You dynamic color",
                checked = settings.dynamicColorEnabled,
                enabled = capabilities.supportsDynamicColor || Build.VERSION.SDK_INT >= Build.VERSION_CODES.S,
                onCheckedChange = {
                    scope.launch { haptic.doubleClick() }
                    viewModel.setDynamicColorEnabled(it)
                }
            )
        }

        // Section 7: Privacy & Details
        ModernSectionCard(
            title = "Privacy & App Info",
            icon = Icons.Filled.Info
        ) {
            ModernClickRow(
                title = "Privacy policy",
                summary = "All audio and video are processed locally on-device.",
                enabled = true,
                onClick = { showPrivacyDialog = true }
            )
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp)
            ) {
                Column {
                    Text(
                        "Hardware features profile",
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        "Android SDK API ${capabilities.androidApi} · " +
                            "H.265 ${if (capabilities.supportsH265) "Supported" else "Unsupported"} · " +
                            "FPS [${capabilities.supportedFrameRates.joinToString()}]",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }

    sheet?.let { current ->
        val options = when (current) {
            SettingsSheet.Countdown -> AppSettings.COUNTDOWN_OPTIONS.map {
                it to countdownLabel(it)
            }
            SettingsSheet.FrameRate -> capabilities.supportedFrameRates.map { it to "$it fps" }
            SettingsSheet.Resolution -> AppSettings.RESOLUTION_OPTIONS.map {
                it to resolutionLabel(it)
            }
            SettingsSheet.Orientation -> listOf(
                "auto" to "Auto adaptive",
                "portrait" to "Portrait forced",
                "landscape" to "Landscape forced",
            )
            SettingsSheet.AudioBitrate -> RecordingConfig.ALLOWED_AUDIO_BITRATES.sorted().map {
                it to "${it / 1000} Kbps"
            }
            SettingsSheet.MaxFileSize -> AppSettings.MAX_FILE_SIZE_OPTIONS.map {
                it to if (it == 0) "Unlimited (Off)" else "$it MB"
            }
        }
        val selected = when (current) {
            SettingsSheet.Countdown -> settings.countdownSec
            SettingsSheet.FrameRate -> settings.frameRate
            SettingsSheet.Resolution -> settings.manualResolutionHeight
            SettingsSheet.Orientation -> settings.orientationMode
            SettingsSheet.AudioBitrate -> settings.audioBitrate
            SettingsSheet.MaxFileSize -> settings.maxFileSizeMb
        }
        ModalBottomSheet(
            onDismissRequest = { sheet = null },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        ) {
            Text(
                text = current.title,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp),
            )
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                options.forEach { (value, label) ->
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = if (value == selected) {
                            MaterialTheme.colorScheme.primaryContainer
                        } else {
                            MaterialTheme.colorScheme.surface
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .squishable()
                            .clickable {
                                scope.launch { haptic.doubleClick() }
                                when (current) {
                                    SettingsSheet.Countdown -> viewModel.setCountdownSec(value as Int)
                                    SettingsSheet.FrameRate -> viewModel.setFrameRate(value as Int)
                                    SettingsSheet.Resolution -> viewModel.setManualResolutionHeight(value as Int)
                                    SettingsSheet.Orientation -> viewModel.setOrientationMode(value as String)
                                    SettingsSheet.AudioBitrate -> viewModel.setAudioBitrate(value as Int)
                                    SettingsSheet.MaxFileSize -> viewModel.setMaxFileSizeMb(value as Int)
                                }
                                sheet = null
                            }
                            .padding(vertical = 4.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Start,
                        ) {
                            RadioButton(
                                selected = value == selected,
                                onClick = null,
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(
                                text = label,
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = if (value == selected) FontWeight.Bold else FontWeight.Normal,
                                color = if (value == selected) {
                                    MaterialTheme.colorScheme.onPrimaryContainer
                                } else {
                                    MaterialTheme.colorScheme.onSurface
                                }
                            )
                        }
                    }
                }
            }
            Spacer(Modifier.height(32.dp))
        }
    }

    if (showPrefixDialog) {
        AlertDialog(
            onDismissRequest = { showPrefixDialog = false },
            title = { Text("Output prefix", fontWeight = FontWeight.Bold) },
            text = {
                OutlinedTextField(
                    value = prefixDraft,
                    onValueChange = { prefixDraft = it },
                    singleLine = true,
                    label = { Text("File Prefix") },
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        scope.launch { haptic.click() }
                        viewModel.setFilePrefix(prefixDraft)
                        showPrefixDialog = false
                    },
                ) { Text("Save", fontWeight = FontWeight.Bold) }
            },
            dismissButton = {
                TextButton(onClick = { showPrefixDialog = false }) {
                    Text("Cancel", fontWeight = FontWeight.Bold)
                }
            },
        )
    }

    if (showPrivacyDialog) {
        AlertDialog(
            onDismissRequest = { showPrivacyDialog = false },
            icon = { Icon(Icons.Filled.RadioButtonChecked, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
            title = { Text("Privacy Policy", fontWeight = FontWeight.Bold) },
            text = {
                Text(
                    text = "Captord processes all screen capturing and audio streams local-only. " +
                        "No data is transmitted off your physical device, and no external analytics or trackers are bundled. " +
                        "All output video files are stored locally in your Movies directory.\n\n" +
                        "Your screen and audio content belong purely to you.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            },
            confirmButton = {
                TextButton(onClick = { showPrivacyDialog = false }) {
                    Text("Close", fontWeight = FontWeight.Bold)
                }
            }
        )
    }
}

@Composable
private fun ModernSectionCard(
    title: String,
    icon: ImageVector,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f))
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(bottom = 16.dp)
            ) {
                Surface(
                    modifier = Modifier.size(40.dp),
                    shape = MaterialTheme.shapes.medium,
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = icon,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Black,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
            content()
        }
    }
}

@Composable
private fun ModernClickRow(
    title: String,
    summary: String,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    val haptic = rememberHapticFeedbackHelper()
    val scope = rememberCoroutineScope()

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.large)
            .then(
                if (enabled) {
                    Modifier
                        .squishable(scaleDown = 0.98f)
                        .clickable {
                            scope.launch { haptic.click() }
                            onClick()
                        }
                } else Modifier
            )
            .padding(vertical = 12.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                color = if (enabled) {
                    MaterialTheme.colorScheme.onSurface
                } else {
                    MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                }
            )
            Text(
                summary,
                style = MaterialTheme.typography.bodySmall,
                color = if (enabled) {
                    MaterialTheme.colorScheme.onSurfaceVariant
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                }
            )
        }
        if (enabled) {
            Icon(
                imageVector = Icons.Default.ArrowDropDown,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

@Composable
private fun ModernSwitchRow(
    title: String,
    checked: Boolean,
    enabled: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            title,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Medium,
            color = if (enabled) {
                MaterialTheme.colorScheme.onSurface
            } else {
                MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
            }
        )
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            enabled = enabled,
        )
    }
}

private fun countdownLabel(sec: Int): String = if (sec == 0) "Off" else "$sec seconds"

private fun resolutionLabel(height: Int): String = when (height) {
    0 -> "Auto adaptive"
    else -> "${height}p"
}

private enum class SettingsSheet(val title: String) {
    Countdown("Select Countdown Timer"),
    FrameRate("Select Frame Rate"),
    Resolution("Select Resolution"),
    Orientation("Select Orientation Mode"),
    AudioBitrate("Select Audio Bitrate"),
    MaxFileSize("Select Auto Split Size"),
}

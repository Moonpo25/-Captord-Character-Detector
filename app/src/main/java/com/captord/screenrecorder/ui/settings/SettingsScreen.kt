package com.captord.screenrecorder.ui.settings

import android.os.Build
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ListItem
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.captord.screenrecorder.core.settings.AppSettings
import com.captord.screenrecorder.feature.recorder.model.RecordingConfig
import com.captord.screenrecorder.feature.settings.SettingsViewModel
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

    var sheet by remember { mutableStateOf<SettingsSheet?>(null) }
    var showPrefixDialog by remember { mutableStateOf(false) }
    var prefixDraft by remember { mutableStateOf(settings.filePrefix) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 12.dp)
            .padding(bottom = 88.dp),
    ) {
        Text("Settings", style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(12.dp))

        if (qualityLocked) {
            Surface(
                color = MaterialTheme.colorScheme.secondaryContainer,
                shape = MaterialTheme.shapes.medium,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    text = "Stop recording to change quality settings.",
                    modifier = Modifier.padding(12.dp),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                )
            }
            Spacer(Modifier.height(12.dp))
        }

        SectionLabel("Recording")
        SettingLabel("Engine")
        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
            SegmentedButton(
                selected = settings.engineType == RecordingConfig.ENGINE_STANDARD,
                onClick = {
                    if (!qualityLocked) viewModel.setEngineType(RecordingConfig.ENGINE_STANDARD)
                },
                shape = SegmentedButtonDefaults.itemShape(0, 2),
                enabled = !qualityLocked,
            ) { Text("Standard") }
            SegmentedButton(
                selected = settings.engineType == RecordingConfig.ENGINE_ADVANCED,
                onClick = { },
                shape = SegmentedButtonDefaults.itemShape(1, 2),
                enabled = false,
            ) { Text("Advanced") }
        }
        Text(
            "Advanced engine arrives in a later phase.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp, bottom = 8.dp),
        )
        ClickRow(
            title = "Countdown",
            summary = countdownLabel(settings.countdownSec),
            enabled = !qualityLocked,
            onClick = { sheet = SettingsSheet.Countdown },
        )

        SectionLabel("Video")
        ClickRow(
            title = "Frame rate",
            summary = "${settings.frameRate} fps",
            enabled = !qualityLocked,
            onClick = { sheet = SettingsSheet.FrameRate },
        )
        SettingLabel("Codec")
        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
            SegmentedButton(
                selected = settings.videoCodec == "H264",
                onClick = { if (!qualityLocked) viewModel.setVideoCodec("H264") },
                shape = SegmentedButtonDefaults.itemShape(0, 2),
                enabled = !qualityLocked,
            ) { Text("H.264") }
            SegmentedButton(
                selected = settings.videoCodec == "H265",
                onClick = {
                    if (!qualityLocked && capabilities.supportsH265) {
                        viewModel.setVideoCodec("H265")
                    }
                },
                shape = SegmentedButtonDefaults.itemShape(1, 2),
                enabled = !qualityLocked && capabilities.supportsH265,
            ) { Text("H.265") }
        }
        if (!capabilities.supportsH265) {
            Text(
                "H.265 is not supported on this device.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 8.dp),
            )
        }
        SettingLabel("Video bitrate")
        Text(
            "${settings.videoBitrate / 1_000_000} Mbps",
            style = MaterialTheme.typography.bodyMedium,
        )
        Slider(
            value = settings.videoBitrate.toFloat(),
            onValueChange = {
                if (!qualityLocked) {
                    val stepped = ((it / 1_000_000f).roundToInt() * 1_000_000)
                        .coerceIn(RecordingConfig.MIN_VIDEO_BITRATE, RecordingConfig.MAX_VIDEO_BITRATE)
                    viewModel.setVideoBitrate(stepped)
                }
            },
            valueRange = RecordingConfig.MIN_VIDEO_BITRATE.toFloat()..
                RecordingConfig.MAX_VIDEO_BITRATE.toFloat(),
            steps = 37,
            enabled = !qualityLocked,
        )
        ClickRow(
            title = "Resolution",
            summary = resolutionLabel(settings.manualResolutionHeight),
            enabled = !qualityLocked,
            onClick = { sheet = SettingsSheet.Resolution },
        )
        ClickRow(
            title = "Orientation",
            summary = settings.orientationMode.replaceFirstChar { it.uppercase() },
            enabled = !qualityLocked,
            onClick = { sheet = SettingsSheet.Orientation },
        )

        SectionLabel("Audio")
        SwitchRow(
            title = "Record audio",
            checked = settings.audioEnabled,
            enabled = !qualityLocked,
            onCheckedChange = viewModel::setAudioEnabled,
        )
        if (settings.audioEnabled) {
            SettingLabel("Source")
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                SegmentedButton(
                    selected = settings.audioSource == "mic",
                    onClick = { if (!qualityLocked) viewModel.setAudioSource("mic") },
                    shape = SegmentedButtonDefaults.itemShape(0, 3),
                    enabled = !qualityLocked,
                ) { Text("Mic") }
                SegmentedButton(
                    selected = settings.audioSource == "internal",
                    onClick = { },
                    shape = SegmentedButtonDefaults.itemShape(1, 3),
                    enabled = false,
                ) { Text("System") }
                SegmentedButton(
                    selected = settings.audioSource == "both",
                    onClick = { },
                    shape = SegmentedButtonDefaults.itemShape(2, 3),
                    enabled = false,
                ) { Text("Both") }
            }
            Text(
                "System/Both require Advanced engine (later phase).",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(vertical = 4.dp),
            )
            SettingLabel("Sample rate")
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                SegmentedButton(
                    selected = settings.sampleRate == 44_100,
                    onClick = { if (!qualityLocked) viewModel.setSampleRate(44_100) },
                    shape = SegmentedButtonDefaults.itemShape(0, 2),
                    enabled = !qualityLocked,
                ) { Text("44.1 kHz") }
                SegmentedButton(
                    selected = settings.sampleRate == 48_000,
                    onClick = { if (!qualityLocked) viewModel.setSampleRate(48_000) },
                    shape = SegmentedButtonDefaults.itemShape(1, 2),
                    enabled = !qualityLocked,
                ) { Text("48 kHz") }
            }
            SettingLabel("Channels")
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                SegmentedButton(
                    selected = settings.audioChannel == 1,
                    onClick = { if (!qualityLocked) viewModel.setAudioChannel(1) },
                    shape = SegmentedButtonDefaults.itemShape(0, 2),
                    enabled = !qualityLocked,
                ) { Text("Mono") }
                SegmentedButton(
                    selected = settings.audioChannel == 2,
                    onClick = { if (!qualityLocked) viewModel.setAudioChannel(2) },
                    shape = SegmentedButtonDefaults.itemShape(1, 2),
                    enabled = !qualityLocked,
                ) { Text("Stereo") }
            }
            ClickRow(
                title = "Audio bitrate",
                summary = "${settings.audioBitrate / 1000} Kbps",
                enabled = !qualityLocked,
                onClick = { sheet = SettingsSheet.AudioBitrate },
            )
        }

        SectionLabel("Controls")
        SwitchRow(
            title = "Floating overlay",
            checked = settings.showOverlay,
            enabled = !qualityLocked,
            onCheckedChange = viewModel::setShowOverlay,
            supporting = "Overlay UI arrives in a later phase; preference is saved.",
        )
        SwitchRow(
            title = "Show touches",
            checked = settings.showTouches,
            enabled = !qualityLocked && capabilities.supportsShowTouches,
            onCheckedChange = viewModel::setShowTouches,
            supporting = if (!capabilities.supportsShowTouches) {
                "Requires privileged system capability."
            } else {
                null
            },
        )
        ClickRow(
            title = "Auto split size",
            summary = if (settings.maxFileSizeMb == 0) "Off" else "${settings.maxFileSizeMb} MB",
            enabled = !qualityLocked,
            onClick = { sheet = SettingsSheet.MaxFileSize },
        )
        ClickRow(
            title = "File prefix",
            summary = settings.filePrefix,
            enabled = !qualityLocked,
            onClick = {
                prefixDraft = settings.filePrefix
                showPrefixDialog = true
            },
        )

        SectionLabel("Files")
        ListItem(
            headlineContent = { Text("Save location") },
            supportingContent = { Text(AppSettings.SAVE_LOCATION_DISPLAY) },
        )

        SectionLabel("Appearance")
        SettingLabel("Theme")
        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
            listOf(
                AppSettings.THEME_SYSTEM to "System",
                AppSettings.THEME_LIGHT to "Light",
                AppSettings.THEME_DARK to "Dark",
            ).forEachIndexed { index, (value, label) ->
                SegmentedButton(
                    selected = settings.themeMode == value,
                    onClick = { viewModel.setThemeMode(value) },
                    shape = SegmentedButtonDefaults.itemShape(index, 3),
                ) { Text(label) }
            }
        }
        Spacer(Modifier.height(8.dp))
        SwitchRow(
            title = "Dynamic color",
            checked = settings.dynamicColorEnabled,
            enabled = capabilities.supportsDynamicColor || Build.VERSION.SDK_INT >= Build.VERSION_CODES.S,
            onCheckedChange = viewModel::setDynamicColorEnabled,
            supporting = if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
                "Requires Android 12+"
            } else {
                null
            },
        )

        SectionLabel("Privacy & App")
        ListItem(
            headlineContent = { Text("Privacy policy") },
            supportingContent = { Text("Coming before public release") },
        )
        ListItem(
            headlineContent = { Text("Device capabilities") },
            supportingContent = {
                Text(
                    "API ${capabilities.androidApi} · " +
                        "H.265 ${if (capabilities.supportsH265) "yes" else "no"} · " +
                        "FPS ${capabilities.supportedFrameRates.joinToString()}",
                )
            },
        )
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
                "auto" to "Auto",
                "portrait" to "Portrait",
                "landscape" to "Landscape",
            )
            SettingsSheet.AudioBitrate -> RecordingConfig.ALLOWED_AUDIO_BITRATES.sorted().map {
                it to "${it / 1000} Kbps"
            }
            SettingsSheet.MaxFileSize -> AppSettings.MAX_FILE_SIZE_OPTIONS.map {
                it to if (it == 0) "Off" else "$it MB"
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
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )
            options.forEach { (value, label) ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            when (current) {
                                SettingsSheet.Countdown -> viewModel.setCountdownSec(value as Int)
                                SettingsSheet.FrameRate -> viewModel.setFrameRate(value as Int)
                                SettingsSheet.Resolution ->
                                    viewModel.setManualResolutionHeight(value as Int)
                                SettingsSheet.Orientation ->
                                    viewModel.setOrientationMode(value as String)
                                SettingsSheet.AudioBitrate ->
                                    viewModel.setAudioBitrate(value as Int)
                                SettingsSheet.MaxFileSize ->
                                    viewModel.setMaxFileSizeMb(value as Int)
                            }
                            sheet = null
                        }
                        .padding(horizontal = 8.dp),
                    horizontalArrangement = Arrangement.Start,
                ) {
                    RadioButton(
                        selected = value == selected,
                        onClick = null,
                    )
                    Text(
                        text = label,
                        modifier = Modifier.padding(start = 8.dp, top = 12.dp, bottom = 12.dp),
                    )
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }

    if (showPrefixDialog) {
        AlertDialog(
            onDismissRequest = { showPrefixDialog = false },
            title = { Text("File prefix") },
            text = {
                OutlinedTextField(
                    value = prefixDraft,
                    onValueChange = { prefixDraft = it },
                    singleLine = true,
                    label = { Text("Prefix") },
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.setFilePrefix(prefixDraft)
                        showPrefixDialog = false
                    },
                ) { Text("Save") }
            },
            dismissButton = {
                TextButton(onClick = { showPrefixDialog = false }) { Text("Cancel") }
            },
        )
    }
}

private enum class SettingsSheet(val title: String) {
    Countdown("Countdown"),
    FrameRate("Frame rate"),
    Resolution("Resolution"),
    Orientation("Orientation"),
    AudioBitrate("Audio bitrate"),
    MaxFileSize("Auto split size"),
}

@Composable
private fun SectionLabel(text: String) {
    Spacer(Modifier.height(16.dp))
    Text(
        text = text.uppercase(),
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
    )
    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
}

@Composable
private fun SettingLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        modifier = Modifier.padding(top = 8.dp, bottom = 4.dp),
    )
}

@Composable
private fun ClickRow(
    title: String,
    summary: String,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = { Text(summary) },
        modifier = Modifier
            .fillMaxWidth()
            .then(if (enabled) Modifier.clickable(onClick = onClick) else Modifier),
    )
}

@Composable
private fun SwitchRow(
    title: String,
    checked: Boolean,
    enabled: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    supporting: String? = null,
) {
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = supporting?.let { { Text(it) } },
        trailingContent = {
            Switch(
                checked = checked,
                onCheckedChange = onCheckedChange,
                enabled = enabled,
            )
        },
    )
}

private fun countdownLabel(sec: Int): String = if (sec == 0) "Off" else "$sec s"

private fun resolutionLabel(height: Int): String = when (height) {
    0 -> "Auto"
    else -> "${height}p"
}

package com.captord.screenrecorder.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.captord.screenrecorder.core.gallery.ShareHelper
import com.captord.screenrecorder.feature.gallery.GalleryViewModel
import com.captord.screenrecorder.feature.recorder.RecordingViewModel
import com.captord.screenrecorder.feature.recorder.model.RecordingSessionState
import com.captord.screenrecorder.ui.gallery.GalleryRow
import com.captord.screenrecorder.ui.theme.CaptordSemanticColors

@Composable
fun HomeScreen(
    viewModel: RecordingViewModel,
    galleryViewModel: GalleryViewModel,
    onRequestStart: () -> Unit,
    onOpenGallery: () -> Unit,
    onOpenPreview: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val countdownRemaining by viewModel.countdownRemaining.collectAsStateWithLifecycle()
    val galleryState by galleryViewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val dark = isSystemInDarkTheme()
    val recColor = if (dark) CaptordSemanticColors.recActiveDark else CaptordSemanticColors.recActiveLight
    val pauseColor = if (dark) CaptordSemanticColors.recPausedDark else CaptordSemanticColors.recPausedLight

    LaunchedEffect(uiState.sessionState, uiState.lastFinalPath) {
        if (uiState.sessionState == RecordingSessionState.IDLE) {
            galleryViewModel.refresh()
        }
    }

    val statusLabel = when (uiState.sessionState) {
        RecordingSessionState.IDLE -> "Idle"
        RecordingSessionState.COUNTDOWN -> "Countdown"
        RecordingSessionState.RECORDING -> "Recording"
        RecordingSessionState.PAUSED -> "Paused"
        RecordingSessionState.STOPPING -> "Stopping"
        RecordingSessionState.ERROR -> "Error"
    }

    val statusColor = when (uiState.sessionState) {
        RecordingSessionState.RECORDING -> recColor
        RecordingSessionState.PAUSED -> pauseColor
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp, vertical = 12.dp),
    ) {
        Text(
            text = "Captord",
            style = MaterialTheme.typography.headlineSmall,
        )
        Spacer(modifier = Modifier.height(16.dp))

        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // Shutter
            val shutterColor = when (uiState.sessionState) {
                RecordingSessionState.RECORDING -> recColor
                RecordingSessionState.PAUSED -> pauseColor
                else -> MaterialTheme.colorScheme.primary
            }
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(if (uiState.sessionState == RecordingSessionState.RECORDING) 72.dp else 64.dp)
                    .clip(CircleShape)
                    .background(shutterColor)
                    .clickable {
                        when (uiState.sessionState) {
                            RecordingSessionState.IDLE, RecordingSessionState.ERROR -> onRequestStart()
                            RecordingSessionState.RECORDING, RecordingSessionState.PAUSED ->
                                viewModel.stopRecording()
                            else -> Unit
                        }
                    },
            ) {
                Text(
                    text = when (uiState.sessionState) {
                        RecordingSessionState.RECORDING, RecordingSessionState.PAUSED -> "■"
                        RecordingSessionState.COUNTDOWN -> countdownRemaining.toString()
                        else -> "●"
                    },
                    color = MaterialTheme.colorScheme.onPrimary,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                )
            }

            Spacer(Modifier.height(16.dp))

            // Status strip
            Surface(
                shape = MaterialTheme.shapes.large,
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Spacer(
                        modifier = Modifier
                            .size(10.dp)
                            .clip(CircleShape)
                            .background(statusColor),
                    )
                    Text(
                        text = statusLabel,
                        color = statusColor,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = if (uiState.sessionState == RecordingSessionState.COUNTDOWN) {
                            countdownRemaining.toString()
                        } else {
                            formatDuration(uiState.durationMs)
                        },
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                    )
                    if (uiState.fileSizeBytes > 0) {
                        Text(
                            text = formatFileSize(uiState.fileSizeBytes),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            Spacer(Modifier.height(12.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                when (uiState.sessionState) {
                    RecordingSessionState.IDLE, RecordingSessionState.ERROR -> {
                        Button(onClick = onRequestStart) { Text("Start") }
                    }
                    RecordingSessionState.COUNTDOWN -> {
                        OutlinedButton(onClick = viewModel::cancelCountdown) { Text("Cancel") }
                    }
                    RecordingSessionState.RECORDING -> {
                        OutlinedButton(onClick = viewModel::pauseRecording) { Text("Pause") }
                        Button(onClick = viewModel::stopRecording) { Text("Stop") }
                    }
                    RecordingSessionState.PAUSED -> {
                        OutlinedButton(onClick = viewModel::resumeRecording) { Text("Resume") }
                        Button(onClick = viewModel::stopRecording) { Text("Stop") }
                    }
                    RecordingSessionState.STOPPING -> {
                        Button(onClick = {}, enabled = false) { Text("Working…") }
                    }
                }
            }

            uiState.lastErrorMessage?.let { message ->
                Text(
                    text = message,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
        }

        Spacer(Modifier.height(20.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Recent", style = MaterialTheme.typography.titleMedium)
            TextButton(onClick = onOpenGallery) { Text("See all") }
        }

        val recent = galleryState.items.take(5)
        if (recent.isEmpty()) {
            Text(
                text = "No recordings yet.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.weight(1f, fill = false),
            ) {
                items(recent, key = { it.filePath }) { item ->
                    GalleryRow(
                        item = item,
                        onOpen = { onOpenPreview(item.filePath) },
                        onShare = { ShareHelper.shareVideo(context, item.filePath) },
                        onDelete = { galleryViewModel.requestDelete(item.filePath) },
                    )
                }
            }
        }
    }

    galleryState.pendingDeletePath?.let { path ->
        AlertDialog(
            onDismissRequest = galleryViewModel::cancelDeleteDialog,
            title = { Text("Delete recording") },
            text = { Text("Delete ${path.substringAfterLast('/')}?") },
            confirmButton = {
                TextButton(onClick = galleryViewModel::confirmDelete) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = galleryViewModel::cancelDeleteDialog) { Text("Cancel") }
            },
        )
    }
}

private fun formatDuration(durationMs: Long): String {
    val totalSeconds = (durationMs / 1000).coerceAtLeast(0)
    val seconds = totalSeconds % 60
    val minutes = (totalSeconds / 60) % 60
    val hours = totalSeconds / 3600
    return if (hours > 0) {
        String.format("%02d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format("%02d:%02d", minutes, seconds)
    }
}

private fun formatFileSize(bytes: Long): String {
    if (bytes < 1024) return "$bytes B"
    val kb = bytes / 1024.0
    if (kb < 1024) return String.format("%.1f KB", kb)
    return String.format("%.1f MB", kb / 1024.0)
}

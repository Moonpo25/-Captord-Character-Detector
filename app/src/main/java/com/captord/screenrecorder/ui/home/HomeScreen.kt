package com.captord.screenrecorder.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.captord.screenrecorder.feature.recorder.RecordingViewModel
import com.captord.screenrecorder.feature.recorder.model.RecordingSessionState
import com.captord.screenrecorder.ui.theme.CaptordSemanticColors

@Composable
fun HomeScreen(
    viewModel: RecordingViewModel,
    onRequestStart: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val countdownRemaining by viewModel.countdownRemaining.collectAsStateWithLifecycle()
    val dark = isSystemInDarkTheme()
    val recColor = if (dark) CaptordSemanticColors.recActiveDark else CaptordSemanticColors.recActiveLight
    val pauseColor = if (dark) CaptordSemanticColors.recPausedDark else CaptordSemanticColors.recPausedLight

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
            .padding(20.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = "Home",
            style = MaterialTheme.typography.headlineSmall,
        )
        Spacer(modifier = Modifier.height(16.dp))

        Row(
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
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
        }

        Text(
            text = if (uiState.sessionState == RecordingSessionState.COUNTDOWN) {
                countdownRemaining.toString()
            } else {
                formatDuration(uiState.durationMs)
            },
            fontSize = 40.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace,
            modifier = Modifier.padding(top = 8.dp),
        )

        if (uiState.fileSizeBytes > 0) {
            Text(
                text = formatFileSize(uiState.fileSizeBytes),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        uiState.lastErrorMessage?.let { message ->
            Text(
                text = message,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(top = 12.dp),
            )
        }

        uiState.lastFinalPath?.let { path ->
            Text(
                text = "Saved: ${path.substringAfterLast('/')}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp),
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally),
        ) {
            when (uiState.sessionState) {
                RecordingSessionState.IDLE, RecordingSessionState.ERROR -> {
                    Button(onClick = onRequestStart) {
                        Text("Start")
                    }
                }
                RecordingSessionState.COUNTDOWN -> {
                    OutlinedButton(onClick = viewModel::cancelCountdown) {
                        Text("Cancel")
                    }
                }
                RecordingSessionState.RECORDING -> {
                    OutlinedButton(onClick = viewModel::pauseRecording) {
                        Text("Pause")
                    }
                    Button(onClick = viewModel::stopRecording) {
                        Text("Stop")
                    }
                }
                RecordingSessionState.PAUSED -> {
                    OutlinedButton(onClick = viewModel::resumeRecording) {
                        Text("Resume")
                    }
                    Button(onClick = viewModel::stopRecording) {
                        Text("Stop")
                    }
                }
                RecordingSessionState.STOPPING -> {
                    Button(onClick = {}, enabled = false) {
                        Text("Working…")
                    }
                }
            }
        }
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
    val mb = kb / 1024.0
    return String.format("%.1f MB", mb)
}

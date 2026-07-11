package com.captord.screenrecorder.ui.home

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.EaseInOutCirc
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FiberManualRecord
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.captord.screenrecorder.core.gallery.ShareHelper
import com.captord.screenrecorder.core.haptics.HapticFeedbackHelper
import com.captord.screenrecorder.core.haptics.rememberHapticFeedbackHelper
import com.captord.screenrecorder.feature.gallery.GalleryViewModel
import com.captord.screenrecorder.feature.recorder.RecordingViewModel
import com.captord.screenrecorder.feature.recorder.model.RecordingSessionState
import com.captord.screenrecorder.ui.components.squishable
import com.captord.screenrecorder.ui.gallery.GalleryRow
import com.captord.screenrecorder.ui.theme.CaptordSemanticColors
import kotlinx.coroutines.launch

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
    val amusementColor = MaterialTheme.colorScheme.primary

    val haptic = rememberHapticFeedbackHelper()
    val scope = rememberCoroutineScope()

    LaunchedEffect(uiState.sessionState, uiState.lastFinalPath) {
        if (uiState.sessionState == RecordingSessionState.IDLE) {
            galleryViewModel.refresh()
        }
    }

    LaunchedEffect(countdownRemaining) {
        if (countdownRemaining > 0) {
            haptic.performHaptic(HapticFeedbackHelper.HapticIntensity.TEXTURE_TICK)
        }
    }

    val isRecording = uiState.sessionState == RecordingSessionState.RECORDING
    val isPaused = uiState.sessionState == RecordingSessionState.PAUSED
    val isActive = isRecording || isPaused

    val statusLabel = when (uiState.sessionState) {
        RecordingSessionState.IDLE -> "Ready"
        RecordingSessionState.COUNTDOWN -> "Countdown"
        RecordingSessionState.RECORDING -> "Recording"
        RecordingSessionState.PAUSED -> "Paused"
        RecordingSessionState.STOPPING -> "Finalizing"
        RecordingSessionState.ERROR -> "Error"
    }

    val statusColor = when (uiState.sessionState) {
        RecordingSessionState.RECORDING -> recColor
        RecordingSessionState.PAUSED -> amusementColor.copy(alpha = 0.8f)
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }

    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.15f,
        targetValue = 0.65f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "alpha"
    )
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1.0f,
        targetValue = 1.48f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = EaseInOutCirc),
            repeatMode = RepeatMode.Reverse
        ),
        label = "scale"
    )
    val rotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(8000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "rotation"
    )
    val blinkAlpha by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(800, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "blink"
    )

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surfaceContainerLowest)
            .padding(horizontal = 24.dp)
            .padding(top = 16.dp),
    ) {
        Text(
            text = "Captord",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Black,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(bottom = 24.dp)
        )

        // Main recording control console
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.extraLarge,
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 40.dp, horizontal = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                // Shutter Console Box with Pulse Ring & Rotating Camcorder Dashes
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier.size(136.dp)
                ) {
                    if (isRecording) {
                        // Pulsing outer glow
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .scale(pulseScale)
                                .alpha(pulseAlpha)
                                .background(recColor.copy(alpha = 0.4f), CircleShape)
                        )
                        // Rotating dashed outer border
                        Canvas(
                            modifier = Modifier
                                .size(108.dp)
                                .graphicsLayer { rotationZ = rotation }
                        ) {
                            drawCircle(
                                color = recColor.copy(alpha = 0.7f),
                                style = Stroke(
                                    width = 2.dp.toPx(),
                                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(15f, 15f), 0f)
                                )
                            )
                        }
                    }

                    // Tactile camera-styled shutter button
                    val shutterBg = when {
                        isRecording -> recColor
                        isPaused -> amusementColor.copy(alpha = 0.8f)
                        else -> amusementColor
                    }

                    Surface(
                        modifier = Modifier
                            .size(76.dp)
                            .squishable(enabled = true, scaleDown = 0.90f)
                            .clickable {
                                when (uiState.sessionState) {
                                    RecordingSessionState.IDLE, RecordingSessionState.ERROR -> {
                                        scope.launch { haptic.heavyClick() }
                                        onRequestStart()
                                    }
                                    RecordingSessionState.RECORDING, RecordingSessionState.PAUSED -> {
                                        scope.launch { haptic.heavyClick() }
                                        viewModel.stopRecording()
                                    }
                                    else -> Unit
                                }
                            },
                        shape = CircleShape,
                        color = shutterBg,
                        border = BorderStroke(4.dp, MaterialTheme.colorScheme.surfaceContainerHigh),
                        shadowElevation = 6.dp
                    ) {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier.fillMaxSize()
                        ) {
                            if (isActive) {
                                Icon(
                                    imageVector = Icons.Default.Stop,
                                    contentDescription = "Stop recording",
                                    tint = Color.White,
                                    modifier = Modifier.size(32.dp)
                                )
                            } else if (uiState.sessionState == RecordingSessionState.COUNTDOWN) {
                                Text(
                                    text = countdownRemaining.toString(),
                                    color = Color.White,
                                    fontSize = 28.sp,
                                    fontWeight = FontWeight.Black
                                )
                            } else {
                                Icon(
                                    imageVector = Icons.Default.FiberManualRecord,
                                    contentDescription = "Start recording",
                                    tint = Color.White,
                                    modifier = Modifier.size(36.dp)
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(28.dp))

                // Time status pill with Blinking Dot Indicator
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.surfaceContainerLowest,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f))
                ) {
                    Row(
                        modifier = Modifier
                            .animateContentSize()
                            .padding(horizontal = 18.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .alpha(if (isRecording) blinkAlpha else 1.0f)
                                .background(statusColor, CircleShape)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = statusLabel,
                            color = statusColor,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (uiState.sessionState == RecordingSessionState.COUNTDOWN) {
                                "00:0$countdownRemaining"
                            } else {
                                formatDuration(uiState.durationMs)
                            },
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Black,
                            fontSize = 14.sp
                        )
                        if (uiState.fileSizeBytes > 0) {
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "·  " + formatFileSize(uiState.fileSizeBytes),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                // Action buttons row (Spring styled & Haptic trigger)
                Row(
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    when (uiState.sessionState) {
                        RecordingSessionState.IDLE, RecordingSessionState.ERROR -> {
                            Button(
                                onClick = {
                                    scope.launch { haptic.click() }
                                    onRequestStart()
                                },
                                shape = CircleShape,
                                modifier = Modifier.squishable(),
                                colors = ButtonDefaults.buttonColors(containerColor = amusementColor)
                            ) {
                                Text("Start screen capture", fontWeight = FontWeight.Bold)
                            }
                        }
                        RecordingSessionState.COUNTDOWN -> {
                            OutlinedButton(
                                onClick = {
                                    scope.launch { haptic.click() }
                                    viewModel.cancelCountdown()
                                },
                                shape = CircleShape,
                                modifier = Modifier.squishable()
                            ) {
                                Text("Cancel", fontWeight = FontWeight.Bold)
                            }
                        }
                        RecordingSessionState.RECORDING -> {
                            OutlinedButton(
                                onClick = {
                                    scope.launch { haptic.click() }
                                    viewModel.pauseRecording()
                                },
                                shape = CircleShape,
                                modifier = Modifier.squishable()
                            ) {
                                Text("Pause", fontWeight = FontWeight.Bold)
                            }
                            Button(
                                onClick = {
                                    scope.launch { haptic.heavyClick() }
                                    viewModel.stopRecording()
                                },
                                shape = CircleShape,
                                modifier = Modifier.squishable(),
                                colors = ButtonDefaults.buttonColors(containerColor = recColor)
                            ) {
                                Text("Stop", fontWeight = FontWeight.Bold)
                            }
                        }
                        RecordingSessionState.PAUSED -> {
                            OutlinedButton(
                                onClick = {
                                    scope.launch { haptic.click() }
                                    viewModel.resumeRecording()
                                },
                                shape = CircleShape,
                                modifier = Modifier.squishable()
                            ) {
                                Text("Resume", fontWeight = FontWeight.Bold)
                            }
                            Button(
                                onClick = {
                                    scope.launch { haptic.heavyClick() }
                                    viewModel.stopRecording()
                                },
                                shape = CircleShape,
                                modifier = Modifier.squishable(),
                                colors = ButtonDefaults.buttonColors(containerColor = recColor)
                            ) {
                                Text("Stop", fontWeight = FontWeight.Bold)
                            }
                        }
                        RecordingSessionState.STOPPING -> {
                            Button(
                                onClick = {},
                                enabled = false,
                                shape = CircleShape
                            ) {
                                Text("Writing output…", fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }

                uiState.lastErrorMessage?.let { message ->
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = message,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }

        Spacer(Modifier.height(24.dp))

        // Recent items header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "Recent recordings",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            TextButton(
                onClick = {
                    scope.launch { haptic.click() }
                    onOpenGallery()
                },
                modifier = Modifier.squishable()
            ) {
                Text("See all", fontWeight = FontWeight.Bold)
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        val recent = galleryState.items.take(3)
        if (recent.isEmpty()) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                shape = MaterialTheme.shapes.large
            ) {
                Box(
                    modifier = Modifier.padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Your recording log is empty.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        } else {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.weight(1f, fill = false),
            ) {
                items(recent, key = { it.filePath }) { item ->
                    GalleryRow(
                        item = item,
                        onOpen = { onOpenPreview(item.filePath) },
                        onShare = { ShareHelper.shareVideo(context, item.filePath) },
                        onDelete = { galleryViewModel.requestDelete(item.filePath) },
                        modifier = Modifier.squishable(scaleDown = 0.97f)
                    )
                }
            }
        }
    }

    galleryState.pendingDeletePath?.let { path ->
        AlertDialog(
            onDismissRequest = galleryViewModel::cancelDeleteDialog,
            title = { Text("Delete file", fontWeight = FontWeight.Bold) },
            text = { Text("This will permanently remove ${path.substringAfterLast('/')} from storage.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        scope.launch { haptic.heavyClick() }
                        galleryViewModel.confirmDelete()
                    }
                ) {
                    Text("Delete", color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = galleryViewModel::cancelDeleteDialog) {
                    Text("Cancel", fontWeight = FontWeight.Bold)
                }
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

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
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.FiberManualRecord
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
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
import com.captord.screenrecorder.R
import com.captord.screenrecorder.feature.gallery.GalleryViewModel
import com.captord.screenrecorder.feature.recorder.RecordingViewModel
import com.captord.screenrecorder.feature.recorder.model.RecordingErrorContext
import com.captord.screenrecorder.feature.recorder.model.RecordingSessionState
import com.captord.screenrecorder.ui.components.squishable
import com.captord.screenrecorder.ui.theme.CaptordSemanticColors
import android.provider.Settings
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
    val pausedColor = if (dark) CaptordSemanticColors.recPausedDark else CaptordSemanticColors.recPausedLight
    val primaryColor = MaterialTheme.colorScheme.primary
    val reduceMotion = Settings.Global.getFloat(
        context.contentResolver,
        Settings.Global.ANIMATOR_DURATION_SCALE,
        1f,
    ) == 0f

    val haptic = rememberHapticFeedbackHelper()
    val scope = rememberCoroutineScope()

    LaunchedEffect(countdownRemaining) {
        if (countdownRemaining > 0) {
            haptic.performHaptic(HapticFeedbackHelper.HapticIntensity.TEXTURE_TICK)
        }
    }

    val isRecording = uiState.sessionState == RecordingSessionState.RECORDING
    val isPaused = uiState.sessionState == RecordingSessionState.PAUSED
    val isActive = isRecording || isPaused

    val statusLabel = when (uiState.sessionState) {
        RecordingSessionState.IDLE -> stringResource(R.string.home_status_ready)
        RecordingSessionState.COUNTDOWN -> stringResource(R.string.home_status_countdown)
        RecordingSessionState.RECORDING -> stringResource(R.string.home_status_recording)
        RecordingSessionState.PAUSED -> stringResource(R.string.home_status_paused)
        RecordingSessionState.STOPPING -> stringResource(R.string.home_status_finalizing)
        RecordingSessionState.ERROR -> stringResource(R.string.home_status_error)
    }

    val statusColor = when (uiState.sessionState) {
        RecordingSessionState.RECORDING -> recColor
        RecordingSessionState.PAUSED -> pausedColor
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
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(bottom = 24.dp),
        ) {
            Surface(
                modifier = Modifier.size(32.dp),
                shape = RoundedCornerShape(10.dp),
                color = Color(0xFF132411),
            ) {
                Image(
                    painter = painterResource(R.drawable.captord_logo),
                    contentDescription = null,
                    modifier = Modifier.size(32.dp),
                )
            }
            Spacer(Modifier.width(10.dp))
            Text(
                text = "Captord",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Black,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }

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
                    if (isRecording && !reduceMotion) {
                        // Pulsing outer glow (optimized via graphicsLayer to avoid recomposition)
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .graphicsLayer {
                                    scaleX = pulseScale
                                    scaleY = pulseScale
                                    alpha = pulseAlpha
                                }
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
                        isPaused -> pausedColor
                        else -> primaryColor
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
                                    contentDescription = stringResource(R.string.home_cd_stop_recording),
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
                                    contentDescription = stringResource(R.string.home_cd_start_recording),
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
                                .graphicsLayer {
                                    alpha = if (isRecording && !reduceMotion) blinkAlpha else 1.0f
                                }
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
                                colors = ButtonDefaults.buttonColors(containerColor = primaryColor)
                            ) {
                                Text(stringResource(R.string.home_start_screen_capture), fontWeight = FontWeight.Bold)
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
                                Text(stringResource(R.string.home_cancel), fontWeight = FontWeight.Bold)
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
                                Text(stringResource(R.string.home_pause), fontWeight = FontWeight.Bold)
                            }
                            OutlinedButton(
                                onClick = {
                                    scope.launch { haptic.click() }
                                    viewModel.addBookmarkAtCurrentTime()
                                },
                                shape = CircleShape,
                                modifier = Modifier.squishable()
                            ) {
                                Text(stringResource(R.string.home_bookmark), fontWeight = FontWeight.Bold)
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
                                Text(stringResource(R.string.home_stop), fontWeight = FontWeight.Bold)
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
                                Text(stringResource(R.string.home_resume), fontWeight = FontWeight.Bold)
                            }
                            OutlinedButton(
                                onClick = {
                                    scope.launch { haptic.click() }
                                    viewModel.addBookmarkAtCurrentTime()
                                },
                                shape = CircleShape,
                                modifier = Modifier.squishable()
                            ) {
                                Text(stringResource(R.string.home_bookmark), fontWeight = FontWeight.Bold)
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
                                Text(stringResource(R.string.home_stop), fontWeight = FontWeight.Bold)
                            }
                        }
                        RecordingSessionState.STOPPING -> {
                            Button(
                                onClick = {},
                                enabled = false,
                                shape = CircleShape
                            ) {
                                Text(stringResource(R.string.home_writing_output), fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }

                uiState.lastErrorMessage?.let { message ->
                    Spacer(modifier = Modifier.height(12.dp))
                    Column(horizontalAlignment = Alignment.Start) {
                        Text(
                            text = message,
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Medium,
                        )
                        uiState.lastErrorContext?.let { ctx ->
                            Spacer(modifier = Modifier.height(8.dp))
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.6f),
                                border = BorderStroke(
                                    1.dp,
                                    MaterialTheme.colorScheme.error.copy(alpha = 0.3f),
                                ),
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    Text(
                                        text = stringResource(R.string.home_error_context_label),
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onErrorContainer,
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = stringResource(
                                            R.string.home_error_context_format,
                                            ctx.engine,
                                            ctx.codec,
                                            ctx.resolution,
                                            ctx.frameRate,
                                            ctx.bitrateBps / 1_000_000,
                                            if (ctx.audioSource != "off") {
                                                stringResource(
                                                    R.string.home_error_context_audio_suffix,
                                                    ctx.audioSource,
                                                )
                                            } else {
                                                ""
                                            },
                                        ),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onErrorContainer,
                                    )
                                }
                            }
                        }
                    }
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
                text = stringResource(R.string.home_recent_recordings),
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
                Text(stringResource(R.string.home_see_all), fontWeight = FontWeight.Bold)
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        val recent = galleryState.items.take(5)
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
                        text = stringResource(R.string.home_empty_log),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        } else {
            // M3 Expressive Carousel Horizontal Row
            LazyRow(
                contentPadding = PaddingValues(horizontal = 4.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                items(recent, key = { it.filePath }) { item ->
                    RecentRecordingCard(
                        item = item,
                        onOpen = { onOpenPreview(item.filePath) },
                        onShare = { ShareHelper.shareVideo(context, item.filePath) },
                        onDelete = { galleryViewModel.requestDelete(item.filePath) }
                    )
                }
            }
        }
    }

    galleryState.pendingDeletePath?.let { path ->
        AlertDialog(
            onDismissRequest = galleryViewModel::cancelDeleteDialog,
            title = { Text(stringResource(R.string.home_delete_dialog_title), fontWeight = FontWeight.Bold) },
            text = {
                Text(
                    stringResource(
                        R.string.home_delete_dialog_text,
                        path.substringAfterLast('/'),
                    ),
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        scope.launch { haptic.heavyClick() }
                        galleryViewModel.confirmDelete()
                    }
                ) {
                    Text(
                        stringResource(R.string.home_delete_dialog_confirm),
                        color = MaterialTheme.colorScheme.error,
                        fontWeight = FontWeight.Bold,
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = galleryViewModel::cancelDeleteDialog) {
                    Text(stringResource(R.string.home_delete_dialog_cancel), fontWeight = FontWeight.Bold)
                }
            },
        )
    }
}

@Composable
private fun RecentRecordingCard(
    item: com.captord.screenrecorder.core.gallery.RecordingItem,
    onOpen: () -> Unit,
    onShare: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier
) {
    val haptic = rememberHapticFeedbackHelper()
    val scope = rememberCoroutineScope()
    
    Card(
        modifier = modifier
            .width(220.dp)
            .height(130.dp)
            .clip(RoundedCornerShape(24.dp))
            .squishable(scaleDown = 0.95f)
            .clickable { onOpen() },
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f))
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(14.dp),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Top
                ) {
                    // Play icon container
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.primaryContainer,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Default.PlayArrow,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }

                    // Actions
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        IconButton(
                            onClick = {
                                scope.launch { haptic.click() }
                                onShare()
                            },
                            modifier = Modifier.size(28.dp)
                        ) {
Icon(
                            imageVector = Icons.Default.Share,
                            contentDescription = stringResource(R.string.home_cd_share),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(16.dp)
                        )
                        }
                        IconButton(
                            onClick = {
                                scope.launch { haptic.click() }
                                onDelete()
                            },
                            modifier = Modifier.size(28.dp)
                        ) {
Icon(
                            imageVector = Icons.Default.DeleteOutline,
                            contentDescription = stringResource(R.string.home_cd_delete),
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(16.dp)
                        )
                        }
                    }
                }

                Column {
                    Text(
                        text = item.filePath.substringAfterLast('/'),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "${formatDuration(item.durationMs ?: 0L)} · ${formatFileSize(item.fileSizeBytes)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = FontWeight.Medium
                    )
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
    return String.format("%.1f MB", kb / 1024.0)
}

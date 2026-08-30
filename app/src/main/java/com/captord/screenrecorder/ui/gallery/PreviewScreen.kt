package com.captord.screenrecorder.ui.gallery

import android.net.Uri
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import com.captord.screenrecorder.R
import com.captord.screenrecorder.core.bookmark.DataStoreBookmarkStore
import com.captord.screenrecorder.core.gallery.ShareHelper
import com.captord.screenrecorder.core.haptics.HapticFeedbackHelper
import com.captord.screenrecorder.core.haptics.rememberHapticFeedbackHelper
import com.captord.screenrecorder.feature.gallery.GalleryViewModel
import com.captord.screenrecorder.feature.recorder.model.RecordingBookmark
import com.captord.screenrecorder.ui.components.squishable
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File
import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PreviewScreen(
    encodedPath: String,
    onBack: () -> Unit,
    galleryViewModel: GalleryViewModel,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val path = remember(encodedPath) {
        URLDecoder.decode(encodedPath, StandardCharsets.UTF_8.toString())
    }
    val fileName = path.substringAfterLast('/')
    val uiState by galleryViewModel.uiState.collectAsStateWithLifecycle()
    var showDelete by remember { mutableStateOf(false) }

    val haptic = rememberHapticFeedbackHelper()
    val scope = rememberCoroutineScope()

    val player = remember {
        ExoPlayer.Builder(context).build().apply {
            setMediaItem(
                MediaItem.fromUri(
                    if (path.startsWith("content:")) Uri.parse(path) else Uri.fromFile(File(path)),
                ),
            )
            prepare()
            playWhenReady = true
        }
    }

    var positionMs by remember { mutableLongStateOf(0L) }
    var durationMs by remember { mutableLongStateOf(0L) }
    var isPlaying by remember { mutableStateOf(true) }
    var bookmarks by remember { mutableStateOf<List<RecordingBookmark>>(emptyList()) }

    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(playing: Boolean) {
                isPlaying = playing
            }
        }
        player.addListener(listener)
        onDispose {
            player.removeListener(listener)
            player.release()
        }
    }

    LaunchedEffect(player) {
        while (true) {
            positionMs = player.currentPosition
            durationMs = player.duration.coerceAtLeast(0L)
            delay(250)
        }
    }

    LaunchedEffect(path) {
        // Look up bookmarks by the same final-path convention the
        // bookmark button uses while recording. If the path is a content://
        // URI (custom SAF folder), we fall back to a best-effort match on
        // display name — the SAF case currently has no stable filename
        // contract, so this is a best effort.
        val lookupPath = if (path.startsWith("content:")) {
            val name = path.substringAfterLast('/')
            "Movies/Captord/$name"
        } else {
            path
        }
        bookmarks = runCatching {
            DataStoreBookmarkStore.instance(context).getBookmarks(lookupPath)
        }.getOrDefault(emptyList())
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
        topBar = {
            TopAppBar(
                title = { Text(fileName, maxLines = 1, fontWeight = FontWeight.Bold) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerLowest
                ),
                navigationIcon = {
                    IconButton(
                        onClick = {
                            scope.launch { haptic.click() }
                            onBack()
                        },
                        modifier = Modifier.squishable(scaleDown = 0.90f)
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.preview_cd_back))
                    }
                },
                actions = {
                    IconButton(
                        onClick = {
                            scope.launch { haptic.click() }
                            ShareHelper.shareVideo(context, path)
                        },
                        modifier = Modifier.squishable(scaleDown = 0.90f)
                    ) {
                        Icon(Icons.Filled.Share, contentDescription = stringResource(R.string.preview_cd_share))
                    }
                    IconButton(
                        onClick = {
                            scope.launch { haptic.click() }
                            showDelete = true
                        },
                        modifier = Modifier.squishable(scaleDown = 0.90f)
                    ) {
                        Icon(Icons.Filled.DeleteOutline, contentDescription = stringResource(R.string.preview_cd_delete), tint = MaterialTheme.colorScheme.error)
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 24.dp, vertical = 16.dp),
        ) {
            if (!File(path).isFile) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        stringResource(R.string.preview_file_not_found),
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
            } else {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(16f / 9f),
                    shape = RoundedCornerShape(24.dp),
                    color = Color.Black,
                    border = androidx.compose.foundation.BorderStroke(
                        1.dp,
                        MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)
                    ),
                    shadowElevation = 8.dp
                ) {
                    AndroidView(
                        factory = { ctx ->
                            PlayerView(ctx).apply {
                                this.player = player
                                useController = false // Use custom overlays below
                            }
                        },
                        modifier = Modifier.fillMaxSize()
                    )
                }

                Spacer(Modifier.height(28.dp))

                if (bookmarks.isNotEmpty()) {
                    // Bookmark chips — tap to seek the player to that timestamp.
                    Text(
                        text = "Bookmarks",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Spacer(Modifier.height(8.dp))
                    androidx.compose.foundation.lazy.                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        items(bookmarks, key = { it.id }) { bm ->
                            Surface(
                                onClick = {
                                    scope.launch { haptic.click() }
                                    player.seekTo(bm.timestampMs)
                                },
                                shape = RoundedCornerShape(16.dp),
                                color = MaterialTheme.colorScheme.primaryContainer,
                                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                            ) {
                                Text(
                                    text = formatDuration(bm.timestampMs),
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = FontFamily.Monospace,
                                )
                            }
                        }
                    }
                    Spacer(Modifier.height(20.dp))
                }

                // Custom seeking slider with haptic ticks
                if (durationMs > 0) {
                    var sliderValue by remember(durationMs) { mutableFloatStateOf(positionMs.toFloat()) }
                    var lastSliderHapticSec by remember { mutableIntStateOf((positionMs / 1000).toInt()) }

                    LaunchedEffect(positionMs) {
                        sliderValue = positionMs.toFloat()
                        lastSliderHapticSec = (positionMs / 1000).toInt()
                    }

                    Column(modifier = Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "${formatDuration(positionMs)} / ${formatDuration(durationMs)}",
                                style = MaterialTheme.typography.bodyMedium,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        Slider(
                            value = sliderValue.coerceIn(0f, durationMs.toFloat()),
                            onValueChange = { newValue ->
                                sliderValue = newValue
                                val currentSec = (newValue / 1000f).roundToInt()
                                if (currentSec != lastSliderHapticSec) {
                                    lastSliderHapticSec = currentSec
                                    scope.launch { haptic.performHaptic(HapticFeedbackHelper.HapticIntensity.TEXTURE_TICK) }
                                }
                            },
                            onValueChangeFinished = {
                                player.seekTo(sliderValue.toLong())
                            },
                            valueRange = 0f..durationMs.toFloat().coerceAtLeast(1f),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }

                Spacer(Modifier.height(28.dp))

                // Play / Pause media controls center
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val playBg = MaterialTheme.colorScheme.primaryContainer
                    val playContent = MaterialTheme.colorScheme.onPrimaryContainer

                    Surface(
                        modifier = Modifier
                            .size(72.dp)
                            .squishable(enabled = true, scaleDown = 0.90f)
                            .clickable {
                                scope.launch { haptic.click() }
                                if (player.isPlaying) player.pause() else player.play()
                            },
                        shape = RoundedCornerShape(22.dp),
                        color = playBg,
                        border = androidx.compose.foundation.BorderStroke(
                            2.dp,
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)
                        ),
                        shadowElevation = 4.dp
                    ) {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier.fillMaxSize()
                        ) {
                            Crossfade(targetState = isPlaying, label = "play_state_anim") { playing ->
                                if (playing) {
                                    Icon(
imageVector = Icons.Default.Pause,
                                    contentDescription = stringResource(R.string.preview_cd_pause),
                                        tint = playContent,
                                        modifier = Modifier.size(32.dp)
                                    )
                                } else {
                                    Icon(
imageVector = Icons.Default.PlayArrow,
                                    contentDescription = stringResource(R.string.preview_cd_play),
                                        tint = playContent,
                                        modifier = Modifier.size(36.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showDelete) {
        AlertDialog(
            onDismissRequest = { showDelete = false },
            title = { Text(stringResource(R.string.home_delete_dialog_title), fontWeight = FontWeight.Bold) },
            text = { Text(stringResource(R.string.home_delete_dialog_text, fileName)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        scope.launch { haptic.heavyClick() }
                        showDelete = false
                        galleryViewModel.requestDelete(path)
                        galleryViewModel.confirmDelete()
                    },
                ) {
                    Text("Delete", color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDelete = false }) {
                    Text("Cancel", fontWeight = FontWeight.Bold)
                }
            },
        )
    }

    LaunchedEffect(uiState.items, uiState.message) {
            val gone = uiState.items.none { it.filePath == path } &&
                !path.startsWith("content:") && !File(path).exists()
        if (gone || (uiState.message == "Recording deleted" && uiState.items.none { it.filePath == path })) {
            onBack()
        }
    }
}

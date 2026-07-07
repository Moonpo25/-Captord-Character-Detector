package com.captord.screenrecorder.ui.gallery

import android.net.Uri
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import com.captord.screenrecorder.core.gallery.ShareHelper
import com.captord.screenrecorder.feature.gallery.GalleryViewModel
import kotlinx.coroutines.delay
import java.io.File
import java.net.URLDecoder
import java.nio.charset.StandardCharsets

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

    val player = remember {
        ExoPlayer.Builder(context).build().apply {
            setMediaItem(MediaItem.fromUri(Uri.fromFile(File(path))))
            prepare()
            playWhenReady = true
        }
    }

    var positionMs by remember { mutableLongStateOf(0L) }
    var durationMs by remember { mutableLongStateOf(0L) }
    var isPlaying by remember { mutableStateOf(true) }

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

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text(fileName, maxLines = 1) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { ShareHelper.shareVideo(context, path) }) {
                        Icon(Icons.Filled.Share, contentDescription = "Share")
                    }
                    IconButton(onClick = { showDelete = true }) {
                        Icon(Icons.Filled.Delete, contentDescription = "Delete")
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
        ) {
            if (!File(path).isFile) {
                Text(
                    "File not found",
                    color = MaterialTheme.colorScheme.error,
                )
            } else {
                AndroidView(
                    factory = { ctx ->
                        PlayerView(ctx).apply {
                            this.player = player
                            useController = true
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(16f / 9f),
                )
                Spacer(Modifier.height(12.dp))
                Text(
                    "${formatDuration(positionMs)} / ${formatDuration(durationMs)}",
                    style = MaterialTheme.typography.bodySmall,
                )
                if (durationMs > 0) {
                    var slider by remember(durationMs) {
                        mutableFloatStateOf(positionMs.toFloat())
                    }
                    LaunchedEffect(positionMs) {
                        slider = positionMs.toFloat()
                    }
                    Slider(
                        value = slider.coerceIn(0f, durationMs.toFloat()),
                        onValueChange = { slider = it },
                        onValueChangeFinished = {
                            player.seekTo(slider.toLong())
                        },
                        valueRange = 0f..durationMs.toFloat().coerceAtLeast(1f),
                    )
                }
                TextButton(onClick = {
                    if (player.isPlaying) player.pause() else player.play()
                }) {
                    Text(if (isPlaying) "Pause" else "Play")
                }
            }
        }
    }

    if (showDelete) {
        AlertDialog(
            onDismissRequest = { showDelete = false },
            title = { Text("Delete recording") },
            text = { Text("Delete $fileName?") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDelete = false
                        galleryViewModel.requestDelete(path)
                        galleryViewModel.confirmDelete()
                    },
                ) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = { showDelete = false }) { Text("Cancel") }
            },
        )
    }

    LaunchedEffect(uiState.items, uiState.message) {
        val gone = uiState.items.none { it.filePath == path } && !File(path).exists()
        if (gone || (uiState.message == "Recording deleted" && uiState.items.none { it.filePath == path })) {
            onBack()
        }
    }
}

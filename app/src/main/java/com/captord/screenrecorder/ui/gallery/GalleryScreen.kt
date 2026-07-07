package com.captord.screenrecorder.ui.gallery

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.captord.screenrecorder.core.gallery.ShareHelper
import com.captord.screenrecorder.feature.gallery.GalleryViewModel

@Composable
fun GalleryScreen(
    onOpenPreview: (String) -> Unit,
    galleryViewModel: GalleryViewModel = viewModel(),
    modifier: Modifier = Modifier,
) {
    val uiState by galleryViewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    LaunchedEffect(Unit) {
        galleryViewModel.refresh()
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        Text("Gallery", style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(12.dp))

        when {
            uiState.isLoading && uiState.items.isEmpty() -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }
            uiState.items.isEmpty() -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        text = "No recordings yet. Tap the shutter to capture your screen.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            else -> {
                LazyColumn(
                    contentPadding = PaddingValues(bottom = 88.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(uiState.items, key = { it.filePath }) { item ->
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
    }

    uiState.pendingDeletePath?.let { path ->
        AlertDialog(
            onDismissRequest = galleryViewModel::cancelDeleteDialog,
            title = { Text("Delete recording") },
            text = { Text("Delete ${path.substringAfterLast('/')}? This cannot be undone.") },
            confirmButton = {
                TextButton(onClick = galleryViewModel::confirmDelete) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = galleryViewModel::cancelDeleteDialog) { Text("Cancel") }
            },
        )
    }
}

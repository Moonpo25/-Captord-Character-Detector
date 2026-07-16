package com.captord.screenrecorder.ui.gallery

import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FiberManualRecord
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.captord.screenrecorder.core.gallery.ShareHelper
import com.captord.screenrecorder.core.haptics.HapticFeedbackHelper
import com.captord.screenrecorder.core.haptics.rememberHapticFeedbackHelper
import com.captord.screenrecorder.feature.gallery.GalleryViewModel
import com.captord.screenrecorder.ui.components.squishable
import kotlinx.coroutines.launch

@Composable
fun GalleryScreen(
    onOpenPreview: (String) -> Unit,
    galleryViewModel: GalleryViewModel = viewModel(),
    modifier: Modifier = Modifier,
    onRequestStart: (() -> Unit)? = null
) {
    val uiState by galleryViewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    val haptic = rememberHapticFeedbackHelper()
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        galleryViewModel.refresh()
    }

    val listState = rememberLazyListState()
    val isFabExpanded by remember {
        derivedStateOf {
            listState.firstVisibleItemIndex == 0 && listState.firstVisibleItemScrollOffset < 10
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surfaceContainerLowest)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp)
                .padding(top = 16.dp),
        ) {
            Text(
                text = "Gallery",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Black,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(Modifier.height(20.dp))

            when {
                uiState.isLoading && uiState.items.isEmpty() -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }
                uiState.items.isEmpty() -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(
                            text = "No recordings found. Tap record to start.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
                else -> {
                    LazyColumn(
                        state = listState,
                        contentPadding = PaddingValues(bottom = 96.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        items(uiState.items, key = { it.filePath }) { item ->
                            LaunchedEffect(item.filePath, item.durationMs) {
                                galleryViewModel.enrichMetadata(item)
                            }
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

        if (onRequestStart != null) {
            ExtendedFloatingActionButton(
                onClick = {
                    scope.launch { haptic.heavyClick() }
                    onRequestStart()
                },
                expanded = isFabExpanded,
                icon = {
                    Icon(
                        imageVector = Icons.Default.FiberManualRecord,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error
                    )
                },
                text = { Text("Record", fontWeight = FontWeight.Bold) },
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(bottom = 96.dp, end = 24.dp)
                    .squishable()
            )
        }
    }

    uiState.pendingDeletePath?.let { path ->
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

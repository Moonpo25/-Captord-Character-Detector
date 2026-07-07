package com.captord.screenrecorder.feature.gallery

import android.app.Application
import android.content.IntentSender
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.captord.screenrecorder.core.gallery.DeleteOutcome
import com.captord.screenrecorder.core.gallery.GalleryRepository
import com.captord.screenrecorder.core.gallery.MediaStoreDeleteHelper
import com.captord.screenrecorder.core.gallery.RecordingItem
import com.captord.screenrecorder.core.gallery.VideoMetadataHelper
import com.captord.screenrecorder.feature.recorder.RecordingRepository
import com.captord.screenrecorder.feature.recorder.model.RecordingEvent
import com.captord.screenrecorder.feature.recorder.model.RecordingSessionState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class GalleryUiState(
    val items: List<RecordingItem> = emptyList(),
    val isLoading: Boolean = false,
    val message: String? = null,
    val pendingDeletePath: String? = null,
)

class GalleryViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = GalleryRepository()
    private val deleteHelper = MediaStoreDeleteHelper(application)
    private val recordingRepository = RecordingRepository.get(application)

    private val _uiState = MutableStateFlow(GalleryUiState(isLoading = true))
    val uiState: StateFlow<GalleryUiState> = _uiState.asStateFlow()

    private var pendingConfirmUri: Uri? = null
    private var pendingConfirmPath: String? = null
    var onNeedDeleteConfirmation: ((IntentSender) -> Unit)? = null

    init {
        refresh()
        viewModelScope.launch {
            recordingRepository.events.collect { event ->
                when (event) {
                    is RecordingEvent.FileSegmentCreated -> refresh()
                    is RecordingEvent.StateChanged -> {
                        if (event.state == RecordingSessionState.IDLE) refresh()
                    }
                    else -> Unit
                }
            }
        }
    }

    fun refresh() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, message = null) }
            val items = withContext(Dispatchers.IO) { repository.listRecordings() }
            _uiState.update { it.copy(items = items, isLoading = false) }
            items.forEach { enrichMetadata(it) }
        }
    }

    private fun enrichMetadata(item: RecordingItem) {
        if (item.durationMs != null) return
        viewModelScope.launch(Dispatchers.IO) {
            val meta = VideoMetadataHelper.extract(getApplication(), item.filePath) ?: return@launch
            _uiState.update { state ->
                state.copy(
                    items = state.items.map { current ->
                        if (current.filePath == item.filePath) {
                            current.copy(
                                durationMs = meta.durationMs,
                                width = meta.width,
                                height = meta.height,
                                thumbnailPath = meta.thumbnailPath,
                            )
                        } else {
                            current
                        }
                    },
                )
            }
        }
    }

    fun requestDelete(path: String) {
        _uiState.update { it.copy(pendingDeletePath = path) }
    }

    fun cancelDeleteDialog() {
        _uiState.update { it.copy(pendingDeletePath = null) }
    }

    fun confirmDelete() {
        val path = _uiState.value.pendingDeletePath ?: return
        _uiState.update { it.copy(pendingDeletePath = null) }
        viewModelScope.launch {
            val outcome = withContext(Dispatchers.IO) { deleteHelper.tryDelete(path) }
            when (outcome) {
                DeleteOutcome.Deleted -> {
                    _uiState.update { state ->
                        state.copy(
                            items = state.items.filterNot { it.filePath == path },
                            message = "Recording deleted",
                        )
                    }
                }
                DeleteOutcome.Failed -> {
                    _uiState.update { it.copy(message = "Could not delete recording") }
                }
                DeleteOutcome.Cancelled -> Unit
                is DeleteOutcome.NeedsUserConfirmation -> {
                    pendingConfirmUri = outcome.uri
                    pendingConfirmPath = path
                    onNeedDeleteConfirmation?.invoke(outcome.intentSender)
                }
            }
        }
    }

    fun onDeleteConfirmationResult(resultCode: Int) {
        val uri = pendingConfirmUri
        val path = pendingConfirmPath
        pendingConfirmUri = null
        pendingConfirmPath = null
        if (uri == null || path == null) return
        viewModelScope.launch {
            val outcome = withContext(Dispatchers.IO) {
                deleteHelper.completeAfterUserGrant(uri, resultCode)
            }
            when (outcome) {
                DeleteOutcome.Deleted -> {
                    // Ensure filesystem gone if MediaStore already removed
                    withContext(Dispatchers.IO) {
                        val file = java.io.File(path)
                        if (file.exists()) file.delete()
                    }
                    _uiState.update { state ->
                        state.copy(
                            items = state.items.filterNot { it.filePath == path },
                            message = "Recording deleted",
                        )
                    }
                }
                DeleteOutcome.Cancelled -> {
                    _uiState.update { it.copy(message = "Delete cancelled") }
                }
                else -> _uiState.update { it.copy(message = "Could not delete recording") }
            }
        }
    }

    fun clearMessage() {
        _uiState.update { it.copy(message = null) }
    }
}

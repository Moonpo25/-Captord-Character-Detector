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
import com.captord.screenrecorder.core.settings.SettingsRepository
import com.captord.screenrecorder.feature.recorder.RecordingRepository
import com.captord.screenrecorder.feature.recorder.model.RecordingEvent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext

data class GalleryUiState(
    val items: List<RecordingItem> = emptyList(),
    val isLoading: Boolean = false,
    val message: String? = null,
    val pendingDeletePath: String? = null,
)

class GalleryViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = GalleryRepository(application)
    private val settingsRepository = SettingsRepository.get(application)
    private val deleteHelper = MediaStoreDeleteHelper(application)
    private val recordingRepository = RecordingRepository.get(application)

    private val _uiState = MutableStateFlow(GalleryUiState(isLoading = true))
    val uiState: StateFlow<GalleryUiState> = _uiState.asStateFlow()

    val badgeCount: StateFlow<Int> = _uiState
        .map { it.items.size }
        .distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    private var pendingConfirmUri: Uri? = null
    private var pendingConfirmPath: String? = null
    private var refreshJob: Job? = null
    private val enrichingPaths = mutableSetOf<String>()
    private val metadataPermits = Semaphore(2)
    var onNeedDeleteConfirmation: ((IntentSender) -> Unit)? = null

    init {
        viewModelScope.launch {
            settingsRepository.settings
                .map { it.customSaveUri }
                .distinctUntilChanged()
                .collect { customSaveUri ->
                    repository.setCustomSaveUri(customSaveUri)
                    refresh()
                }
        }
        viewModelScope.launch {
            recordingRepository.events.collect { event ->
                when (event) {
                    is RecordingEvent.FileSegmentCreated -> refresh()
                    else -> Unit
                }
            }
        }
    }

    fun refresh() {
        refreshJob?.cancel()
        refreshJob = viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, message = null) }
            val scanned = withContext(Dispatchers.IO) { repository.listRecordings() }
            val previous = _uiState.value.items.associateBy { it.filePath }
            val items = scanned.map { item ->
                val known = previous[item.filePath]
                    ?.takeIf {
                        it.fileSizeBytes == item.fileSizeBytes &&
                            it.modifiedAtMs == item.modifiedAtMs
                    }
                if (known == null) item else item.copy(
                    durationMs = known.durationMs,
                    width = known.width,
                    height = known.height,
                    thumbnailPath = known.thumbnailPath,
                )
            }
            _uiState.update { it.copy(items = items, isLoading = false) }
        }
    }

    fun enrichMetadata(item: RecordingItem) {
        if (
            item.durationMs != null ||
            !synchronized(enrichingPaths) { enrichingPaths.add(item.filePath) }
        ) {
            return
        }
        viewModelScope.launch(Dispatchers.IO) {
            try {
                metadataPermits.withPermit {
                    val meta = VideoMetadataHelper.extract(getApplication(), item.filePath)
                        ?: return@withPermit
                    _uiState.update { state ->
                        state.copy(
                            items = state.items.map { current ->
                                if (
                                    current.filePath == item.filePath &&
                                    current.fileSizeBytes == item.fileSizeBytes &&
                                    current.modifiedAtMs == item.modifiedAtMs
                                ) {
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
            } finally {
                synchronized(enrichingPaths) {
                    enrichingPaths.remove(item.filePath)
                }
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

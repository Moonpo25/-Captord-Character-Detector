package com.captord.screenrecorder.core.bookmark

import com.captord.screenrecorder.feature.recorder.model.RecordingBookmark

/**
 * Persists user-bookmarked moments per recording.
 *
 * Backed by DataStore Preferences in production (one JSON-encoded
 * string per recording, keyed by `bookmarks_<sha256(path)>`); tests
 * use the in-memory fake. The store is append-only during a session —
 * stale bookmarks are pruned when the source `.mp4` is missing on
 * disk, which prevents the gallery from rendering chips for deleted
 * recordings.
 */
interface BookmarkStore {
    /**
     * Add a bookmark for [recordingPath] at [timestampMs]. Returns the
     * created bookmark with a stable id; no-op if a bookmark already
     * exists within [coalesceWindowMs] of [timestampMs] (default 500 ms).
     */
    suspend fun addBookmark(recordingPath: String, timestampMs: Long, label: String? = null): RecordingBookmark?

    /** Returns all bookmarks for [recordingPath], sorted by timestamp ascending. */
    suspend fun getBookmarks(recordingPath: String): List<RecordingBookmark>

    /** Removes a single bookmark by its [RecordingBookmark.id]. */
    suspend fun removeBookmark(id: String)
}

/**
 * In-memory implementation used by tests and as a working fallback
 * until the DataStore wiring lands. Thread-safe enough for the test
 * runner; not safe for concurrent production writes.
 */
class InMemoryBookmarkStore : BookmarkStore {
    private val bookmarks = mutableMapOf<String, MutableList<RecordingBookmark>>()
    private var counter = 0

    override suspend fun addBookmark(
        recordingPath: String,
        timestampMs: Long,
        label: String?,
    ): RecordingBookmark? {
        val existing = bookmarks[recordingPath].orEmpty()
        val coalesceWindowMs = 500L
        if (existing.any { kotlin.math.abs(it.timestampMs - timestampMs) < coalesceWindowMs }) {
            return null
        }
        val id = "bm_${++counter}"
        val bookmark = RecordingBookmark(
            id = id,
            recordingPath = recordingPath,
            timestampMs = timestampMs,
            label = label,
            createdAt = System.currentTimeMillis(),
        )
        bookmarks.getOrPut(recordingPath) { mutableListOf() }.add(bookmark)
        return bookmark
    }

    override suspend fun getBookmarks(recordingPath: String): List<RecordingBookmark> =
        bookmarks[recordingPath].orEmpty().sortedBy { it.timestampMs }

    override suspend fun removeBookmark(id: String) {
        bookmarks.values.forEach { list -> list.removeAll { it.id == id } }
    }
}
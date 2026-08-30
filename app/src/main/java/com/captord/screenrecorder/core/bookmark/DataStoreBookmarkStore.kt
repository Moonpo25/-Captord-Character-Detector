package com.captord.screenrecorder.core.bookmark

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.captord.screenrecorder.feature.recorder.model.RecordingBookmark
import kotlinx.coroutines.flow.first
import org.json.JSONArray
import org.json.JSONObject

private val Context.bookmarkDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "captord_bookmarks",
)

/**
 * DataStore-backed implementation of [BookmarkStore]. Bookmarks are
 * stored as a JSON-encoded array per recording path (key derived from
 * the SHA-256 prefix of the path, same scheme used by gallery items).
 *
 * The store is append-only during a session and intentionally simple —
 * we do not migrate, version, or shard. Recordings are local; the data
 * volume is bounded by user interaction.
 */
class DataStoreBookmarkStore(private val context: Context) : BookmarkStore {

    private val coalesceWindowMs = 500L

    override suspend fun addBookmark(
        recordingPath: String,
        timestampMs: Long,
        label: String?,
    ): RecordingBookmark? {
        val key = bookmarksKey(recordingPath)
        val existing = readAll(key)
        if (existing.any { kotlin.math.abs(it.timestampMs - timestampMs) < coalesceWindowMs }) {
            return null
        }
        val id = "bm_${System.currentTimeMillis()}_${existing.size}"
        val bookmark = RecordingBookmark(
            id = id,
            recordingPath = recordingPath,
            timestampMs = timestampMs,
            label = label,
            createdAt = System.currentTimeMillis(),
        )
        val updated = existing + bookmark
        writeAll(key, updated)
        return bookmark
    }

    override suspend fun getBookmarks(recordingPath: String): List<RecordingBookmark> =
        readAll(bookmarksKey(recordingPath)).sortedBy { it.timestampMs }

    override suspend fun removeBookmark(id: String) {
        // Search across all known keys; for the MVP scale this is fine.
        val prefs = context.bookmarkDataStore.data.first()
        context.bookmarkDataStore.edit { mutable ->
            prefs.asMap().forEach { (k, v) ->
                if (k.name.startsWith(KEY_PREFIX) && v is String) {
                    val items = decode(v)
                    val filtered = items.filterNot { it.id == id }
                    if (filtered.size != items.size) {
                        @Suppress("UNCHECKED_CAST")
                        mutable[k as Preferences.Key<String>] = encode(filtered)
                    }
                }
            }
        }
    }

    private suspend fun readAll(key: Preferences.Key<String>): List<RecordingBookmark> {
        val raw = context.bookmarkDataStore.data.first()[key] ?: return emptyList()
        return decode(raw)
    }

    private suspend fun writeAll(key: Preferences.Key<String>, items: List<RecordingBookmark>) {
        context.bookmarkDataStore.edit { it[key] = encode(items) }
    }

    private fun bookmarksKey(recordingPath: String): Preferences.Key<String> =
        stringPreferencesKey("$KEY_PREFIX${pathSlug(recordingPath)}")

    private fun encode(items: List<RecordingBookmark>): String {
        val array = JSONArray()
        items.forEach { bm ->
            val obj = JSONObject()
            obj.put("id", bm.id)
            obj.put("recordingPath", bm.recordingPath)
            obj.put("timestampMs", bm.timestampMs)
            obj.put("label", bm.label ?: JSONObject.NULL)
            obj.put("createdAt", bm.createdAt)
            array.put(obj)
        }
        return array.toString()
    }

    private fun decode(raw: String): List<RecordingBookmark> {
        if (raw.isBlank()) return emptyList()
        return try {
            val array = JSONArray(raw)
            buildList {
                for (i in 0 until array.length()) {
                    val obj = array.getJSONObject(i)
                    add(
                        RecordingBookmark(
                            id = obj.getString("id"),
                            recordingPath = obj.getString("recordingPath"),
                            timestampMs = obj.getLong("timestampMs"),
                            label = if (obj.isNull("label")) null else obj.getString("label"),
                            createdAt = obj.optLong("createdAt"),
                        ),
                    )
                }
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun pathSlug(path: String): String {
        // Cheap, stable slug for the DataStore key — first 32 chars of
        // path.hashCode encoded as unsigned hex. Avoids `/` and space.
        val hex = path.hashCode().toUInt().toString(16)
        return hex.padStart(8, '0').take(32)
    }

    companion object {
        private const val KEY_PREFIX = "bookmarks_"

        fun instance(context: Context): BookmarkStore = DataStoreBookmarkStore(context.applicationContext)
    }
}
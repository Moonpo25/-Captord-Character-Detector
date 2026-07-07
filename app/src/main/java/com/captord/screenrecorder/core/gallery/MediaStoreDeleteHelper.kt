package com.captord.screenrecorder.core.gallery

import android.app.Activity
import android.app.PendingIntent
import android.app.RecoverableSecurityException
import android.content.ContentUris
import android.content.Context
import android.content.IntentSender
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.util.Log
import java.io.File

sealed class DeleteOutcome {
    data object Deleted : DeleteOutcome()
    data object Cancelled : DeleteOutcome()
    data object Failed : DeleteOutcome()
    data class NeedsUserConfirmation(
        val intentSender: IntentSender,
        val uri: Uri,
    ) : DeleteOutcome()
}

class MediaStoreDeleteHelper(private val context: Context) {

    fun tryDelete(filePath: String): DeleteOutcome {
        val uri = queryMediaStoreUri(filePath)
        if (uri != null) {
            return try {
                val deleted = context.contentResolver.delete(uri, null, null)
                if (deleted > 0) DeleteOutcome.Deleted else fallbackFileDelete(filePath)
            } catch (e: SecurityException) {
                Log.i(TAG, "Delete needs user confirmation for $filePath")
                val sender = createDeleteIntentSender(uri, e) ?: return DeleteOutcome.Failed
                DeleteOutcome.NeedsUserConfirmation(sender, uri)
            } catch (e: Exception) {
                Log.e(TAG, "MediaStore delete failed", e)
                fallbackFileDelete(filePath)
            }
        }
        return fallbackFileDelete(filePath)
    }

    fun completeAfterUserGrant(uri: Uri, resultCode: Int): DeleteOutcome {
        if (resultCode != Activity.RESULT_OK) return DeleteOutcome.Cancelled
        return try {
            if (Build.VERSION.SDK_INT == Build.VERSION_CODES.Q) {
                val deleted = context.contentResolver.delete(uri, null, null)
                if (deleted > 0) DeleteOutcome.Deleted else DeleteOutcome.Failed
            } else {
                // R+: createDeleteRequest already deleted on OK
                DeleteOutcome.Deleted
            }
        } catch (e: Exception) {
            Log.e(TAG, "Post-confirmation delete failed", e)
            DeleteOutcome.Failed
        }
    }

    private fun createDeleteIntentSender(uri: Uri, securityException: SecurityException): IntentSender? {
        return try {
            when {
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.R -> {
                    val pi: PendingIntent =
                        MediaStore.createDeleteRequest(context.contentResolver, listOf(uri))
                    pi.intentSender
                }
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q &&
                    securityException is RecoverableSecurityException -> {
                    securityException.userAction.actionIntent.intentSender
                }
                else -> null
            }
        } catch (e: Exception) {
            Log.e(TAG, "createDeleteRequest failed", e)
            null
        }
    }

    private fun fallbackFileDelete(filePath: String): DeleteOutcome {
        return try {
            val file = File(filePath).canonicalFile
            if (file.exists() && file.delete()) DeleteOutcome.Deleted else DeleteOutcome.Failed
        } catch (_: Exception) {
            DeleteOutcome.Failed
        }
    }

    fun queryMediaStoreUri(filePath: String): Uri? {
        val collection = MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
        val projection = arrayOf(MediaStore.Video.Media._ID)
        val file = try {
            File(filePath).canonicalFile
        } catch (_: Exception) {
            File(filePath)
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val parentPath = file.parent?.removePrefix("/storage/emulated/0/")?.removePrefix("/") ?: ""
            val relativePath = if (parentPath.endsWith("/")) parentPath else "$parentPath/"
            val selection =
                "${MediaStore.Video.Media.RELATIVE_PATH} = ? AND ${MediaStore.Video.Media.DISPLAY_NAME} = ?"
            context.contentResolver.query(
                collection,
                projection,
                selection,
                arrayOf(relativePath, file.name),
                null,
            )?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val id = cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.Video.Media._ID))
                    return ContentUris.withAppendedId(collection, id)
                }
            }
        }

        val selection = "${MediaStore.Video.Media.DATA} = ?"
        context.contentResolver.query(
            collection,
            projection,
            selection,
            arrayOf(file.absolutePath),
            null,
        )?.use { cursor ->
            if (cursor.moveToFirst()) {
                val id = cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.Video.Media._ID))
                return ContentUris.withAppendedId(collection, id)
            }
        }
        return null
    }

    companion object {
        private const val TAG = "MediaStoreDeleteHelper"
    }
}

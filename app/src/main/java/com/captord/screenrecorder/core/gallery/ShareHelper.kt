package com.captord.screenrecorder.core.gallery

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import com.captord.screenrecorder.R
import java.io.File

object ShareHelper {
    fun shareVideo(context: Context, filePath: String) {
        val uri = if (filePath.startsWith("content:")) {
            Uri.parse(filePath)
        } else {
            val file = File(filePath)
            if (!file.isFile) return
            FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        }
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "video/mp4"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        val chooserTitle = context.getString(R.string.share_chooser_title)
        context.startActivity(Intent.createChooser(intent, chooserTitle))
    }
}

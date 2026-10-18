
package com.captord.screenrecorder.core.detection

import android.content.Context
import android.graphics.Bitmap
import com.googlecode.tesseract.android.TessBaseAPI
import java.io.File

class ArabicTextRecognizer(context: Context) {

    private val dataDirectory =
        File(context.filesDir, "tesseract")

    private var tess: TessBaseAPI? = null

    @Synchronized
    fun initialize(): Boolean {
        if (tess != null) return true

        val tessDataDirectory =
            File(dataDirectory, "tessdata")

        if (!tessDataDirectory.exists() &&
            !tessDataDirectory.mkdirs()
        ) {
            return false
        }

        val languageFile =
            File(tessDataDirectory, "ara.traineddata")

        if (!languageFile.exists()) {
            return false
        }

        val engine = TessBaseAPI()

        return try {
            if (engine.init(dataDirectory.absolutePath, "ara")) {
                tess = engine
                true
            } else {
                engine.recycle()
                false
            }
        } catch (e: Exception) {
            engine.recycle()
            false
        }
    }

    @Synchronized
    fun recognize(bitmap: Bitmap): String {
        val engine = tess ?: return ""

        return try {
            engine.setImage(bitmap)
            engine.getUTF8Text() ?: ""
        } catch (e: Exception) {
            ""
        }
    }

    @Synchronized
    fun close() {
        tess?.recycle()
        tess = null
    }
}

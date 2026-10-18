
package com.captord.screenrecorder.core.detection

import android.content.Context
import android.graphics.Bitmap
import com.googlecode.tesseract.android.TessBaseAPI
import java.io.File
import java.io.FileOutputStream

class ArabicTextRecognizer(context: Context) {

    private val appContext = context.applicationContext

    private val dataDirectory =
        File(appContext.filesDir, "tesseract")

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

        return try {
            if (!languageFile.exists() || languageFile.length() == 0L) {
                appContext.assets.open("tessdata/ara.traineddata").use { input ->
                    FileOutputStream(languageFile).use { output ->
                        input.copyTo(output)
                    }
                }
            }

            if (!languageFile.exists() || languageFile.length() == 0L) {
                return false
            }

            val engine = TessBaseAPI()

            if (engine.init(dataDirectory.absolutePath, "ara")) {
                tess = engine
                true
            } else {
                engine.recycle()
                false
            }
        } catch (e: Exception) {
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
        } finally {
            engine.clear()
        }
    }

    @Synchronized
    fun close() {
        tess?.recycle()
        tess = null
    }
}

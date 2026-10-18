
package com.captord.screenrecorder.core.detection

import android.content.Context
import android.graphics.Bitmap
import android.os.Handler
import android.os.Looper
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

class LiveNameDetector(
    context: Context,
    private val onNameDetected: (String) -> Unit,
) {
    private val recognizer = ArabicTextRecognizer(context)
    private val executor = Executors.newSingleThreadExecutor()
    private val busy = AtomicBoolean(false)
    private val mainHandler = Handler(Looper.getMainLooper())

    @Volatile
    private var enabled = false

    @Volatile
    private var lastDetectedNames = emptySet<String>()

    fun start(): Boolean {
        if (enabled) return true

        val initialized = recognizer.initialize()
        if (initialized) enabled = true
        return initialized
    }

    fun analyze(bitmap: Bitmap, names: List<String>) {
        if (!enabled || bitmap.isRecycled) return
        if (!busy.compareAndSet(false, true)) return

        val frame = try {
            bitmap.copy(bitmap.config ?: Bitmap.Config.ARGB_8888, false)
        } catch (_: Exception) {
            busy.set(false)
            return
        }

        executor.execute {
            try {
                if (!enabled) return@execute

                val text = recognizer.recognize(frame)
                val matches = NameMatcher.findMatches(text, names)
                    .map { NameMatcher.normalize(it) }
                    .toSet()

                val newlyDetected = matches - lastDetectedNames
                lastDetectedNames = matches

                newlyDetected.forEach { name ->
                    mainHandler.post {
                        if (enabled) onNameDetected(name)
                    }
                }
            } finally {
                frame.recycle()
                busy.set(false)
            }
        }
    }

    fun resetDetectionHistory() {
        lastDetectedNames = emptySet()
    }

    fun stop() {
        enabled = false
        lastDetectedNames = emptySet()
        executor.execute {
            recognizer.close()
        }
    }

    fun release() {
        enabled = false
        executor.shutdownNow()
        recognizer.close()
    }
}

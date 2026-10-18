
package com.captord.screenrecorder.core.engine

import android.graphics.Bitmap
import android.os.Handler
import android.os.Looper
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Receives screen frames and forwards selected frames
 * to the Arabic name detector.
 *
 * This class does not capture the screen by itself.
 */
class ScreenFrameAnalyzer(
    private val onNameDetected: (String) -> Unit
) {

    private val executor: ExecutorService =
        Executors.newSingleThreadExecutor()

    private val busy = AtomicBoolean(false)

    private val mainHandler = Handler(Looper.getMainLooper())

    @Volatile
    private var running = false

    private var detector:
        com.captord.screenrecorder.core.detection.LiveNameDetector? = null

    fun start(
        context: android.content.Context,
        names: List<String>
    ): Boolean {
        if (running) return true

        val newDetector =
            com.captord.screenrecorder.core.detection.LiveNameDetector(
                context.applicationContext
            ) { detectedName ->
                onNameDetected(detectedName)
            }

        if (!newDetector.start()) {
            newDetector.release()
            return false
        }

        detector = newDetector
        running = true
        return true
    }

    fun submitFrame(bitmap: Bitmap, names: List<String>) {
        if (!running || bitmap.isRecycled) return
        if (!busy.compareAndSet(false, true)) return

        val frame = try {
            bitmap.copy(Bitmap.Config.ARGB_8888, false)
        } catch (_: Exception) {
            busy.set(false)
            return
        }

        executor.execute {
            try {
                if (running) {
                    detector?.analyze(frame, names)
                }
            } finally {
                frame.recycle()
                busy.set(false)
            }
        }
    }

    fun reset() {
        detector?.resetDetectionHistory()
    }

    fun stop() {
        running = false
        detector?.release()
        detector = null
    }

    fun release() {
        stop()
        executor.shutdownNow()
    }
}

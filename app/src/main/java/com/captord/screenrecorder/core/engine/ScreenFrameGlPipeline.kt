package com.captord.screenrecorder.core.engine

import android.graphics.Bitmap
import android.graphics.SurfaceTexture
import android.view.Surface

/**
 * وسيط بين التقاط الشاشة ومحرك التسجيل.
 * يحتاج ربطه بمحرك التسجيل قبل أن يعمل.
 */
class ScreenFrameGlPipeline(
    private val encoderSurface: Surface,
    private val width: Int,
    private val height: Int,
    private val onFrame: (Bitmap) -> Unit
) {
    private var inputTexture: SurfaceTexture? = null
    private var inputSurface: Surface? = null

    fun getInputSurface(): Surface? = inputSurface

    fun release() {
        try {
            inputSurface?.release()
        } catch (_: Exception) {
        }

        try {
            inputTexture?.release()
        } catch (_: Exception) {
        }

        inputSurface = null
        inputTexture = null
    }
}

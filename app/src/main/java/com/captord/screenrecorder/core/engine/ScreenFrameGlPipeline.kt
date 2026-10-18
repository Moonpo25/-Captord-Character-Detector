package com.captord.screenrecorder.core.engine

import android.graphics.Bitmap
import android.graphics.SurfaceTexture
import android.view.Surface

class ScreenFrameGlPipeline(
    private val encoderSurface: Surface,
    private val width: Int,
    private val height: Int,
    private val onFrame: (Bitmap) -> Unit
) {
    private var surfaceTexture: SurfaceTexture? = null
    private var inputSurface: Surface? = null

    fun getInputSurface(): Surface = encoderSurface

    fun release() {
        inputSurface?.release()
        inputSurface = null

        surfaceTexture?.release()
        surfaceTexture = null
    }
}

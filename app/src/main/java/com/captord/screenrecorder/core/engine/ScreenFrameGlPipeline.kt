package com.captord.screenrecorder.core.engine

import android.graphics.Bitmap
import android.view.Surface

class ScreenFrameGlPipeline(
    private val encoderSurface: Surface,
    private val width: Int,
    private val height: Int,
    private val onFrame: (Bitmap) -> Unit
) {
    fun getInputSurface(): Surface = encoderSurface

    fun release() {
        // Surface is owned by the recorder.
    }
}

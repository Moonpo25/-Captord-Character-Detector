
package com.captord.screenrecorder.core.engine

import android.graphics.Bitmap
import android.graphics.Matrix
import android.graphics.SurfaceTexture
import android.opengl.EGL14
import android.opengl.EGLExt
import android.opengl.GLES11Ext
import android.opengl.GLES20
import android.opengl.Matrix as GLMatrix
import android.os.Handler
import android.os.HandlerThread
import android.os.Looper
import android.view.Surface
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

class ScreenFrameGlPipeline(
    private val encoderSurface: Surface,
    private val width: Int,
    private val height: Int,
    private val onFrame: (Bitmap) -> Unit
) {

    companion object {
        private const val EGL_RECORDABLE_ANDROID = 0x3142
        private const val EGL_OPENGL_ES2_BIT = 4
        private const val EGL_CONTEXT_CLIENT_VERSION = 0x3098
        private const val FRAME_CAPTURE_INTERVAL_NS = 1_000_000_000L

        private const val VERTEX_SHADER = """
            attribute vec4 aPosition;
            attribute vec4 aTexCoord;
            uniform mat4 uTextureMatrix;
            varying vec2 vTexCoord;

            void main() {
                gl_Position = aPosition;
                vTexCoord = (uTextureMatrix * aTexCoord).xy;
            }
        """

        private const val FRAGMENT_SHADER = """
            #extension GL_OES_EGL_image_external : require
            precision mediump float;

            uniform samplerExternalOES uTexture;
            varying vec2 vTexCoord;

            void main() {
                gl_FragColor = texture2D(uTexture, vTexCoord);
            }
        """
    }

    private val released = AtomicBoolean(false)
    private val initialized = CountDownLatch(1)

    private val glThread = HandlerThread("ScreenFrameGL").apply {
        start()
    }

    private val glHandler = Handler(glThread.looper)

    @Volatile
    private var initializationError: Throwable? = null

    @Volatile
    private var inputSurface: Surface? = null

    private var eglDisplay = EGL14.EGL_NO_DISPLAY
    private var eglContext = EGL14.EGL_NO_CONTEXT
    private var eglConfig: android.opengl.EGLConfig? = null
    private var eglWindowSurface = EGL14.EGL_NO_SURFACE

    private var surfaceTexture: SurfaceTexture? = null
    private var textureInputSurface: Surface? = null

    private var textureId = 0
    private var program = 0

    private var positionLocation = -1
    private var texCoordLocation = -1
    private var textureMatrixLocation = -1
    private var samplerLocation = -1

    private var lastCaptureTimestampNs = 0L

    private val textureMatrix = FloatArray(16)

    private val vertexBuffer: FloatBuffer =
        floatBuffer(
            floatArrayOf(
                -1f, -1f, 0f, 1f,
                 1f, -1f, 1f, 1f,
                -1f,  1f, 0f, 0f,
                 1f,  1f, 1f, 0f
            )
        )

    private val framePending = AtomicBoolean(false)

    init {
        require(width > 0 && height > 0) {
            "Width and height must be positive"
        }

        glHandler.post {
            try {
                initializeGl()
            } catch (error: Throwable) {
                initializationError = error
                releaseGlResources()
            } finally {
                initialized.countDown()
            }
        }
    }

    /**
     * Give this surface to MediaProjection.createVirtualDisplay().
     * The returned surface receives screen frames for GL processing.
     */
    fun getInputSurface(): Surface {
        check(!released.get()) {
            "Pipeline has been released"
        }

        check(initialized.await(10, TimeUnit.SECONDS)) {
            "Timed out initializing OpenGL pipeline"
        }

        initializationError?.let {
            throw IllegalStateException(
                "Failed to initialize OpenGL pipeline",
                it
            )
        }

        return checkNotNull(inputSurface) {
            "Input surface was not created"
        }
    }

    private fun initializeGl() {
        check(!released.get()) {
            "Pipeline has been released"
        }

        eglDisplay = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY)

        check(eglDisplay != EGL14.EGL_NO_DISPLAY) {
            "Unable to get EGL display"
        }

        val version = IntArray(2)

        check(
            EGL14.eglInitialize(
                eglDisplay,
                version,
                0,
                version,
                1
            )
        ) {
            "Unable to initialize EGL"
        }

        val configAttributes = intArrayOf(
            EGL14.EGL_RED_SIZE, 8,
            EGL14.EGL_GREEN_SIZE, 8,
            EGL14.EGL_BLUE_SIZE, 8,
            EGL14.EGL_ALPHA_SIZE, 8,
            EGL14.EGL_RENDERABLE_TYPE, EGL_OPENGL_ES2_BIT,
            EGL_RECORDABLE_ANDROID, 1,
            EGL14.EGL_NONE
        )

        val configs = arrayOfNulls<android.opengl.EGLConfig>(1)
        val configCount = IntArray(1)

        check(
            EGL14.eglChooseConfig(
                eglDisplay,
                configAttributes,
                0,
                configs,
                0,
                configs.size,
                configCount,
                0
            )
        ) {
            "Unable to choose EGL config"
        }

        check(configCount[0] > 0 && configs[0] != null) {
            "No suitable EGL config found"
        }

        eglConfig = configs[0]

        val contextAttributes = intArrayOf(
            EGL_CONTEXT_CLIENT_VERSION, 2,
            EGL14.EGL_NONE
        )

        eglContext = EGL14.eglCreateContext(
            eglDisplay,
            eglConfig,
            EGL14.EGL_NO_CONTEXT,
            contextAttributes,
            0
        )

        check(eglContext != EGL14.EGL_NO_CONTEXT) {
            "Unable to create EGL context"
        }

        val surfaceAttributes = intArrayOf(
            EGL14.EGL_NONE
        )

        eglWindowSurface = EGL14.eglCreateWindowSurface(
            eglDisplay,
            eglConfig,
            encoderSurface,
            surfaceAttributes,
            0
        )

        check(eglWindowSurface != EGL14.EGL_NO_SURFACE) {
            "Unable to create encoder EGL surface"
        }

        makeCurrent()

        program = createProgram(VERTEX_SHADER, FRAGMENT_SHADER)

        positionLocation =
            GLES20.glGetAttribLocation(program, "aPosition")
        texCoordLocation =
            GLES20.glGetAttribLocation(program, "aTexCoord")
        textureMatrixLocation =
            GLES20.glGetUniformLocation(program, "uTextureMatrix")
        samplerLocation =
            GLES20.glGetUniformLocation(program, "uTexture")

        check(
            positionLocation >= 0 &&
                texCoordLocation >= 0 &&
                textureMatrixLocation >= 0 &&
                samplerLocation >= 0
        ) {
            "Unable to find OpenGL shader variables"
        }

        val textures = IntArray(1)
        GLES20.glGenTextures(1, textures, 0)
        textureId = textures[0]

        check(textureId != 0) {
            "Unable to create external texture"
        }

        GLES20.glBindTexture(
            GLES11Ext.GL_TEXTURE_EXTERNAL_OES,
            textureId
        )

        GLES20.glTexParameteri(
            GLES11Ext.GL_TEXTURE_EXTERNAL_OES,
            GLES20.GL_TEXTURE_MIN_FILTER,
            GLES20.GL_LINEAR
        )

        GLES20.glTexParameteri(
            GLES11Ext.GL_TEXTURE_EXTERNAL_OES,
            GLES20.GL_TEXTURE_MAG_FILTER,
            GLES20.GL_LINEAR
        )

        GLES20.glTexParameteri(
            GLES11Ext.GL_TEXTURE_EXTERNAL_OES,
            GLES20.GL_TEXTURE_WRAP_S,
            GLES20.GL_CLAMP_TO_EDGE
        )

        GLES20.glTexParameteri(
            GLES11Ext.GL_TEXTURE_EXTERNAL_OES,
            GLES20.GL_TEXTURE_WRAP_T,
            GLES20.GL_CLAMP_TO_EDGE
        )

        surfaceTexture = SurfaceTexture(textureId).apply {
            setDefaultBufferSize(width, height)

            setOnFrameAvailableListener({
                if (!released.get() &&
                    framePending.compareAndSet(false, true)
                ) {
                    glHandler.post {
                        framePending.set(false)

                        if (!released.get()) {
                            renderFrame()
                        }
                    }
                }
            }, glHandler)
        }

        textureInputSurface = Surface(
            checkNotNull(surfaceTexture)
        )

        inputSurface = textureInputSurface

        GLMatrix.setIdentityM(textureMatrix, 0)

        GLES20.glViewport(0, 0, width, height)
        checkGlError("initializeGl")
    }

    private fun renderFrame() {
        if (released.get()) return

        try {
            makeCurrent()

            val texture = surfaceTexture ?: return

            texture.updateTexImage()
            texture.getTransformMatrix(textureMatrix)

            val timestampNs = texture.timestamp

            GLES20.glViewport(0, 0, width, height)
            GLES20.glClearColor(0f, 0f, 0f, 1f)
            GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT)

            GLES20.glUseProgram(program)

            GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
            GLES20.glBindTexture(
                GLES11Ext.GL_TEXTURE_EXTERNAL_OES,
                textureId
            )

            GLES20.glUniform1i(samplerLocation, 0)

            GLES20.glUniformMatrix4fv(
                textureMatrixLocation,
                1,
                false,
                textureMatrix,
                0
            )

            vertexBuffer.position(0)

            GLES20.glEnableVertexAttribArray(positionLocation)
            GLES20.glVertexAttribPointer(
                positionLocation,
                2,
                GLES20.GL_FLOAT,
                false,
                16,
                vertexBuffer
            )

            vertexBuffer.position(2)

            GLES20.glEnableVertexAttribArray(texCoordLocation)
            GLES20.glVertexAttribPointer(
                texCoordLocation,
                2,
                GLES20.GL_FLOAT,
                false,
                16,
                vertexBuffer
            )

            GLES20.glDrawArrays(
                GLES20.GL_TRIANGLE_STRIP,
                0,
                4
            )

            GLES20.glDisableVertexAttribArray(positionLocation)
            GLES20.glDisableVertexAttribArray(texCoordLocation)

            checkGlError("renderFrame")

            if (timestampNs > 0L) {
                EGLExt.eglPresentationTimeANDROID(
                    eglDisplay,
                    eglWindowSurface,
                    timestampNs
                )
            }

            val nowNs = System.nanoTime()

            if (
                lastCaptureTimestampNs == 0L ||
                nowNs - lastCaptureTimestampNs >=
                FRAME_CAPTURE_INTERVAL_NS
            ) {
                lastCaptureTimestampNs = nowNs
                captureFrame()
            }

            check(
                EGL14.eglSwapBuffers(
                    eglDisplay,
                    eglWindowSurface
                )
            ) {
                "Unable to send frame to video encoder"
            }
        } catch (error: Throwable) {
            if (!released.get()) {
                android.util.Log.e(
                    "ScreenFrameGL",
                    "Frame processing failed",
                    error
                )
            }
        }
    }

    private fun captureFrame() {
        val buffer = ByteBuffer.allocateDirect(
            width * height * 4
        ).order(ByteOrder.nativeOrder())

        GLES20.glReadPixels(
            0,
            0,
            width,
            height,
            GLES20.GL_RGBA,
            GLES20.GL_UNSIGNED_BYTE,
            buffer
        )

        checkGlError("captureFrame")

        buffer.rewind()

        val rawBitmap = Bitmap.createBitmap(
            width,
            height,
            Bitmap.Config.ARGB_8888
        )

        try {
            rawBitmap.copyPixelsFromBuffer(buffer)

            val flipMatrix = Matrix().apply {
                setScale(
                    1f,
                    -1f,
                    width / 2f,
                    height / 2f
                )
            }

            val outputBitmap = Bitmap.createBitmap(
                rawBitmap,
                0,
                0,
                width,
                height,
                flipMatrix,
                true
            )

            try {
                // The callback receives ownership of this bitmap.
                // The receiver must recycle it when finished.
                onFrame(outputBitmap)
            } catch (error: Throwable) {
                if (!outputBitmap.isRecycled) {
                    outputBitmap.recycle()
                }
                throw error
            }
        } finally {
            if (!rawBitmap.isRecycled) {
                rawBitmap.recycle()
            }
        }
    }

    private fun makeCurrent() {
        check(
            EGL14.eglMakeCurrent(
                eglDisplay,
                eglWindowSurface,
                eglWindowSurface,
                eglContext
            )
        ) {
            "Unable to make EGL context current"
        }
    }

    private fun createProgram(
        vertexSource: String,
        fragmentSource: String
    ): Int {
        val vertexShader = compileShader(
            GLES20.GL_VERTEX_SHADER,
            vertexSource
        )

        val fragmentShader = compileShader(
            GLES20.GL_FRAGMENT_SHADER,
            fragmentSource
        )

        val shaderProgram = GLES20.glCreateProgram()

        check(shaderProgram != 0) {
            "Unable to create shader program"
        }

        GLES20.glAttachShader(shaderProgram, vertexShader)
        GLES20.glAttachShader(shaderProgram, fragmentShader)
        GLES20.glLinkProgram(shaderProgram)

        val linkStatus = IntArray(1)

        GLES20.glGetProgramiv(
            shaderProgram,
            GLES20.GL_LINK_STATUS,
            linkStatus,
            0
        )

        val linkLog = GLES20.glGetProgramInfoLog(shaderProgram)

        GLES20.glDeleteShader(vertexShader)
        GLES20.glDeleteShader(fragmentShader)

        check(linkStatus[0] == GLES20.GL_TRUE) {
            "Shader linking failed: $linkLog"
        }

        return shaderProgram
    }

    private fun compileShader(
        type: Int,
        source: String
    ): Int {
        val shader = GLES20.glCreateShader(type)

        check(shader != 0) {
            "Unable to create shader"
        }

        GLES20.glShaderSource(shader, source)
        GLES20.glCompileShader(shader)

        val compileStatus = IntArray(1)

        GLES20.glGetShaderiv(
            shader,
            GLES20.GL_COMPILE_STATUS,
            compileStatus,
            0
        )

        val compileLog = GLES20.glGetShaderInfoLog(shader)

        check(compileStatus[0] == GLES20.GL_TRUE) {
            GLES20.glDeleteShader(shader)
            "Shader compilation failed: $compileLog"
        }

        return shader
    }

    private fun checkGlError(operation: String) {
        val error = GLES20.glGetError()

        check(error == GLES20.GL_NO_ERROR) {
            "$operation: OpenGL error 0x${error.toString(16)}"
        }
    }

    private fun releaseGlResources() {
        if (eglDisplay != EGL14.EGL_NO_DISPLAY) {
            if (eglContext != EGL14.EGL_NO_CONTEXT &&
                eglWindowSurface != EGL14.EGL_NO_SURFACE
            ) {
                EGL14.eglMakeCurrent(
                    eglDisplay,
                    eglWindowSurface,
                    eglWindowSurface,
                    eglContext
                )
            }

            textureInputSurface?.release()
            textureInputSurface = null
            inputSurface = null

            surfaceTexture?.setOnFrameAvailableListener(null)
            surfaceTexture?.release()
            surfaceTexture = null

            if (textureId != 0) {
                GLES20.glDeleteTextures(
                    1,
                    intArrayOf(textureId),
                    0
                )
                textureId = 0
            }

            if (program != 0) {
                GLES20.glDeleteProgram(program)
                program = 0
            }

            if (eglWindowSurface != EGL14.EGL_NO_SURFACE) {
                EGL14.eglDestroySurface(
                    eglDisplay,
                    eglWindowSurface
                )
                eglWindowSurface = EGL14.EGL_NO_SURFACE
            }

            if (eglContext != EGL14.EGL_NO_CONTEXT) {
                EGL14.eglDestroyContext(
                    eglDisplay,
                    eglContext
                )
                eglContext = EGL14.EGL_NO_CONTEXT
            }

            EGL14.eglTerminate(eglDisplay)
            eglDisplay = EGL14.EGL_NO_DISPLAY
        }
    }

    fun release() {
        if (!released.compareAndSet(false, true)) return

        if (Looper.myLooper() == glThread.looper) {
            releaseGlResources()
            glThread.quitSafely()
            return
        }

        val done = CountDownLatch(1)

        glHandler.post {
            try {
                releaseGlResources()
            } finally {
                done.countDown()
            }
        }

        if (!done.await(5, TimeUnit.SECONDS)) {
            android.util.Log.w(
                "ScreenFrameGL",
                "Timed out waiting for GL cleanup"
            )
        }

        glThread.quitSafely()
    }

    private fun floatBuffer(values: FloatArray): FloatBuffer {
        return ByteBuffer
            .allocateDirect(values.size * 4)
            .order(ByteOrder.nativeOrder())
            .asFloatBuffer()
            .apply {
                put(values)
                position(0)
            }
    }
}

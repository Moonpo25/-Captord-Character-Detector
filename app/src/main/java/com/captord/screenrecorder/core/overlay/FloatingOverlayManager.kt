package com.captord.screenrecorder.core.overlay

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.provider.Settings
import android.util.TypedValue
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.TextView
import com.captord.screenrecorder.core.service.ScreenRecorderService

/**
 * Native WindowManager overlay (not Compose). High-contrast fixed colors for readability
 * independent of Material You wallpaper.
 */
class FloatingOverlayManager(private val context: Context) {

    private val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private var rootView: LinearLayout? = null
    private var timerView: TextView? = null
    private var pauseView: TextView? = null
    private var layoutParams: WindowManager.LayoutParams? = null

    private var dragStartX = 0
    private var dragStartY = 0
    private var touchStartX = 0f
    private var touchStartY = 0f

    val isShowing: Boolean get() = rootView != null

    fun show() {
        if (!Settings.canDrawOverlays(context)) return
        if (rootView != null) return

        val density = context.resources.displayMetrics.density
        fun dp(v: Int) = (v * density).toInt()

        val container = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(12), dp(8), dp(12), dp(8))
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#E6121212"))
                cornerRadius = dp(28).toFloat()
                setStroke(dp(1), Color.parseColor("#66FFFFFF"))
            }
            elevation = dp(8).toFloat()
        }

        val timer = TextView(context).apply {
            setTextColor(Color.parseColor("#30D158"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            typeface = Typeface.MONOSPACE
            text = "00:00"
            setPadding(dp(4), 0, dp(10), 0)
        }

        val pause = TextView(context).apply {
            setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
            text = "Pause"
            setPadding(dp(10), dp(6), dp(10), dp(6))
            background = pillBg(Color.parseColor("#33FFFFFF"), dp(16))
            setOnClickListener {
                ScreenRecorderService.sendAction(context, ScreenRecorderService.ACTION_PAUSE)
            }
        }

        val stop = TextView(context).apply {
            setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
            text = "Stop"
            setPadding(dp(10), dp(6), dp(10), dp(6))
            background = pillBg(Color.parseColor("#FF453A"), dp(16))
            setOnClickListener {
                ScreenRecorderService.sendAction(context, ScreenRecorderService.ACTION_STOP)
            }
        }

        val pauseLp = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT,
            LinearLayout.LayoutParams.WRAP_CONTENT,
        )
        val stopLp = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT,
            LinearLayout.LayoutParams.WRAP_CONTENT,
        ).apply { marginStart = dp(8) }
        container.addView(timer)
        container.addView(pause, pauseLp)
        container.addView(stop, stopLp)

        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            type,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = dp(16)
            y = dp(120)
        }

        enableDrag(container, params)

        try {
            windowManager.addView(container, params)
            rootView = container
            timerView = timer
            pauseView = pause
            layoutParams = params
        } catch (_: Exception) {
            rootView = null
        }
    }

    fun updateTimer(text: String) {
        timerView?.text = text
    }

    fun updatePaused(isPaused: Boolean) {
        pauseView?.apply {
            text = if (isPaused) "Resume" else "Pause"
            setOnClickListener {
                val action = if (isPaused) {
                    ScreenRecorderService.ACTION_RESUME
                } else {
                    ScreenRecorderService.ACTION_PAUSE
                }
                ScreenRecorderService.sendAction(context, action)
            }
        }
        timerView?.setTextColor(
            if (isPaused) Color.parseColor("#FF9F0A") else Color.parseColor("#30D158"),
        )
    }

    fun remove() {
        val view = rootView ?: return
        try {
            windowManager.removeView(view)
        } catch (_: Exception) {
        }
        rootView = null
        timerView = null
        pauseView = null
        layoutParams = null
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun enableDrag(view: View, params: WindowManager.LayoutParams) {
        view.setOnTouchListener { v, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    dragStartX = params.x
                    dragStartY = params.y
                    touchStartX = event.rawX
                    touchStartY = event.rawY
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    params.x = dragStartX + (event.rawX - touchStartX).toInt()
                    params.y = dragStartY + (event.rawY - touchStartY).toInt()
                    try {
                        windowManager.updateViewLayout(v, params)
                    } catch (_: Exception) {
                    }
                    true
                }
                else -> false
            }
        }
    }

    private fun pillBg(color: Int, radiusDp: Int): GradientDrawable {
        return GradientDrawable().apply {
            setColor(color)
            cornerRadius = radiusDp.toFloat()
        }
    }

    companion object {
        fun canDrawOverlays(context: Context): Boolean = Settings.canDrawOverlays(context)

        fun createOverlaySettingsIntent(context: Context): Intent {
            return Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                android.net.Uri.parse("package:${context.packageName}"),
            ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    }
}

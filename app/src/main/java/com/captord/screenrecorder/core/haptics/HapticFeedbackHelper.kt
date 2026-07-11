package com.captord.screenrecorder.core.haptics

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.withContext
import java.util.concurrent.Executors

object HapticFeedbackHelper {

    private val executor = Executors.newSingleThreadExecutor().asCoroutineDispatcher()

    enum class HapticIntensity(val value: Int) {
        TEXTURE_TICK(1),
        TICK(2),
        CLICK(3),
        DOUBLE_CLICK(4),
        HEAVY_CLICK(5)
    }

    suspend fun triggerVibration(context: Context, intensity: HapticIntensity) {
        withContext(executor) {
            try {
                val vibrator = getVibrator(context) ?: return@withContext
                if (!vibrator.hasVibrator()) return@withContext

                val effect = createVibrationEffect(intensity) ?: return@withContext
                vibrator.cancel()
                vibrator.vibrate(effect)
            } catch (_: Exception) {
            }
        }
    }

    private fun getVibrator(context: Context): Vibrator? {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
                vibratorManager?.defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
            }
        } catch (_: Exception) {
            null
        }
    }

    private val textureTickValue: Int by lazy {
        try {
            VibrationEffect::class.java.getField("EFFECT_TEXTURE_TICK").get(null) as Int
        } catch (_: Exception) {
            2 // Fallback to VibrationEffect.EFFECT_TICK
        }
    }

    private fun createVibrationEffect(intensity: HapticIntensity): VibrationEffect? {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                when (intensity) {
                    HapticIntensity.TEXTURE_TICK -> VibrationEffect.createPredefined(textureTickValue)
                    HapticIntensity.TICK -> VibrationEffect.createPredefined(VibrationEffect.EFFECT_TICK)
                    HapticIntensity.CLICK -> VibrationEffect.createPredefined(VibrationEffect.EFFECT_CLICK)
                    HapticIntensity.DOUBLE_CLICK -> VibrationEffect.createPredefined(VibrationEffect.EFFECT_DOUBLE_CLICK)
                    HapticIntensity.HEAVY_CLICK -> VibrationEffect.createPredefined(VibrationEffect.EFFECT_HEAVY_CLICK)
                }
            } else {
                val duration = when (intensity) {
                    HapticIntensity.TEXTURE_TICK -> 10L
                    HapticIntensity.TICK -> 20L
                    HapticIntensity.CLICK -> 40L
                    HapticIntensity.DOUBLE_CLICK -> 80L
                    HapticIntensity.HEAVY_CLICK -> 120L
                }
                val amplitude = when (intensity) {
                    HapticIntensity.TEXTURE_TICK -> 120
                    HapticIntensity.TICK -> 180
                    HapticIntensity.CLICK -> 220
                    HapticIntensity.DOUBLE_CLICK -> 240
                    HapticIntensity.HEAVY_CLICK -> 255
                }
                VibrationEffect.createOneShot(duration, amplitude)
            }
        } catch (_: Exception) {
            null
        }
    }
}

@Composable
fun rememberHapticFeedbackHelper(): HapticFeedbackHelperWrapper {
    val context = LocalContext.current
    return remember { HapticFeedbackHelperWrapper(context) }
}

class HapticFeedbackHelperWrapper(private val context: Context) {
    suspend fun performHaptic(intensity: HapticFeedbackHelper.HapticIntensity) {
        HapticFeedbackHelper.triggerVibration(context, intensity)
    }

    suspend fun click() = performHaptic(HapticFeedbackHelper.HapticIntensity.CLICK)
    suspend fun doubleClick() = performHaptic(HapticFeedbackHelper.HapticIntensity.DOUBLE_CLICK)
    suspend fun heavyClick() = performHaptic(HapticFeedbackHelper.HapticIntensity.HEAVY_CLICK)
    suspend fun tick() = performHaptic(HapticFeedbackHelper.HapticIntensity.TICK)
    suspend fun textureTick() = performHaptic(HapticFeedbackHelper.HapticIntensity.TEXTURE_TICK)
}

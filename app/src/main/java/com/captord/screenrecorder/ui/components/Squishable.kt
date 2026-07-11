package com.captord.screenrecorder.ui.components

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.indication
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput

@Composable
fun Modifier.squishable(
    enabled: Boolean = true,
    scaleDown: Float = 0.95f
): Modifier {
    var isPressed by remember { mutableStateOf(false) }

    val scale by animateFloatAsState(
        targetValue = if (isPressed && enabled) scaleDown else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "squish_scale"
    )

    return this
        .graphicsLayer {
            scaleX = scale
            scaleY = scale
        }
        .indication(
            interactionSource = remember { MutableInteractionSource() },
            indication = null
        )
        .pointerInput(enabled) {
            if (enabled) {
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent()
                        when (event.type) {
                            PointerEventType.Press -> {
                                isPressed = true
                            }
                            PointerEventType.Release, PointerEventType.Exit -> {
                                isPressed = false
                            }
                        }
                    }
                }
            }
        }
}

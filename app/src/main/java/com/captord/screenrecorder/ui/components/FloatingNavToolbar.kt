package com.captord.screenrecorder.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.captord.screenrecorder.core.haptics.HapticFeedbackHelper
import com.captord.screenrecorder.core.haptics.rememberHapticFeedbackHelper
import kotlinx.coroutines.launch

@Composable
fun FloatingNavToolbar(
    currentPage: Int,
    onPageSelected: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val haptic = rememberHapticFeedbackHelper()
    val scope = rememberCoroutineScope()

    val destinations = listOf(
        FloatingNavDestination(0, "Home", Icons.Filled.Home),
        FloatingNavDestination(1, "Gallery", Icons.Filled.VideoLibrary),
        FloatingNavDestination(2, "Settings", Icons.Filled.Settings)
    )

    Surface(
        modifier = modifier
            .shadow(
                elevation = 16.dp,
                shape = CircleShape,
                ambientColor = Color.Black.copy(alpha = 0.3f),
                spotColor = Color.Black.copy(alpha = 0.4f)
            ),
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.95f),
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)
        )
    ) {
        Row(
            modifier = Modifier
                .padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            destinations.forEach { dest ->
                val isSelected = currentPage == dest.index
                val contentColor = if (isSelected) {
                    MaterialTheme.colorScheme.onPrimaryContainer
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                }
                val containerColor = if (isSelected) {
                    MaterialTheme.colorScheme.primaryContainer
                } else {
                    Color.Transparent
                }

                Box(
                    modifier = Modifier
                        .height(48.dp)
                        .clip(CircleShape)
                        .background(containerColor)
                        .squishable(enabled = true, scaleDown = 0.92f)
                        .clickable {
                            scope.launch {
                                haptic.performHaptic(HapticFeedbackHelper.HapticIntensity.DOUBLE_CLICK)
                            }
                            onPageSelected(dest.index)
                        }
                        .padding(horizontal = 16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.animateContentSize(
                            animationSpec = spring(
                                dampingRatio = Spring.DampingRatioLowBouncy,
                                stiffness = Spring.StiffnessLow
                            )
                        )
                    ) {
                        Icon(
                            imageVector = dest.icon,
                            contentDescription = dest.label,
                            tint = contentColor,
                            modifier = Modifier.size(22.dp)
                        )

                        AnimatedVisibility(
                            visible = isSelected,
                            enter = expandHorizontally(expandFrom = Alignment.Start) + fadeIn(),
                            exit = shrinkHorizontally(shrinkTowards = Alignment.Start) + fadeOut()
                        ) {
                            Row {
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = dest.label,
                                    color = contentColor,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

private data class FloatingNavDestination(
    val index: Int,
    val label: String,
    val icon: ImageVector
)

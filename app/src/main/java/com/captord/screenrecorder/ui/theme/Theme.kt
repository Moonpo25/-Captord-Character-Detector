package com.captord.screenrecorder.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

private val DarkColorScheme = darkColorScheme(
    primary = BaselinePrimaryDark,
    onPrimary = BaselineOnPrimaryDark,
    primaryContainer = BaselinePrimaryContainerDark,
    onPrimaryContainer = BaselineOnPrimaryContainerDark,
    secondary = BaselineSecondaryDark,
    onSecondary = BaselineOnSecondaryDark,
    secondaryContainer = BaselineSecondaryContainerDark,
    onSecondaryContainer = BaselineOnSecondaryContainerDark,
    tertiary = BaselineTertiaryDark,
    onTertiary = BaselineOnTertiaryDark,
    tertiaryContainer = BaselineTertiaryContainerDark,
    onTertiaryContainer = BaselineOnTertiaryContainerDark,
    error = BaselineErrorDark,
    onError = BaselineOnErrorDark,
    errorContainer = BaselineErrorContainerDark,
    onErrorContainer = BaselineOnErrorContainerDark,
    background = BaselineBackgroundDark,
    onBackground = BaselineOnBackgroundDark,
    surface = BaselineSurfaceDark,
    onSurface = BaselineOnSurfaceDark,
    surfaceVariant = BaselineSurfaceVariantDark,
    onSurfaceVariant = BaselineOnSurfaceVariantDark,
    outline = BaselineOutlineDark,
    outlineVariant = BaselineOutlineVariantDark,
)

private val LightColorScheme = lightColorScheme(
    primary = BaselinePrimaryLight,
    onPrimary = BaselineOnPrimaryLight,
    primaryContainer = BaselinePrimaryContainerLight,
    onPrimaryContainer = BaselineOnPrimaryContainerLight,
    secondary = BaselineSecondaryLight,
    onSecondary = BaselineOnSecondaryLight,
    secondaryContainer = BaselineSecondaryContainerLight,
    onSecondaryContainer = BaselineOnSecondaryContainerLight,
    tertiary = BaselineTertiaryLight,
    onTertiary = BaselineOnTertiaryLight,
    tertiaryContainer = BaselineTertiaryContainerLight,
    onTertiaryContainer = BaselineOnTertiaryContainerLight,
    error = BaselineErrorLight,
    onError = BaselineOnErrorLight,
    errorContainer = BaselineErrorContainerLight,
    onErrorContainer = BaselineOnErrorContainerLight,
    background = BaselineBackgroundLight,
    onBackground = BaselineOnBackgroundLight,
    surface = BaselineSurfaceLight,
    onSurface = BaselineOnSurfaceLight,
    surfaceVariant = BaselineSurfaceVariantLight,
    onSurfaceVariant = BaselineOnSurfaceVariantLight,
    outline = BaselineOutlineLight,
    outlineVariant = BaselineOutlineVariantLight,
)

@Composable
fun CaptordTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit,
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }

        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content,
    )
}

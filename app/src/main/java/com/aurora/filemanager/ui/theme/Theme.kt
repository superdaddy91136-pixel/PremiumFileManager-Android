package com.aurora.filemanager.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

import androidx.compose.ui.graphics.Color

enum class ThemeMode {
    DARK,
    LIGHT,
    SYSTEM
}

val LocalAuroraColors = staticCompositionLocalOf { DarkAuroraGlassColors }

private val MaterialDarkScheme = darkColorScheme(
    primary = AuroraPalette.DeepIndigo,
    onPrimary = AuroraPalette.TextPrimaryDark,
    secondary = AuroraPalette.OceanBlue,
    onSecondary = AuroraPalette.TextPrimaryDark,
    tertiary = AuroraPalette.CyanHighlight,
    background = AuroraPalette.MatteObsidian,
    onBackground = AuroraPalette.TextPrimaryDark,
    surface = AuroraPalette.DeepCharcoal,
    onSurface = AuroraPalette.TextPrimaryDark,
    surfaceVariant = AuroraPalette.ElevatedCharcoal,
    onSurfaceVariant = AuroraPalette.TextSecondaryDark,
    error = AuroraPalette.DangerCrimson
)

private val MaterialAmoledScheme = darkColorScheme(
    primary = AuroraPalette.DeepIndigo,
    onPrimary = Color.White,
    secondary = AuroraPalette.OceanBlue,
    onSecondary = Color.White,
    tertiary = AuroraPalette.CyanHighlight,
    background = Color(0xFF000000),
    onBackground = Color.White,
    surface = Color(0xFF0C0C0C),
    onSurface = Color.White,
    surfaceVariant = Color(0xFF161616),
    onSurfaceVariant = AuroraPalette.TextSecondaryDark,
    error = AuroraPalette.DangerCrimson
)

private val MaterialLightScheme = lightColorScheme(
    primary = AuroraPalette.DeepIndigo,
    onPrimary = AuroraPalette.PearlSurface,
    secondary = AuroraPalette.OceanBlue,
    onSecondary = AuroraPalette.PearlSurface,
    tertiary = AuroraPalette.CyanHighlight,
    background = AuroraPalette.AlabasterBg,
    onBackground = AuroraPalette.TextPrimaryLight,
    surface = AuroraPalette.PearlSurface,
    onSurface = AuroraPalette.TextPrimaryLight,
    surfaceVariant = AuroraPalette.GlassInteractiveLight,
    onSurfaceVariant = AuroraPalette.TextSecondaryLight,
    error = AuroraPalette.DangerCrimson
)

@Composable
fun AuroraFileManagerTheme(
    themeMode: ThemeMode = ThemeMode.DARK,
    amoledMode: Boolean = false,
    content: @Composable () -> Unit
) {
    val useDarkTheme = when (themeMode) {
        ThemeMode.DARK -> true
        ThemeMode.LIGHT -> false
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
    }

    val auroraColors = when {
        useDarkTheme && amoledMode -> AmoledAuroraGlassColors
        useDarkTheme -> DarkAuroraGlassColors
        else -> LightAuroraGlassColors
    }
    val materialScheme = when {
        useDarkTheme && amoledMode -> MaterialAmoledScheme
        useDarkTheme -> MaterialDarkScheme
        else -> MaterialLightScheme
    }

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window
            if (window != null) {
                window.statusBarColor = android.graphics.Color.TRANSPARENT
                window.navigationBarColor = android.graphics.Color.TRANSPARENT
                val controller = WindowCompat.getInsetsController(window, view)
                controller.isAppearanceLightStatusBars = !useDarkTheme
                controller.isAppearanceLightNavigationBars = !useDarkTheme
            }
        }
    }

    CompositionLocalProvider(LocalAuroraColors provides auroraColors) {
        MaterialTheme(
            colorScheme = materialScheme,
            typography = AuroraTypography,
            shapes = AuroraShapes,
            content = content
        )
    }
}

object AuroraTheme {
    val colors: AuroraGlassColors
        @Composable
        get() = LocalAuroraColors.current
}

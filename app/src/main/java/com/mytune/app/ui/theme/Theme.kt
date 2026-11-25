package com.mytune.app.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val DarkColors = darkColorScheme(
    primary = Color(0xFF4ADE80),
    onPrimary = Color(0xFF00391A),
    primaryContainer = Color(0xFF14532D),
    secondary = Color(0xFF86EFAC),
    background = Color(0xFF111114),
    surface = Color(0xFF111114),
    surfaceVariant = Color(0xFF25302A),
)

private val LightColors = lightColorScheme(
    primary = Color(0xFF16A34A),
    secondary = Color(0xFF15803D),
    background = Color(0xFFF7FCF8),
    surface = Color(0xFFF7FCF8),
    surfaceVariant = Color(0xFFDDE8DF),
)

/** [mode] is "dark" (default), "light" or "system". */
@Composable
fun MyTuneTheme(mode: String, content: @Composable () -> Unit) {
    val dark = when (mode) {
        "light" -> false
        "system" -> isSystemInDarkTheme()
        else -> true
    }
    val view = LocalView.current
    SideEffect {
        val window = (view.context as? Activity)?.window ?: return@SideEffect
        val controller = WindowCompat.getInsetsController(window, view)
        controller.isAppearanceLightStatusBars = !dark
        controller.isAppearanceLightNavigationBars = !dark
    }
    MaterialTheme(colorScheme = if (dark) DarkColors else LightColors, content = content)
}

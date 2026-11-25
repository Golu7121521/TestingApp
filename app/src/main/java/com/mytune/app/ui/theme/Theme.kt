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
    primary = Color(0xFFB69DF8),
    onPrimary = Color(0xFF2A0F5E),
    primaryContainer = Color(0xFF41307A),
    secondary = Color(0xFF8AD6C8),
    background = Color(0xFF111114),
    surface = Color(0xFF111114),
    surfaceVariant = Color(0xFF2B2A33),
)

private val LightColors = lightColorScheme(
    primary = Color(0xFF5B3FC0),
    secondary = Color(0xFF006B5E),
    background = Color(0xFFFCF8FF),
    surface = Color(0xFFFCF8FF),
    surfaceVariant = Color(0xFFE6E0EC),
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

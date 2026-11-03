package com.linkplayer.app

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val Accent = Color(0xFF2979FF)

private val LightScheme = lightColorScheme(
    primary = Accent,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD9E5FF),
    onPrimaryContainer = Color(0xFF001A44),
    secondary = Accent,
    tertiary = Accent,
    surfaceTint = Accent,
    background = Color(0xFFF5F8FF),
    surface = Color.White,
    surfaceVariant = Color(0xFFE6ECF7),
)

val PlayerDarkScheme = darkColorScheme(
    primary = Accent,
    onPrimary = Color.White,
    primaryContainer = Color(0xFF16336B),
    onPrimaryContainer = Color(0xFFD6E3FF),
    secondary = Accent,
    tertiary = Accent,
    surfaceTint = Accent,
    background = Color(0xFF0E1013),
    surface = Color(0xFF16191E),
    surfaceVariant = Color(0xFF22262D),
)

@Composable
fun AppTheme(content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    MaterialTheme(colorScheme = if (dark) PlayerDarkScheme else LightScheme, content = content)
}

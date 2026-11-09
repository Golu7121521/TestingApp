package com.example.filerecovery.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkColors = darkColorScheme(
    primary = Color(0xFFB39DFF),
    onPrimary = Color(0xFF1E0F4F),
    secondary = Color(0xFF5CE1E6),
    tertiary = Color(0xFFFF7EB6),
    background = Color(0xFF0E0E1A),
    surface = Color(0xFF181828),
    surfaceVariant = Color(0xFF232340),
    onBackground = Color(0xFFEDEDFF),
    onSurface = Color(0xFFEDEDFF),
    onSurfaceVariant = Color(0xFFB8B8D8)
)

private val LightColors = lightColorScheme(
    primary = Color(0xFF6A4CF5),
    onPrimary = Color.White,
    secondary = Color(0xFF00A8B5),
    tertiary = Color(0xFFE040FB),
    background = Color(0xFFF7F5FF),
    surface = Color.White,
    surfaceVariant = Color(0xFFEDE8FF)
)

@Composable
fun FileRecoveryTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (isSystemInDarkTheme()) DarkColors else LightColors,
        content = content
    )
}

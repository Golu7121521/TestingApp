package com.example.youtubeclone.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val YouTubeRed = Color(0xFFFF0000)
val DarkBackground = Color(0xFF0F0F0F)
val DarkSurface = Color(0xFF212121)
val LightBackground = Color(0xFFFFFFFF)
val LightSurface = Color(0xFFF2F2F2)

private val DarkColorPalette = darkColorScheme(
    primary = YouTubeRed,
    background = DarkBackground,
    surface = DarkSurface,
    onPrimary = Color.White,
    onBackground = Color.White,
    onSurface = Color.White,
    surfaceVariant = Color(0xFF272727),
    onSurfaceVariant = Color(0xFFAAAAAA)
)

private val LightColorPalette = lightColorScheme(
    primary = YouTubeRed,
    background = LightBackground,
    surface = LightSurface,
    onPrimary = Color.White,
    onBackground = Color(0xFF0F0F0F),
    onSurface = Color(0xFF0F0F0F),
    surfaceVariant = Color(0xFFE5E5E5),
    onSurfaceVariant = Color(0xFF606060)
)

@Composable
fun YouTubeCloneTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colors = if (darkTheme) DarkColorPalette else LightColorPalette
    MaterialTheme(
        colorScheme = colors,
        content = content
    )
}

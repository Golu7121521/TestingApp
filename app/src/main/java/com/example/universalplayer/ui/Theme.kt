package com.example.universalplayer.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightScheme = lightColorScheme(
    primary = Color(0xFF0288D1), onPrimary = Color.White,
    primaryContainer = Color(0xFFCDEBFF), onPrimaryContainer = Color(0xFF001E30),
    secondary = Color(0xFF4F616E), onSecondary = Color.White,
    secondaryContainer = Color(0xFFD2E5F5), onSecondaryContainer = Color(0xFF0B1D29),
    tertiary = Color(0xFF00ACC1), onTertiary = Color.White,
    background = Color(0xFFF6FAFE), onBackground = Color(0xFF171C20),
    surface = Color(0xFFF6FAFE), onSurface = Color(0xFF171C20),
    surfaceVariant = Color(0xFFDCE3E9), onSurfaceVariant = Color(0xFF40484D),
    surfaceContainer = Color(0xFFEAEEF2), surfaceContainerHigh = Color(0xFFE4E9ED),
    outline = Color(0xFF70787D), outlineVariant = Color(0xFFC0C8CD)
)

private val DarkScheme = darkColorScheme(
    primary = Color(0xFF4FC3F7), onPrimary = Color(0xFF003549),
    primaryContainer = Color(0xFF0B4A66), onPrimaryContainer = Color(0xFFC3E8FF),
    secondary = Color(0xFFB3C9D6), onSecondary = Color(0xFF1C333F),
    secondaryContainer = Color(0xFF1D3E50), onSecondaryContainer = Color(0xFFD0E5F2),
    tertiary = Color(0xFF26C6DA), onTertiary = Color(0xFF00363D),
    background = Color(0xFF0E1317), onBackground = Color(0xFFE0E3E7),
    surface = Color(0xFF0E1317), onSurface = Color(0xFFE0E3E7),
    surfaceVariant = Color(0xFF3E484F), onSurfaceVariant = Color(0xFFBEC8D0),
    surfaceContainer = Color(0xFF1A2025), surfaceContainerHigh = Color(0xFF242A2F),
    outline = Color(0xFF88929A), outlineVariant = Color(0xFF3E484F)
)

@Composable
fun UniversalPlayerTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (isSystemInDarkTheme()) DarkScheme else LightScheme,
        content = content
    )
}

package com.streamplay.player.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val BlueAccent = Color(0xFF0066FF)
val BlueSecondary = Color(0xFF3385FF)
val BlueContainerDark = Color(0xFF002B73)
val BlueContainerLight = Color(0xFFD6E4FF)

private val DarkColorScheme = darkColorScheme(
    primary = BlueAccent,
    onPrimary = Color.White,
    primaryContainer = BlueContainerDark,
    onPrimaryContainer = Color.White,
    secondary = BlueSecondary,
    background = Color(0xFF0E1117),
    surface = Color(0xFF161B22),
    surfaceVariant = Color(0xFF21262D)
)

private val LightColorScheme = lightColorScheme(
    primary = BlueAccent,
    onPrimary = Color.White,
    primaryContainer = BlueContainerLight,
    onPrimaryContainer = Color(0xFF001B4D),
    secondary = BlueSecondary,
    background = Color(0xFFF7F9FC),
    surface = Color(0xFFFFFFFF),
    surfaceVariant = Color(0xFFEEF2F8)
)

@Composable
fun StreamPlayTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme
    MaterialTheme(
        colorScheme = colorScheme,
        content = content
    )
}

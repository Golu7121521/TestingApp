package com.mytube.app.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val YtRed = Color(0xFFFF0000)

private val Dark = darkColorScheme(
    primary = YtRed,
    background = Color(0xFF0F0F0F),
    surface = Color(0xFF0F0F0F),
    onBackground = Color.White,
    onSurface = Color.White,
    surfaceVariant = Color(0xFF272727),
    onSurfaceVariant = Color(0xFFAAAAAA)
)

private val Light = lightColorScheme(
    primary = YtRed,
    background = Color.White,
    surface = Color.White,
    onBackground = Color(0xFF0F0F0F),
    onSurface = Color(0xFF0F0F0F),
    surfaceVariant = Color(0xFFF2F2F2),
    onSurfaceVariant = Color(0xFF606060)
)

@Composable
fun MyTubeTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (isSystemInDarkTheme()) Dark else Light,
        content = content
    )
}

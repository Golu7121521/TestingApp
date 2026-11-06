package com.example.devicehealth.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

val AccentChoices = listOf(
    Color(0xFF7C5CFF),
    Color(0xFF0EA5E9),
    Color(0xFF10B981),
    Color(0xFFF97316),
)

val LocalDark = compositionLocalOf { false }

fun appColorScheme(dark: Boolean, accent: Color): ColorScheme = if (dark) {
    darkColorScheme(
        primary = accent,
        onPrimary = Color.White,
        secondary = Color(0xFF22D3EE),
        background = Color(0xFF0B0F1A),
        onBackground = Color(0xFFF1F5F9),
        surface = Color(0xFF141A2E),
        onSurface = Color(0xFFF1F5F9),
        surfaceVariant = Color(0xFF1B2340),
        onSurfaceVariant = Color(0xFF94A3B8),
    )
} else {
    lightColorScheme(
        primary = accent,
        onPrimary = Color.White,
        secondary = Color(0xFF0891B2),
        background = Color(0xFFF3F5FB),
        onBackground = Color(0xFF0F172A),
        surface = Color.White,
        onSurface = Color(0xFF0F172A),
        surfaceVariant = Color(0xFFE8EDF8),
        onSurfaceVariant = Color(0xFF64748B),
    )
}

fun backgroundBrush(dark: Boolean): Brush = Brush.verticalGradient(
    if (dark) {
        listOf(Color(0xFF0B0F1A), Color(0xFF151C3A), Color(0xFF0B0F1A))
    } else {
        listOf(Color(0xFFF3F5FB), Color(0xFFE6EBF8), Color(0xFFF3F5FB))
    }
)

@Composable
fun DeviceHealthTheme(themeMode: Int, accentIndex: Int, content: @Composable () -> Unit) {
    val dark = when (themeMode) {
        1 -> false
        2 -> true
        else -> isSystemInDarkTheme()
    }
    val accent = AccentChoices[accentIndex.coerceIn(0, AccentChoices.lastIndex)]
    MaterialTheme(colorScheme = appColorScheme(dark, accent)) {
        CompositionLocalProvider(LocalDark provides dark) {
            Surface(color = Color.Transparent, contentColor = MaterialTheme.colorScheme.onBackground) {
                content()
            }
        }
    }
}

package com.example.devicehealth.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val PremiumDark = darkColorScheme(
    primary = Color(0xFF7C5CFF),
    secondary = Color(0xFF22D3EE),
    background = Color(0xFF0B0F1A),
    surface = Color(0xFF141A2E),
    onBackground = Color.White,
    onSurface = Color.White,
)

@Composable
fun DeviceHealthTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = PremiumDark, content = content)
}

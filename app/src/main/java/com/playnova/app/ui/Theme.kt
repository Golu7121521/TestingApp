package com.playnova.app.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.playnova.app.data.Prefs

val Accents = listOf(
    "Purple" to Color(0xFF7C4DFF), "Blue" to Color(0xFF2979FF), "Teal" to Color(0xFF00BFA5),
    "Pink" to Color(0xFFFF4081), "Orange" to Color(0xFFFF9100)
)

val Muted = Color(0xFF9AA0B4)

@Composable
fun PlayNovaTheme(p: Prefs, content: @Composable () -> Unit) {
    val dark = when (p.theme) { 0 -> true; 1 -> false; else -> isSystemInDarkTheme() }
    val accent = Accents[p.accent.coerceIn(Accents.indices)].second
    val scheme = if (dark) darkColorScheme(
        primary = accent, onPrimary = Color.White,
        primaryContainer = accent.copy(alpha = 0.25f), onPrimaryContainer = Color.White,
        secondary = accent, background = Color(0xFF0E0F13), onBackground = Color(0xFFF2F3F7),
        surface = Color(0xFF171922), onSurface = Color(0xFFF2F3F7),
        surfaceVariant = Color(0xFF1F2230), onSurfaceVariant = Color(0xFFB4B9CC),
        outline = Color(0xFF3A3E52), error = Color(0xFFFF5C6C)
    ) else lightColorScheme(
        primary = accent, onPrimary = Color.White,
        primaryContainer = accent.copy(alpha = 0.15f), onPrimaryContainer = Color(0xFF14151B),
        secondary = accent, background = Color(0xFFF6F7FB), onBackground = Color(0xFF14151B),
        surface = Color(0xFFFFFFFF), onSurface = Color(0xFF14151B),
        surfaceVariant = Color(0xFFECEEF5), onSurfaceVariant = Color(0xFF50556B),
        outline = Color(0xFFC5C9D8), error = Color(0xFFD32F2F)
    )
    val base = Typography()
    val typo = base.copy(
        titleLarge = base.titleLarge.copy(fontWeight = FontWeight.Bold, fontSize = 22.sp),
        titleMedium = base.titleMedium.copy(fontWeight = FontWeight.SemiBold),
        labelSmall = TextStyle(fontSize = 11.sp, fontWeight = FontWeight.Medium)
    )
    MaterialTheme(colorScheme = scheme, typography = typo, content = content)
}

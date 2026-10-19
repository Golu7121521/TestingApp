package com.playnova.app.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
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
    // Neutral surfaces. Accent is only used for primary actions and small highlights,
    // selected states use a faint accent wash on top of the surface (no pastel purple blobs).
    val scheme = if (dark) {
        val surface = Color(0xFF171922)
        val wash = accent.copy(alpha = 0.22f).compositeOver(surface)
        darkColorScheme(
            primary = accent, onPrimary = Color.White,
            primaryContainer = wash, onPrimaryContainer = Color(0xFFF2F3F7),
            secondary = accent, onSecondary = Color.White,
            secondaryContainer = wash, onSecondaryContainer = Color(0xFFF2F3F7),
            tertiary = accent, tertiaryContainer = wash, onTertiaryContainer = Color(0xFFF2F3F7),
            background = Color(0xFF0E0F13), onBackground = Color(0xFFF2F3F7),
            surface = surface, onSurface = Color(0xFFF2F3F7),
            surfaceVariant = Color(0xFF1F2230), onSurfaceVariant = Color(0xFFB4B9CC),
            surfaceTint = Color.Transparent,
            surfaceContainerLowest = Color(0xFF0E0F13), surfaceContainerLow = Color(0xFF14161D),
            surfaceContainer = Color(0xFF1B1D27), surfaceContainerHigh = Color(0xFF22242F),
            surfaceContainerHighest = Color(0xFF2A2D3A),
            outline = Color(0xFF3A3E52), outlineVariant = Color(0xFF2A2D3A), error = Color(0xFFFF5C6C)
        )
    } else {
        val surface = Color(0xFFFFFFFF)
        val wash = accent.copy(alpha = 0.14f).compositeOver(surface)
        lightColorScheme(
            primary = accent, onPrimary = Color.White,
            primaryContainer = wash, onPrimaryContainer = Color(0xFF14151B),
            secondary = accent, onSecondary = Color.White,
            secondaryContainer = wash, onSecondaryContainer = Color(0xFF14151B),
            tertiary = accent, tertiaryContainer = wash, onTertiaryContainer = Color(0xFF14151B),
            background = Color(0xFFF6F7FB), onBackground = Color(0xFF14151B),
            surface = surface, onSurface = Color(0xFF14151B),
            surfaceVariant = Color(0xFFECEEF5), onSurfaceVariant = Color(0xFF50556B),
            surfaceTint = Color.Transparent,
            surfaceContainerLowest = Color(0xFFFFFFFF), surfaceContainerLow = Color(0xFFF6F7FB),
            surfaceContainer = Color(0xFFF0F1F7), surfaceContainerHigh = Color(0xFFECEEF5),
            surfaceContainerHighest = Color(0xFFE6E8F0),
            outline = Color(0xFFC5C9D8), outlineVariant = Color(0xFFE0E3EC), error = Color(0xFFD32F2F)
        )
    }
    val base = Typography()
    val typo = base.copy(
        titleLarge = base.titleLarge.copy(fontWeight = FontWeight.Bold, fontSize = 22.sp),
        titleMedium = base.titleMedium.copy(fontWeight = FontWeight.SemiBold),
        labelSmall = TextStyle(fontSize = 11.sp, fontWeight = FontWeight.Medium)
    )
    MaterialTheme(colorScheme = scheme, typography = typo, content = content)
}

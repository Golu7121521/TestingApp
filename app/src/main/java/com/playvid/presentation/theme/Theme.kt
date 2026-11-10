package com.playvid.presentation.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// Every colour role is set explicitly (blue-tinted neutrals + one blue accent),
// so no default Material purple/pink can leak into any component.
private val DarkColors = darkColorScheme(
    primary = Color(0xFF7F9CF5),
    onPrimary = Color(0xFF0A0B10),
    primaryContainer = Color(0xFF25315A),
    onPrimaryContainer = Color(0xFFDCE4FF),
    secondary = Color(0xFF9FB1E8),
    onSecondary = Color(0xFF0A0B10),
    secondaryContainer = Color(0xFF1B2440),
    onSecondaryContainer = Color(0xFFDCE4FF),
    tertiary = Color(0xFF8FA8E6),
    onTertiary = Color(0xFF0A0B10),
    tertiaryContainer = Color(0xFF222C4D),
    onTertiaryContainer = Color(0xFFDCE4FF),
    background = Color(0xFF0A0B10),
    onBackground = Color(0xFFEDEFF6),
    surface = Color(0xFF0A0B10),
    onSurface = Color(0xFFEDEFF6),
    surfaceVariant = Color(0xFF161A26),
    onSurfaceVariant = Color(0xFF9AA1B8),
    surfaceTint = Color(0xFF7F9CF5),
    inverseSurface = Color(0xFFEDEFF6),
    inverseOnSurface = Color(0xFF0A0B10),
    inversePrimary = Color(0xFF3F5FD0),
    surfaceDim = Color(0xFF0A0B10),
    surfaceBright = Color(0xFF232A3D),
    surfaceContainerLowest = Color(0xFF080910),
    surfaceContainerLow = Color(0xFF0E111A),
    surfaceContainer = Color(0xFF11151F),
    surfaceContainerHigh = Color(0xFF171C29),
    surfaceContainerHighest = Color(0xFF1E2434),
    outline = Color(0xFF3A4260),
    outlineVariant = Color(0xFF232A3C),
    error = Color(0xFFE5736E)
)

private val LightColors = lightColorScheme(
    primary = Color(0xFF3F5FD0),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFDCE4FF),
    onPrimaryContainer = Color(0xFF0B1A4D),
    secondary = Color(0xFF5A6A9A),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFE0E6F8),
    onSecondaryContainer = Color(0xFF141C3A),
    tertiary = Color(0xFF4A62B0),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFDDE4F8),
    onTertiaryContainer = Color(0xFF0F1A3F),
    background = Color(0xFFF6F7FB),
    onBackground = Color(0xFF14161C),
    surface = Color(0xFFF6F7FB),
    onSurface = Color(0xFF14161C),
    surfaceVariant = Color(0xFFE6E9F3),
    onSurfaceVariant = Color(0xFF5F6578),
    surfaceTint = Color(0xFF3F5FD0),
    surfaceDim = Color(0xFFD9DCE8),
    surfaceBright = Color(0xFFF6F7FB),
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color(0xFFF0F2F9),
    surfaceContainer = Color(0xFFE9EDF8),
    surfaceContainerHigh = Color(0xFFE2E7F5),
    surfaceContainerHighest = Color(0xFFDCE2F2),
    outline = Color(0xFFB5BBD0),
    outlineVariant = Color(0xFFD5D9E6)
)

private val AppShapes = Shapes(
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(12.dp),
    large = RoundedCornerShape(16.dp)
)

private val base = Typography()
private val AppTypography = base.copy(
    headlineMedium = base.headlineMedium.copy(fontSize = 30.sp, lineHeight = 36.sp, fontWeight = FontWeight.SemiBold),
    titleMedium = base.titleMedium.copy(fontSize = 16.sp, fontWeight = FontWeight.Medium),
    bodyLarge = base.bodyLarge.copy(fontSize = 16.sp),
    bodyMedium = base.bodyMedium.copy(fontSize = 14.sp),
    bodySmall = base.bodySmall.copy(fontSize = 12.sp)
)

/** Dark is the default; Light and System are selectable in Settings. */
@Composable
fun PlayVidTheme(darkTheme: Boolean, content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        typography = AppTypography,
        shapes = AppShapes,
        content = content
    )
}

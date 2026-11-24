package com.vibemusic.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.vibemusic.app.domain.model.ThemeModeOption

val AmoledBackground = Color(0xFF09090D)
val DarkCharcoal = Color(0xFF15151D)
val DarkCardElevated = Color(0xFF1E1E29)
val ElectricPurple = Color(0xFFA855F7)
val NeonBlue = Color(0xFF38BDF8)

val AccentColorOptions = listOf(
    "Electric Purple" to 0xFFA855F7,
    "Neon Blue" to 0xFF38BDF8,
    "Cyber Pink" to 0xFFEC4899,
    "Emerald Pulse" to 0xFF10B981,
    "Solar Amber" to 0xFFF59E0B,
    "Crimson Wave" to 0xFFEF4444
)

val VibeTypography = Typography(
    headlineLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Bold,
        fontSize = 26.sp,
        lineHeight = 32.sp,
        letterSpacing = (-0.5).sp
    ),
    headlineMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Bold,
        fontSize = 22.sp,
        lineHeight = 28.sp
    ),
    titleLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 18.sp,
        lineHeight = 24.sp
    ),
    titleMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 15.sp,
        lineHeight = 20.sp
    ),
    bodyLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 15.sp,
        lineHeight = 22.sp
    ),
    bodyMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 13.sp,
        lineHeight = 18.sp
    ),
    labelMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Medium,
        fontSize = 12.sp,
        lineHeight = 16.sp
    ),
    labelSmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Medium,
        fontSize = 11.sp,
        lineHeight = 14.sp
    )
)

@Composable
fun VibeMusicTheme(
    themeMode: ThemeModeOption = ThemeModeOption.AMOLED_DARK,
    accentColorHex: Long = 0xFFA855F7,
    content: @Composable () -> Unit
) {
    val accent = Color(accentColorHex)
    val useDark = when (themeMode) {
        ThemeModeOption.AMOLED_DARK -> true
        ThemeModeOption.LIGHT -> false
        ThemeModeOption.SYSTEM -> isSystemInDarkTheme()
    }

    val colorScheme = if (useDark) {
        darkColorScheme(
            primary = accent,
            onPrimary = Color.White,
            secondary = NeonBlue,
            onSecondary = Color(0xFF061520),
            tertiary = Color(0xFFC084FC),
            background = AmoledBackground,
            onBackground = Color(0xFFF4F4F8),
            surface = DarkCharcoal,
            onSurface = Color(0xFFF4F4F8),
            surfaceVariant = DarkCardElevated,
            onSurfaceVariant = Color(0xFFA1A1B5),
            outline = Color(0xFF2D2D3D)
        )
    } else {
        lightColorScheme(
            primary = accent,
            onPrimary = Color.White,
            secondary = Color(0xFF0284C7),
            onSecondary = Color.White,
            background = Color(0xFFF8F9FC),
            onBackground = Color(0xFF111118),
            surface = Color.White,
            onSurface = Color(0xFF111118),
            surfaceVariant = Color(0xFFEDEEF5),
            onSurfaceVariant = Color(0xFF525266),
            outline = Color(0xFFD5D7E2)
        )
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = VibeTypography,
        content = content
    )
}

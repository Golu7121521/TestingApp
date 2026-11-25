package com.mytune.app.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

// Every role is set explicitly (green/neutral) so no Material default purple leaks through.
private val DarkColors = darkColorScheme(
    primary = Color(0xFF4ADE80),
    onPrimary = Color(0xFF00391A),
    primaryContainer = Color(0xFF14532D),
    onPrimaryContainer = Color(0xFFBBF7D0),
    inversePrimary = Color(0xFF16A34A),
    secondary = Color(0xFF86EFAC),
    onSecondary = Color(0xFF00391A),
    secondaryContainer = Color(0xFF1F3D2B),
    onSecondaryContainer = Color(0xFFD1FAE5),
    tertiary = Color(0xFF5EEAD4),
    onTertiary = Color(0xFF003731),
    tertiaryContainer = Color(0xFF134E4A),
    onTertiaryContainer = Color(0xFFCCFBF1),
    background = Color(0xFF0E1210),
    onBackground = Color(0xFFE1E8E3),
    surface = Color(0xFF0E1210),
    onSurface = Color(0xFFE1E8E3),
    surfaceVariant = Color(0xFF25302A),
    onSurfaceVariant = Color(0xFFB7C4BB),
    surfaceTint = Color(0xFF4ADE80),
    inverseSurface = Color(0xFFE1E8E3),
    inverseOnSurface = Color(0xFF1B211E),
    outline = Color(0xFF7F8C84),
    outlineVariant = Color(0xFF3A4540),
    scrim = Color(0xFF000000),
    surfaceBright = Color(0xFF2A322D),
    surfaceDim = Color(0xFF0E1210),
    surfaceContainerLowest = Color(0xFF090D0B),
    surfaceContainerLow = Color(0xFF151A17),
    surfaceContainer = Color(0xFF1A201D),
    surfaceContainerHigh = Color(0xFF242B27),
    surfaceContainerHighest = Color(0xFF2F3732),
)

private val LightColors = lightColorScheme(
    primary = Color(0xFF16A34A),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFBBF7D0),
    onPrimaryContainer = Color(0xFF052E16),
    inversePrimary = Color(0xFF4ADE80),
    secondary = Color(0xFF15803D),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFD1FAE5),
    onSecondaryContainer = Color(0xFF052E16),
    tertiary = Color(0xFF0F766E),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFCCFBF1),
    onTertiaryContainer = Color(0xFF042F2E),
    background = Color(0xFFF7FCF8),
    onBackground = Color(0xFF171D19),
    surface = Color(0xFFF7FCF8),
    onSurface = Color(0xFF171D19),
    surfaceVariant = Color(0xFFDDE8DF),
    onSurfaceVariant = Color(0xFF414B44),
    surfaceTint = Color(0xFF16A34A),
    inverseSurface = Color(0xFF2C322E),
    inverseOnSurface = Color(0xFFEDF3EE),
    outline = Color(0xFF707A73),
    outlineVariant = Color(0xFFC0CBC3),
    scrim = Color(0xFF000000),
    surfaceBright = Color(0xFFF7FCF8),
    surfaceDim = Color(0xFFD7DDD8),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFF1F6F2),
    surfaceContainer = Color(0xFFEBF0EC),
    surfaceContainerHigh = Color(0xFFE5EBE6),
    surfaceContainerHighest = Color(0xFFDFE5E0),
)

/** [mode] is "system" (default), "dark" or "light". */
@Composable
fun MyTuneTheme(mode: String, content: @Composable () -> Unit) {
    val dark = when (mode) {
        "light" -> false
        "dark" -> true
        else -> isSystemInDarkTheme()
    }
    val view = LocalView.current
    SideEffect {
        val window = (view.context as? Activity)?.window ?: return@SideEffect
        val controller = WindowCompat.getInsetsController(window, view)
        controller.isAppearanceLightStatusBars = !dark
        controller.isAppearanceLightNavigationBars = !dark
    }
    MaterialTheme(colorScheme = if (dark) DarkColors else LightColors, content = content)
}

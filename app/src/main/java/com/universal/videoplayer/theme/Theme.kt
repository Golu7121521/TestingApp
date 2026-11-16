package com.universal.videoplayer.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import com.universal.videoplayer.utils.findActivity

private val Dark = darkColorScheme(
    primary = Color(0xFFA99BFF), onPrimary = Color(0xFF1B0A5C),
    primaryContainer = Color(0xFF3B2F8F), onPrimaryContainer = Color(0xFFE6DEFF),
    secondaryContainer = Color(0xFF33305A), onSecondaryContainer = Color(0xFFE3DFFF),
    background = Color(0xFF121214), onBackground = Color(0xFFF2F2F5),
    surface = Color(0xFF1C1C20), onSurface = Color(0xFFF2F2F5),
    surfaceVariant = Color(0xFF2A2A30), onSurfaceVariant = Color(0xFFB8B8C2),
    error = Color(0xFFFF8A80),
)

private val Light = lightColorScheme(
    primary = Color(0xFF5B4BDB), onPrimary = Color.White,
    primaryContainer = Color(0xFFE4DFFF), onPrimaryContainer = Color(0xFF1B0A5C),
    secondaryContainer = Color(0xFFE6E3FA), onSecondaryContainer = Color(0xFF1D1A3D),
    background = Color(0xFFFAF9FC), onBackground = Color(0xFF1B1B20),
    surface = Color.White, onSurface = Color(0xFF1B1B20),
    surfaceVariant = Color(0xFFEEEDF4), onSurfaceVariant = Color(0xFF5E5E68),
)

@Composable
fun VideoPlayerTheme(dark: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    val ctx = LocalContext.current
    val scheme = when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
            if (dark) dynamicDarkColorScheme(ctx) else dynamicLightColorScheme(ctx)
        dark -> Dark
        else -> Light
    }
    val view = LocalView.current
    SideEffect {
        ctx.findActivity()?.window?.let {
            WindowCompat.getInsetsController(it, view).apply {
                isAppearanceLightStatusBars = !dark
                isAppearanceLightNavigationBars = !dark
            }
        }
    }
    MaterialTheme(colorScheme = scheme, content = content)
}

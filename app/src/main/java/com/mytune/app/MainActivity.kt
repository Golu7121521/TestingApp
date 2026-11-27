package com.mytune.app

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import com.mytune.app.ui.AppRoot
import com.mytune.app.ui.screens.OnboardingScreen
import com.mytune.app.ui.screens.SplashScreen
import com.mytune.app.ui.theme.MyTuneTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = androidx.activity.SystemBarStyle.auto(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT),
            navigationBarStyle = androidx.activity.SystemBarStyle.auto(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT),
        )
        val container = (application as MyTuneApp).container
        container.player.connect()
        setContent {
            val theme by container.settings.theme.collectAsState(initial = "system")
            val accent by container.settings.accent.collectAsState(initial = "green")
            // null until the first DataStore read finishes (covered by the splash screen)
            val onboarded by container.settings.onboarded.collectAsState(initial = null)
            val ctx = LocalContext.current
            // keep the launcher icon colour in sync with the accent
            LaunchedEffect(accent) { com.mytune.app.ui.theme.IconManager.apply(ctx, accent) }
            var splashDone by rememberSaveable { mutableStateOf(false) }
            MyTuneTheme(theme, accent) {
                Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    Box(Modifier.fillMaxSize()) {
                        NotificationPermission()
                        when (onboarded) {
                            true -> AppRoot(container)
                            false -> OnboardingScreen(container)
                            null -> {}
                        }
                        if (!splashDone) SplashScreen { splashDone = true }
                    }
                }
            }
        }
    }
}

/** Asks for POST_NOTIFICATIONS once on Android 13+. */
@Composable
private fun NotificationPermission() {
    if (Build.VERSION.SDK_INT < 33) return
    val ctx = LocalContext.current
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {}
    LaunchedEffect(Unit) {
        val granted = ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
        if (!granted) launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
    }
}

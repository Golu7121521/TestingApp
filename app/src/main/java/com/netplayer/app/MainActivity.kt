package com.netplayer.app

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT)
        )
        super.onCreate(savedInstanceState)
        if (savedInstanceState == null && intent?.action == Intent.ACTION_VIEW) {
            intent.data?.let { startActivity(StreamRequest(it.toString()).toIntent(this)) }
        }
        setContent { AppTheme { AppRoot() } }
    }
}

@Composable
fun AppRoot() {
    val ctx = LocalContext.current
    val prefs = remember { GesturePrefs(ctx) }
    val store = remember { HistoryStore(ctx) }
    var screen by remember { mutableStateOf("home") }
    var req by remember { mutableStateOf(StreamRequest("")) }
    var showSettings by remember { mutableStateOf(false) }

    BackHandler(screen == "history") { screen = "home" }

    Surface(color = MaterialTheme.colorScheme.background, modifier = Modifier.fillMaxSize()) {
        if (screen == "home") {
            HomeScreen(
                req = req, onReq = { req = it },
                onHistory = { screen = "history" },
                onSettings = { showSettings = true },
                onPlay = { ctx.startActivity(req.toIntent(ctx)) }
            )
        } else {
            HistoryScreen(store, onBack = { screen = "home" }, onPick = { req = it; screen = "home" })
        }
    }
    if (showSettings) SettingsDialog(prefs) { showSettings = false }
}

package com.streamx.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import com.streamx.app.core.designsystem.StreamXTheme
import com.streamx.app.core.designsystem.ThemeMode
import com.streamx.app.navigation.StreamXApp
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            // Phase 6 will read this from DataStore; default follows the system.
            var themeMode by rememberSaveable { mutableStateOf(ThemeMode.SYSTEM) }

            StreamXTheme(themeMode = themeMode) {
                StreamXApp()
            }
        }
    }
}

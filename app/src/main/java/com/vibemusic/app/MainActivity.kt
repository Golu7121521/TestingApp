package com.vibemusic.app

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.vibemusic.app.navigation.VibeMainScaffold
import com.vibemusic.app.presentation.VibeViewModel
import com.vibemusic.app.ui.theme.VibeMusicTheme

class MainActivity : ComponentActivity() {

    private val viewModel: VibeViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            val prefs by viewModel.preferences.collectAsState()

            val requiredPermissions = buildList {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    add(Manifest.permission.READ_MEDIA_AUDIO)
                    add(Manifest.permission.POST_NOTIFICATIONS)
                } else {
                    add(Manifest.permission.READ_EXTERNAL_STORAGE)
                }
            }.toTypedArray()

            val permissionLauncher = rememberLauncherForActivityResult(
                contract = ActivityResultContracts.RequestMultiplePermissions()
            ) { resultMap ->
                val audioGranted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    resultMap[Manifest.permission.READ_MEDIA_AUDIO] == true
                } else {
                    resultMap[Manifest.permission.READ_EXTERNAL_STORAGE] == true
                }
                viewModel.onPermissionResult(audioGranted)
            }

            LaunchedEffect(Unit) {
                permissionLauncher.launch(requiredPermissions)
            }

            VibeMusicTheme(
                themeMode = prefs.themeMode,
                accentColorHex = prefs.accentColorHex
            ) {
                VibeMainScaffold(
                    viewModel = viewModel,
                    onRequestAudioPermission = {
                        permissionLauncher.launch(requiredPermissions)
                    }
                )
            }
        }
    }
}

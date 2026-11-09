package com.example.filerecovery

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.filerecovery.ui.PermissionScreen
import com.example.filerecovery.ui.RecoveryScreen
import com.example.filerecovery.ui.RecoveryViewModel
import com.example.filerecovery.ui.theme.FileRecoveryTheme
import com.example.filerecovery.util.hasStorageAccess
import com.example.filerecovery.util.mediaPermissions
import com.example.filerecovery.util.openAllFilesSettings

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            FileRecoveryTheme {
                AppRoot()
            }
        }
    }
}

@Composable
fun AppRoot(vm: RecoveryViewModel = viewModel()) {
    val context = androidx.compose.ui.platform.LocalContext.current
    var hasAccess by remember { mutableStateOf(hasStorageAccess(context)) }

    // Jab user Settings se wapas aaye, permission dobara check karo
    LifecycleResumeEffect(Unit) {
        hasAccess = hasStorageAccess(context)
        onPauseOrDispose { }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) {
        hasAccess = hasStorageAccess(context)
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        if (hasAccess) {
            RecoveryScreen(vm)
        } else {
            PermissionScreen(
                onGrant = { permissionLauncher.launch(mediaPermissions()) },
                onAllFiles = { openAllFilesSettings(context) }
            )
        }
    }
}

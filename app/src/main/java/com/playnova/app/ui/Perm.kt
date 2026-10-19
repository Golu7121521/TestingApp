package com.playnova.app.ui

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import com.playnova.app.AppViewModel

fun videoPermission(): String =
    if (Build.VERSION.SDK_INT >= 33) Manifest.permission.READ_MEDIA_VIDEO else Manifest.permission.READ_EXTERNAL_STORAGE

fun hasVideoPermission(c: Context) =
    ContextCompat.checkSelfPermission(c, videoPermission()) == PackageManager.PERMISSION_GRANTED

/** Returns an action that asks for permission when needed, then (re)scans the library. */
@Composable
fun rememberScanAction(vm: AppViewModel, onResult: (Boolean) -> Unit = {}): () -> Unit {
    val ctx = LocalContext.current
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        if (ok) vm.refresh()
        onResult(ok)
    }
    return remember(ctx) { { if (hasVideoPermission(ctx)) vm.refresh() else launcher.launch(videoPermission()) } }
}

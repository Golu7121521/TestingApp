package com.playvid.presentation.local

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.text.format.Formatter
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.playvid.domain.model.PlaybackRequest
import com.playvid.presentation.AppViewModelProvider
import com.playvid.presentation.app
import com.playvid.presentation.components.EmptyState
import com.playvid.presentation.components.LoadingIndicator
import com.playvid.presentation.components.VideoRow
import com.playvid.presentation.util.formatTime

/** The runtime permission that lets us read the device's video index, for this API level. */
private fun videoPermission(): String =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) Manifest.permission.READ_MEDIA_VIDEO
    else Manifest.permission.READ_EXTERNAL_STORAGE

/** Local tab, step 1: every device folder that has a video in it. Tap a folder to see its videos. */
@Composable
fun LocalFoldersScreen(onOpenFolder: (Long, String) -> Unit) {
    val context = LocalContext.current
    val permission = remember { videoPermission() }
    
    // Track permission state
    var granted by remember {
        mutableStateOf(ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED)
    }
    
    val requestPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
        granted = isGranted
    }

    val vm: LocalFoldersViewModel = viewModel(factory = AppViewModelProvider.Factory)
    val folders by vm.folders.collectAsStateWithLifecycle()

    // Re-check permission status every time this screen enters composition
    // This handles cases where permission was granted from MainActivity or System Settings
    LaunchedEffect(context, permission) {
        val isCurrentlyGranted = ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
        if (granted != isCurrentlyGranted) {
            granted = isCurrentlyGranted
        }
    }

    // Load folders immediately when permission is granted
    LaunchedEffect(granted) { 
        if (granted) {
            vm.load() 
        }
    }

    LazyColumn(contentPadding = PaddingValues(horizontal = 20.dp, vertical = 24.dp)) {
        item {
            Text("Local", style = MaterialTheme.typography.headlineMedium)
            Spacer(Modifier.height(20.dp))
        }
        if (!granted) {
            item {
                Column {
                    Text(
                        "PlayVid needs permission to see the videos on your device.",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(12.dp))
                    Button(onClick = { requestPermission.launch(permission) }) { Text("Grant access") }
                }
            }
        } else when {
            folders == null -> item { LoadingIndicator() }
            folders!!.isEmpty() -> item { EmptyState("No videos found", "Videos on your device will show up here, grouped by folder.") }
            else -> items(folders!!, key = { it.id }) { folder ->
                FolderRow(folder = folder, onClick = { onOpenFolder(folder.id, folder.name) })
            }
        }
    }
}

@Composable
private fun FolderRow(folder: LocalFolder, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClickLabel = "Open folder", onClick = onClick).padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(Icons.Filled.Folder, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.height(28.dp))
        Column(Modifier.padding(start = 16.dp)) {
            Text(folder.name, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                if (folder.videoCount == 1) "1 video" else "${folder.videoCount} videos",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/** Local tab, step 2: the videos inside one folder. */
@Composable
fun LocalFolderScreen(folderId: Long, folderName: String, onBack: () -> Unit, onPlay: (PlaybackRequest) -> Unit) {
    val vm: LocalFolderViewModel = viewModel(
        key = "local-folder-$folderId",
        factory = viewModelFactory { initializer { LocalFolderViewModel(app(), folderId) } }
    )
    val videos by vm.videos.collectAsStateWithLifecycle()

    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
            Text(
                folderName,
                style = MaterialTheme.typography.titleLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(start = 8.dp)
            )
        }
        when {
            videos == null -> Box(Modifier.fillMaxSize()) { LoadingIndicator() }
            videos!!.isEmpty() -> EmptyState("No videos", "This folder has no videos anymore.")
            else -> LazyColumn(contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp)) {
                items(videos!!, key = { it.uri }) { video ->
                    LocalVideoRow(video = video, onClick = {
                        onPlay(PlaybackRequest(source = video.uri, title = video.name, isLocal = true))
                    })
                }
            }
        }
    }
}

@Composable
private fun LocalVideoRow(video: LocalVideo, onClick: () -> Unit) {
    val context = LocalContext.current
    val details = listOfNotNull(
        Formatter.formatShortFileSize(context, video.sizeBytes),
        video.durationMs.takeIf { it > 0 }?.let { formatTime(it) }
    ).joinToString(" \u2022 ")
    VideoRow(title = video.name, details = details, progress = 0f, onClick = onClick)
}

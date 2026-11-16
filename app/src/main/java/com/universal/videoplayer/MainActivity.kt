package com.universal.videoplayer

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
    import androidx.media3.datasource.DefaultHttpDataSource
    import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.PlayerView
import androidx.compose.ui.viewinterop.AndroidView
import com.universal.videoplayer.data.AppDatabase
import com.universal.videoplayer.data.VideoEntity
import com.universal.videoplayer.playback.MediaSourceResolver
import com.universal.videoplayer.playback.PlaybackRequest
import com.universal.videoplayer.playback.ResolveResult
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val dao = AppDatabase.get(this).videoDao()
        setContent { UniversalVideoApp(dao) }
    }
}

private enum class Destination(val label: String) { Home("Home"), Advance("Advance"), History("History"), Playlist("Playlist") }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun UniversalVideoApp(dao: com.universal.videoplayer.data.VideoDao) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val history by dao.observeHistory().collectAsState(initial = emptyList())
    val playlists by dao.observePlaylists().collectAsState(initial = emptyList())
    var selected by remember { mutableStateOf(Destination.Home) }
    var url by remember { mutableStateOf("") }
    var cookie by remember { mutableStateOf("") }
    var referer by remember { mutableStateOf("") }
    var origin by remember { mutableStateOf("") }
    var userAgent by remember { mutableStateOf("Android Chrome") }
    var customAgent by remember { mutableStateOf("") }
    var dark by remember { mutableStateOf(true) }
    var showPlayer by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var playerTitle by remember { mutableStateOf("") }
    var showClipboardPrompt by remember { mutableStateOf(false) }
    var clipboardUrl by remember { mutableStateOf("") }
    var playlistName by remember { mutableStateOf("") }
    var showCreatePlaylist by remember { mutableStateOf(false) }

    val player = remember {
        ExoPlayer.Builder(context).build().apply {
            addListener(object : Player.Listener {
                override fun onPlayerError(errorInfo: androidx.media3.common.PlaybackException) {
                    error = "Playback failed: ${errorInfo.errorCodeName}. Check the URL, network access, and source permissions."
                }
            })
        }
    }
    DisposableEffect(Unit) { onDispose { player.release() } }

    fun startPlayback(input: String) {
        val headers = buildMap {
            if (cookie.isNotBlank()) put("Cookie", cookie.trim())
            if (referer.isNotBlank()) put("Referer", referer.trim())
            if (origin.isNotBlank()) put("Origin", origin.trim())
        }
        val resolvedAgent = when (userAgent) {
            "Android Chrome" -> "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 Chrome/120.0 Mobile Safari/537.36"
            "Chrome Desktop" -> "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 Chrome/120.0 Safari/537.36"
            "Firefox Desktop" -> "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:120.0) Gecko/20100101 Firefox/120.0"
            "Firefox Android" -> "Mozilla/5.0 (Android 14; Mobile; rv:120.0) Gecko/120.0 Firefox/120.0"
            "Custom User-Agent" -> customAgent.trim().ifBlank { null }
            else -> null
        }
        when (val result = MediaSourceResolver.resolve(PlaybackRequest(input, headers, resolvedAgent))) {
            is ResolveResult.Failure -> error = result.message
            is ResolveResult.Success -> {
                error = null
                url = input.trim()
                playerTitle = UriTitle(input)
                val httpFactory = DefaultHttpDataSource.Factory()
                    .setAllowCrossProtocolRedirects(true)
                    .setDefaultRequestProperties(headers)
                    .apply { resolvedAgent?.let { setUserAgent(it) } }
                player.setMediaSource(
                    DefaultMediaSourceFactory(httpFactory).createMediaSource(result.mediaItem)
                )
                player.prepare()
                player.playWhenReady = true
                showPlayer = true
                scope.launch {
                    dao.save(VideoEntity(
                        url = input.trim(),
                        title = playerTitle,
                        source = MediaSourceResolver.sourceDomain(input)
                    ))
                }
            }
        }
    }

    MaterialTheme(
        colorScheme = if (dark) darkColorScheme(primary = androidx.compose.ui.graphics.Color(0xFF9B8CFF))
            else lightColorScheme(primary = androidx.compose.ui.graphics.Color(0xFF6254A8))
    ) {
        if (showPlayer) {
            PlayerScreen(
                player = player, title = playerTitle, onBack = { showPlayer = false },
                onSaveProgress = {
                    scope.launch {
                        dao.save(VideoEntity(
                            url = url, title = playerTitle,
                            source = MediaSourceResolver.sourceDomain(url),
                            durationMs = player.duration.coerceAtLeast(0),
                            positionMs = player.currentPosition.coerceAtLeast(0)
                        ))
                    }
                }
            )
        } else {
            Scaffold(
                topBar = {
                    TopAppBar(
                        title = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.PlayCircle, null, tint = MaterialTheme.colorScheme.primary)
                                Spacer(Modifier.width(8.dp))
                                Text("Universal Video Player", fontWeight = FontWeight.SemiBold)
                            }
                        },
                        actions = {
                            IconButton(onClick = { dark = !dark }) {
                                Icon(if (dark) Icons.Default.LightMode else Icons.Default.DarkMode, "Toggle theme")
                            }
                            IconButton(onClick = { selected = Destination.Advance }) {
                                Icon(Icons.Default.Settings, "Settings and advanced options")
                            }
                        }
                    )
                },
                bottomBar = {
                    NavigationBar {
                        Destination.values().forEach { destination ->
                            val icon = when (destination) {
                                Destination.Home -> Icons.Default.Home
                                Destination.Advance -> Icons.Default.Tune
                                Destination.History -> Icons.Default.History
                                Destination.Playlist -> Icons.Default.PlaylistPlay
                            }
                            NavigationBarItem(
                                selected = selected == destination,
                                onClick = { selected = destination },
                                icon = { Icon(icon, null) },
                                label = { Text(destination.label) }
                            )
                        }
                    }
                }
            ) { padding ->
                Column(Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp)) {
                    when (selected) {
                        Destination.Home -> {
                            Spacer(Modifier.height(12.dp))
                            Text("Play a video link", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                            Text("Play supported direct media and streaming URLs.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Spacer(Modifier.height(18.dp))
                            OutlinedTextField(
                                value = url, onValueChange = { url = it },
                                modifier = Modifier.fillMaxWidth(),
                                label = { Text("Video URL") },
                                placeholder = { Text("Paste your video link here...") },
                                minLines = 2,
                                shape = RoundedCornerShape(18.dp)
                            )
                            Spacer(Modifier.height(10.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedButton(onClick = {
                                    val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                    val clip = cm.primaryClip
                                    val candidate = if (clip != null && clip.itemCount > 0) clip.getItemAt(0).coerceToText(context).toString() else ""
                                    if (candidate.startsWith("http://") || candidate.startsWith("https://")) {
                                        clipboardUrl = candidate
                                        showClipboardPrompt = true
                                    } else error = "No valid web link found in the clipboard."
                                }) { Icon(Icons.Default.ContentPaste, null); Spacer(Modifier.width(4.dp)); Text("Paste") }
                                Button(onClick = { startPlayback(url) }, enabled = url.isNotBlank()) {
                                    Icon(Icons.Default.PlayArrow, null); Spacer(Modifier.width(4.dp)); Text("Play")
                                }
                                TextButton(onClick = { url = ""; error = null }) { Text("Clear") }
                            }
                            error?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = 8.dp)) }
                            Spacer(Modifier.height(24.dp))
                            Text("Recent videos", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                            Spacer(Modifier.height(8.dp))
                            if (history.isEmpty()) EmptyState("Your recently played videos will appear here.")
                            else LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                items(history.take(8), key = { it.url }) { item ->
                                    VideoRow(item, onPlay = { url = item.url; startPlayback(item.url) }, onDelete = { scope.launch { dao.delete(item.url) } })
                                }
                            }
                        }
                        Destination.Advance -> {
                            Spacer(Modifier.height(12.dp))
                            Text("Advanced playback", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                            Text("Optional request headers for sources you are authorized to access.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Spacer(Modifier.height(12.dp))
                            OutlinedTextField(url, { url = it }, Modifier.fillMaxWidth(), label = { Text("Video source URL") })
                            OutlinedTextField(cookie, { cookie = it }, Modifier.fillMaxWidth(), label = { Text("Cookie (authorized sources only)") })
                            OutlinedTextField(referer, { referer = it }, Modifier.fillMaxWidth(), label = { Text("Referer") })
                            OutlinedTextField(origin, { origin = it }, Modifier.fillMaxWidth(), label = { Text("Origin") })
                            var expanded by remember { mutableStateOf(false) }
                            ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
                                OutlinedTextField(userAgent, { }, Modifier.menuAnchor().fillMaxWidth(), readOnly = true, label = { Text("User-Agent") }, trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) })
                                ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                                    listOf("Android Chrome", "Chrome Desktop", "Firefox Desktop", "Firefox Android", "Custom User-Agent").forEach {
                                        DropdownMenuItem(text = { Text(it) }, onClick = { userAgent = it; expanded = false })
                                    }
                                }
                            }
                            if (userAgent == "Custom User-Agent") OutlinedTextField(customAgent, { customAgent = it }, Modifier.fillMaxWidth(), label = { Text("Custom User-Agent value") })
                            Spacer(Modifier.height(12.dp))
                            Text("DRM-protected streams are not configured in this starter build. Use the platform's normal authorized playback flow.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Spacer(Modifier.height(12.dp))
                            Button(onClick = { startPlayback(url) }, enabled = url.isNotBlank(), modifier = Modifier.fillMaxWidth()) { Text("Play Video") }
                            TextButton(onClick = { cookie = ""; referer = ""; origin = ""; userAgent = "Android Chrome"; customAgent = "" }) { Text("Reset settings") }
                            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                        }
                        Destination.History -> {
                            Spacer(Modifier.height(12.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("History", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                                TextButton(onClick = { scope.launch { dao.clear() } }, enabled = history.isNotEmpty()) { Text("Clear all") }
                            }
                            if (history.isEmpty()) EmptyState("No playback history yet.")
                            else LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                items(history, key = { it.url }) { item ->
                                    VideoRow(item, onPlay = { url = item.url; startPlayback(item.url) }, onDelete = { scope.launch { dao.delete(item.url) } })
                                }
                            }
                        }
                        Destination.Playlist -> {
                            Spacer(Modifier.height(12.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("Playlists", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                                FilledTonalButton(onClick = { showCreatePlaylist = true }) { Icon(Icons.Default.Add, null); Text("Create") }
                            }
                            if (playlists.isEmpty()) EmptyState("Create a playlist to organize your video links.")
                            else LazyColumn {
                                items(playlists, key = { it.id }) { playlist ->
                                    Card(Modifier.fillMaxWidth().padding(vertical = 5.dp)) {
                                        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                                            Icon(Icons.Default.PlaylistPlay, null)
                                            Spacer(Modifier.width(12.dp))
                                            Text(playlist.name, Modifier.weight(1f), fontWeight = FontWeight.Medium)
                                            IconButton(onClick = { scope.launch { dao.deletePlaylist(playlist.id) } }) { Icon(Icons.Default.Delete, "Delete playlist") }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showClipboardPrompt) {
        AlertDialog(
            onDismissRequest = { showClipboardPrompt = false },
            title = { Text("Video link detected") },
            text = { Text("A web link is available in your clipboard. Would you like to use it?") },
            confirmButton = { TextButton(onClick = { url = clipboardUrl; showClipboardPrompt = false; startPlayback(clipboardUrl) }) { Text("Play") } },
            dismissButton = {
                Row {
                    TextButton(onClick = { url = clipboardUrl; showClipboardPrompt = false }) { Text("Paste") }
                    TextButton(onClick = { showClipboardPrompt = false }) { Text("Dismiss") }
                }
            }
        )
    }
    if (showCreatePlaylist) {
        AlertDialog(
            onDismissRequest = { showCreatePlaylist = false; playlistName = "" },
            title = { Text("Create playlist") },
            text = { OutlinedTextField(playlistName, { playlistName = it }, label = { Text("Playlist name") }) },
            confirmButton = {
                TextButton(onClick = {
                    if (playlistName.isNotBlank()) scope.launch { dao.createPlaylist(com.universal.videoplayer.data.PlaylistEntity(name = playlistName.trim())) }
                    showCreatePlaylist = false; playlistName = ""
                }, enabled = playlistName.isNotBlank()) { Text("Create") }
            },
            dismissButton = { TextButton(onClick = { showCreatePlaylist = false }) { Text("Cancel") } }
        )
    }
}

@Composable
private fun EmptyState(message: String) {
    Box(Modifier.fillMaxWidth().padding(vertical = 42.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(Icons.Default.VideoLibrary, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(42.dp))
            Spacer(Modifier.height(10.dp))
            Text(message, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun VideoRow(item: VideoEntity, onPlay: () -> Unit, onDelete: () -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Row(Modifier.clickable(onClick = onPlay).padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(54.dp).background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(10.dp)), contentAlignment = Alignment.Center) {
                Icon(Icons.Default.PlayCircle, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(30.dp))
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(item.title, maxLines = 1, fontWeight = FontWeight.SemiBold)
                Text(item.source, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (item.durationMs > 0) Text("Resume at ${formatTime(item.positionMs)}", style = MaterialTheme.typography.labelSmall)
            }
            IconButton(onClick = onDelete) { Icon(Icons.Default.DeleteOutline, "Remove") }
        }
    }
}

@Composable
private fun PlayerScreen(player: ExoPlayer, title: String, onBack: () -> Unit, onSaveProgress: () -> Unit) {
    DisposableEffect(player) {
        onDispose { onSaveProgress() }
    }
    var controlsVisible by remember { mutableStateOf(true) }
    var playing by remember { mutableStateOf(player.isPlaying) }
    Column(Modifier.fillMaxSize().background(androidx.compose.ui.graphics.Color.Black)) {
        Row(Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, "Back", tint = androidx.compose.ui.graphics.Color.White) }
            Text(title, color = androidx.compose.ui.graphics.Color.White, maxLines = 1, modifier = Modifier.weight(1f))
            IconButton(onClick = { player.seekTo(0) }) { Icon(Icons.Default.Replay, "Restart", tint = androidx.compose.ui.graphics.Color.White) }
        }
        Box(Modifier.fillMaxWidth().weight(1f).clickable { controlsVisible = !controlsVisible }, contentAlignment = Alignment.Center) {
            AndroidView(
                factory = { ctx -> PlayerView(ctx).apply { this.player = player; useController = true } },
                modifier = Modifier.fillMaxSize()
            )
            if (!player.isPlaying && controlsVisible) {
                FilledIconButton(onClick = { player.play(); playing = true }) {
                    Icon(Icons.Default.PlayArrow, "Play", modifier = Modifier.size(36.dp))
                }
            }
        }
        if (controlsVisible) {
            Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(formatTime(player.currentPosition.coerceAtLeast(0)), color = androidx.compose.ui.graphics.Color.White)
                Spacer(Modifier.weight(1f))
                IconButton(onClick = {
                    if (player.isPlaying) player.pause() else player.play()
                    playing = player.isPlaying
                }) { Icon(if (player.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow, "Play or pause", tint = androidx.compose.ui.graphics.Color.White) }
                IconButton(onClick = { player.seekTo((player.currentPosition - 10_000).coerceAtLeast(0)) }) { Icon(Icons.Default.Replay10, "Back 10 seconds", tint = androidx.compose.ui.graphics.Color.White) }
                IconButton(onClick = { player.seekTo(player.currentPosition + 10_000) }) { Icon(Icons.Default.Forward10, "Forward 10 seconds", tint = androidx.compose.ui.graphics.Color.White) }
            }
        }
    }
}

private fun UriTitle(url: String): String =
    runCatching { android.net.Uri.parse(url).lastPathSegment?.substringBefore('?')?.ifBlank { null } }
        .getOrNull() ?: "Video"

private fun formatTime(ms: Long): String {
    val total = (ms / 1000).coerceAtLeast(0)
    return "%d:%02d".format(total / 60, total % 60)
}

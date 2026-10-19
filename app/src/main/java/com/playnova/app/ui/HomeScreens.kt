@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)

package com.playnova.app.ui

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.playnova.app.AppViewModel
import com.playnova.app.data.MediaRepo
import com.playnova.app.data.Video
import com.playnova.app.data.fmtSize
import com.playnova.app.data.fmtTime
import com.playnova.app.i18n.tr
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import java.text.DateFormat
import java.util.Date

@Composable
private fun QuickCard(icon: ImageVector, label: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Column(
        modifier.clip(CardShape)
            .background(Brush.linearGradient(listOf(MaterialTheme.colorScheme.primary.copy(0.22f), MaterialTheme.colorScheme.surface)))
            .clickable(onClick = onClick).padding(14.dp).heightIn(min = 48.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Icon(icon, null, tint = MaterialTheme.colorScheme.primary)
        Text(tr(label), style = MaterialTheme.typography.bodyMedium, maxLines = 1)
    }
}

@Composable
fun HomeScreen(
    vm: AppViewModel, onNav: (String) -> Unit,
    onPlay: (List<Video>, Int) -> Unit, onPlayExternal: (Uri) -> Unit, onPlayUrl: (String) -> Unit
) {
    val videos by vm.videos.collectAsState()
    val loading by vm.loading.collectAsState()
    val scanned by vm.scanned.collectAsState()
    val history by vm.history.collectAsState()
    val progress by vm.progress.collectAsState()
    val ctx = LocalContext.current
    val scan = rememberScanAction(vm)
    var target by remember { mutableStateOf<List<Video>?>(null) }
    var urlDialog by remember { mutableStateOf(false) }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            runCatching { ctx.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) }
            onPlayExternal(uri)
        }
    }

    val resume = remember(history, videos) {
        vm.historyVideos(history).filter { (h, v) ->
            val d = if (h.durationMs > 0) h.durationMs else v.durationMs
            h.positionMs > 3000 && (d <= 0 || h.positionMs < d * 0.95)
        }.take(10)
    }
    val recent = remember(videos) { videos.sortedByDescending { it.dateAdded }.take(15) }

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 16.dp)) {
        item {
            Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp, top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(36.dp).clip(RoundedCornerShape(10.dp)).background(MaterialTheme.colorScheme.primary), contentAlignment = Alignment.Center) {
                    Icon(Icons.Outlined.PlayArrow, null, tint = Color.White)
                }
                Text("PlayNova", Modifier.weight(1f).padding(start = 10.dp), style = MaterialTheme.typography.titleLarge)
                IconButton(onClick = { onNav("search") }) { Icon(Icons.Outlined.Search, tr("Search")) }
                IconButton(onClick = { onNav("settings") }) { Icon(Icons.Outlined.Settings, tr("Settings")) }
            }
        }
        item {
            val cards = listOf(
                Triple(Icons.Outlined.VideoLibrary, "All Videos", "videos"), Triple(Icons.Outlined.Folder, "Folders", "folders"),
                Triple(Icons.Outlined.PlaylistPlay, "Playlists", "playlists"), Triple(Icons.Outlined.Favorite, "Favorites", "favorites"),
                Triple(Icons.Outlined.History, "History", "history")
            )
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                cards.chunked(3).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        row.forEach { (i, l, r) -> QuickCard(i, l, Modifier.weight(1f)) { onNav(r) } }
                        repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedButton(onClick = { picker.launch(arrayOf("video/*")) }, Modifier.weight(1f), shape = SmallShape) {
                        Icon(Icons.Outlined.FolderOpen, null); Spacer(Modifier.width(6.dp)); Text(tr("Open file"), maxLines = 1)
                    }
                    OutlinedButton(onClick = { urlDialog = true }, Modifier.weight(1f), shape = SmallShape) {
                        Icon(Icons.Outlined.Link, null); Spacer(Modifier.width(6.dp)); Text(tr("Stream URL"), maxLines = 1)
                    }
                }
            }
        }
        if (videos.isEmpty()) item {
            if (loading && !scanned) SkeletonGrid() else EmptyState(
                Icons.Outlined.VideoLibrary, tr("No videos yet"),
                if (hasVideoPermission(ctx)) tr("Scan for videos, or open a file directly using the buttons above.") else tr("Allow access so PlayNova can show the videos on your phone."),
                if (hasVideoPermission(ctx)) tr("Scan videos") else tr("Grant permission"), scan
            )
        }
        if (resume.isNotEmpty()) {
            item { SectionHeader(tr("Continue Watching")) }
            item {
                LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    items(resume, key = { it.first.videoId }) { (h, v) ->
                        val d = if (h.durationMs > 0) h.durationMs else v.durationMs
                        Column(
                            Modifier.width(240.dp).clip(CardShape).background(MaterialTheme.colorScheme.surface)
                                .combinedClickable(onClick = { onPlay(listOf(v), 0) }, onLongClick = { target = listOf(v) }).padding(8.dp)
                        ) {
                            Box {
                                Thumb(v, Modifier.fillMaxWidth().aspectRatio(16f / 9f))
                                if (d > 0) LinearProgressIndicator({ (h.positionMs.toFloat() / d).coerceIn(0f, 1f) },
                                    Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(3.dp))
                                IconButton(onClick = { vm.removeHistory(v.id) }, Modifier.align(Alignment.TopEnd)) {
                                    Icon(Icons.Outlined.Close, tr("Remove from Continue Watching"), tint = Color.White)
                                }
                            }
                            Text(v.title, Modifier.padding(top = 6.dp), maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("${fmtTime(h.positionMs)} / ${fmtTime(d)}", Modifier.weight(1f), color = Muted, style = MaterialTheme.typography.labelSmall)
                                FilledTonalButton(onClick = { onPlay(listOf(v), 0) }, contentPadding = PaddingValues(horizontal = 12.dp), modifier = Modifier.height(36.dp)) { Text(tr("Resume")) }
                            }
                        }
                    }
                }
            }
        }
        if (recent.isNotEmpty()) {
            item { SectionHeader(tr("Recently Added"), tr("See all")) { onNav("videos") } }
            item {
                LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    items(recent, key = { it.id }) { v ->
                        Column(
                            Modifier.width(160.dp).combinedClickable(onClick = { onPlay(recent, recent.indexOf(v)) }, onLongClick = { target = listOf(v) })
                        ) {
                            Thumb(v, Modifier.fillMaxWidth().aspectRatio(16f / 9f))
                            Text(v.title, Modifier.padding(top = 6.dp), maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyMedium)
                            Text("${fmtTime(v.durationMs)} | ${fmtSize(v.size)}", color = Muted, style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
            }
        }
    }
    VideoActions(vm, target, { target = null }) { v -> onPlay(listOf(v), 0) }
    if (urlDialog) {
        var url by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { urlDialog = false }, title = { Text(tr("Stream from URL")) },
            text = {
                Column {
                    OutlinedTextField(
                        url, { url = it }, singleLine = true, label = { Text(tr("Video link")) },
                        placeholder = { Text("https://example.com/video.mp4") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Go),
                        keyboardActions = KeyboardActions(onGo = { if (url.isNotBlank()) { urlDialog = false; onPlayUrl(url) } })
                    )
                    Text(tr("Supports direct links, HLS (.m3u8), DASH (.mpd), RTSP, redirects and links without a file extension."),
                        Modifier.padding(top = 8.dp), color = Muted, style = MaterialTheme.typography.bodySmall)
                }
            },
            confirmButton = {
                TextButton(enabled = url.isNotBlank(), onClick = { urlDialog = false; onPlayUrl(url) }) { Text(tr("Play")) }
            },
            dismissButton = { TextButton(onClick = { urlDialog = false }) { Text(tr("Cancel")) } }
        )
    }
}

@Composable
fun FoldersScreen(vm: AppViewModel, onOpen: (String) -> Unit) {
    val folders by vm.folders.collectAsState()
    val loading by vm.loading.collectAsState()
    var q by remember { mutableStateOf("") }
    val scan = rememberScanAction(vm)
    val shown = folders.filter { q.isBlank() || it.name.contains(q, true) }
    Column(Modifier.fillMaxSize()) {
        ScreenTitle(tr("Folders")) { RefreshButton(scan) }
        OutlinedTextField(q, { q = it }, singleLine = true, placeholder = { Text(tr("Search folders")) }, leadingIcon = { Icon(Icons.Outlined.Search, null) },
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp), shape = SmallShape)
        when {
            loading && folders.isEmpty() -> SkeletonGrid()
            shown.isEmpty() -> EmptyState(Icons.Outlined.FolderOff, tr("No folders"), tr("Only folders that contain videos and that the app can access are shown."), tr("Scan videos"), scan)
            else -> LazyColumn(contentPadding = PaddingValues(vertical = 4.dp)) {
                items(shown, key = { it.name }) { f ->
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp).clip(CardShape)
                            .background(MaterialTheme.colorScheme.surface).clickable { onOpen(f.name) }.padding(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Thumb(f.thumb, Modifier.size(72.dp))
                        Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                            Text(f.name, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.titleMedium)
                            Text(tr("%d videos", f.count), color = Muted, style = MaterialTheme.typography.bodySmall)
                        }
                        Icon(Icons.Outlined.ChevronRight, null, tint = Muted)
                    }
                }
            }
        }
    }
}

@Composable
fun SearchScreen(vm: AppViewModel, onBack: () -> Unit, onPlay: (List<Video>, Int) -> Unit) {
    val videos by vm.videos.collectAsState()
    val searches by vm.searches.collectAsState()
    val progress by vm.progress.collectAsState()
    val favs by vm.favoriteIds.collectAsState()
    val folders by vm.folders.collectAsState()
    var q by remember { mutableStateOf("") }
    var folderFilter by remember { mutableStateOf<String?>(null) }
    var dur by remember { mutableStateOf(0) }
    var target by remember { mutableStateOf<List<Video>?>(null) }
    val focus = remember { androidx.compose.ui.focus.FocusRequester() }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }

    val results = remember(videos, q, folderFilter, dur) {
        if (q.isBlank()) emptyList() else videos.filter { v ->
            v.title.contains(q.trim(), true) && (folderFilter == null || v.folder == folderFilter) &&
                when (dur) { 1 -> v.durationMs < 5 * 60_000; 2 -> v.durationMs in (5 * 60_000)..(30 * 60_000); 3 -> v.durationMs > 30 * 60_000; else -> true }
        }
    }
    val suggestions = remember(videos, q) {
        if (q.isBlank()) emptyList() else videos.flatMap { it.title.substringBeforeLast('.').split(' ', '_', '-', '.') }
            .filter { it.length > 2 && it.startsWith(q.trim(), true) && !it.equals(q.trim(), true) }.map { it.lowercase() }.distinct().take(6)
    }
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.Outlined.ArrowBack, tr("Back")) }
            OutlinedTextField(q, { q = it }, singleLine = true, placeholder = { Text(tr("Type a video name")) },
                modifier = Modifier.weight(1f).padding(end = 12.dp).focusRequester(focus), shape = SmallShape,
                trailingIcon = { if (q.isNotEmpty()) IconButton(onClick = { q = "" }) { Icon(Icons.Outlined.Close, tr("Clear")) } },
                keyboardActions = androidx.compose.foundation.text.KeyboardActions(onSearch = { vm.addSearch(q) }),
                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(imeAction = androidx.compose.ui.text.input.ImeAction.Search))
        }
        if (q.isBlank()) {
            if (searches.isEmpty()) EmptyState(Icons.Outlined.Search, tr("Local search"), tr("Search the videos on your phone by file name. No internet needed."))
            else {
                SectionHeader(tr("Recent searches"), tr("Clear")) { vm.clearSearches() }
                searches.forEach { s ->
                    ListItem(headlineContent = { Text(s.text) }, leadingContent = { Icon(Icons.Outlined.History, null) },
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent), modifier = Modifier.clickable { q = s.text })
                }
            }
        } else {
            LazyRow(Modifier.padding(horizontal = 16.dp)) {
                items(suggestions) { s -> SuggestionChip(onClick = { q = s }, label = { Text(s) }, modifier = Modifier.padding(end = 6.dp)) }
            }
            LazyRow(Modifier.padding(horizontal = 16.dp)) {
                item { Chip(tr("< 5 min"), dur == 1) { dur = if (dur == 1) 0 else 1 } }
                item { Chip(tr("5-30 min"), dur == 2) { dur = if (dur == 2) 0 else 2 } }
                item { Chip(tr("> 30 min"), dur == 3) { dur = if (dur == 3) 0 else 3 } }
                items(folders) { f -> Chip(f.name, folderFilter == f.name) { folderFilter = if (folderFilter == f.name) null else f.name } }
            }
            if (results.isEmpty()) EmptyState(Icons.Outlined.SearchOff, tr("Nothing found"), tr("No video matches \"%s\".", q))
            else LazyColumn {
                items(results, key = { it.id }) { v ->
                    VideoListItem(v, progress[v.id], v.id in favs, false, false, q,
                        onClick = { vm.addSearch(q); onPlay(results, results.indexOf(v)) }, onLong = { target = listOf(v) })
                }
            }
        }
    }
    VideoActions(vm, target, { target = null }) { v -> onPlay(results, results.indexOf(v).coerceAtLeast(0)) }
}


@Composable
fun HistoryScreen(vm: AppViewModel, onBack: () -> Unit, onPlay: (List<Video>, Int) -> Unit) {
    val history by vm.history.collectAsState()
    val videos by vm.videos.collectAsState()
    val favs by vm.favoriteIds.collectAsState()
    var confirm by remember { mutableStateOf(false) }
    var target by remember { mutableStateOf<List<Video>?>(null) }
    val rows = remember(history, videos) { vm.historyVideos(history) }
    val list = rows.map { it.second }
    Column(Modifier.fillMaxSize()) {
        ScreenTitle(tr("History"), onBack) {
            if (rows.isNotEmpty()) IconButton(onClick = { confirm = true }) { Icon(Icons.Outlined.DeleteSweep, tr("Clear history")) }
        }
        if (rows.isEmpty()) EmptyState(Icons.Outlined.History, tr("History is empty"), tr("Videos you watch will show up here."))
        else LazyColumn {
            items(rows, key = { it.first.videoId }) { (h, v) ->
                VideoListItem(v, h, v.id in favs, false, false, "", onClick = { onPlay(list, list.indexOf(v)) }, onLong = { target = listOf(v) },
                    trailing = {
                        Column(horizontalAlignment = Alignment.End) {
                            Text(DateFormat.getDateInstance(DateFormat.SHORT).format(Date(h.lastPlayed)), color = Muted, style = MaterialTheme.typography.labelSmall)
                            IconButton(onClick = { vm.removeHistory(v.id) }) { Icon(Icons.Outlined.Close, tr("Remove from history")) }
                        }
                    })
            }
        }
    }
    if (confirm) ConfirmDialog(tr("Clear all history?"), tr("Only the history list will be cleared. Your videos stay on the phone."), tr("Clear"), danger = true,
        onConfirm = { vm.clearHistory() }, onDismiss = { confirm = false })
    VideoActions(vm, target, { target = null }) { v -> onPlay(list, list.indexOf(v).coerceAtLeast(0)) }
}

@Composable
fun FavoritesScreen(vm: AppViewModel, onBack: () -> Unit, onPlay: (List<Video>, Int, Boolean) -> Unit) {
    val ids by vm.favoriteIds.collectAsState()
    val videos by vm.videos.collectAsState()
    val progress by vm.progress.collectAsState()
    var q by remember { mutableStateOf("") }
    var byName by remember { mutableStateOf(false) }
    var target by remember { mutableStateOf<List<Video>?>(null) }
    val list = remember(ids, videos, q, byName) {
        val m = videos.associateBy { it.id }
        val l = ids.mapNotNull { m[it] }.filter { q.isBlank() || it.title.contains(q, true) }
        if (byName) l.sortedBy { it.title.lowercase() } else l
    }
    Column(Modifier.fillMaxSize()) {
        ScreenTitle(tr("Favorites"), onBack) {
            IconButton(onClick = { byName = !byName }) { Icon(Icons.Outlined.SortByAlpha, if (byName) tr("Sort by name (on)") else tr("Sort by name")) }
        }
        OutlinedTextField(q, { q = it }, singleLine = true, placeholder = { Text(tr("Search favorites")) }, leadingIcon = { Icon(Icons.Outlined.Search, null) },
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp), shape = SmallShape)
        if (list.isNotEmpty()) Row(Modifier.padding(horizontal = 16.dp, vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { onPlay(list, 0, false) }, shape = SmallShape) { Icon(Icons.Outlined.PlayArrow, null); Text(tr("Play all")) }
            OutlinedButton(onClick = { onPlay(list, 0, true) }, shape = SmallShape) { Icon(Icons.Outlined.Shuffle, null); Text(tr("Shuffle")) }
        }
        if (list.isEmpty()) EmptyState(Icons.Outlined.FavoriteBorder, tr("No favorites yet"), tr("Long-press a video and add it to Favorites."))
        else LazyColumn {
            items(list, key = { it.id }) { v ->
                VideoListItem(v, progress[v.id], true, false, false, q, onClick = { onPlay(list, list.indexOf(v), false) }, onLong = { target = listOf(v) },
                    trailing = { IconButton(onClick = { vm.toggleFavorite(v) }) { Icon(Icons.Outlined.Favorite, tr("Remove from Favorites"), tint = Color(0xFFFF5C8A)) } })
            }
        }
    }
    VideoActions(vm, target, { target = null }) { v -> onPlay(list, list.indexOf(v).coerceAtLeast(0), false) }
}

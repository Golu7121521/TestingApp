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
        Text(label, style = MaterialTheme.typography.bodyMedium, maxLines = 1)
    }
}

@Composable
fun HomeScreen(
    vm: AppViewModel, onNav: (String) -> Unit,
    onPlay: (List<Video>, Int) -> Unit, onPlayExternal: (Uri) -> Unit
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
                IconButton(onClick = { onNav("search") }) { Icon(Icons.Outlined.Search, "Search") }
                IconButton(onClick = { onNav("settings") }) { Icon(Icons.Outlined.Settings, "Settings") }
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
                        Icon(Icons.Outlined.FolderOpen, null); Spacer(Modifier.width(6.dp)); Text("File kholo", maxLines = 1)
                    }
                    OutlinedButton(onClick = { urlDialog = true }, Modifier.weight(1f), shape = SmallShape) {
                        Icon(Icons.Outlined.Link, null); Spacer(Modifier.width(6.dp)); Text("URL stream", maxLines = 1)
                    }
                }
            }
        }
        if (videos.isEmpty()) item {
            if (loading && !scanned) SkeletonGrid() else EmptyState(
                Icons.Outlined.VideoLibrary, "Abhi koi video nahi",
                if (hasVideoPermission(ctx)) "Videos scan karo ya upar se koi file seedha kholo." else "Phone ke videos dikhane ke liye permission do.",
                if (hasVideoPermission(ctx)) "Scan Videos" else "Permission do", scan
            )
        }
        if (resume.isNotEmpty()) {
            item { SectionHeader("Continue Watching") }
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
                                    Icon(Icons.Outlined.Close, "Continue watching se hatao", tint = Color.White)
                                }
                            }
                            Text(v.title, Modifier.padding(top = 6.dp), maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("${fmtTime(h.positionMs)} / ${fmtTime(d)}", Modifier.weight(1f), color = Muted, style = MaterialTheme.typography.labelSmall)
                                FilledTonalButton(onClick = { onPlay(listOf(v), 0) }, contentPadding = PaddingValues(horizontal = 12.dp), modifier = Modifier.height(36.dp)) { Text("Resume") }
                            }
                        }
                    }
                }
            }
        }
        if (recent.isNotEmpty()) {
            item { SectionHeader("Recently Added", "Sab dekho") { onNav("videos") } }
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
            onDismissRequest = { urlDialog = false }, title = { Text("Network stream") },
            text = { OutlinedTextField(url, { url = it }, singleLine = true, label = { Text("http(s):// video URL") }) },
            confirmButton = {
                TextButton(enabled = url.startsWith("http"), onClick = { urlDialog = false; onPlayExternal(Uri.parse(url.trim())) }) { Text("Play") }
            },
            dismissButton = { TextButton(onClick = { urlDialog = false }) { Text("Cancel") } }
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
        ScreenTitle("Folders") { IconButton(onClick = scan) { Icon(Icons.Outlined.Refresh, "Rescan") } }
        OutlinedTextField(q, { q = it }, singleLine = true, placeholder = { Text("Folder dhundo") }, leadingIcon = { Icon(Icons.Outlined.Search, null) },
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp), shape = SmallShape)
        when {
            loading && folders.isEmpty() -> SkeletonGrid()
            shown.isEmpty() -> EmptyState(Icons.Outlined.FolderOff, "Koi folder nahi", "Sirf wahi folders dikhte hain jinme videos hain aur jinki permission di gayi hai.", "Scan Videos", scan)
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
                            Text("${f.count} videos", color = Muted, style = MaterialTheme.typography.bodySmall)
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
            IconButton(onClick = onBack) { Icon(Icons.Outlined.ArrowBack, "Back") }
            OutlinedTextField(q, { q = it }, singleLine = true, placeholder = { Text("Video ka naam likho") },
                modifier = Modifier.weight(1f).padding(end = 12.dp).focusRequester(focus), shape = SmallShape,
                trailingIcon = { if (q.isNotEmpty()) IconButton(onClick = { q = "" }) { Icon(Icons.Outlined.Close, "Clear") } },
                keyboardActions = androidx.compose.foundation.text.KeyboardActions(onSearch = { vm.addSearch(q) }),
                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(imeAction = androidx.compose.ui.text.input.ImeAction.Search))
        }
        if (q.isBlank()) {
            if (searches.isEmpty()) EmptyState(Icons.Outlined.Search, "Local search", "Phone ke videos filename se dhundo. Internet ki zaroorat nahi.")
            else {
                SectionHeader("Recent searches", "Clear") { vm.clearSearches() }
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
                item { Chip("< 5 min", dur == 1) { dur = if (dur == 1) 0 else 1 } }
                item { Chip("5-30 min", dur == 2) { dur = if (dur == 2) 0 else 2 } }
                item { Chip("> 30 min", dur == 3) { dur = if (dur == 3) 0 else 3 } }
                items(folders) { f -> Chip(f.name, folderFilter == f.name) { folderFilter = if (folderFilter == f.name) null else f.name } }
            }
            if (results.isEmpty()) EmptyState(Icons.Outlined.SearchOff, "Kuch nahi mila", "\"$q\" se match karta koi video nahi hai.")
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
        ScreenTitle("History", onBack) {
            if (rows.isNotEmpty()) IconButton(onClick = { confirm = true }) { Icon(Icons.Outlined.DeleteSweep, "History saaf karo") }
        }
        if (rows.isEmpty()) EmptyState(Icons.Outlined.History, "History khaali hai", "Jo videos tum dekhoge yahan dikhenge.")
        else LazyColumn {
            items(rows, key = { it.first.videoId }) { (h, v) ->
                VideoListItem(v, h, v.id in favs, false, false, "", onClick = { onPlay(list, list.indexOf(v)) }, onLong = { target = listOf(v) },
                    trailing = {
                        Column(horizontalAlignment = Alignment.End) {
                            Text(DateFormat.getDateInstance(DateFormat.SHORT).format(Date(h.lastPlayed)), color = Muted, style = MaterialTheme.typography.labelSmall)
                            IconButton(onClick = { vm.removeHistory(v.id) }) { Icon(Icons.Outlined.Close, "History se hatao") }
                        }
                    })
            }
        }
    }
    if (confirm) ConfirmDialog("Poori history saaf karein?", "Sirf history list saaf hogi. Videos phone mein rahenge.", "Saaf karo", danger = true,
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
        ScreenTitle("Favorites", onBack) {
            IconButton(onClick = { byName = !byName }) { Icon(Icons.Outlined.SortByAlpha, if (byName) "Naam se sort (on)" else "Naam se sort") }
        }
        OutlinedTextField(q, { q = it }, singleLine = true, placeholder = { Text("Favorites mein dhundo") }, leadingIcon = { Icon(Icons.Outlined.Search, null) },
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp), shape = SmallShape)
        if (list.isNotEmpty()) Row(Modifier.padding(horizontal = 16.dp, vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { onPlay(list, 0, false) }, shape = SmallShape) { Icon(Icons.Outlined.PlayArrow, null); Text("Play all") }
            OutlinedButton(onClick = { onPlay(list, 0, true) }, shape = SmallShape) { Icon(Icons.Outlined.Shuffle, null); Text("Shuffle") }
        }
        if (list.isEmpty()) EmptyState(Icons.Outlined.FavoriteBorder, "Koi favorite nahi", "Kisi video par long-press karke Favorites mein jodo.")
        else LazyColumn {
            items(list, key = { it.id }) { v ->
                VideoListItem(v, progress[v.id], true, false, false, q, onClick = { onPlay(list, list.indexOf(v), false) }, onLong = { target = listOf(v) },
                    trailing = { IconButton(onClick = { vm.toggleFavorite(v) }) { Icon(Icons.Outlined.Favorite, "Favorites se hatao", tint = Color(0xFFFF5C8A)) } })
            }
        }
    }
    VideoActions(vm, target, { target = null }) { v -> onPlay(list, list.indexOf(v).coerceAtLeast(0), false) }
}

package com.example.universalplayer.ui

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed as gridItemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.runtime.saveable.rememberSaveable
import com.example.universalplayer.data.EpgStore
import com.example.universalplayer.data.FavoriteStore
import com.example.universalplayer.data.HistoryItem
import kotlinx.coroutines.delay
import com.example.universalplayer.data.M3u
import com.example.universalplayer.data.Playlist
import com.example.universalplayer.data.PlaylistStore
import com.example.universalplayer.normalizeUrl
import kotlinx.coroutines.launch

@Composable
fun PlaylistScreen(
    openId: String?, onOpen: (String?) -> Unit,
    query: String, onQuery: (String) -> Unit, listState: LazyListState,
    onPlay: (List<HistoryItem>, Int) -> Unit
) {
    val lists by PlaylistStore.playlists.collectAsState()
    val open = lists.firstOrNull { it.id == openId }
    if (open == null) {
        PlaylistsHome(lists) { onOpen(it) }
    } else {
        BackHandler { onOpen(null) }
        PlaylistDetail(open, query, onQuery, listState, onBack = { onOpen(null) }, onPlay = onPlay)
    }
}

// ------------------------------------------------------------------ all playlists
@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
private fun PlaylistsHome(lists: List<Playlist>, onOpen: (String) -> Unit) {
    var showCreate by remember { mutableStateOf(false) }
    var target by remember { mutableStateOf<Playlist?>(null) }

    Scaffold(
        topBar = { TopAppBar(title = { Text("Playlist") }) },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { showCreate = true }, icon = { Icon(Icons.Rounded.Add, null) },
                text = { Text("New playlist") }
            )
        },
        contentWindowInsets = WindowInsets(0, 0, 0, 0)
    ) { pad ->
        if (lists.isEmpty()) {
            Box(Modifier.padding(pad)) {
                EmptyState(
                    Icons.Rounded.VideoLibrary, "No playlists yet",
                    "Create a playlist and import an M3U link or add videos one by one"
                )
            }
        } else {
            LazyColumn(Modifier.padding(pad).fillMaxSize(), contentPadding = PaddingValues(bottom = 88.dp)) {
                items(lists, key = { it.id }) { pl ->
                    Row(
                        Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.background)
                            .longPressable({ onOpen(pl.id) }, { target = pl })
                            .padding(horizontal = 16.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Thumb(pl.items.firstOrNull()?.thumb, Modifier.width(96.dp), fit = true)
                        Spacer(Modifier.width(14.dp))
                        Column(Modifier.weight(1f)) {
                            Text(pl.name, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.titleMedium)
                            Text(
                                "${pl.items.size} videos", style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Icon(Icons.Rounded.ChevronRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
    if (showCreate) CreatePlaylistDialog { showCreate = false }
    target?.let { t ->
        ItemMenuHost(
            name = t.name,
            removeText = "\"${t.name}\" and its ${t.items.size} videos will be removed.",
            onDismiss = { target = null },
            onRename = { PlaylistStore.rename(t.id, it) },
            onRemove = { PlaylistStore.delete(t.id) }
        )
    }
}

// ------------------------------------------------------------------ one playlist
@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
private fun PlaylistDetail(
    pl: Playlist, query: String, onQuery: (String) -> Unit, listState: LazyListState,
    onBack: () -> Unit, onPlay: (List<HistoryItem>, Int) -> Unit
) {
    val favs by FavoriteStore.urls.collectAsState()
    var group by rememberSaveable { mutableStateOf("") } // "" = all, "*" = favorites
    var grid by remember { mutableStateOf(com.example.universalplayer.player.PlayerSettings.playlistGrid) }
    val groups = remember(pl.items) { pl.items.map { it.group }.filter { it.isNotBlank() }.distinct() }
    if (group.isNotEmpty() && group != "*" && group !in groups) group = ""
    val shown = remember(pl.items, query, group, favs) {
        pl.items.filter {
            (query.isBlank() || it.title.contains(query, true)) &&
                    (group.isEmpty() || (group == "*" && it.url in favs) || it.group == group)
        }
    }
    val tvgIds = remember(pl.items) { pl.items.map { it.tvgId }.filter { it.isNotBlank() }.toSet() }
    val epgStatus by EpgStore.status.collectAsState()
    val epgData by EpgStore.data.collectAsState()
    var tick by remember { mutableStateOf(System.currentTimeMillis()) }
    LaunchedEffect(pl.epgUrl, tvgIds) {
        if (pl.epgUrl.isNotBlank() && tvgIds.isNotEmpty()) EpgStore.load(pl.epgUrl, tvgIds)
    }
    LaunchedEffect(Unit) { while (true) { delay(60_000); tick = System.currentTimeMillis() } }
    val gridState = rememberLazyGridState()
    val scope = rememberCoroutineScope()
    var menu by remember { mutableStateOf(false) }
    var dialog by remember { mutableStateOf(0) } // 1 rename, 2 add link, 3 import, 4 delete, 5 guide link
    var target by remember { mutableStateOf<HistoryItem?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(pl.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back") } },
                actions = {
                    IconButton(onClick = { grid = !grid; com.example.universalplayer.player.PlayerSettings.playlistGrid = grid }) {
                        Icon(if (grid) Icons.Rounded.ViewList else Icons.Rounded.GridView, "Grid / list")
                    }
                    if (pl.items.isNotEmpty()) {
                        IconButton(onClick = { onPlay(pl.items.shuffled(), 0) }) { Icon(Icons.Rounded.Shuffle, "Shuffle play") }
                        IconButton(onClick = { onPlay(shown, 0) }) { Icon(Icons.Rounded.PlayArrow, "Play all") }
                    }
                    Box {
                        IconButton(onClick = { menu = true }) { Icon(Icons.Rounded.MoreVert, "More") }
                        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                            DropdownMenuItem(text = { Text("Rename") }, onClick = { menu = false; dialog = 1 })
                            DropdownMenuItem(text = { Text("Add link") }, onClick = { menu = false; dialog = 2 })
                            DropdownMenuItem(text = { Text("Import M3U link") }, onClick = { menu = false; dialog = 3 })
                            DropdownMenuItem(text = { Text("Channel guide (EPG) link") }, onClick = { menu = false; dialog = 5 })
                            if (pl.epgUrl.isNotBlank()) {
                                DropdownMenuItem(text = { Text("Refresh guide") }, onClick = {
                                    menu = false
                                    scope.launch { EpgStore.load(pl.epgUrl, tvgIds, force = true) }
                                })
                            }
                            DropdownMenuItem(text = { Text("Delete playlist") }, onClick = { menu = false; dialog = 4 })
                        }
                    }
                }
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { dialog = 2 }, icon = { Icon(Icons.Rounded.Add, null) }, text = { Text("Add link") }
            )
        },
        contentWindowInsets = WindowInsets(0, 0, 0, 0)
    ) { pad ->
        Column(Modifier.padding(pad).fillMaxSize()) {
            OutlinedTextField(
                query, onQuery,
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                placeholder = { Text("Search channels") },
                leadingIcon = { Icon(Icons.Rounded.Search, null) },
                trailingIcon = {
                    if (query.isNotEmpty()) IconButton(onClick = { onQuery("") }) { Icon(Icons.Rounded.Close, "Clear") }
                },
                singleLine = true, shape = RoundedCornerShape(28.dp)
            )
            if (groups.isNotEmpty() || favs.isNotEmpty()) {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.padding(bottom = 6.dp)
                ) {
                    item { FilterChip(group.isEmpty(), { group = "" }, label = { Text("All") }) }
                    item {
                        FilterChip(
                            group == "*", { group = "*" }, label = { Text("Favorites") },
                            leadingIcon = { Icon(Icons.Rounded.Star, null, Modifier.size(16.dp)) }
                        )
                    }
                    items(groups.size) { gi ->
                        val g = groups[gi]
                        FilterChip(group == g, { group = g }, label = { Text(g, maxLines = 1) })
                    }
                }
            }
            if (pl.epgUrl.isNotBlank() && epgStatus.isNotBlank() && epgData.isEmpty()) {
                Text(
                    epgStatus, style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 2.dp)
                )
            }
            if (shown.isEmpty()) {
                EmptyState(
                    Icons.Rounded.VideoLibrary,
                    if (pl.items.isEmpty()) "This playlist is empty" else "No results",
                    if (pl.items.isEmpty()) "Add a link or import an M3U playlist from the menu" else "Try a different search or category"
                )
            } else if (grid) {
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(130.dp), state = gridState,
                    contentPadding = PaddingValues(start = 12.dp, end = 12.dp, bottom = 88.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    gridItemsIndexed(shown, key = { _, item -> item.url }) { i, h ->
                        Column(
                            Modifier.clip(RoundedCornerShape(12.dp)).longPressable({ onPlay(shown, i) }, { target = h })
                        ) {
                            Box {
                                Thumb(h.thumb, Modifier.fillMaxWidth(), h.progress(), fit = true)
                                IconButton(
                                    onClick = { FavoriteStore.toggle(h.url) },
                                    modifier = Modifier.align(Alignment.TopEnd).size(34.dp)
                                ) {
                                    Icon(
                                        if (h.url in favs) Icons.Rounded.Star else Icons.Rounded.StarBorder, "Favorite",
                                        tint = if (h.url in favs) androidx.compose.ui.graphics.Color(0xFFFFC107) else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                            Text(h.title, maxLines = 2, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyMedium)
                            nowText(h, tick, epgData)?.let {
                                Text(
                                    it, maxLines = 1, overflow = TextOverflow.Ellipsis,
                                    style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }
                }
            } else {
                LazyColumn(Modifier.fillMaxSize(), state = listState, contentPadding = PaddingValues(bottom = 88.dp)) {
                    itemsIndexed(shown, key = { _, item -> item.url }) { i, h ->
                        Row(
                            Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.background)
                                .longPressable({ onPlay(shown, i) }, { target = h })
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Thumb(h.thumb, Modifier.width(96.dp), h.progress(), fit = true)
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(h.title, maxLines = 2, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyLarge)
                                val now = nowText(h, tick, epgData)
                                if (now != null) {
                                    Text(
                                        now, maxLines = 1, overflow = TextOverflow.Ellipsis,
                                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary
                                    )
                                } else {
                                    Text(
                                        if (h.group.isNotBlank()) h.group else h.url, maxLines = 1, overflow = TextOverflow.Ellipsis,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                            IconButton(onClick = { FavoriteStore.toggle(h.url) }) {
                                Icon(
                                    if (h.url in favs) Icons.Rounded.Star else Icons.Rounded.StarBorder, "Favorite",
                                    tint = if (h.url in favs) androidx.compose.ui.graphics.Color(0xFFFFC107) else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    target?.let { t ->
        ItemMenuHost(
            name = t.title,
            removeText = "This video will be removed from \"${pl.name}\".",
            onDismiss = { target = null },
            onRename = { PlaylistStore.renameItem(pl.id, t.url, it) },
            onRemove = { PlaylistStore.removeItem(pl.id, t.url) }
        )
    }

    when (dialog) {
        1 -> TextDialog("Rename playlist", "Playlist name", pl.name, "Save", { dialog = 0 }) {
            PlaylistStore.rename(pl.id, it); dialog = 0
        }
        2 -> TextDialog("Add link", "Video link", "", "Add", { dialog = 0 }, validate = { normalizeUrl(it) != null },
            error = "Enter a valid video link") {
            val u = normalizeUrl(it)!!
            PlaylistStore.addItem(pl.id, HistoryItem(u, u, null, System.currentTimeMillis()))
            dialog = 0
        }
        3 -> ImportDialog(pl.id) { dialog = 0 }
        5 -> TextDialog("Channel guide (EPG)", "XMLTV link (.xml / .xml.gz)", pl.epgUrl, "Save", { dialog = 0 }) {
            PlaylistStore.setEpg(pl.id, it); dialog = 0
        }
        4 -> CompactDialog(
            onDismissRequest = { dialog = 0 },
            title = { Text("Delete playlist?") },
            text = { Text("\"${pl.name}\" and its ${pl.items.size} videos will be removed.") },
            confirmButton = { TextButton(onClick = { PlaylistStore.delete(pl.id); dialog = 0 }) { Text("Delete") } },
            dismissButton = { TextButton(onClick = { dialog = 0 }) { Text("Cancel") } }
        )
    }
}

/** "Now: <title>  ·  Next: <title>" for a channel, or null when no guide data. */
private fun nowText(h: HistoryItem, tick: Long, data: Map<String, List<com.example.universalplayer.data.Programme>>): String? {
    if (h.tvgId.isBlank() || data.isEmpty()) return null
    val (cur, nxt) = EpgStore.nowNext(h.tvgId, tick)
    return when {
        cur != null && nxt != null -> "Now: ${cur.title}  ·  Next: ${nxt.title}"
        cur != null -> "Now: ${cur.title}"
        nxt != null -> "Next: ${nxt.title}"
        else -> null
    }
}

// ------------------------------------------------------------------ dialogs
@Composable
private fun CreatePlaylistDialog(onDismiss: () -> Unit) {
    var name by remember { mutableStateOf("") }
    var url by remember { mutableStateOf("") }
    var fileName by remember { mutableStateOf<String?>(null) }
    var fileText by remember { mutableStateOf<String?>(null) }
    var err by remember { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val ctx = LocalContext.current

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            scope.launch {
                val text = withContext(Dispatchers.IO) {
                    runCatching { ctx.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() } }.getOrNull()
                }
                val label = withContext(Dispatchers.IO) {
                    runCatching {
                        ctx.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use {
                            if (it.moveToFirst()) it.getString(0) else null
                        }
                    }.getOrNull()
                }
                if (text.isNullOrBlank()) {
                    err = "Could not read this file"
                } else {
                    fileText = text
                    fileName = label ?: "Playlist file"
                    err = null
                }
            }
        }
    }

    CompactDialog(
        onDismissRequest = { if (!loading) onDismiss() },
        title = { Text("New playlist") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    name, { name = it; err = null }, singleLine = true, enabled = !loading,
                    label = { Text("Playlist name") }
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = fileName ?: url,
                        onValueChange = { if (fileName == null) { url = it; err = null } },
                        singleLine = true,
                        enabled = !loading && fileName == null,
                        label = { Text(if (fileName != null) "Playlist file" else "M3U playlist link") },
                        trailingIcon = {
                            if (fileName != null) {
                                IconButton(onClick = { fileName = null; fileText = null }) { Icon(Icons.Rounded.Close, "Remove file") }
                            }
                        },
                        modifier = Modifier.weight(1f)
                    )
                    // only one source works: link OR file
                    IconButton(
                        enabled = !loading && url.isBlank() && fileName == null,
                        onClick = { picker.launch(arrayOf("*/*")) }
                    ) { Icon(Icons.Rounded.FolderOpen, "Pick file") }
                }
                if (loading) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.width(10.dp))
                        Text("Importing playlist...")
                    }
                }
                err?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
            }
        },
        confirmButton = {
            TextButton(enabled = !loading, onClick = {
                if (name.isBlank()) { err = "Enter a playlist name"; return@TextButton }
                val text = fileText
                if (text != null) {
                    val res = M3u.parseFull(text)
                    if (res.items.isEmpty()) { err = "No videos found in this file"; return@TextButton }
                    PlaylistStore.create(name, res.items, res.epgUrl)
                    onDismiss()
                    return@TextButton
                }
                val link = url.trim()
                if (link.isEmpty()) { PlaylistStore.create(name); onDismiss(); return@TextButton }
                val u = normalizeUrl(link)
                if (u == null) { err = "Enter a valid link"; return@TextButton }
                loading = true
                scope.launch {
                    try {
                        val res = M3u.fetchFull(u)
                        PlaylistStore.create(name, res.items, res.epgUrl)
                        onDismiss()
                    } catch (e: Exception) {
                        err = e.message ?: "Could not import this link"
                        loading = false
                    }
                }
            }) { Text(if (url.isNotBlank() || fileName != null) "Create & import" else "Create") }
        },
        dismissButton = { TextButton(enabled = !loading, onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
private fun ImportDialog(playlistId: String, onDismiss: () -> Unit) {
    var url by remember { mutableStateOf("") }
    var err by remember { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val ctx = LocalContext.current

    CompactDialog(
        onDismissRequest = { if (!loading) onDismiss() },
        title = { Text("Import M3U link") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    url, { url = it; err = null }, singleLine = true, enabled = !loading,
                    label = { Text("M3U playlist link") }
                )
                if (loading) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.width(10.dp))
                        Text("Importing playlist...")
                    }
                }
                err?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
            }
        },
        confirmButton = {
            TextButton(enabled = !loading, onClick = {
                val u = normalizeUrl(url)
                if (u == null) { err = "Enter a valid link"; return@TextButton }
                loading = true
                scope.launch {
                    try {
                        val res = M3u.fetchFull(u)
                        val n = PlaylistStore.addItems(playlistId, res.items)
                        if (res.epgUrl.isNotBlank() && PlaylistStore.playlists.value.firstOrNull { it.id == playlistId }?.epgUrl.isNullOrBlank()) {
                            PlaylistStore.setEpg(playlistId, res.epgUrl)
                        }
                        Toast.makeText(ctx, "Added $n videos", Toast.LENGTH_SHORT).show()
                        onDismiss()
                    } catch (e: Exception) {
                        err = e.message ?: "Could not import this link"
                        loading = false
                    }
                }
            }) { Text("Import") }
        },
        dismissButton = { TextButton(enabled = !loading, onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
fun AddToPlaylistDialog(item: HistoryItem, onDismiss: () -> Unit) {
    val lists by PlaylistStore.playlists.collectAsState()
    val ctx = LocalContext.current
    var newName by remember { mutableStateOf("") }

    CompactDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add to playlist") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Column(Modifier.heightIn(max = 220.dp).verticalScroll(rememberScrollState())) {
                    lists.forEach { pl ->
                        TextButton(
                            onClick = {
                                PlaylistStore.addItem(pl.id, item)
                                Toast.makeText(ctx, "Added to ${pl.name}", Toast.LENGTH_SHORT).show()
                                onDismiss()
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) { Text(pl.name, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.fillMaxWidth()) }
                    }
                }
                OutlinedTextField(
                    newName, { newName = it }, singleLine = true, label = { Text("New playlist name") }
                )
            }
        },
        confirmButton = {
            TextButton(enabled = newName.isNotBlank(), onClick = {
                PlaylistStore.create(newName, listOf(item))
                Toast.makeText(ctx, "Added to $newName", Toast.LENGTH_SHORT).show()
                onDismiss()
            }) { Text("Create & add") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

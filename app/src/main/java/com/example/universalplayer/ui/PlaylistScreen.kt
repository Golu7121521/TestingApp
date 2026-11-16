package com.example.universalplayer.ui

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.universalplayer.data.HistoryItem
import com.example.universalplayer.data.M3u
import com.example.universalplayer.data.Playlist
import com.example.universalplayer.data.PlaylistStore
import com.example.universalplayer.normalizeUrl
import kotlinx.coroutines.launch

@Composable
fun PlaylistScreen(onPlay: (List<HistoryItem>, Int) -> Unit) {
    val lists by PlaylistStore.playlists.collectAsState()
    var openId by remember { mutableStateOf<String?>(null) }
    val open = lists.firstOrNull { it.id == openId }
    if (open == null) {
        PlaylistsHome(lists) { openId = it }
    } else {
        BackHandler { openId = null }
        PlaylistDetail(open, onBack = { openId = null }, onPlay = onPlay)
    }
}

// ------------------------------------------------------------------ all playlists
@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
private fun PlaylistsHome(lists: List<Playlist>, onOpen: (String) -> Unit) {
    var showCreate by remember { mutableStateOf(false) }
    val snack = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    Scaffold(
        topBar = { TopAppBar(title = { Text("Playlist") }) },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { showCreate = true }, icon = { Icon(Icons.Rounded.Add, null) },
                text = { Text("New playlist") }
            )
        },
        snackbarHost = { SnackbarHost(snack) },
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
                    SwipeRow(
                        onDelete = {
                            PlaylistStore.delete(pl.id)
                            scope.launch {
                                val r = snack.showSnackbar("Playlist deleted", "Undo", duration = SnackbarDuration.Short)
                                if (r == SnackbarResult.ActionPerformed) PlaylistStore.restore(pl)
                            }
                        },
                        modifier = Modifier.animateItemPlacement()
                    ) {
                        Row(
                            Modifier.fillMaxWidth().clickable { onOpen(pl.id) }
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
    }
    if (showCreate) CreatePlaylistDialog { showCreate = false }
}

// ------------------------------------------------------------------ one playlist
@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
private fun PlaylistDetail(pl: Playlist, onBack: () -> Unit, onPlay: (List<HistoryItem>, Int) -> Unit) {
    var query by remember { mutableStateOf("") }
    val shown = remember(pl.items, query) {
        if (query.isBlank()) pl.items else pl.items.filter { it.title.contains(query, true) }
    }
    var menu by remember { mutableStateOf(false) }
    var dialog by remember { mutableStateOf(0) } // 1 rename, 2 add link, 3 import, 4 delete
    val snack = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(pl.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back") } },
                actions = {
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
        snackbarHost = { SnackbarHost(snack) },
        contentWindowInsets = WindowInsets(0, 0, 0, 0)
    ) { pad ->
        Column(Modifier.padding(pad).fillMaxSize()) {
            if (pl.items.size > 8) {
                OutlinedTextField(
                    query, { query = it },
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                    placeholder = { Text("Search in playlist") },
                    leadingIcon = { Icon(Icons.Rounded.Search, null) },
                    trailingIcon = {
                        if (query.isNotEmpty()) IconButton(onClick = { query = "" }) { Icon(Icons.Rounded.Close, "Clear") }
                    },
                    singleLine = true, shape = RoundedCornerShape(28.dp)
                )
            }
            if (shown.isEmpty()) {
                EmptyState(
                    Icons.Rounded.VideoLibrary,
                    if (pl.items.isEmpty()) "This playlist is empty" else "No results",
                    if (pl.items.isEmpty()) "Add a link or import an M3U playlist from the menu" else "Try a different search"
                )
            } else {
                LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 88.dp)) {
                    itemsIndexed(shown, key = { _, item -> item.url }) { i, h ->
                        SwipeRow(
                            onDelete = {
                                PlaylistStore.removeItem(pl.id, h.url)
                                scope.launch {
                                    val r = snack.showSnackbar("Removed", "Undo", duration = SnackbarDuration.Short)
                                    if (r == SnackbarResult.ActionPerformed) PlaylistStore.addItem(pl.id, h)
                                }
                            },
                            modifier = Modifier.animateItemPlacement()
                        ) {
                            Row(
                                Modifier.fillMaxWidth().clickable { onPlay(shown, i) }
                                    .padding(horizontal = 12.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Thumb(h.thumb, Modifier.width(96.dp), h.progress(), fit = true)
                                Spacer(Modifier.width(12.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(h.title, maxLines = 2, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyLarge)
                                    Text(
                                        h.url, maxLines = 1, overflow = TextOverflow.Ellipsis,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
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
        4 -> AlertDialog(
            onDismissRequest = { dialog = 0 },
            title = { Text("Delete playlist?") },
            text = { Text("\"${pl.name}\" and its ${pl.items.size} videos will be removed.") },
            confirmButton = { TextButton(onClick = { PlaylistStore.delete(pl.id); dialog = 0 }) { Text("Delete") } },
            dismissButton = { TextButton(onClick = { dialog = 0 }) { Text("Cancel") } }
        )
    }
}

// ------------------------------------------------------------------ dialogs
@Composable
private fun TextDialog(
    title: String, label: String, initial: String, confirm: String,
    onDismiss: () -> Unit,
    validate: (String) -> Boolean = { it.isNotBlank() },
    error: String = "Enter a value",
    onConfirm: (String) -> Unit
) {
    var text by remember { mutableStateOf(initial) }
    var err by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                text, { text = it; err = false }, singleLine = true, label = { Text(label) },
                isError = err, supportingText = { if (err) Text(error) }
            )
        },
        confirmButton = {
            TextButton(onClick = { if (validate(text)) onConfirm(text) else err = true }) { Text(confirm) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
private fun CreatePlaylistDialog(onDismiss: () -> Unit) {
    var name by remember { mutableStateOf("") }
    var url by remember { mutableStateOf("") }
    var err by remember { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    AlertDialog(
        onDismissRequest = { if (!loading) onDismiss() },
        title = { Text("New playlist") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    name, { name = it; err = null }, singleLine = true, enabled = !loading,
                    label = { Text("Playlist name") }
                )
                OutlinedTextField(
                    url, { url = it; err = null }, singleLine = true, enabled = !loading,
                    label = { Text("M3U playlist link (optional)") }
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
                if (name.isBlank()) { err = "Enter a playlist name"; return@TextButton }
                val link = url.trim()
                if (link.isEmpty()) { PlaylistStore.create(name); onDismiss(); return@TextButton }
                val u = normalizeUrl(link)
                if (u == null) { err = "Enter a valid link"; return@TextButton }
                loading = true
                scope.launch {
                    try {
                        val items = M3u.fetch(u)
                        PlaylistStore.create(name, items)
                        onDismiss()
                    } catch (e: Exception) {
                        err = e.message ?: "Could not import this link"
                        loading = false
                    }
                }
            }) { Text(if (url.isBlank()) "Create" else "Create & import") }
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

    AlertDialog(
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
                        val n = PlaylistStore.addItems(playlistId, M3u.fetch(u))
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

    AlertDialog(
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

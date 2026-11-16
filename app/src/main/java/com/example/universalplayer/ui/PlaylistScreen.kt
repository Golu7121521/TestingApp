package com.example.universalplayer.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material.icons.rounded.VideoLibrary
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.universalplayer.data.HistoryItem
import com.example.universalplayer.data.PlaylistStore
import com.example.universalplayer.normalizeUrl
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class, androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun PlaylistScreen(onPlay: (List<HistoryItem>, Int) -> Unit) {
    val items by PlaylistStore.items.collectAsState()
    var showAdd by remember { mutableStateOf(false) }
    var confirm by remember { mutableStateOf(false) }
    val snack = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("Playlist") },
                actions = {
                    if (items.isNotEmpty()) {
                        IconButton(onClick = { onPlay(items.shuffled(), 0) }) { Icon(Icons.Rounded.Shuffle, "Shuffle play") }
                        IconButton(onClick = { onPlay(items, 0) }) { Icon(Icons.Rounded.PlayArrow, "Play all") }
                        IconButton(onClick = { confirm = true }) { Icon(Icons.Rounded.DeleteSweep, "Clear playlist") }
                    }
                }
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { showAdd = true }, icon = { Icon(Icons.Rounded.Add, null) }, text = { Text("Add link") }
            )
        },
        snackbarHost = { SnackbarHost(snack) },
        contentWindowInsets = WindowInsets(0, 0, 0, 0)
    ) { pad ->
        if (items.isEmpty()) {
            Box(Modifier.padding(pad)) {
                EmptyState(Icons.Rounded.VideoLibrary, "Your playlist is empty", "Add links or use the playlist button in History")
            }
        } else {
            LazyColumn(Modifier.padding(pad).fillMaxSize(), contentPadding = PaddingValues(bottom = 88.dp)) {
                itemsIndexed(items, key = { _, item -> item.url }) { i, h ->
                    SwipeRow(
                        onDelete = {
                            PlaylistStore.remove(h.url)
                            scope.launch {
                                val r = snack.showSnackbar("Removed from playlist", "Undo", duration = SnackbarDuration.Short)
                                if (r == SnackbarResult.ActionPerformed) PlaylistStore.add(h)
                            }
                        },
                        modifier = Modifier.animateItemPlacement()
                    ) {
                        Row(
                            Modifier.fillMaxWidth().clickable { onPlay(items, i) }
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                "${i + 1}", style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.width(24.dp)
                            )
                            Thumb(h.thumb, progress = h.progress())
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(
                                    h.title, maxLines = 2, overflow = TextOverflow.Ellipsis,
                                    style = MaterialTheme.typography.bodyLarge
                                )
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

    if (showAdd) {
        var text by remember { mutableStateOf("") }
        var err by remember { mutableStateOf(false) }
        AlertDialog(
            onDismissRequest = { showAdd = false },
            title = { Text("Add to playlist") },
            text = {
                OutlinedTextField(
                    text, { text = it; err = false }, singleLine = true, label = { Text("Video link") },
                    isError = err, supportingText = { if (err) Text("Enter a valid video link") }
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    val u = normalizeUrl(text)
                    if (u == null) err = true else {
                        PlaylistStore.add(HistoryItem(u, u, null, System.currentTimeMillis()))
                        showAdd = false
                    }
                }) { Text("Add") }
            },
            dismissButton = { TextButton(onClick = { showAdd = false }) { Text("Cancel") } }
        )
    }
    if (confirm) {
        AlertDialog(
            onDismissRequest = { confirm = false },
            title = { Text("Clear playlist?") },
            text = { Text("All videos will be removed from the playlist.") },
            confirmButton = { TextButton(onClick = { PlaylistStore.clear(); confirm = false }) { Text("Clear") } },
            dismissButton = { TextButton(onClick = { confirm = false }) { Text("Cancel") } }
        )
    }
}

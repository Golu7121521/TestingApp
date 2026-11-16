package com.universal.videoplayer.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PlaylistPlay
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.Column
import com.universal.videoplayer.player.QueueItem
import com.universal.videoplayer.resolver.PlaybackRequest
import com.universal.videoplayer.utils.fmtTime

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlaylistScreen(vm: AppViewModel, actions: Actions) {
    val lists by vm.playlists.collectAsStateWithLifecycleCompat()
    val allItems by vm.items.collectAsStateWithLifecycleCompat()
    var selected by rememberSaveable { mutableStateOf<Long?>(null) }
    var creating by remember { mutableStateOf(false) }
    var renaming by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf(false) }
    val current = lists.firstOrNull { it.id == selected }
    if (selected != null && current == null) selected = null
    BackHandler(enabled = current != null) { selected = null }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(current?.name ?: "Playlists") },
                navigationIcon = {
                    if (current != null) IconButton({ selected = null }) { Icon(Icons.Filled.ArrowBack, "Back") }
                },
                actions = {
                    if (current == null) IconButton({ creating = true }) { Icon(Icons.Filled.Add, "Create playlist") }
                    else {
                        IconButton({ renaming = true }) { Icon(Icons.Filled.Edit, "Rename playlist") }
                        IconButton({ deleting = true }) { Icon(Icons.Filled.Delete, "Delete playlist") }
                    }
                },
            )
        },
    ) { pad ->
        if (current == null) {
            if (lists.isEmpty()) {
                EmptyState(
                    Icons.Filled.PlaylistPlay, "No playlists", "Create a playlist to group videos and play them in order.",
                    Modifier.padding(pad),
                ) { Button({ creating = true }) { Text("Create Playlist") } }
            } else {
                LazyColumn(Modifier.fillMaxSize().padding(pad), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(lists, key = { it.id }) { p ->
                        val count = allItems.count { it.playlistId == p.id }
                        Card(Modifier.fillMaxWidth().clickable { selected = p.id }) {
                            Column(Modifier.padding(16.dp)) {
                                Text(p.name, style = MaterialTheme.typography.titleMedium)
                                Text("$count video${if (count == 1) "" else "s"}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }
        } else {
            val entries = allItems.filter { it.playlistId == current.id }
            Column(Modifier.fillMaxSize().padding(pad)) {
                Button(
                    { actions.playQueue(entries.map { QueueItem(it.url, it.title) }, 0) },
                    Modifier.fillMaxWidth().padding(16.dp), enabled = entries.isNotEmpty(),
                ) { Icon(Icons.Filled.PlayArrow, null); Text("Play all") }
                if (entries.isEmpty()) {
                    EmptyState(Icons.Filled.PlaylistPlay, "Empty playlist", "Add videos from History using the item menu.")
                } else {
                    LazyColumn(contentPadding = PaddingValues(horizontal = 12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        items(entries.size, key = { entries[it].id }) { i ->
                            val e = entries[i]
                            VideoRow(
                                thumb = e.thumbPath, title = e.title,
                                meta = e.domain + if (e.durationMs > 0) " • " + fmtTime(e.durationMs) else "",
                                progress = null,
                                onClick = { actions.playQueue(entries.map { x -> QueueItem(x.url, x.title) }, i) },
                                trailing = {
                                    Row {
                                        IconButton({ vm.move(e, true) }, enabled = i > 0) { Icon(Icons.Filled.ArrowUpward, "Move up") }
                                        IconButton({ vm.move(e, false) }, enabled = i < entries.lastIndex) { Icon(Icons.Filled.ArrowDownward, "Move down") }
                                        IconButton({ vm.removeItem(e.id) }) { Icon(Icons.Filled.Close, "Remove from playlist") }
                                    }
                                },
                            )
                        }
                    }
                }
            }
        }
    }

    if (creating) TextInputDialog("New playlist", "", "Create", { vm.createPlaylist(it) }) { creating = false }
    if (renaming && current != null) TextInputDialog("Rename playlist", current.name, "Rename", { vm.renamePlaylist(current.id, it) }) { renaming = false }
    if (deleting && current != null) ConfirmDialog("Delete playlist?", "\"${current.name}\" and its list of videos will be removed.", "Delete", { vm.deletePlaylist(current.id) }) { deleting = false }
}

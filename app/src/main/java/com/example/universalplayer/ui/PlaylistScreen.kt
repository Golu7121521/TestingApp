package com.example.universalplayer.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.universalplayer.data.PlaylistStore
import com.example.universalplayer.normalizeUrl

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlaylistScreen(onPlay: (String) -> Unit) {
    val items by PlaylistStore.items.collectAsState()
    var newLink by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }

    fun add() {
        val u = normalizeUrl(newLink)
        if (u == null) error = "Enter a valid video link"
        else {
            PlaylistStore.add(u)
            newLink = ""
            error = null
        }
    }

    Scaffold(topBar = { CenterAlignedTopAppBar(title = { Text("Playlist") }) }) { pad ->
        Column(Modifier.padding(pad).fillMaxSize()) {
            Row(
                Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.Top
            ) {
                OutlinedTextField(
                    value = newLink,
                    onValueChange = { newLink = it; error = null },
                    modifier = Modifier.weight(1f),
                    label = { Text("Video link") },
                    singleLine = true,
                    isError = error != null,
                    supportingText = { error?.let { Text(it) } }
                )
                Spacer(Modifier.width(8.dp))
                FilledIconButton(onClick = { add() }, enabled = newLink.isNotBlank(), modifier = Modifier.padding(top = 8.dp)) {
                    Icon(Icons.Rounded.Add, "Add to playlist")
                }
            }

            if (items.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("Playlist is empty", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            } else {
                LazyColumn(Modifier.fillMaxSize()) {
                    items(items, key = { it.url }) { item ->
                        Row(
                            Modifier.fillMaxWidth().clickable { onPlay(item.url) }
                                .padding(horizontal = 16.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Rounded.PlayArrow, null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Spacer(Modifier.width(12.dp))
                            Text(
                                item.url,
                                modifier = Modifier.weight(1f),
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                                style = MaterialTheme.typography.bodyLarge
                            )
                            IconButton(onClick = { PlaylistStore.remove(item.url) }) {
                                Icon(Icons.Rounded.Delete, "Remove")
                            }
                        }
                    }
                }
            }
        }
    }
}

package com.example.universalplayer.ui

import android.text.format.DateUtils
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.universalplayer.data.HistoryStore

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(onPlay: (String) -> Unit) {
    val items by HistoryStore.items.collectAsState()
    var confirm by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("History") },
                actions = {
                    if (items.isNotEmpty()) {
                        IconButton(onClick = { confirm = true }) { Icon(Icons.Rounded.DeleteSweep, "Clear all") }
                    }
                }
            )
        }
    ) { pad ->
        if (items.isEmpty()) {
            Box(Modifier.padding(pad).fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("No history yet", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            LazyColumn(Modifier.padding(pad).fillMaxSize()) {
                items(items, key = { it.url }) { h ->
                    Row(
                        Modifier.fillMaxWidth().clickable { onPlay(h.url) }.padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            Modifier.width(140.dp).aspectRatio(16f / 9f)
                                .clip(RoundedCornerShape(8.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant),
                            contentAlignment = Alignment.Center
                        ) {
                            if (h.thumb != null) {
                                AsyncImage(
                                    model = h.thumb, contentDescription = null,
                                    contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize()
                                )
                            } else {
                                Icon(Icons.Rounded.PlayArrow, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(h.title, maxLines = 2, overflow = TextOverflow.Ellipsis,
                                style = MaterialTheme.typography.bodyLarge)
                            Text(h.url, maxLines = 1, overflow = TextOverflow.Ellipsis,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(
                                DateUtils.getRelativeTimeSpanString(h.time).toString(),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        IconButton(onClick = { HistoryStore.remove(h.url) }) {
                            Icon(Icons.Rounded.Delete, "Delete")
                        }
                    }
                }
            }
        }
    }

    if (confirm) {
        AlertDialog(
            onDismissRequest = { confirm = false },
            title = { Text("Clear history?") },
            text = { Text("All watched videos will be removed from history.") },
            confirmButton = {
                TextButton(onClick = { HistoryStore.clear(); confirm = false }) { Text("Clear") }
            },
            dismissButton = { TextButton(onClick = { confirm = false }) { Text("Cancel") } }
        )
    }
}

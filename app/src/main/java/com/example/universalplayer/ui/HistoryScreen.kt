package com.example.universalplayer.ui

import android.text.format.DateUtils
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.PlaylistAdd
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.universalplayer.data.HistoryItem
import com.example.universalplayer.data.HistoryStore
import com.example.universalplayer.data.PlaylistStore
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class, androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun HistoryScreen(onPlay: (HistoryItem) -> Unit) {
    val all by HistoryStore.items.collectAsState()
    var query by remember { mutableStateOf("") }
    val shown = remember(all, query) {
        if (query.isBlank()) all else all.filter { it.title.contains(query, true) || it.url.contains(query, true) }
    }
    var confirm by remember { mutableStateOf(false) }
    val ctx = LocalContext.current
    val snack = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("History") },
                actions = {
                    if (all.isNotEmpty()) {
                        IconButton(onClick = { confirm = true }) { Icon(Icons.Rounded.DeleteSweep, "Clear all") }
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snack) },
        contentWindowInsets = WindowInsets(0, 0, 0, 0)
    ) { pad ->
        Column(Modifier.padding(pad).fillMaxSize()) {
            if (all.isNotEmpty()) {
                OutlinedTextField(
                    query, { query = it },
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                    placeholder = { Text("Search history") },
                    leadingIcon = { Icon(Icons.Rounded.Search, null) },
                    trailingIcon = {
                        if (query.isNotEmpty()) IconButton(onClick = { query = "" }) { Icon(Icons.Rounded.Close, "Clear") }
                    },
                    singleLine = true, shape = RoundedCornerShape(28.dp)
                )
            }
            if (shown.isEmpty()) {
                EmptyState(
                    Icons.Rounded.History,
                    if (all.isEmpty()) "No history yet" else "No results",
                    if (all.isEmpty()) "Videos you watch will show up here" else "Try a different search"
                )
            } else {
                LazyColumn(Modifier.fillMaxSize()) {
                    items(shown, key = { it.url }) { h ->
                        SwipeRow(
                            onDelete = {
                                HistoryStore.remove(h.url)
                                scope.launch {
                                    val r = snack.showSnackbar("Removed from history", "Undo", duration = SnackbarDuration.Short)
                                    if (r == SnackbarResult.ActionPerformed) HistoryStore.add(h)
                                }
                            },
                            modifier = Modifier.animateItemPlacement()
                        ) {
                            Row(
                                Modifier.fillMaxWidth().clickable { onPlay(h) }
                                    .padding(horizontal = 12.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
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
                                    Text(
                                        DateUtils.getRelativeTimeSpanString(h.time).toString(),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                IconButton(onClick = {
                                    PlaylistStore.add(h)
                                    Toast.makeText(ctx, "Added to playlist", Toast.LENGTH_SHORT).show()
                                }) { Icon(Icons.Rounded.PlaylistAdd, "Add to playlist") }
                            }
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
            confirmButton = { TextButton(onClick = { HistoryStore.clear(); confirm = false }) { Text("Clear") } },
            dismissButton = { TextButton(onClick = { confirm = false }) { Text("Cancel") } }
        )
    }
}

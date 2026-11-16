package com.universal.videoplayer.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.universal.videoplayer.data.HistoryEntity
import com.universal.videoplayer.data.MediaInfo
import com.universal.videoplayer.resolver.PlaybackRequest
import com.universal.videoplayer.utils.fmtDate
import com.universal.videoplayer.utils.fmtTime

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(vm: AppViewModel, actions: Actions) {
    val history by vm.history.collectAsStateWithLifecycleCompat()
    var confirmClear by remember { mutableStateOf(false) }
    var addTo by remember { mutableStateOf<HistoryEntity?>(null) }

    Scaffold(topBar = {
        TopAppBar(title = { Text("History") }, actions = {
            IconButton({ confirmClear = true }, enabled = history.isNotEmpty()) { Icon(Icons.Filled.DeleteSweep, "Clear all history") }
        })
    }) { pad ->
        if (history.isEmpty()) {
            EmptyState(
                Icons.Filled.History, "No history", "Videos you play will appear here so you can pick up where you left off.",
                Modifier.padding(pad),
            )
        } else {
            LazyColumn(
                Modifier.fillMaxSize().padding(pad), contentPadding = PaddingValues(12.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                items(history, key = { it.url }) { h ->
                    var menu by remember { mutableStateOf(false) }
                    VideoRow(
                        thumb = h.thumbPath, title = h.title,
                        meta = buildString {
                            append(h.domain)
                            if (h.durationMs > 0) append(" • ").append(fmtTime(h.durationMs))
                            append("\n").append(fmtDate(h.lastWatched))
                        },
                        progress = if (h.durationMs > 0) h.positionMs.toFloat() / h.durationMs else null,
                        onClick = { actions.play(PlaybackRequest(url = h.url, title = h.title), true) },
                        trailing = {
                            IconButton({ menu = true }) { Icon(Icons.Filled.MoreVert, "More") }
                            DropdownMenu(menu, { menu = false }) {
                                if (h.positionMs > 5_000) DropdownMenuItem({ Text("Continue Watching") }, {
                                    menu = false; actions.play(PlaybackRequest(url = h.url, title = h.title), true)
                                })
                                DropdownMenuItem({ Text("Play Again") }, {
                                    menu = false; actions.play(PlaybackRequest(url = h.url, title = h.title), false)
                                })
                                DropdownMenuItem({ Text("Add to Playlist") }, { menu = false; addTo = h })
                                DropdownMenuItem({ Text("Remove from History") }, { menu = false; vm.removeHistory(h.url) })
                            }
                        },
                    )
                }
            }
        }
    }

    if (confirmClear) ConfirmDialog("Clear all history?", "This removes every entry and its saved position.", "Clear all", { vm.clearHistory() }) { confirmClear = false }
    addTo?.let { h ->
        AddToPlaylistDialog(vm, MediaInfo(h.url, h.title, h.domain, h.durationMs, h.thumbPath)) { addTo = null }
    }
}

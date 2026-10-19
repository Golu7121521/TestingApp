@file:OptIn(ExperimentalMaterial3Api::class)

package com.playnova.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.playnova.app.AppViewModel
import com.playnova.app.data.Video
import com.playnova.app.data.fmtTime

@Composable
fun PlaylistsScreen(vm: AppViewModel, onOpen: (Long) -> Unit, onFavorites: () -> Unit) {
    val lists by vm.playlists.collectAsState()
    val items by vm.playlistItems.collectAsState()
    val videos by vm.videos.collectAsState()
    val favs by vm.favoriteIds.collectAsState()
    var create by remember { mutableStateOf(false) }
    val byId = remember(videos) { videos.associateBy { it.id } }
    Column(Modifier.fillMaxSize()) {
        ScreenTitle("Playlists") { IconButton(onClick = { create = true }) { Icon(Icons.Outlined.Add, "Nayi playlist") } }
        LazyColumn(contentPadding = PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            item {
                Row(Modifier.fillMaxWidth().clip(CardShape).background(MaterialTheme.colorScheme.primaryContainer).clickable(onClick = onFavorites).padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Outlined.Favorite, null, tint = Color(0xFFFF5C8A))
                    Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                        Text("Favorites", style = MaterialTheme.typography.titleMedium)
                        Text("${favs.count { it in byId }} videos | automatic", color = Muted, style = MaterialTheme.typography.bodySmall)
                    }
                    Icon(Icons.Outlined.ChevronRight, null)
                }
            }
            if (lists.isEmpty()) item {
                EmptyState(Icons.Outlined.PlaylistPlay, "Koi playlist nahi", "Apni pasandida videos ki playlist banao.", "Playlist banao") { create = true }
            }
            itemsIndexed(lists, key = { _, p -> p.id }) { _, p ->
                val its = items.filter { it.playlistId == p.id }.mapNotNull { byId[it.videoId] }
                Row(Modifier.fillMaxWidth().clip(CardShape).background(MaterialTheme.colorScheme.surface).clickable { onOpen(p.id) }.padding(8.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    if (its.isNotEmpty()) Thumb(its.first(), Modifier.size(64.dp))
                    else Box(Modifier.size(64.dp).clip(SmallShape).background(MaterialTheme.colorScheme.surfaceVariant), contentAlignment = Alignment.Center) { Icon(Icons.Outlined.PlaylistPlay, null, tint = Muted) }
                    Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                        Text(p.name, style = MaterialTheme.typography.titleMedium, maxLines = 1)
                        Text("${its.size} videos | ${fmtTime(its.sumOf { it.durationMs })}", color = Muted, style = MaterialTheme.typography.bodySmall)
                    }
                    Icon(Icons.Outlined.ChevronRight, null, tint = Muted)
                }
            }
        }
    }
    if (create) NameDialog("Nayi playlist", "", "Banao", onOk = { vm.createPlaylist(it) }, onDismiss = { create = false })
}

@Composable
fun PlaylistDetailScreen(vm: AppViewModel, id: Long, onBack: () -> Unit, onPlay: (List<Video>, Int, Boolean) -> Unit) {
    val lists by vm.playlists.collectAsState()
    val allItems by vm.playlistItems.collectAsState()
    val videos by vm.videos.collectAsState()
    val progress by vm.progress.collectAsState()
    val favs by vm.favoriteIds.collectAsState()
    val pl = lists.firstOrNull { it.id == id }
    var rename by remember { mutableStateOf(false) }
    var delete by remember { mutableStateOf(false) }
    var target by remember { mutableStateOf<List<Video>?>(null) }
    if (pl == null) { LaunchedEffect(Unit) { onBack() }; return }
    val byId = remember(videos) { videos.associateBy { it.id } }
    val rows = allItems.filter { it.playlistId == id }.sortedBy { it.position }.mapNotNull { r -> byId[r.videoId]?.let { r to it } }
    val list = rows.map { it.second }
    Column(Modifier.fillMaxSize()) {
        ScreenTitle(pl.name, onBack) {
            IconButton(onClick = { rename = true }) { Icon(Icons.Outlined.Edit, "Rename") }
            IconButton(onClick = { delete = true }) { Icon(Icons.Outlined.Delete, "Playlist delete karo") }
        }
        Text("${list.size} videos | ${fmtTime(list.sumOf { it.durationMs })}", Modifier.padding(horizontal = 16.dp), color = Muted)
        if (list.isNotEmpty()) Row(Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { onPlay(list, 0, false) }, shape = SmallShape) { Icon(Icons.Outlined.PlayArrow, null); Text("Play all") }
            OutlinedButton(onClick = { onPlay(list, 0, true) }, shape = SmallShape) { Icon(Icons.Outlined.Shuffle, null); Text("Shuffle") }
        }
        if (list.isEmpty()) EmptyState(Icons.Outlined.PlaylistAdd, "Playlist khaali hai", "Library mein kisi video par long-press karke 'Playlist mein jodo' chuno.")
        else LazyColumn {
            itemsIndexed(rows, key = { _, r -> r.first.id }) { idx, (r, v) ->
                VideoListItem(v, progress[v.id], v.id in favs, false, false, "", onClick = { onPlay(list, idx, false) }, onLong = { target = listOf(v) },
                    trailing = {
                        Column {
                            IconButton(onClick = { vm.movePlaylistItem(id, idx, idx - 1) }, enabled = idx > 0, modifier = Modifier.size(36.dp)) { Icon(Icons.Outlined.KeyboardArrowUp, "Upar le jao") }
                            IconButton(onClick = { vm.movePlaylistItem(id, idx, idx + 1) }, enabled = idx < rows.lastIndex, modifier = Modifier.size(36.dp)) { Icon(Icons.Outlined.KeyboardArrowDown, "Neeche le jao") }
                        }
                        IconButton(onClick = { vm.removePlaylistItem(r.id) }) { Icon(Icons.Outlined.RemoveCircleOutline, "Playlist se hatao") }
                    })
            }
        }
    }
    if (rename) NameDialog("Playlist rename", pl.name, "Save", onOk = { vm.renamePlaylist(id, it) }, onDismiss = { rename = false })
    if (delete) ConfirmDialog("\"${pl.name}\" delete karein?", "Sirf playlist delete hogi. Isme jude videos aapke phone mein jaise hain waise rahenge.", "Delete", danger = true,
        onConfirm = { vm.deletePlaylist(id); onBack() }, onDismiss = { delete = false })
    VideoActions(vm, target, { target = null }) { v -> onPlay(list, list.indexOf(v).coerceAtLeast(0), false) }
}

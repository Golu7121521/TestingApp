@file:OptIn(ExperimentalMaterial3Api::class)

package com.playnova.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.playnova.app.AppViewModel
import com.playnova.app.data.Video
import com.playnova.app.i18n.tr

enum class SortKey(val label: String) { NAME("Name"), DATE("Date added"), DURATION("Duration"), SIZE("Size"), PLAYED("Last played") }

fun sortVideos(l: List<Video>, key: SortKey, asc: Boolean, last: Map<Long, Long>): List<Video> {
    val c: Comparator<Video> = when (key) {
        SortKey.NAME -> compareBy { it.title.lowercase() }
        SortKey.DATE -> compareBy { it.dateAdded }
        SortKey.DURATION -> compareBy { it.durationMs }
        SortKey.SIZE -> compareBy { it.size }
        SortKey.PLAYED -> compareBy { last[it.id] ?: 0L }
    }
    return l.sortedWith(if (asc) c else c.reversed())
}

/** All videos (folder == null) or the videos of a single folder. */
@Composable
fun LibraryScreen(
    vm: AppViewModel, folder: String?, onBack: (() -> Unit)?, onPlay: (List<Video>, Int) -> Unit
) {
    val prefs by vm.settings.state.collectAsState()
    val all by vm.videos.collectAsState()
    val loading by vm.loading.collectAsState()
    val scanned by vm.scanned.collectAsState()
    val progress by vm.progress.collectAsState()
    val favs by vm.favoriteIds.collectAsState()
    val folders by vm.folders.collectAsState()

    var grid by remember { mutableStateOf(prefs.gridDefault) }
    var sort by remember { mutableStateOf(SortKey.DATE) }
    var asc by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }
    var searching by remember { mutableStateOf(false) }
    var durFilter by remember { mutableStateOf(0) }
    var folderFilter by remember { mutableStateOf<String?>(null) }
    var sortMenu by remember { mutableStateOf(false) }
    var selected by remember { mutableStateOf(setOf<Long>()) }
    var actionTarget by remember { mutableStateOf<List<Video>?>(null) }
    val scan = rememberScanAction(vm)

    val last = remember(progress) { progress.mapValues { it.value.lastPlayed } }
    val shown = remember(all, folder, folderFilter, durFilter, query, sort, asc, last) {
        val base = all.filter { v ->
            (folder == null || v.folder == folder) &&
                (folderFilter == null || v.folder == folderFilter) &&
                (query.isBlank() || v.title.contains(query.trim(), true)) &&
                when (durFilter) {
                    1 -> v.durationMs < 5 * 60_000
                    2 -> v.durationMs in (5 * 60_000)..(30 * 60_000)
                    3 -> v.durationMs > 30 * 60_000
                    else -> true
                }
        }
        sortVideos(base, sort, asc, last)
    }
    val selecting = selected.isNotEmpty()

    Column(Modifier.fillMaxSize()) {
        if (selecting) {
            ScreenTitle(tr("%d selected", selected.size), onBack = { selected = emptySet() }) {
                IconButton(onClick = { selected = shown.map { it.id }.toSet() }) { Icon(Icons.Outlined.SelectAll, tr("Select all")) }
                IconButton(onClick = { actionTarget = shown.filter { it.id in selected } }) { Icon(Icons.Outlined.MoreVert, tr("Batch actions")) }
            }
        } else {
            ScreenTitle(folder ?: tr("Videos"), onBack = onBack) {
                IconButton(onClick = { searching = !searching; if (!searching) query = "" }) { Icon(Icons.Outlined.Search, tr("Search")) }
                IconButton(onClick = { grid = !grid }) { Icon(if (grid) Icons.Outlined.ViewList else Icons.Outlined.GridView, if (grid) tr("List view") else tr("Grid view")) }
                Box {
                    IconButton(onClick = { sortMenu = true }) { Icon(Icons.Outlined.Sort, tr("Sort")) }
                    DropdownMenu(sortMenu, { sortMenu = false }) {
                        SortKey.values().forEach { k ->
                            DropdownMenuItem(
                                text = { Text(tr(k.label)) },
                                leadingIcon = { if (sort == k) Icon(Icons.Outlined.Check, tr("Selected")) },
                                onClick = { sort = k; sortMenu = false })
                        }
                        HorizontalDivider()
                        DropdownMenuItem(
                            text = { Text(if (asc) tr("Ascending (A-Z, oldest first)") else tr("Descending (Z-A, newest first)")) },
                            leadingIcon = { Icon(if (asc) Icons.Outlined.ArrowUpward else Icons.Outlined.ArrowDownward, null) },
                            onClick = { asc = !asc; sortMenu = false })
                    }
                }
                RefreshButton(scan)
            }
        }
        if (searching) OutlinedTextField(
            query, { query = it }, singleLine = true, placeholder = { Text(tr("Search by file name")) },
            leadingIcon = { Icon(Icons.Outlined.Search, null) },
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp), shape = SmallShape
        )
        LazyRow(Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
            item { Chip(tr("All"), durFilter == 0) { durFilter = 0 } }
            item { Chip(tr("< 5 min"), durFilter == 1) { durFilter = if (durFilter == 1) 0 else 1 } }
            item { Chip(tr("5-30 min"), durFilter == 2) { durFilter = if (durFilter == 2) 0 else 2 } }
            item { Chip(tr("> 30 min"), durFilter == 3) { durFilter = if (durFilter == 3) 0 else 3 } }
            if (folder == null) items(folders) { f ->
                Chip(f.name, folderFilter == f.name) { folderFilter = if (folderFilter == f.name) null else f.name }
            }
        }
        when {
            loading && all.isEmpty() -> SkeletonGrid()
            shown.isEmpty() -> if (all.isEmpty()) EmptyState(
                Icons.Outlined.VideoLibrary, tr("No videos found"),
                if (scanned) tr("No videos were found on this phone. Try rescanning.") else tr("Permission is needed to show your videos."),
                if (hasVideoPermission(androidx.compose.ui.platform.LocalContext.current)) tr("Scan videos") else tr("Grant permission"), scan
            ) else EmptyState(Icons.Outlined.SearchOff, tr("Nothing matches"), tr("Change the filter or the search text."))
            grid -> LazyVerticalGrid(
                GridCells.Adaptive(160.dp), contentPadding = PaddingValues(12.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(shown, key = { it.id }) { v ->
                    VideoGridItem(v, progress[v.id], v.id in favs, v.id in selected, selecting,
                        onClick = {
                            if (selecting) selected = if (v.id in selected) selected - v.id else selected + v.id
                            else onPlay(shown, shown.indexOf(v))
                        },
                        onLong = { if (selecting) actionTarget = shown.filter { it.id in selected } else selected = selected + v.id })
                }
            }
            else -> LazyColumn(contentPadding = PaddingValues(vertical = 4.dp)) {
                items(shown, key = { it.id }) { v ->
                    VideoListItem(v, progress[v.id], v.id in favs, v.id in selected, selecting, query,
                        onClick = {
                            if (selecting) selected = if (v.id in selected) selected - v.id else selected + v.id
                            else onPlay(shown, shown.indexOf(v))
                        },
                        onLong = { if (selecting) actionTarget = shown.filter { it.id in selected } else selected = selected + v.id },
                        trailing = { IconButton(onClick = { actionTarget = listOf(v) }) { Icon(Icons.Outlined.MoreVert, tr("More actions")) } })
                }
            }
        }
    }
    VideoActions(vm, actionTarget, onDismiss = { actionTarget = null; selected = emptySet() },
        onPlay = { v -> onPlay(shown, shown.indexOf(v).coerceAtLeast(0)) })
}

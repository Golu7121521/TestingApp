package com.mytune.app.ui.screens

import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.PlaylistAdd
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.*
import androidx.compose.ui.draw.*
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import coil.compose.AsyncImage
import com.mytune.app.R
import com.mytune.app.di.AppContainer
import com.mytune.app.domain.*
import com.mytune.app.ui.UiState
import com.mytune.app.ui.components.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

import com.mytune.app.data.db.DownloadStatus
import com.mytune.app.data.db.PlaylistInfo
import com.mytune.app.data.repository.toSong

@Composable
fun LibraryScreen(c: AppContainer, onOpen: (String, String) -> Unit, onDownloads: () -> Unit) {
    var tab by rememberSaveable { mutableIntStateOf(0) }
    val tabs = listOf(R.string.tab_downloads, R.string.tab_favorites, R.string.tab_playlists, R.string.tab_recents)
    val favorites by c.library.favorites.collectAsState(initial = emptyList())
    val recents by c.library.recents.collectAsState(initial = emptyList())
    val downloads by c.downloads.items.collectAsState(initial = emptyList())
    val playlists by c.library.playlists.collectAsState(initial = emptyList<PlaylistInfo>())
    val scope = rememberCoroutineScope()
    var showCreate by remember { mutableStateOf(false) }
    var renaming by remember { mutableStateOf<PlaylistInfo?>(null) }
    var deleting by remember { mutableStateOf<PlaylistInfo?>(null) }

    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        Text(
            stringResource(R.string.nav_library),
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(16.dp),
        )
        ScrollableTabRow(selectedTabIndex = tab, edgePadding = 8.dp) {
            tabs.forEachIndexed { i, res -> Tab(selected = tab == i, onClick = { tab = i }, text = { Text(stringResource(res)) }) }
        }
        SongActionsHost(c) { actions ->
            LazyColumn(Modifier.fillMaxSize()) {
                when (tab) {
                    0 -> {
                        item {
                            TextButton(onClick = onDownloads, modifier = Modifier.padding(horizontal = 8.dp)) {
                                Icon(Icons.Default.Download, null)
                                Spacer(Modifier.width(8.dp))
                                Text(stringResource(R.string.manage_downloads))
                            }
                        }
                        val done = downloads.filter { it.download.status == DownloadStatus.DONE && it.song != null }
                            .map { it.song!!.toSong() }
                        if (done.isEmpty()) item { EmptyText(R.string.empty_list) } else songItems(done, actions, c)
                    }
                    1 -> if (favorites.isEmpty()) item { EmptyText(R.string.empty_list) } else songItems(favorites, actions, c)
                    2 -> {
                        item {
                            TextButton(onClick = { showCreate = true }, modifier = Modifier.padding(horizontal = 8.dp)) {
                                Icon(Icons.Default.Add, null)
                                Spacer(Modifier.width(8.dp))
                                Text(stringResource(R.string.new_playlist))
                            }
                        }
                        if (playlists.isEmpty()) item { EmptyText(R.string.no_playlists) }
                        items(playlists.size) { i ->
                            val p = playlists[i]
                            var menu by remember { mutableStateOf(false) }
                            Row(
                                Modifier.fillMaxWidth().clickable { onOpen(CollectionType.USER, p.id.toString()) }
                                    .padding(start = 16.dp, top = 6.dp, bottom = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Icon(Icons.AutoMirrored.Filled.QueueMusic, null, Modifier.size(40.dp))
                                Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                                    Text(p.name, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Text(stringResource(R.string.songs_count_fmt, p.songCount), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                Box {
                                    IconButton(onClick = { menu = true }) { Icon(Icons.Default.MoreVert, stringResource(R.string.more_options)) }
                                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                                        DropdownMenuItem(text = { Text(stringResource(R.string.rename)) }, onClick = { menu = false; renaming = p })
                                        DropdownMenuItem(text = { Text(stringResource(R.string.delete)) }, onClick = { menu = false; deleting = p })
                                    }
                                }
                            }
                        }
                    }
                    else -> if (recents.isEmpty()) item { EmptyText(R.string.empty_list) } else songItems(recents, actions, c)
                }
            }
        }
    }

    if (showCreate) {
        NameDialog(R.string.new_playlist, "", onConfirm = { name ->
            scope.launch { c.library.createPlaylist(name) }
            showCreate = false
        }, onDismiss = { showCreate = false })
    }
    renaming?.let { p ->
        NameDialog(R.string.rename, p.name, onConfirm = { name ->
            scope.launch { c.library.renamePlaylist(p.id, name) }
            renaming = null
        }, onDismiss = { renaming = null })
    }
    deleting?.let { p ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text(stringResource(R.string.delete_playlist_title)) },
            text = { Text(p.name) },
            confirmButton = {
                TextButton(onClick = { scope.launch { c.library.deletePlaylist(p.id) }; deleting = null }) { Text(stringResource(R.string.delete)) }
            },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text(stringResource(R.string.cancel)) } },
        )
    }
}

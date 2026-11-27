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

import android.text.format.Formatter
import com.mytune.app.data.db.DownloadItem
import com.mytune.app.data.db.DownloadStatus
import com.mytune.app.data.repository.toSong

@Composable
fun DownloadsScreen(c: AppContainer, onBack: () -> Unit) {
    val items by c.downloads.items.collectAsState(initial = emptyList())
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var confirmAll by remember { mutableStateOf(false) }
    val active = items.filter { it.download.status != DownloadStatus.DONE }
    val done = items.filter { it.download.status == DownloadStatus.DONE }
    val used = items.sumOf { it.download.downloadedBytes }

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent, scrolledContainerColor = Color.Transparent),
                title = { Text(stringResource(R.string.downloads)) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back)) } },
                actions = {
                    if (items.isNotEmpty()) {
                        IconButton(onClick = { confirmAll = true }) { Icon(Icons.Default.DeleteSweep, stringResource(R.string.delete_all)) }
                    }
                },
            )
        },
    ) { pad ->
        SongActionsHost(c) { actions ->
            LazyColumn(Modifier.fillMaxSize().padding(pad), contentPadding = PaddingValues(bottom = LocalBottomInset.current + 16.dp)) {
                item {
                    Text(
                        stringResource(R.string.storage_used_fmt, Formatter.formatShortFileSize(ctx, used)),
                        modifier = Modifier.padding(16.dp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (items.isEmpty()) item { EmptyText(R.string.empty_list) }
                if (active.isNotEmpty()) {
                    item { SectionTitle(R.string.active_downloads) }
                    items(active.size) { i -> ActiveRow(c, active[i]) }
                }
                if (done.isNotEmpty()) {
                    item { SectionTitle(R.string.downloaded_songs) }
                    items(done.size) { i ->
                        val d = done[i]
                        val song = d.song?.toSong() ?: return@items
                        Row(
                            Modifier.fillMaxWidth().clickable { c.player.play(done.mapNotNull { it.song?.toSong() }, i.coerceAtMost(done.size - 1)) }
                                .padding(start = 16.dp, top = 6.dp, bottom = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Artwork(song.artworkUrl, Modifier.size(52.dp))
                            Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                                Text(song.title, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(
                                    "${song.artist} · ${Formatter.formatShortFileSize(ctx, d.download.totalBytes)}",
                                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            IconButton(onClick = { scope.launch { c.downloads.delete(song.id) } }) {
                                Icon(Icons.Default.Delete, stringResource(R.string.delete))
                            }
                        }
                    }
                }
            }
        }
    }

    if (confirmAll) {
        AlertDialog(
            onDismissRequest = { confirmAll = false },
            title = { Text(stringResource(R.string.delete_all)) },
            text = { Text(stringResource(R.string.delete_all_confirm)) },
            confirmButton = {
                TextButton(onClick = { scope.launch { c.downloads.deleteAll() }; confirmAll = false }) { Text(stringResource(R.string.delete)) }
            },
            dismissButton = { TextButton(onClick = { confirmAll = false }) { Text(stringResource(R.string.cancel)) } },
        )
    }
}

@Composable
private fun ActiveRow(c: AppContainer, item: DownloadItem) {
    val scope = rememberCoroutineScope()
    val d = item.download
    val running = d.status == DownloadStatus.RUNNING || d.status == DownloadStatus.QUEUED
    val progress = if (d.totalBytes > 0) (d.downloadedBytes.toFloat() / d.totalBytes).coerceIn(0f, 1f) else 0f
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Artwork(item.song?.artworkUrl.orEmpty(), Modifier.size(44.dp))
            Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                Text(item.song?.title ?: d.songId, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    stringResource(
                        when (d.status) {
                            DownloadStatus.QUEUED -> R.string.status_queued
                            DownloadStatus.PAUSED -> R.string.status_paused
                            DownloadStatus.FAILED -> R.string.status_failed
                            else -> R.string.status_downloading
                        }
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = if (d.status == DownloadStatus.FAILED) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (running) {
                IconButton(onClick = { scope.launch { c.downloads.pause(d.songId) } }) { Icon(Icons.Default.Pause, stringResource(R.string.pause)) }
            } else {
                // Paused -> resume, failed -> retry (both continue from the partial file)
                IconButton(onClick = { scope.launch { c.downloads.resume(d.songId) } }) {
                    Icon(if (d.status == DownloadStatus.FAILED) Icons.Default.Refresh else Icons.Default.PlayArrow, stringResource(R.string.resume))
                }
            }
            IconButton(onClick = { scope.launch { c.downloads.delete(d.songId) } }) { Icon(Icons.Default.Close, stringResource(R.string.cancel_download)) }
        }
        if (d.totalBytes > 0) {
            LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth().padding(top = 4.dp))
        } else if (d.status == DownloadStatus.RUNNING) {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth().padding(top = 4.dp))
        }
    }
}

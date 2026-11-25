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

import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.draw.alpha
import androidx.core.graphics.drawable.toBitmap
import androidx.media3.common.Player
import androidx.palette.graphics.Palette
import coil.imageLoader
import coil.request.ImageRequest
import coil.request.SuccessResult
import com.mytune.app.data.db.DownloadStatus
import com.mytune.app.player.PlayerState

@Composable
fun PlayerScreen(c: AppContainer, onBack: () -> Unit) {
    val s by c.player.state.collectAsState()
    val qualities by c.resolver.qualities.collectAsState()
    val sleepEnd by c.player.sleepEnd.collectAsState()
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val id = s.mediaId.orEmpty()
    val fav by remember(id) { c.library.isFavorite(id) }.collectAsState(initial = false)
    val dl by remember(id) { c.downloads.statusOf(id) }.collectAsState(initial = null)
    var showQueue by remember { mutableStateOf(false) }
    var sleepMenu by remember { mutableStateOf(false) }

    // Nothing to show once the queue is empty
    LaunchedEffect(s.hasMedia) { if (!s.hasMedia) onBack() }

    // Background colour extracted from the artwork with Palette, animated on song change
    var target by remember { mutableStateOf(Color(0xFF25302A)) }
    LaunchedEffect(s.artworkUrl) {
        if (s.artworkUrl.isBlank()) return@LaunchedEffect
        val req = ImageRequest.Builder(ctx).data(s.artworkUrl).allowHardware(false).build()
        val bitmap = (ctx.imageLoader.execute(req) as? SuccessResult)?.drawable?.toBitmap()
        if (bitmap != null) {
            val p = Palette.from(bitmap).generate()
            val rgb = p.getDarkMutedColor(p.getMutedColor(p.getDominantColor(0xFF25302A.toInt())))
            target = Color(rgb)
        }
    }
    val bg by animateColorAsState(target, tween(600), label = "bg")

    var dragTotal = 0f
    Column(
        Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(bg, Color(0xFF0B0B0D))))
            .pointerInput(Unit) {
                // Swipe down to collapse
                detectVerticalDragGestures(
                    onDragStart = { dragTotal = 0f },
                    onDragEnd = { if (dragTotal > 250f) onBack() },
                ) { _, dy -> dragTotal += dy }
            }
            .systemBarsPadding()
            .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        CompositionLocalProvider(LocalContentColor provides Color.White) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) { Icon(Icons.Default.KeyboardArrowDown, stringResource(R.string.close)) }
                Text(stringResource(R.string.now_playing), Modifier.weight(1f), textAlign = TextAlign.Center, style = MaterialTheme.typography.titleMedium)
                Box {
                    IconButton(onClick = { sleepMenu = true }) {
                        Icon(Icons.Default.Timer, stringResource(R.string.sleep_timer), tint = if (sleepEnd != null) MaterialTheme.colorScheme.secondary else Color.White)
                    }
                    DropdownMenu(expanded = sleepMenu, onDismissRequest = { sleepMenu = false }) {
                        listOf(5, 15, 30, 60).forEach { m ->
                            DropdownMenuItem(text = { Text(stringResource(R.string.minutes_fmt, m)) }, onClick = { sleepMenu = false; c.player.setSleepTimer(m) })
                        }
                        DropdownMenuItem(text = { Text(stringResource(R.string.sleep_off)) }, onClick = { sleepMenu = false; c.player.setSleepTimer(null) })
                    }
                }
            }

            Spacer(Modifier.height(8.dp))
            Artwork(
                s.artworkUrl,
                Modifier.fillMaxWidth().aspectRatio(1f).shadow(24.dp, RoundedCornerShape(28.dp)),
                RoundedCornerShape(28.dp),
            )
            Spacer(Modifier.height(20.dp))

            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(s.title, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Text(s.artist, maxLines = 1, overflow = TextOverflow.Ellipsis, color = Color.White.copy(alpha = 0.7f))
                }
                IconButton(onClick = {
                    scope.launch { c.library.toggleFavorite(currentSong(s)) }
                }) {
                    Icon(if (fav) Icons.Default.Favorite else Icons.Default.FavoriteBorder, stringResource(R.string.favorite), tint = if (fav) MaterialTheme.colorScheme.primary else Color.White)
                }
            }

            // Quality indicator
            val q = qualities[id]
            if (q != null) {
                Text(
                    if (q == 0) stringResource(R.string.offline) else stringResource(R.string.kbps_fmt, q),
                    style = MaterialTheme.typography.labelMedium,
                    color = Color.White.copy(alpha = 0.7f),
                    modifier = Modifier.align(Alignment.Start).padding(top = 4.dp),
                )
            }
            if (s.error) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.error_playback), color = MaterialTheme.colorScheme.error, modifier = Modifier.weight(1f))
                    TextButton(onClick = { c.player.retry() }) { Text(stringResource(R.string.retry)) }
                }
            }

            // Seek bar
            var dragging by remember { mutableStateOf<Float?>(null) }
            val dur = s.durationMs.coerceAtLeast(1L)
            val fraction = dragging ?: (s.positionMs.toFloat() / dur).coerceIn(0f, 1f)
            Slider(
                value = fraction,
                onValueChange = { dragging = it },
                onValueChangeFinished = {
                    dragging?.let { c.player.seekTo((it * dur).toLong()) }
                    dragging = null
                },
                colors = SliderDefaults.colors(thumbColor = Color.White, activeTrackColor = Color.White, inactiveTrackColor = Color.White.copy(alpha = 0.25f)),
            )
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(formatTime((fraction * dur).toLong()), style = MaterialTheme.typography.labelMedium)
                Text(formatTime(s.durationMs), style = MaterialTheme.typography.labelMedium)
            }

            // Transport controls
            Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { c.player.toggleShuffle() }) {
                    Icon(Icons.Default.Shuffle, stringResource(R.string.shuffle), tint = if (s.shuffle) MaterialTheme.colorScheme.secondary else Color.White)
                }
                IconButton(onClick = { c.player.previous() }, modifier = Modifier.size(52.dp)) {
                    Icon(Icons.Default.SkipPrevious, stringResource(R.string.previous), Modifier.size(36.dp))
                }
                FilledIconButton(
                    onClick = { c.player.togglePlay() },
                    modifier = Modifier.size(72.dp),
                    colors = IconButtonDefaults.filledIconButtonColors(containerColor = Color.White, contentColor = Color.Black),
                ) {
                    if (s.isBuffering && !s.isPlaying) {
                        CircularProgressIndicator(Modifier.size(28.dp), color = Color.Black, strokeWidth = 3.dp)
                    } else {
                        Icon(
                            if (s.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                            stringResource(if (s.isPlaying) R.string.pause else R.string.play),
                            Modifier.size(40.dp),
                        )
                    }
                }
                IconButton(onClick = { c.player.next() }, modifier = Modifier.size(52.dp)) {
                    Icon(Icons.Default.SkipNext, stringResource(R.string.next), Modifier.size(36.dp))
                }
                IconButton(onClick = { c.player.cycleRepeat() }) {
                    Icon(
                        if (s.repeatMode == Player.REPEAT_MODE_ONE) Icons.Default.RepeatOne else Icons.Default.Repeat,
                        stringResource(R.string.repeat),
                        tint = if (s.repeatMode == Player.REPEAT_MODE_OFF) Color.White else MaterialTheme.colorScheme.secondary,
                    )
                }
            }

            Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                val status = dl?.status
                IconButton(onClick = {
                    if (status == null || status == DownloadStatus.FAILED) {
                        scope.launch { c.downloads.enqueue(currentSong(s), c.settings.downloadQualityNow) }
                        Toast.makeText(ctx, ctx.getString(R.string.download_started), Toast.LENGTH_SHORT).show()
                    }
                }) {
                    Icon(
                        if (status == DownloadStatus.DONE) Icons.Default.DownloadDone else Icons.Default.Download,
                        stringResource(R.string.menu_download),
                        modifier = Modifier.alpha(if (status == null || status == DownloadStatus.DONE || status == DownloadStatus.FAILED) 1f else 0.5f),
                    )
                }
                val remaining = sleepEnd?.let { ((it - System.currentTimeMillis()) / 60_000L + 1).coerceAtLeast(1) }
                if (remaining != null) {
                    Text(stringResource(R.string.sleep_active_fmt, remaining.toInt()), style = MaterialTheme.typography.labelMedium, modifier = Modifier.align(Alignment.CenterVertically))
                }
                IconButton(onClick = { showQueue = true }) { Icon(Icons.AutoMirrored.Filled.QueueMusic, stringResource(R.string.queue)) }
            }
        }
    }

    if (showQueue) QueueSheet(c, s) { showQueue = false }
}

private fun currentSong(s: PlayerState) = Song(
    id = s.mediaId.orEmpty(), title = s.title, artist = s.artist, album = s.album,
    artworkUrl = s.artworkUrl, durationSec = s.durationMs / 1000,
)

@Composable
private fun QueueSheet(c: AppContainer, s: PlayerState, onDismiss: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Text(stringResource(R.string.queue), style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
        LazyColumn(Modifier.fillMaxWidth().heightIn(max = 480.dp)) {
            items(s.queue.size) { i ->
                val item = s.queue[i]
                val current = i == s.currentIndex
                Row(
                    Modifier.fillMaxWidth().clickable { c.player.skipTo(i) }.padding(start = 16.dp, top = 4.dp, bottom = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Artwork(item.artworkUrl, Modifier.size(44.dp))
                    Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                        Text(item.title, maxLines = 1, overflow = TextOverflow.Ellipsis, color = if (current) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface)
                        Text(item.artist, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    // Reorder with up/down buttons, remove with the X
                    IconButton(onClick = { c.player.moveQueueItem(i, i - 1) }, enabled = i > 0) { Icon(Icons.Default.KeyboardArrowUp, stringResource(R.string.move_up)) }
                    IconButton(onClick = { c.player.moveQueueItem(i, i + 1) }, enabled = i < s.queue.lastIndex) { Icon(Icons.Default.KeyboardArrowDown, stringResource(R.string.move_down)) }
                    IconButton(onClick = { c.player.removeQueueItem(i) }) { Icon(Icons.Default.Close, stringResource(R.string.menu_remove)) }
                }
            }
        }
    }
}

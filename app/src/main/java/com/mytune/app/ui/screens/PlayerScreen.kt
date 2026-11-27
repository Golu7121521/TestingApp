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

import android.content.Intent
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import com.mytune.app.data.repository.Lyrics
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.core.graphics.drawable.toBitmap
import androidx.media3.common.Player
import androidx.palette.graphics.Palette
import coil.imageLoader
import coil.request.ImageRequest
import coil.request.SuccessResult
import com.mytune.app.data.db.DownloadStatus
import com.mytune.app.player.PlayerState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlayerScreen(c: AppContainer, onBack: () -> Unit) {
    val s by c.player.state.collectAsState()
    val sleepEnd by c.player.sleepEnd.collectAsState()
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val id = s.mediaId.orEmpty()
    val fav by remember(id) { c.library.isFavorite(id) }.collectAsState(initial = false)
    val dl by remember(id) { c.downloads.statusOf(id) }.collectAsState(initial = null)
    var showQueue by remember { mutableStateOf(false) }
    var showLyrics by remember { mutableStateOf(false) }
    var showPlaylist by remember { mutableStateOf(false) }
    var sleepMenu by remember { mutableStateOf(false) }
    var moreMenu by remember { mutableStateOf(false) }

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

    // The swipe-down gesture is handled by AppRoot (it follows the finger and collapses into the mini player).
    SongActionsHost(c) { actions ->
    Column(
        Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(bg, Color(0xFF0B0B0D))))
            .systemBarsPadding()
            .padding(horizontal = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        CompositionLocalProvider(LocalContentColor provides Color.White) {
            // Top bar: collapse on the left, three-dot menu on the right
            Row(Modifier.fillMaxWidth().padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) { Icon(Icons.Default.KeyboardArrowDown, stringResource(R.string.close), Modifier.size(30.dp)) }
                Spacer(Modifier.weight(1f))
                Box {
                    IconButton(onClick = { moreMenu = true }) { Icon(Icons.Default.MoreVert, stringResource(R.string.more_options)) }
                    DropdownMenu(expanded = moreMenu, onDismissRequest = { moreMenu = false }) {
                        SongMenuItems(currentSong(s), actions, onDismiss = { moreMenu = false })
                    }
                }
            }

            // Big square artwork that takes the free height; it breathes a little with play / pause
            BoxWithConstraints(Modifier.weight(1f).fillMaxWidth().padding(vertical = 6.dp), contentAlignment = Alignment.Center) {
                val side = minOf(maxWidth, maxHeight)
                val sc by animateFloatAsState(if (s.isPlaying) 1f else 0.92f, spring(dampingRatio = 0.6f, stiffness = 300f), label = "artScale")
                Artwork(
                    s.artworkUrl,
                    Modifier.size(side).graphicsLayer { scaleX = sc; scaleY = sc }.shadow(24.dp, RoundedCornerShape(28.dp), clip = false),
                    RoundedCornerShape(28.dp),
                )
            }

            Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(s.title, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.ExtraBold)
                    Text(
                        s.artist, maxLines = 1, overflow = TextOverflow.Ellipsis, color = Color.White.copy(alpha = 0.75f),
                        style = MaterialTheme.typography.titleSmall,
                        modifier = Modifier.clickable { actions.goToArtist(currentSong(s)) },
                    )
                }
                IconButton(onClick = { scope.launch { c.library.toggleFavorite(currentSong(s)) } }) {
                    Icon(
                        if (fav) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                        stringResource(R.string.favorite),
                        tint = if (fav) MaterialTheme.colorScheme.primary else Color.White,
                        modifier = Modifier.size(28.dp),
                    )
                }
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
            Spacer(Modifier.height(6.dp))
            SeekBar(
                fraction = fraction,
                active = dragging != null,
                onChange = { dragging = it },
                onFinished = {
                    dragging?.let { c.player.seekTo((it * dur).toLong()) }
                    dragging = null
                },
            )
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(formatTime((fraction * dur).toLong()), style = MaterialTheme.typography.labelMedium, color = Color.White.copy(alpha = 0.8f))
                Text(formatTime(s.durationMs), style = MaterialTheme.typography.labelMedium, color = Color.White.copy(alpha = 0.8f))
            }

            // Transport: shuffle | previous | PLAY | next | repeat
            Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { c.player.toggleShuffle() }) {
                    Icon(Icons.Default.Shuffle, stringResource(R.string.shuffle), tint = if (s.shuffle) MaterialTheme.colorScheme.primary else Color.White.copy(alpha = 0.8f))
                }
                IconButton(onClick = { c.player.previous() }, modifier = Modifier.size(56.dp)) {
                    Icon(Icons.Default.SkipPrevious, stringResource(R.string.previous), Modifier.size(40.dp))
                }
                Box(
                    Modifier.size(76.dp).shadow(14.dp, CircleShape).clip(CircleShape)
                        .background(Brush.linearGradient(listOf(MaterialTheme.colorScheme.secondary, MaterialTheme.colorScheme.primary)))
                        .clickable { c.player.togglePlay() },
                    contentAlignment = Alignment.Center,
                ) {
                    if (s.isBuffering && !s.isPlaying) {
                        CircularProgressIndicator(Modifier.size(30.dp), color = MaterialTheme.colorScheme.onPrimary, strokeWidth = 3.dp)
                    } else {
                        Icon(
                            if (s.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                            stringResource(if (s.isPlaying) R.string.pause else R.string.play),
                            Modifier.size(44.dp), tint = MaterialTheme.colorScheme.onPrimary,
                        )
                    }
                }
                IconButton(onClick = { c.player.next() }, modifier = Modifier.size(56.dp)) {
                    Icon(Icons.Default.SkipNext, stringResource(R.string.next), Modifier.size(40.dp))
                }
                IconButton(onClick = { c.player.cycleRepeat() }) {
                    Icon(
                        if (s.repeatMode == Player.REPEAT_MODE_ONE) Icons.Default.RepeatOne else Icons.Default.Repeat,
                        stringResource(R.string.repeat),
                        tint = if (s.repeatMode == Player.REPEAT_MODE_OFF) Color.White.copy(alpha = 0.8f) else MaterialTheme.colorScheme.primary,
                    )
                }
            }

            // Secondary actions in a rounded glass bar: download / delete, sleep timer, lyrics, playlist, equalizer, queue
            Row(
                Modifier.fillMaxWidth().padding(top = 4.dp, bottom = 10.dp).clip(RoundedCornerShape(28.dp)).background(Color.White.copy(alpha = 0.09f)).padding(horizontal = 4.dp),
                horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically,
            ) {
                val status = dl?.status
                IconButton(onClick = {
                    when (status) {
                        null, DownloadStatus.FAILED -> {
                            scope.launch { c.downloads.enqueue(currentSong(s), c.settings.downloadQualityNow) }
                            Toast.makeText(ctx, ctx.getString(R.string.download_started), Toast.LENGTH_SHORT).show()
                        }
                        DownloadStatus.DONE -> actions.deleteDownload(currentSong(s))
                        else -> {}
                    }
                }) {
                    Icon(
                        if (status == DownloadStatus.DONE) Icons.Default.DownloadDone else Icons.Default.Download,
                        stringResource(R.string.menu_download),
                        tint = if (status == DownloadStatus.DONE) MaterialTheme.colorScheme.primary else Color.White,
                        modifier = Modifier.alpha(if (status == null || status == DownloadStatus.DONE || status == DownloadStatus.FAILED) 1f else 0.5f),
                    )
                }
                Box {
                    val remaining = sleepEnd?.let { ((it - System.currentTimeMillis()) / 60_000L + 1).coerceAtLeast(1) }
                    IconButton(onClick = { sleepMenu = true }) {
                        if (remaining != null) {
                            BadgedBox(badge = { Badge { Text("${remaining}m") } }) {
                                Icon(Icons.Default.Timer, stringResource(R.string.sleep_timer), tint = MaterialTheme.colorScheme.primary)
                            }
                        } else {
                            Icon(Icons.Default.Timer, stringResource(R.string.sleep_timer))
                        }
                    }
                    DropdownMenu(expanded = sleepMenu, onDismissRequest = { sleepMenu = false }) {
                        listOf(5, 15, 30, 60).forEach { m ->
                            DropdownMenuItem(text = { Text(stringResource(R.string.minutes_fmt, m)) }, onClick = { sleepMenu = false; c.player.setSleepTimer(m) })
                        }
                        DropdownMenuItem(text = { Text(stringResource(R.string.sleep_off)) }, onClick = { sleepMenu = false; c.player.setSleepTimer(null) })
                    }
                }
                IconButton(onClick = { showLyrics = true }) { Icon(Icons.Default.Lyrics, "Lyrics") }
                IconButton(onClick = { showPlaylist = true }) { Icon(Icons.AutoMirrored.Filled.PlaylistAdd, stringResource(R.string.menu_add_playlist)) }
                IconButton(onClick = { actions.equalizer() }) { Icon(Icons.Default.Equalizer, "Equalizer") }
                IconButton(onClick = { showQueue = true }) { Icon(Icons.AutoMirrored.Filled.QueueMusic, stringResource(R.string.queue)) }
            }
        }
    }

    }
    if (showQueue) QueueSheet(c, s) { showQueue = false }
    if (showLyrics) LyricsSheet(c, s) { showLyrics = false }
    if (showPlaylist) PlaylistPickerDialog(c, currentSong(s)) { showPlaylist = false }
}

private fun shareSong(ctx: android.content.Context, s: PlayerState) {
    val i = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, "${s.title} - ${s.artist}")
    }
    ctx.startActivity(Intent.createChooser(i, null).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
}

/** Slim custom seek bar: rounded track with a green->light-green fill and a small vertical pill thumb. */
@Composable
private fun SeekBar(fraction: Float, active: Boolean, onChange: (Float) -> Unit, onFinished: () -> Unit) {
    val change by rememberUpdatedState(onChange)
    val finished by rememberUpdatedState(onFinished)
    val primary = MaterialTheme.colorScheme.primary
    val secondary = MaterialTheme.colorScheme.secondary
    val trackH by animateDpAsState(if (active) 8.dp else 5.dp, label = "trackH")
    val thumbH by animateDpAsState(if (active) 26.dp else 18.dp, label = "thumbH")
    Canvas(
        Modifier
            .fillMaxWidth()
            .height(36.dp)
            .pointerInput(Unit) {
                detectTapGestures { off ->
                    change((off.x / size.width).coerceIn(0f, 1f))
                    finished()
                }
            }
            .pointerInput(Unit) {
                detectHorizontalDragGestures(
                    onDragStart = { off -> change((off.x / size.width).coerceIn(0f, 1f)) },
                    onDragEnd = { finished() },
                    onDragCancel = { finished() },
                ) { ch, _ ->
                    ch.consume()
                    change((ch.position.x / size.width).coerceIn(0f, 1f))
                }
            },
    ) {
        val h = trackH.toPx()
        val cy = size.height / 2f
        val r = CornerRadius(h / 2f, h / 2f)
        drawRoundRect(Color.White.copy(alpha = 0.25f), topLeft = Offset(0f, cy - h / 2f), size = Size(size.width, h), cornerRadius = r)
        val x = (size.width * fraction).coerceIn(0f, size.width)
        if (x > 0f) {
            drawRoundRect(
                Brush.horizontalGradient(listOf(primary, secondary), endX = size.width.coerceAtLeast(1f)),
                topLeft = Offset(0f, cy - h / 2f), size = Size(x, h), cornerRadius = r,
            )
        }
        val tw = 6.dp.toPx()
        val th = thumbH.toPx()
        drawRoundRect(
            Color.White,
            topLeft = Offset((x - tw / 2f).coerceIn(0f, size.width - tw), cy - th / 2f),
            size = Size(tw, th), cornerRadius = CornerRadius(tw / 2f, tw / 2f),
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LyricsSheet(c: AppContainer, s: PlayerState, onDismiss: () -> Unit) {
    var loading by remember { mutableStateOf(true) }
    var lyrics by remember { mutableStateOf<Lyrics?>(null) }
    LaunchedEffect(s.mediaId) {
        loading = true
        lyrics = c.lyrics.get(s.title, s.artist, s.durationMs / 1000)
        loading = false
    }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Text("Lyrics", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(horizontal = 16.dp))
        Text(s.title, maxLines = 1, overflow = TextOverflow.Ellipsis, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp))
        val l = lyrics
        when {
            loading -> Box(Modifier.fillMaxWidth().height(240.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            l == null -> Box(Modifier.fillMaxWidth().height(240.dp), contentAlignment = Alignment.Center) {
                Text("Lyrics not available for this song", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            l.synced.isNotEmpty() -> {
                val current = l.synced.indexOfLast { it.timeMs <= s.positionMs }.coerceAtLeast(0)
                val listState = rememberLazyListState()
                LaunchedEffect(current) { listState.animateScrollToItem((current - 2).coerceAtLeast(0)) }
                LazyColumn(Modifier.fillMaxWidth().heightIn(max = 560.dp), state = listState, contentPadding = PaddingValues(16.dp)) {
                    items(l.synced.size) { i ->
                        val line = l.synced[i]
                        val isCur = i == current
                        Text(
                            line.text.ifBlank { "♪" },
                            style = if (isCur) MaterialTheme.typography.titleLarge else MaterialTheme.typography.titleMedium,
                            fontWeight = if (isCur) FontWeight.Bold else FontWeight.Normal,
                            color = if (isCur) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(vertical = 8.dp).clickable { c.player.seekTo(line.timeMs) },
                        )
                    }
                }
            }
            else -> LazyColumn(Modifier.fillMaxWidth().heightIn(max = 560.dp), contentPadding = PaddingValues(16.dp)) {
                item { Text(l.plain, style = MaterialTheme.typography.bodyLarge, lineHeight = 28.sp) }
            }
        }
    }
}

private fun currentSong(s: PlayerState) = Song(
    id = s.mediaId.orEmpty(), title = s.title, artist = s.artist, album = s.album,
    artworkUrl = s.artworkUrl, durationSec = s.durationMs / 1000, artistId = s.artistId,
)

@OptIn(ExperimentalMaterial3Api::class)
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
                    IconButton(onClick = { c.player.removeQueueItem(i) }) { Icon(Icons.Default.Close, stringResource(R.string.menu_remove)) }
                }
            }
        }
    }
}

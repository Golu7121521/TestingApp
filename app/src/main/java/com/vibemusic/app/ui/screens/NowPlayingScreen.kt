package com.vibemusic.app.ui.screens

import android.app.Activity
import android.graphics.drawable.BitmapDrawable
import android.view.WindowManager
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Lyrics
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.RepeatOne
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.palette.graphics.Palette
import coil.ImageLoader
import coil.request.ImageRequest
import coil.request.SuccessResult
import com.vibemusic.app.domain.model.RepeatModeOption
import com.vibemusic.app.domain.model.Song
import com.vibemusic.app.presentation.VibeViewModel
import com.vibemusic.app.ui.components.AlbumArtwork
import com.vibemusic.app.utils.MediaUtils

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NowPlayingScreen(
    viewModel: VibeViewModel,
    onClose: () -> Unit,
    onOpenEqualizer: () -> Unit,
    onSongMoreClick: (Song) -> Unit
) {
    val playbackState by viewModel.playbackState.collectAsState()
    val prefs by viewModel.preferences.collectAsState()
    val lyrics by viewModel.currentLyrics.collectAsState()
    val songs by viewModel.songs.collectAsState()

    val currentSong = playbackState.currentSong
    val liveSong = songs.find { it.id == currentSong?.id } ?: currentSong

    val context = LocalContext.current
    val defaultPrimary = MaterialTheme.colorScheme.primary
    val bgColor = MaterialTheme.colorScheme.background

    var dominantColor by remember { mutableStateOf(defaultPrimary) }
    var showLyrics by remember { mutableStateOf(false) }
    var showQueueSheet by remember { mutableStateOf(false) }
    var showSpeedDialog by remember { mutableStateOf(false) }
    var showSleepDialog by remember { mutableStateOf(false) }

    // Keep Screen On while Now Playing is visible if enabled
    DisposableEffect(prefs.keepScreenOn) {
        val window = (context as? Activity)?.window
        if (prefs.keepScreenOn) {
            window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
        onDispose {
            window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }

    // Extract dynamic dominant gradient color from album artwork
    LaunchedEffect(liveSong?.albumArtUri, defaultPrimary) {
        val artUri = liveSong?.albumArtUri
        if (artUri == null) {
            dominantColor = defaultPrimary
            return@LaunchedEffect
        }
        try {
            val loader = ImageLoader(context)
            val request = ImageRequest.Builder(context)
                .data(artUri)
                .allowHardware(false)
                .build()
            val result = loader.execute(request)
            if (result is SuccessResult) {
                val bitmap = (result.drawable as? BitmapDrawable)?.bitmap
                if (bitmap != null) {
                    val palette = Palette.from(bitmap).generate()
                    val swatch = palette.vibrantSwatch
                        ?: palette.darkVibrantSwatch
                        ?: palette.dominantSwatch
                    dominantColor = swatch?.let { Color(it.rgb) } ?: defaultPrimary
                }
            } else {
                dominantColor = defaultPrimary
            }
        } catch (_: Exception) {
            dominantColor = defaultPrimary
        }
    }

    // Subtle rotation animation when playing and enabled
    val infiniteTransition = rememberInfiniteTransition(label = "artRotation")
    val rotationAngle by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 24000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "rotation"
    )

    if (liveSong == null) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(bgColor),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("No track selected", style = MaterialTheme.typography.titleLarge)
                Spacer(modifier = Modifier.height(12.dp))
                TextButton(onClick = onClose) { Text("Back to Library") }
            }
        }
        return
    }

    var isDraggingSlider by remember { mutableStateOf(false) }
    var sliderPosition by remember { mutableFloatStateOf(0f) }
    val duration = playbackState.durationMs.coerceAtLeast(1L)
    val currentProgress = if (isDraggingSlider) {
        sliderPosition
    } else {
        (playbackState.currentPositionMs.toFloat() / duration.toFloat()).coerceIn(0f, 1f)
    }

    var swipeDragOffset by remember { mutableFloatStateOf(0f) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        dominantColor.copy(alpha = 0.48f),
                        bgColor.copy(alpha = 0.94f),
                        bgColor
                    )
                )
            )
            .padding(horizontal = 20.dp, vertical = 14.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Top Bar
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onClose) {
                    Icon(
                        imageVector = Icons.Default.KeyboardArrowDown,
                        contentDescription = "Minimize Player",
                        modifier = Modifier.size(30.dp)
                    )
                }
                Column(
                    modifier = Modifier.weight(1f),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "NOW PLAYING",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = liveSong.album,
                        style = MaterialTheme.typography.labelMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                IconButton(onClick = { onSongMoreClick(liveSong) }) {
                    Icon(Icons.Default.MoreVert, contentDescription = "Song Options")
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Large Album Artwork OR Lyrics View
            if (showLyrics && lyrics.isNotEmpty()) {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    shape = RoundedCornerShape(24.dp),
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.65f)
                ) {
                    val activeLineIdx = lyrics.indexOfLast {
                        it.timestampMs <= playbackState.currentPositionMs
                    }.coerceAtLeast(0)

                    LazyColumn(
                        modifier = Modifier.padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        itemsIndexed(lyrics) { idx, line ->
                            val isActive = line.timestampMs > 0L && idx == activeLineIdx
                            Text(
                                text = line.text,
                                style = if (isActive) MaterialTheme.typography.titleLarge else MaterialTheme.typography.bodyLarge,
                                fontWeight = if (isActive) FontWeight.Bold else FontWeight.Normal,
                                color = if (isActive) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.72f),
                                textAlign = TextAlign.Center,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        if (line.timestampMs > 0L) {
                                            viewModel.player.seekTo(line.timestampMs)
                                        }
                                    }
                            )
                        }
                    }
                }
            } else {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .pointerInput(liveSong.id) {
                            detectHorizontalDragGestures(
                                onDragEnd = {
                                    if (swipeDragOffset < -90f) {
                                        viewModel.player.skipToNext()
                                    } else if (swipeDragOffset > 90f) {
                                        viewModel.player.skipToPrevious()
                                    }
                                    swipeDragOffset = 0f
                                },
                                onHorizontalDrag = { _, dragAmount ->
                                    swipeDragOffset += dragAmount
                                }
                            )
                        },
                    contentAlignment = Alignment.Center
                ) {
                    val useCircleArt = prefs.albumArtAnimation && playbackState.isPlaying
                    AlbumArtwork(
                        uri = liveSong.albumArtUri,
                        contentDescription = liveSong.album,
                        modifier = Modifier
                            .fillMaxWidth(0.86f)
                            .aspectRatio(1f)
                            .shadow(20.dp, if (useCircleArt) CircleShape else RoundedCornerShape(26.dp))
                            .then(
                                if (useCircleArt) Modifier.rotate(rotationAngle) else Modifier
                            ),
                        cornerRadius = if (useCircleArt) 200.dp else 26.dp,
                        iconSize = 84.dp
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Track Info & Favorite Button
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = liveSong.title,
                        style = MaterialTheme.typography.headlineMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = liveSong.artist,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                IconButton(onClick = { viewModel.toggleFavorite(liveSong.id) }) {
                    Icon(
                        imageVector = if (liveSong.isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                        contentDescription = "Favorite",
                        tint = if (liveSong.isFavorite) Color(0xFFEC4899) else MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.size(28.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Progress Slider & Timestamps
            Slider(
                value = currentProgress,
                onValueChange = {
                    isDraggingSlider = true
                    sliderPosition = it
                },
                onValueChangeFinished = {
                    viewModel.player.seekTo((sliderPosition * duration).toLong())
                    isDraggingSlider = false
                },
                colors = SliderDefaults.colors(
                    thumbColor = MaterialTheme.colorScheme.primary,
                    activeTrackColor = MaterialTheme.colorScheme.primary
                ),
                modifier = Modifier.fillMaxWidth()
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                val displayedMs = if (isDraggingSlider) (sliderPosition * duration).toLong() else playbackState.currentPositionMs
                Text(
                    text = MediaUtils.formatDuration(displayedMs),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = MediaUtils.formatDuration(duration),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Main Transport Controls
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = { viewModel.player.toggleShuffle() }) {
                    Icon(
                        imageVector = Icons.Default.Shuffle,
                        contentDescription = "Shuffle",
                        tint = if (playbackState.isShuffleEnabled) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                IconButton(
                    onClick = { viewModel.player.skipToPrevious() },
                    modifier = Modifier.size(52.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.SkipPrevious,
                        contentDescription = "Previous",
                        modifier = Modifier.size(36.dp)
                    )
                }

                IconButton(
                    onClick = { viewModel.player.togglePlayPause() },
                    modifier = Modifier
                        .size(70.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary)
                ) {
                    Icon(
                        imageVector = if (playbackState.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = if (playbackState.isPlaying) "Pause" else "Play",
                        tint = Color.White,
                        modifier = Modifier.size(38.dp)
                    )
                }

                IconButton(
                    onClick = { viewModel.player.skipToNext() },
                    modifier = Modifier.size(52.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.SkipNext,
                        contentDescription = "Next",
                        modifier = Modifier.size(36.dp)
                    )
                }

                IconButton(onClick = { viewModel.player.cycleRepeatMode() }) {
                    val icon = when (playbackState.repeatMode) {
                        RepeatModeOption.ONE -> Icons.Default.RepeatOne
                        else -> Icons.Default.Repeat
                    }
                    val tint = when (playbackState.repeatMode) {
                        RepeatModeOption.OFF -> MaterialTheme.colorScheme.onSurfaceVariant
                        else -> MaterialTheme.colorScheme.primary
                    }
                    Icon(
                        imageVector = icon,
                        contentDescription = playbackState.repeatMode.label,
                        tint = tint
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Secondary Action Strip (Lyrics, Equalizer, Speed, Sleep Timer, Queue)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (lyrics.isNotEmpty()) {
                    FilterChip(
                        selected = showLyrics,
                        onClick = { showLyrics = !showLyrics },
                        label = { Text("Lyrics") },
                        leadingIcon = {
                            Icon(Icons.Default.Lyrics, contentDescription = null, modifier = Modifier.size(16.dp))
                        }
                    )
                }

                IconButton(onClick = onOpenEqualizer) {
                    Icon(Icons.Default.GraphicEq, contentDescription = "Equalizer")
                }

                IconButton(onClick = { showSpeedDialog = true }) {
                    Icon(Icons.Default.Speed, contentDescription = "Playback Speed")
                }

                IconButton(onClick = { showSleepDialog = true }) {
                    Icon(
                        imageVector = Icons.Default.Bedtime,
                        contentDescription = "Sleep Timer",
                        tint = if (playbackState.sleepTimerRemainingMs > 0L) MaterialTheme.colorScheme.secondary
                        else MaterialTheme.colorScheme.onSurface
                    )
                }

                IconButton(onClick = { showQueueSheet = true }) {
                    Icon(Icons.AutoMirrored.Filled.QueueMusic, contentDescription = "Queue")
                }
            }
        }
    }

    // Queue Management Bottom Sheet
    if (showQueueSheet) {
        ModalBottomSheet(onDismissRequest = { showQueueSheet = false }) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                Text(
                    text = "Up Next Queue (${playbackState.queue.size})",
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.padding(bottom = 12.dp)
                )
                LazyColumn(
                    modifier = Modifier.height(360.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    itemsIndexed(playbackState.queue) { idx, qSong ->
                        val isCurrent = idx == playbackState.currentIndex
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .background(
                                    if (isCurrent) MaterialTheme.colorScheme.primary.copy(alpha = 0.16f)
                                    else Color.Transparent
                                )
                                .clickable { viewModel.player.jumpToQueueIndex(idx) }
                                .padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "${idx + 1}.",
                                style = MaterialTheme.typography.labelMedium,
                                modifier = Modifier.width(28.dp)
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = qSong.title,
                                    style = MaterialTheme.typography.titleMedium,
                                    color = if (isCurrent) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.onSurface,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = qSong.artist,
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1
                                )
                            }
                            if (!isCurrent) {
                                IconButton(onClick = { viewModel.player.removeFromQueue(idx) }) {
                                    Icon(Icons.Default.Close, contentDescription = "Remove")
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // Playback Speed Dialog
    if (showSpeedDialog) {
        val speeds = listOf(0.75f, 1.0f, 1.25f, 1.5f, 1.75f, 2.0f)
        AlertDialog(
            onDismissRequest = { showSpeedDialog = false },
            title = { Text("Playback Speed") },
            text = {
                Column {
                    speeds.forEach { sp ->
                        TextButton(
                            onClick = {
                                viewModel.player.setPlaybackSpeed(sp)
                                showSpeedDialog = false
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = "${sp}x",
                                fontWeight = if (playbackState.playbackSpeed == sp) FontWeight.Bold else FontWeight.Normal,
                                color = if (playbackState.playbackSpeed == sp) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showSpeedDialog = false }) { Text("Close") }
            }
        )
    }

    // Sleep Timer Dialog
    if (showSleepDialog) {
        val options = listOf(0, 15, 30, 45, 60, 90)
        AlertDialog(
            onDismissRequest = { showSleepDialog = false },
            title = { Text("Sleep Timer") },
            text = {
                Column {
                    if (playbackState.sleepTimerRemainingMs > 0L) {
                        Text(
                            text = "Remaining: ${MediaUtils.formatDuration(playbackState.sleepTimerRemainingMs)}",
                            color = MaterialTheme.colorScheme.secondary,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )
                    }
                    options.forEach { mins ->
                        TextButton(
                            onClick = {
                                viewModel.player.startSleepTimer(mins)
                                showSleepDialog = false
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(if (mins == 0) "Turn Off Timer" else "$mins minutes")
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showSleepDialog = false }) { Text("Close") }
            }
        )
    }
}

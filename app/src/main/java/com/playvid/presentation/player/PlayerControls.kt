package com.playvid.presentation.player

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Forward10
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.FullscreenExit
import androidx.compose.material.icons.filled.AspectRatio
import androidx.compose.material.icons.filled.Audiotrack
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.HighQuality
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PictureInPictureAlt
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Subtitles
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay10
import androidx.compose.material.icons.filled.VolumeOff
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.playvid.playback.PlayerProgress
import com.playvid.playback.PlayerUiState
import com.playvid.presentation.util.formatTime
import kotlinx.coroutines.flow.StateFlow

/**
 * Controls overlay.
 * Top: back, title.  Centre: -10 s, play/pause (no background), +10 s.
 * Bottom: a row of small tool icons (quality, audio, subtitles, speed, fit/fill, PiP, copy URL)
 * directly above one slim row: mute, time, thin seek bar, duration, fullscreen.
 */
@Composable
fun PlayerControls(
    state: PlayerUiState,
    progress: StateFlow<PlayerProgress>,
    isLandscape: Boolean,
    onBack: () -> Unit,
    onTogglePlay: () -> Unit,
    onSeekBack: () -> Unit,
    onSeekForward: () -> Unit,
    onSeekTo: (Long) -> Unit,
    onToggleMute: () -> Unit,
    showQuality: Boolean,
    showAudio: Boolean,
    showSubtitles: Boolean,
    showPip: Boolean,
    showCopyUrl: Boolean,
    showDownload: Boolean,
    onPicker: (PickerKind) -> Unit,
    onToggleResize: () -> Unit,
    onPip: () -> Unit,
    onCopyUrl: () -> Unit,
    onDownload: () -> Unit,
    onToggleOrientation: () -> Unit,
    onLock: () -> Unit,
    onGoLive: () -> Unit,
    onInteract: () -> Unit
) {
    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.35f))
            .windowInsetsPadding(WindowInsets.safeDrawing)
    ) {
        // Top bar
        Row(Modifier.fillMaxWidth().align(Alignment.TopCenter), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.White)
            }
            Text(
                text = state.title,
                color = Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.weight(1f)
            )
            GoLiveButton(progress = progress, onGoLive = { onGoLive(); onInteract() })
        }

        // Centre transport: plain white icons, no filled background.
        Row(
            Modifier.align(Alignment.Center),
            horizontalArrangement = Arrangement.spacedBy(40.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = { onSeekBack(); onInteract() }, modifier = Modifier.size(56.dp)) {
                Icon(Icons.Filled.Replay10, contentDescription = "Back 10 seconds", tint = Color.White, modifier = Modifier.size(36.dp))
            }
            if (state.isLoading || state.isBuffering) {
                // The spinner from PlayerScreen sits exactly here.
                Spacer(Modifier.size(72.dp))
            } else {
                IconButton(onClick = { onTogglePlay(); onInteract() }, modifier = Modifier.size(72.dp)) {
                    Icon(
                        imageVector = if (state.isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                        contentDescription = if (state.isPlaying) "Pause" else "Play",
                        tint = Color.White,
                        modifier = Modifier.size(56.dp)
                    )
                }
            }
            IconButton(onClick = { onSeekForward(); onInteract() }, modifier = Modifier.size(56.dp)) {
                Icon(Icons.Filled.Forward10, contentDescription = "Forward 10 seconds", tint = Color.White, modifier = Modifier.size(36.dp))
            }
        }

        // Bottom: small tool icons right above the seek row
        Column(Modifier.fillMaxWidth().align(Alignment.BottomCenter)) {
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(2.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (showQuality) ToolIcon(Icons.Filled.HighQuality, "Quality") { onPicker(PickerKind.QUALITY); onInteract() }
                if (showAudio) ToolIcon(Icons.Filled.Audiotrack, "Audio track") { onPicker(PickerKind.AUDIO); onInteract() }
                if (showSubtitles) ToolIcon(Icons.Filled.Subtitles, "Subtitles") { onPicker(PickerKind.SUBTITLES); onInteract() }
                ToolIcon(Icons.Filled.Speed, "Playback speed") { onPicker(PickerKind.SPEED); onInteract() }
                ToolIcon(Icons.Filled.AspectRatio, if (state.resizeFill) "Fit to screen" else "Fill screen") { onToggleResize(); onInteract() }
                if (showPip) ToolIcon(Icons.Filled.PictureInPictureAlt, "Picture in picture") { onPip() }
                if (showCopyUrl) ToolIcon(Icons.Filled.ContentCopy, "Copy URL") { onCopyUrl(); onInteract() }
                if (showDownload) ToolIcon(Icons.Filled.Download, "Download") { onDownload(); onInteract() }
                ToolIcon(Icons.Filled.Lock, "Lock controls") { onLock() }
            }
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = { onToggleMute(); onInteract() }) {
                    Icon(
                        imageVector = if (state.isMuted) Icons.Filled.VolumeOff else Icons.Filled.VolumeUp,
                        contentDescription = if (state.isMuted) "Unmute" else "Mute",
                        tint = Color.White
                    )
                }
                SeekRow(progress = progress, onSeekTo = onSeekTo, onInteract = onInteract, modifier = Modifier.weight(1f))
                IconButton(onClick = onToggleOrientation) {
                    Icon(
                        imageVector = if (isLandscape) Icons.Filled.FullscreenExit else Icons.Filled.Fullscreen,
                        contentDescription = if (isLandscape) "Exit fullscreen" else "Fullscreen",
                        tint = Color.White
                    )
                }
            }
        }
    }
}

/** Small (20 dp) icon with a 36 dp touch target. */
@Composable
private fun ToolIcon(icon: androidx.compose.ui.graphics.vector.ImageVector, description: String, onClick: () -> Unit) {
    IconButton(onClick = onClick, modifier = Modifier.size(36.dp)) {
        Icon(icon, contentDescription = description, tint = Color.White, modifier = Modifier.size(20.dp))
    }
}

/** "LIVE" (red, at the edge) or "GO LIVE" (tap to jump back) when the current stream is live. */
@Composable
private fun GoLiveButton(progress: StateFlow<PlayerProgress>, onGoLive: () -> Unit) {
    val p by progress.collectAsStateWithLifecycle()
    if (!p.isLive) return
    val behind = p.liveOffsetMs > 5_000
    Row(
        Modifier
            .padding(end = 4.dp)
            .background(if (behind) Color.White.copy(alpha = 0.18f) else Color(0xFFE5473F), RoundedCornerShape(50))
            .then(if (behind) Modifier.clickable(onClick = onGoLive) else Modifier)
            .padding(horizontal = 10.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp)
    ) {
        Box(Modifier.size(6.dp).background(Color.White, CircleShape))
        Text(if (behind) "GO LIVE" else "LIVE", color = Color.White, style = MaterialTheme.typography.labelSmall)
    }
}

/** The only part that observes the ticking position, so the rest of the screen stays still. */
@Composable
private fun SeekRow(
    progress: StateFlow<PlayerProgress>,
    onSeekTo: (Long) -> Unit,
    onInteract: () -> Unit,
    modifier: Modifier = Modifier
) {
    val p by progress.collectAsStateWithLifecycle()
    var dragging by remember { mutableStateOf(false) }
    var dragFraction by remember { mutableFloatStateOf(0f) }

    val duration = p.durationMs
    val seekable = duration > 0 && !p.isLive
    val fraction = when {
        dragging -> dragFraction
        duration > 0 -> (p.positionMs.toFloat() / duration).coerceIn(0f, 1f)
        else -> 0f
    }
    val shownPosition = if (dragging) (dragFraction * duration).toLong() else p.positionMs

    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        Text(formatTime(shownPosition), color = Color.White, style = MaterialTheme.typography.bodySmall)
        ThinSeekBar(
            fraction = fraction,
            enabled = seekable,
            dragging = dragging,
            description = "${formatTime(shownPosition)} of ${formatTime(duration)}",
            onDrag = { dragging = true; dragFraction = it; onInteract() },
            onDragEnd = { onSeekTo((dragFraction * duration).toLong()); dragging = false },
            onTap = { onSeekTo((it * duration).toLong()); onInteract() },
            modifier = Modifier.weight(1f).padding(horizontal = 6.dp)
        )
        Text(if (seekable) formatTime(duration) else "LIVE", color = Color.White, style = MaterialTheme.typography.bodySmall)
    }
}

/** 2 dp track with a tiny thumb. Tap or drag anywhere in the 32 dp-high touch area to seek. */
@Composable
private fun ThinSeekBar(
    fraction: Float,
    enabled: Boolean,
    dragging: Boolean,
    description: String,
    onDrag: (Float) -> Unit,
    onDragEnd: () -> Unit,
    onTap: (Float) -> Unit,
    modifier: Modifier = Modifier
) {
    val accent = MaterialTheme.colorScheme.primary
    val trackColor = Color.White.copy(alpha = 0.3f)
    val currentOnDrag by rememberUpdatedState(onDrag)
    val currentOnDragEnd by rememberUpdatedState(onDragEnd)
    val currentOnTap by rememberUpdatedState(onTap)
    var widthPx by remember { mutableFloatStateOf(1f) }

    Box(
        modifier
            .height(32.dp)
            .onSizeChanged { widthPx = it.width.toFloat().coerceAtLeast(1f) }
            .pointerInput(enabled) {
                if (enabled) {
                    detectTapGestures { offset -> currentOnTap((offset.x / widthPx).coerceIn(0f, 1f)) }
                }
            }
            .pointerInput(enabled) {
                if (enabled) {
                    detectHorizontalDragGestures(
                        onDragStart = { offset -> currentOnDrag((offset.x / widthPx).coerceIn(0f, 1f)) },
                        onDragEnd = { currentOnDragEnd() },
                        onDragCancel = { currentOnDragEnd() }
                    ) { change, _ ->
                        change.consume()
                        currentOnDrag((change.position.x / widthPx).coerceIn(0f, 1f))
                    }
                }
            }
            .semantics {
                contentDescription = "Seek bar"
                stateDescription = description
                progressBarRangeInfo = ProgressBarRangeInfo(fraction, 0f..1f)
                setProgress { target -> currentOnTap(target.coerceIn(0f, 1f)); true }
            }
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val centerY = size.height / 2f
            val thickness = 2.dp.toPx()
            val top = centerY - thickness / 2f
            val corner = CornerRadius(thickness / 2f)
            drawRoundRect(trackColor, Offset(0f, top), Size(size.width, thickness), corner)
            val x = size.width * fraction.coerceIn(0f, 1f)
            drawRoundRect(accent, Offset(0f, top), Size(x, thickness), corner)
            if (enabled) {
                drawCircle(accent, radius = (if (dragging) 6.dp else 4.dp).toPx(), center = Offset(x, centerY))
            }
        }
    }
}

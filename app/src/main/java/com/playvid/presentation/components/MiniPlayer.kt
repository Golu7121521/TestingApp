package com.playvid.presentation.components

import android.view.LayoutInflater
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.ui.PlayerView
import com.playvid.R
import com.playvid.domain.model.PlaybackRequest
import com.playvid.playback.PlaybackController

/**
 * Slim bar above the bottom navigation while a video is still loaded but the player screen
 * is closed: a small live video thumbnail (like YouTube's mini player), title, play/pause,
 * close. Tapping the bar returns to the full player.
 */
@Composable
fun MiniPlayer(controller: PlaybackController, onOpen: (PlaybackRequest) -> Unit) {
    val current by controller.current.collectAsStateWithLifecycle()
    val request = current ?: return
    val state by controller.state.collectAsStateWithLifecycle()
    val progress by controller.progress.collectAsStateWithLifecycle()

    val fraction = if (progress.durationMs > 0) {
        (progress.positionMs.toFloat() / progress.durationMs).coerceIn(0f, 1f)
    } else 0f

    Surface(color = MaterialTheme.colorScheme.surfaceContainerHigh) {
        Column {
            LinearProgressIndicator(
                progress = { fraction },
                modifier = Modifier.fillMaxWidth().height(2.dp),
                trackColor = MaterialTheme.colorScheme.outlineVariant
            )
            Row(
                Modifier
                    .fillMaxWidth()
                    .height(54.dp)
                    .clickable(onClickLabel = "Open player") { onOpen(request) }
                    .padding(start = 8.dp, end = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                AndroidView(
                    modifier = Modifier.width(78.dp).height(44.dp).clip(RoundedCornerShape(6.dp)),
                    factory = { ctx ->
                        (LayoutInflater.from(ctx).inflate(R.layout.view_player, null, false) as PlayerView).also {
                            it.player = controller.player
                        }
                    },
                    update = { it.player = controller.player },
                    onRelease = { it.player = null }
                )
                Text(
                    text = request.title,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f).padding(start = 12.dp)
                )
                IconButton(onClick = controller::togglePlay) {
                    Icon(
                        imageVector = if (state.isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                        contentDescription = if (state.isPlaying) "Pause" else "Play"
                    )
                }
                IconButton(onClick = controller::close) {
                    Icon(Icons.Filled.Close, contentDescription = "Close mini player")
                }
            }
        }
    }
}

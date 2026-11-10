package com.playvid.presentation.player

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.dp
import com.playvid.playback.AudioDownloadOption
import com.playvid.playback.DownloadOption
import com.playvid.playback.PlayerUiState
import com.playvid.playback.TrackOption
import com.playvid.presentation.util.speedLabel
import kotlin.math.abs

/** Which list the panel shows. Opened from the small icon row above the seek bar. */
enum class PickerKind { QUALITY, AUDIO, SUBTITLES, SPEED, DOWNLOAD_TYPE, DOWNLOAD, DOWNLOAD_AUDIO }

private val SPEEDS = listOf(0.5f, 0.75f, 1f, 1.25f, 1.5f, 2f)

/**
 * Small picker (quality / audio / subtitles / speed), drawn inside the player itself
 * (not a dialog window) so Android never brings the system bars back while it is open.
 */
@Composable
fun PlayerPickerPanel(
    kind: PickerKind,
    state: PlayerUiState,
    onDismiss: () -> Unit,
    onAutoQuality: () -> Unit,
    onQuality: (TrackOption) -> Unit,
    onAudio: (TrackOption) -> Unit,
    onText: (TrackOption?) -> Unit,
    onSpeed: (Float) -> Unit,
    onDownload: (DownloadOption) -> Unit = {},
    onDownloadAudio: (AudioDownloadOption) -> Unit = {},
    onPickDownloadType: (isAudio: Boolean) -> Unit = {}
) {
    BackHandler { onDismiss() }
    val maxHeight = (LocalConfiguration.current.screenHeightDp * 0.75f).dp
    val title = when (kind) {
        PickerKind.QUALITY -> "Quality"
        PickerKind.AUDIO -> "Audio Track"
        PickerKind.SUBTITLES -> "Subtitles"
        PickerKind.SPEED -> "Playback Speed"
        PickerKind.DOWNLOAD_TYPE -> "Download"
        PickerKind.DOWNLOAD -> "Download \u2014 Video"
        PickerKind.DOWNLOAD_AUDIO -> "Download \u2014 Audio"
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.5f))
            .pointerInput(Unit) { detectTapGestures(onTap = { onDismiss() }) }
    ) {
        Surface(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .widthIn(max = 420.dp)
                .fillMaxWidth()
                .heightIn(max = maxHeight)
                .pointerInput(Unit) { detectTapGestures { } }, // taps inside the panel must not close it
            shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh
        ) {
            Column(Modifier.verticalScroll(rememberScrollState()).padding(vertical = 8.dp)) {
                Text(
                    title,
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                )
                when (kind) {
                    PickerKind.QUALITY -> {
                        OptionRow("Auto", state.isAutoQuality, onAutoQuality)
                        state.qualities.forEach { q -> OptionRow(q.label, q.selected && !state.isAutoQuality) { onQuality(q) } }
                    }
                    PickerKind.AUDIO ->
                        state.audioTracks.forEach { a -> OptionRow(a.label, a.selected) { onAudio(a) } }
                    PickerKind.SUBTITLES -> {
                        OptionRow("Off", !state.textEnabled) { onText(null) }
                        state.textTracks.forEach { t -> OptionRow(t.label, t.selected && state.textEnabled) { onText(t) } }
                    }
                    PickerKind.SPEED ->
                        SPEEDS.forEach { s -> OptionRow(speedLabel(s), abs(state.speed - s) < 0.01f) { onSpeed(s) } }
                    PickerKind.DOWNLOAD_TYPE -> {
                        if (state.downloadOptions.isNotEmpty()) OptionRow("Video", selected = false) { onPickDownloadType(false) }
                        if (state.audioDownloadOptions.isNotEmpty()) OptionRow("Audio", selected = false) { onPickDownloadType(true) }
                    }
                    PickerKind.DOWNLOAD ->
                        state.downloadOptions.forEach { d -> OptionRow(d.label, selected = false) { onDownload(d) } }
                    PickerKind.DOWNLOAD_AUDIO ->
                        state.audioDownloadOptions.forEach { a -> OptionRow(a.label, selected = false) { onDownloadAudio(a) } }
                }
            }
        }
    }
}

@Composable
private fun OptionRow(label: String, selected: Boolean, onClick: () -> Unit) {
    ListItem(
        headlineContent = { Text(label) },
        // A check mark (not only colour) shows the current choice.
        trailingContent = if (selected) {
            { Icon(Icons.Filled.Check, contentDescription = "Selected", tint = MaterialTheme.colorScheme.primary) }
        } else null,
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        modifier = Modifier.clickable(onClick = onClick)
    )
}

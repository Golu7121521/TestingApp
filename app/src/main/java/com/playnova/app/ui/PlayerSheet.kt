@file:OptIn(ExperimentalMaterial3Api::class)

package com.playnova.app.ui

import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.common.Tracks
import com.playnova.app.AppViewModel
import com.playnova.app.data.fmtSize
import com.playnova.app.data.fmtTime
import com.playnova.app.i18n.tr
import java.util.Locale

/** UI state of the player screen that the sheet can change. */
class PlayerUiState(aspect: Int, orient: Int) {
    var aspect by mutableIntStateOf(aspect)
    var orient by mutableIntStateOf(orient)
    var audioOnly by mutableStateOf(false)
}

val AspectLabels = listOf("Fit", "Stretch", "Crop", "Fill width", "Fill height")
val OrientLabels = listOf("Auto", "Landscape", "Portrait")

fun trackLabel(f: androidx.media3.common.Format, n: Int): String {
    val lang = f.language?.takeIf { it != "und" }?.let { Locale(it).displayLanguage }
    val name = f.label ?: lang ?: tr("Track %d", n + 1)
    val extra = buildList {
        f.sampleMimeType?.substringAfter('/')?.uppercase()?.let { add(it) }
        if (f.channelCount > 0) add("${f.channelCount}ch")
    }.joinToString(" ")
    return if (extra.isBlank()) name else "$name  ($extra)"
}

@Composable
private fun Title(t: String) = Text(t, Modifier.padding(top = 14.dp, bottom = 4.dp), style = MaterialTheme.typography.titleMedium)

@Composable
private fun Row1(content: @Composable RowScope.() -> Unit) =
    Row(Modifier.horizontalScroll(rememberScrollState()), content = content)

@Composable
private fun PickRow(label: String, selected: Boolean, onClick: () -> Unit) {
    ListItem(
        headlineContent = { Text(label) },
        trailingContent = { if (selected) Icon(Icons.Outlined.Check, tr("Selected"), tint = MaterialTheme.colorScheme.primary) },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent), modifier = Modifier.clickable(onClick = onClick)
    )
}

@Composable
fun PlayerSheet(vm: AppViewModel, st: PlayerUiState, startPage: String, onDismiss: () -> Unit) {
    val holder = vm.holder
    val player = holder.player
    val ctx = LocalContext.current
    val tracks by holder.tracks.collectAsState()
    val speed by holder.speed.collectAsState()
    val repeat by holder.repeat.collectAsState()
    val shuffle by holder.shuffle.collectAsState()
    val sleepEnd by holder.sleepEnd.collectAsState()
    val a by holder.abA.collectAsState()
    val b by holder.abB.collectAsState()
    val queue by holder.queue.collectAsState()
    val idx by holder.index.collectAsState()
    val cur = queue.getOrNull(idx)
    var page by remember(startPage) { mutableStateOf(startPage) }

    val subPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri != null) {
            val name = runCatching {
                ctx.contentResolver.query(uri, null, null, null, null)?.use { c ->
                    if (c.moveToFirst()) c.getString(c.getColumnIndexOrThrow(OpenableColumns.DISPLAY_NAME)) else null
                }
            }.getOrNull() ?: uri.lastPathSegment ?: "subtitle.srt"
            holder.addSubtitle(uri, name)
            onDismiss()
        }
    }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).padding(bottom = 32.dp)) {
            if (page != "main") {
                TextButton(onClick = { page = "main" }) { Icon(Icons.Outlined.ArrowBack, null); Spacer(Modifier.width(6.dp)); Text(tr("Back")) }
            }
            when (page) {
                "audio" -> {
                    Title(tr("Audio track"))
                    val groups = tracks.groups.filter { it.type == C.TRACK_TYPE_AUDIO }
                    var shown = 0
                    groups.forEach { g ->
                        for (i in 0 until g.length) if (g.isTrackSupported(i)) {
                            PickRow(trackLabel(g.getTrackFormat(i), shown), g.isTrackSelected(i)) { holder.selectAudio(g, i) }
                            shown++
                        }
                    }
                    if (shown == 0) Text(tr("This video has no separate audio tracks."), color = Muted)
                }
                "subs" -> {
                    Title(tr("Subtitles"))
                    val groups = tracks.groups.filter { it.type == C.TRACK_TYPE_TEXT }
                    val anySelected = groups.any { g -> (0 until g.length).any { g.isTrackSelected(it) } }
                    PickRow(tr("Off"), !anySelected) { holder.selectText(null, 0) }
                    var n = 0
                    groups.forEach { g ->
                        for (i in 0 until g.length) if (g.isTrackSupported(i)) {
                            PickRow(trackLabel(g.getTrackFormat(i), n), g.isTrackSelected(i)) { holder.selectText(g, i) }
                            n++
                        }
                    }
                    ListItem(
                        headlineContent = { Text(tr("Open external file (SRT / VTT / ASS)")) }, leadingContent = { Icon(Icons.Outlined.FileOpen, null) },
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                        modifier = Modifier.clickable { subPicker.launch(arrayOf("*/*")) }
                    )
                    Text(tr("Change the style (size, color, background) in Settings > Subtitles."), color = Muted, style = MaterialTheme.typography.bodySmall)
                }
                "info" -> {
                    Title(tr("Playback information"))
                    val vf = player.videoFormat; val af = player.audioFormat
                    val rows = listOf(
                        tr("Name") to (cur?.title ?: "-"),
                        tr("Duration") to fmtTime(if (player.duration > 0) player.duration else cur?.durationMs ?: 0),
                        tr("Size") to fmtSize(cur?.size ?: 0),
                        tr("Resolution") to (if (vf != null && vf.width > 0) "${vf.width}x${vf.height}" else "-"),
                        tr("Video codec") to (vf?.sampleMimeType?.substringAfter('/') ?: "-") + (vf?.codecs?.let { " ($it)" } ?: ""),
                        tr("Frame rate") to (vf?.frameRate?.takeIf { it > 0 }?.let { "%.2f fps".format(it) } ?: "-"),
                        tr("Video bitrate") to (vf?.bitrate?.takeIf { it > 0 }?.let { "${it / 1000} kbps" } ?: "-"),
                        tr("Audio") to (af?.sampleMimeType?.substringAfter('/') ?: "-") + (af?.let { if (it.channelCount > 0) ", ${it.channelCount}ch, ${it.sampleRate}Hz" else "" } ?: "")
                    )
                    rows.forEach { (k, v) -> Column(Modifier.padding(vertical = 4.dp)) { Text(k, color = Muted, style = MaterialTheme.typography.labelSmall); Text(v) } }
                }
                else -> {
                    Title(tr("Speed"))
                    val speeds = listOf(0.25f, 0.5f, 0.75f, 1f, 1.25f, 1.5f, 1.75f, 2f, 3f, 4f)
                    Row1 { speeds.forEach { s -> Chip("${s}x", speed == s) { holder.setSpeed(s) } } }
                    Title(tr("Aspect ratio"))
                    Row1 { AspectLabels.forEachIndexed { i, l -> Chip(tr(l), st.aspect == i) { st.aspect = i } } }
                    Title(tr("Screen orientation"))
                    Row1 { OrientLabels.forEachIndexed { i, l -> Chip(tr(l), st.orient == i) { st.orient = i } } }
                    Title(tr("Repeat"))
                    Row1 {
                        Chip(tr("Off"), repeat == Player.REPEAT_MODE_OFF) { holder.setRepeat(Player.REPEAT_MODE_OFF) }
                        Chip(tr("Current video"), repeat == Player.REPEAT_MODE_ONE) { holder.setRepeat(Player.REPEAT_MODE_ONE) }
                        Chip(tr("Playlist"), repeat == Player.REPEAT_MODE_ALL) { holder.setRepeat(Player.REPEAT_MODE_ALL) }
                    }
                    ListItem(headlineContent = { Text(tr("Shuffle")) }, trailingContent = { Switch(shuffle, { holder.setShuffle(it) }) },
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent))
                    ListItem(headlineContent = { Text(tr("Audio-only mode")) }, supportingContent = { Text(tr("Video off, sound only")) },
                        trailingContent = { Switch(st.audioOnly, { st.audioOnly = it }) }, colors = ListItemDefaults.colors(containerColor = Color.Transparent))
                    Title(tr("Sleep timer"))
                    val left = if (sleepEnd > 0) ((sleepEnd - System.currentTimeMillis()) / 60000 + 1).toInt() else 0
                    Row1 {
                        Chip(tr("Off"), sleepEnd == 0L) { holder.setSleep(0) }
                        listOf(15, 30, 45, 60, 90).forEach { m -> Chip(tr("%d min", m), false) { holder.setSleep(m) } }
                    }
                    if (left > 0) Text(tr("Playback will stop in about %d min", left), color = Muted, style = MaterialTheme.typography.bodySmall)
                    Title(tr("A-B repeat"))
                    Row1 {
                        Chip(if (a >= 0) "A: ${fmtTime(a)}" else tr("Set A"), a >= 0) { holder.markA() }
                        Chip(if (b >= 0) "B: ${fmtTime(b)}" else tr("Set B"), b >= 0) { holder.markB() }
                        if (a >= 0) Chip(tr("Clear"), false) { holder.clearAB() }
                    }
                    Title(tr("Tracks and info"))
                    ListItem(headlineContent = { Text(tr("Audio track")) }, leadingContent = { Icon(Icons.Outlined.Audiotrack, null) },
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent), modifier = Modifier.clickable { page = "audio" })
                    ListItem(headlineContent = { Text(tr("Subtitles")) }, leadingContent = { Icon(Icons.Outlined.Subtitles, null) },
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent), modifier = Modifier.clickable { page = "subs" })
                    ListItem(headlineContent = { Text(tr("Playback information")) }, leadingContent = { Icon(Icons.Outlined.Info, null) },
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent), modifier = Modifier.clickable { page = "info" })
                }
            }
        }
    }
}

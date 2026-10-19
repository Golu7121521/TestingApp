@file:OptIn(ExperimentalMaterial3Api::class)

package com.playnova.app.ui

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.imageLoader
import com.playnova.app.AppViewModel
import com.playnova.app.data.Prefs
import com.playnova.app.data.fmtSize
import com.playnova.app.i18n.Lang
import com.playnova.app.i18n.tr

fun subColorOf(i: Int) = when (i) { 1 -> Color(0xFFFFEB3B); 2 -> Color(0xFF4DD0E1); else -> Color.White }

@Composable
private fun Group(title: String, content: @Composable ColumnScope.() -> Unit) {
    Text(title, Modifier.padding(start = 16.dp, top = 20.dp, bottom = 6.dp), color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.titleMedium)
    Column(Modifier.padding(horizontal = 12.dp).clip(CardShape).background(MaterialTheme.colorScheme.surface).padding(vertical = 4.dp), content = content)
}

@Composable
private fun SwitchRow(title: String, sub: String? = null, checked: Boolean, onChange: (Boolean) -> Unit) {
    ListItem(
        headlineContent = { Text(title) }, supportingContent = sub?.let { { Text(it) } },
        trailingContent = { Switch(checked, onChange) },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        modifier = Modifier.clickable { onChange(!checked) }
    )
}

@Composable
private fun ChoiceRow(title: String, labels: List<String>, sel: Int, onSel: (Int) -> Unit) {
    Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
        Text(title)
        Row(Modifier.horizontalScroll(rememberScrollState()).padding(top = 6.dp)) {
            labels.forEachIndexed { i, l -> Chip(l, i == sel) { onSel(i) } }
        }
    }
}

@Composable
private fun SliderRow(title: String, value: Float, range: ClosedFloatingPointRange<Float>, label: String, onChange: (Float) -> Unit) {
    Column(Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
        Row { Text(title, Modifier.weight(1f)); Text(label, color = Muted) }
        Slider(value, onChange, valueRange = range)
    }
}

@Composable
private fun LinkRow(icon: ImageVector, title: String, sub: String? = null, onClick: () -> Unit) {
    ListItem(
        headlineContent = { Text(title) }, supportingContent = sub?.let { { Text(it) } }, leadingContent = { Icon(icon, null) },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent), modifier = Modifier.clickable(onClick = onClick)
    )
}

@Composable
fun SettingsScreen(vm: AppViewModel, onStorage: () -> Unit) {
    val p by vm.settings.state.collectAsState()
    val ctx = LocalContext.current
    fun up(f: (Prefs) -> Prefs) = vm.settings.update(f)
    var confirmHistory by remember { mutableStateOf(false) }
    var dialog by remember { mutableStateOf("") }
    val speeds = listOf(0.5f, 0.75f, 1f, 1.25f, 1.5f, 2f)
    val skips = listOf(5, 10, 15, 30)
    val lpSpeeds = listOf(1.5f, 2f, 3f, 4f)

    Column(Modifier.fillMaxSize()) {
        ScreenTitle(tr("Settings"))
        Column(Modifier.verticalScroll(rememberScrollState()).padding(bottom = 24.dp)) {
            Group(tr("Playback")) {
                ChoiceRow(tr("Default speed"), speeds.map { "${it}x" }, speeds.indexOf(p.defaultSpeed).coerceAtLeast(2)) { up { s -> s.copy(defaultSpeed = speeds[it]) } }
                SwitchRow(tr("Resume from last position"), tr("Remembers the position of every video"), p.resume) { up { s -> s.copy(resume = it) } }
                SwitchRow(tr("Auto-play next video"), null, p.autoNext) { up { s -> s.copy(autoNext = it) } }
                ChoiceRow(tr("Screen orientation"), listOf(tr("Auto"), tr("Landscape"), tr("Portrait")), p.orientation) { up { s -> s.copy(orientation = it) } }
                ChoiceRow(tr("Aspect ratio"), listOf(tr("Fit"), tr("Stretch"), tr("Crop"), tr("Fill width"), tr("Fill height")), p.aspect) { up { s -> s.copy(aspect = it) } }
                ChoiceRow(tr("Skip interval"), skips.map { "${it}s" }, skips.indexOf(p.skipSec).coerceAtLeast(1)) { up { s -> s.copy(skipSec = skips[it]) } }
                SwitchRow(tr("Auto picture-in-picture"), tr("Floating window when you leave the player"), p.autoPip) { up { s -> s.copy(autoPip = it) } }
            }
            Group(tr("Headphones")) {
                SwitchRow(
                    tr("Headset button control"),
                    tr("Press once: play or pause. Press twice: skip forward. Press three times: skip back."),
                    p.headset
                ) { up { s -> s.copy(headset = it) } }
            }
            Group(tr("Gestures")) {
                SwitchRow(tr("Brightness (left swipe)"), null, p.gBrightness) { up { s -> s.copy(gBrightness = it) } }
                SwitchRow(tr("Volume (right swipe)"), null, p.gVolume) { up { s -> s.copy(gVolume = it) } }
                SwitchRow(tr("Seek (horizontal swipe)"), null, p.gSeek) { up { s -> s.copy(gSeek = it) } }
                SwitchRow(tr("Double-tap seek"), tr("The skip interval is set under Playback"), p.gDoubleTap) { up { s -> s.copy(gDoubleTap = it) } }
                SwitchRow(tr("Long-press speed"), null, p.gLongPress) { up { s -> s.copy(gLongPress = it) } }
                ChoiceRow(tr("Long-press speed value"), lpSpeeds.map { "${it}x" }, lpSpeeds.indexOf(p.longPressSpeed).coerceAtLeast(1)) { up { s -> s.copy(longPressSpeed = lpSpeeds[it]) } }
                SliderRow(tr("Gesture sensitivity"), p.sensitivity, 0.5f..2f, "%.1fx".format(p.sensitivity)) { up { s -> s.copy(sensitivity = it) } }
            }
            Group(tr("Subtitles")) {
                SliderRow(tr("Font size"), p.subSize, 0.6f..2f, "%.1fx".format(p.subSize)) { up { s -> s.copy(subSize = it) } }
                ChoiceRow(tr("Font color"), listOf(tr("White"), tr("Yellow"), tr("Cyan")), p.subColor) { up { s -> s.copy(subColor = it) } }
                SliderRow(tr("Background opacity"), p.subBg, 0f..1f, "${(p.subBg * 100).toInt()}%") { up { s -> s.copy(subBg = it) } }
                Box(Modifier.fillMaxWidth().padding(16.dp).clip(SmallShape).background(Color(0xFF3B4252)).padding(vertical = 24.dp), contentAlignment = Alignment.BottomCenter) {
                    Text(tr("Subtitle preview: PlayNova"), color = subColorOf(p.subColor), fontSize = (16 * p.subSize).sp,
                        modifier = Modifier.background(Color.Black.copy(alpha = p.subBg)).padding(horizontal = 8.dp, vertical = 2.dp))
                }
            }
            Group(tr("Interface")) {
                ChoiceRow(tr("Theme"), listOf(tr("Dark"), tr("Light"), tr("System")), p.theme) { up { s -> s.copy(theme = it) } }
                Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                    Text(tr("Accent color"))
                    Text(tr("The app icon changes to match when you leave the app."), color = Muted, style = MaterialTheme.typography.bodySmall)
                    Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Accents.forEachIndexed { i, (name, c) ->
                            Box(
                                Modifier.size(40.dp).clip(CircleShape).background(c)
                                    .then(if (i == p.accent) Modifier.border(3.dp, MaterialTheme.colorScheme.onSurface, CircleShape) else Modifier)
                                    .clickable { up { s -> s.copy(accent = i) } }.then(sem(tr(name) + if (i == p.accent) " " + tr("selected") else "")),
                                contentAlignment = Alignment.Center
                            ) { if (i == p.accent) Icon(Icons.Outlined.Check, null, tint = Color.White) }
                        }
                    }
                }
                SwitchRow(tr("Library default: grid view"), tr("Off = list view"), p.gridDefault) { up { s -> s.copy(gridDefault = it) } }
                LinkRow(Icons.Outlined.Language, tr("Language"), Lang.options.firstOrNull { it.first == p.lang }?.second?.let { if (p.lang == "system") tr("System default") else it }) { dialog = "language" }
            }
            Group(tr("Privacy and Storage")) {
                LinkRow(Icons.Outlined.Storage, tr("Storage and permissions"), tr("Cache, permission status"), onStorage)
                LinkRow(Icons.Outlined.History, tr("Clear playback history")) { confirmHistory = true }
                LinkRow(Icons.Outlined.SearchOff, tr("Clear search history")) { vm.clearSearches(); Toast.makeText(ctx, tr("Search history cleared"), Toast.LENGTH_SHORT).show() }
                LinkRow(Icons.Outlined.PrivacyTip, tr("Privacy policy")) { dialog = "privacy" }
            }
            Group(tr("About")) {
                val ver = remember { runCatching { ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName }.getOrDefault("1.0") }
                LinkRow(Icons.Outlined.Info, "PlayNova", tr("Version %s", ver)) {}
                LinkRow(Icons.Outlined.Star, tr("Features: what works and what is missing")) { dialog = "features" }
                LinkRow(Icons.Outlined.Description, tr("Open-source licenses")) { dialog = "licenses" }
            }
        }
    }
    if (confirmHistory) ConfirmDialog(tr("Clear history?"), tr("Continue Watching and History will be emptied. No videos are deleted."), tr("Clear"), danger = true,
        onConfirm = { vm.clearHistory() }, onDismiss = { confirmHistory = false })
    if (dialog == "language") {
        AlertDialog(
            onDismissRequest = { dialog = "" }, title = { Text(tr("Language")) },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    Lang.options.forEach { (code, name) ->
                        ListItem(
                            headlineContent = { Text(if (code == "system") tr("System default") else name) },
                            trailingContent = { if (p.lang == code) Icon(Icons.Outlined.Check, tr("Selected"), tint = MaterialTheme.colorScheme.primary) },
                            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                            modifier = Modifier.clickable {
                                dialog = ""
                                if (p.lang != code) {
                                    up { s -> s.copy(lang = code) }
                                    Lang.init(code)
                                    ctx.findActivity()?.recreate()
                                }
                            }
                        )
                    }
                }
            },
            confirmButton = {}, dismissButton = { TextButton(onClick = { dialog = "" }) { Text(tr("Cancel")) } }
        )
    } else if (dialog.isNotEmpty()) {
        val (t, body) = when (dialog) {
            "privacy" -> tr("Privacy policy") to tr("PlayNova does not collect or upload any data. Playlists, favorites, history and settings are saved only on your phone. The internet permission is used only when you play a stream URL yourself.")
            "licenses" -> tr("Open-source licenses") to "Apache License 2.0:\n- AndroidX (Compose, Room, Navigation, Lifecycle)\n- AndroidX Media3 (ExoPlayer)\n- Coil\n- Kotlin / kotlinx.coroutines"
            else -> tr("Features") to tr("WORKS:\nVideo playback with hardware decoding, resume, gestures, speed, aspect ratio, audio and subtitle track selection, external subtitles (SRT, VTT, ASS), repeat, shuffle, sleep timer, A-B repeat, picture-in-picture, mini-player, library (grid, list, sort, filter, multi-select), folders, search, playlists, favorites, history, network streams (HTTP, HTTPS, HLS, DASH, RTSP), headset button control, themes, accent colors, dynamic app icon and 8 languages.\n\nNOT AVAILABLE YET:\nBackground audio service, subtitle and audio delay, subtitle auto-detect (Android storage limit), file rename, drag-and-drop reorder (arrows are used), equalizer, bookmarks, SMB/WebDAV, iOS version.")
        }
        AlertDialog(onDismissRequest = { dialog = "" }, title = { Text(t) }, text = { Text(body, Modifier.verticalScroll(rememberScrollState())) },
            confirmButton = { TextButton(onClick = { dialog = "" }) { Text(tr("OK")) } })
    }
}

@Composable
fun StorageScreen(vm: AppViewModel, onBack: () -> Unit) {
    val ctx = LocalContext.current
    var granted by remember { mutableStateOf(hasVideoPermission(ctx)) }
    val scan = rememberScanAction(vm) { granted = it }
    val hidden by vm.settings.hidden.collectAsState()
    var tick by remember { mutableStateOf(0) }
    var confirmPos by remember { mutableStateOf(false) }
    val cacheSize = remember(tick) { ctx.imageLoader.diskCache?.size ?: 0L }
    val dbSize = remember(tick) { ctx.getDatabasePath("playnova.db").let { if (it.exists()) it.length() else 0L } }
    val videos by vm.videos.collectAsState()
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        ScreenTitle(tr("Storage and permissions"), onBack)
        Group(tr("Permission")) {
            ListItem(
                headlineContent = { Text(tr("Video access")) },
                supportingContent = { Text(if (granted) tr("Granted") else tr("Not granted. The library will be empty, but you can still play files with Open file.")) },
                leadingContent = { Icon(if (granted) Icons.Outlined.CheckCircle else Icons.Outlined.Block, null) },
                colors = ListItemDefaults.colors(containerColor = Color.Transparent)
            )
            if (!granted) LinkRow(Icons.Outlined.LockOpen, tr("Grant permission")) { scan() }
            LinkRow(Icons.Outlined.Settings, tr("Open system app settings")) {
                ctx.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", ctx.packageName, null)))
                granted = hasVideoPermission(ctx)
            }
        }
        Group(tr("Storage usage")) {
            ListItem(headlineContent = { Text(tr("Thumbnail cache")) }, supportingContent = { Text(fmtSize(cacheSize).let { if (it == "-") "0 MB" else it }) }, colors = ListItemDefaults.colors(containerColor = Color.Transparent))
            ListItem(headlineContent = { Text(tr("App database (playlists, history)")) }, supportingContent = { Text("%.1f KB".format(dbSize / 1024.0)) }, colors = ListItemDefaults.colors(containerColor = Color.Transparent))
            ListItem(headlineContent = { Text(tr("Videos in library")) }, supportingContent = { Text(tr("%d videos", videos.size)) }, colors = ListItemDefaults.colors(containerColor = Color.Transparent))
        }
        Group(tr("Manage")) {
            LinkRow(Icons.Outlined.Image, tr("Clear thumbnail cache")) {
                ctx.imageLoader.diskCache?.clear(); ctx.imageLoader.memoryCache?.clear(); tick++
                Toast.makeText(ctx, tr("Cache cleared"), Toast.LENGTH_SHORT).show()
            }
            LinkRow(Icons.Outlined.RestartAlt, tr("Reset saved playback positions"), tr("All videos will start from the beginning")) { confirmPos = true }
            LinkRow(Icons.Outlined.Visibility, tr("Restore removed videos"), tr("%d videos were removed from the library", hidden.size)) {
                vm.settings.unhideAll(); Toast.makeText(ctx, tr("Restored"), Toast.LENGTH_SHORT).show()
            }
        }
    }
    if (confirmPos) ConfirmDialog(tr("Reset positions?"), tr("Resume positions will be forgotten. The history list and videos stay the same."), tr("Reset"), danger = true,
        onConfirm = { vm.resetPositions() }, onDismiss = { confirmPos = false })
}

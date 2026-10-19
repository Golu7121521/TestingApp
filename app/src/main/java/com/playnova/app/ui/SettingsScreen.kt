@file:OptIn(ExperimentalMaterial3Api::class)

package com.playnova.app.ui

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.border
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.imageLoader
import com.playnova.app.AppViewModel
import com.playnova.app.data.Prefs
import com.playnova.app.data.fmtSize
import java.io.File

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
private fun LinkRow(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, sub: String? = null, onClick: () -> Unit) {
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
        ScreenTitle("Settings")
        Column(Modifier.verticalScroll(rememberScrollState()).padding(bottom = 24.dp)) {
            Group("Playback") {
                ChoiceRow("Default speed", speeds.map { "${it}x" }, speeds.indexOf(p.defaultSpeed).coerceAtLeast(2)) { up { s -> s.copy(defaultSpeed = speeds[it]) } }
                SwitchRow("Last position se resume", "Har video ki position yaad rehti hai", p.resume) { up { s -> s.copy(resume = it) } }
                SwitchRow("Agla video auto-play", null, p.autoNext) { up { s -> s.copy(autoNext = it) } }
                ChoiceRow("Screen orientation", listOf("Auto", "Landscape", "Portrait"), p.orientation) { up { s -> s.copy(orientation = it) } }
                ChoiceRow("Aspect ratio", listOf("Fit", "Stretch", "Crop", "Fill width", "Fill height"), p.aspect) { up { s -> s.copy(aspect = it) } }
                ChoiceRow("Skip interval", skips.map { "${it}s" }, skips.indexOf(p.skipSec).coerceAtLeast(1)) { up { s -> s.copy(skipSec = skips[it]) } }
                SwitchRow("Auto picture-in-picture", "Player se home dabane par floating window", p.autoPip) { up { s -> s.copy(autoPip = it) } }
            }
            Group("Gestures") {
                SwitchRow("Brightness (left swipe)", null, p.gBrightness) { up { s -> s.copy(gBrightness = it) } }
                SwitchRow("Volume (right swipe)", null, p.gVolume) { up { s -> s.copy(gVolume = it) } }
                SwitchRow("Seek (horizontal swipe)", null, p.gSeek) { up { s -> s.copy(gSeek = it) } }
                SwitchRow("Double-tap seek", "Skip interval upar Playback mein set hota hai", p.gDoubleTap) { up { s -> s.copy(gDoubleTap = it) } }
                SwitchRow("Long-press speed", null, p.gLongPress) { up { s -> s.copy(gLongPress = it) } }
                ChoiceRow("Long-press speed value", lpSpeeds.map { "${it}x" }, lpSpeeds.indexOf(p.longPressSpeed).coerceAtLeast(1)) { up { s -> s.copy(longPressSpeed = lpSpeeds[it]) } }
                SliderRow("Gesture sensitivity", p.sensitivity, 0.5f..2f, "%.1fx".format(p.sensitivity)) { up { s -> s.copy(sensitivity = it) } }
            }
            Group("Subtitles") {
                SliderRow("Font size", p.subSize, 0.6f..2f, "%.1fx".format(p.subSize)) { up { s -> s.copy(subSize = it) } }
                ChoiceRow("Font color", listOf("Safed", "Peela", "Cyan"), p.subColor) { up { s -> s.copy(subColor = it) } }
                SliderRow("Background opacity", p.subBg, 0f..1f, "${(p.subBg * 100).toInt()}%") { up { s -> s.copy(subBg = it) } }
                Box(Modifier.fillMaxWidth().padding(16.dp).clip(SmallShape).background(Color(0xFF3B4252)).padding(vertical = 24.dp), contentAlignment = Alignment.BottomCenter) {
                    Text("Subtitle preview: PlayNova", color = subColorOf(p.subColor), fontSize = (16 * p.subSize).sp,
                        modifier = Modifier.background(Color.Black.copy(alpha = p.subBg)).padding(horizontal = 8.dp, vertical = 2.dp))
                }
            }
            Group("Interface") {
                ChoiceRow("Theme", listOf("Dark", "Light", "System"), p.theme) { up { s -> s.copy(theme = it) } }
                Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                    Text("Accent color")
                    Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Accents.forEachIndexed { i, (name, c) ->
                            Box(
                                Modifier.size(40.dp).clip(CircleShape).background(c)
                                    .then(if (i == p.accent) Modifier.border(3.dp, MaterialTheme.colorScheme.onSurface, CircleShape) else Modifier)
                                    .clickable { up { s -> s.copy(accent = i) } }.then(sem(name + if (i == p.accent) " selected" else "")),
                                contentAlignment = Alignment.Center
                            ) { if (i == p.accent) Icon(Icons.Outlined.Check, null, tint = Color.White) }
                        }
                    }
                }
                SwitchRow("Library default: grid view", "Off = list view", p.gridDefault) { up { s -> s.copy(gridDefault = it) } }
            }
            Group("Privacy aur Storage") {
                LinkRow(Icons.Outlined.Storage, "Storage aur permissions", "Cache, permission status", onStorage)
                LinkRow(Icons.Outlined.History, "Playback history saaf karo") { confirmHistory = true }
                LinkRow(Icons.Outlined.SearchOff, "Search history saaf karo") { vm.clearSearches(); Toast.makeText(ctx, "Search history saaf", Toast.LENGTH_SHORT).show() }
                LinkRow(Icons.Outlined.PrivacyTip, "Privacy policy") { dialog = "privacy" }
            }
            Group("About") {
                val ver = remember { runCatching { ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName }.getOrDefault("1.0") }
                LinkRow(Icons.Outlined.Info, "PlayNova", "Version $ver") {}
                LinkRow(Icons.Outlined.Star, "Features: kya chalta hai, kya baaki hai") { dialog = "features" }
                LinkRow(Icons.Outlined.Description, "Open-source licenses") { dialog = "licenses" }
            }
        }
    }
    if (confirmHistory) ConfirmDialog("History saaf karein?", "Continue Watching aur History khaali ho jayengi. Videos delete nahi honge.", "Saaf karo", danger = true,
        onConfirm = { vm.clearHistory() }, onDismiss = { confirmHistory = false })
    if (dialog.isNotEmpty()) {
        val (t, body) = when (dialog) {
            "privacy" -> "Privacy policy" to "PlayNova koi data collect ya upload nahi karta. Playlists, favorites, history aur settings sirf aapke phone mein save hote hain. Internet permission sirf tab use hoti hai jab aap khud koi stream URL chalate ho."
            "licenses" -> "Open-source licenses" to "Apache License 2.0:\n- AndroidX (Compose, Room, Navigation, Lifecycle)\n- AndroidX Media3 (ExoPlayer)\n- Coil\n- Kotlin / kotlinx.coroutines"
            else -> "Features" to "CHALTA HAI:\nVideo playback (hardware decoding), resume, gestures, speed, aspect ratio, audio/subtitle track select, external subtitle (SRT/VTT/ASS), repeat, shuffle, sleep timer, A-B repeat, PiP, mini-player, library (grid/list/sort/filter/multi-select), folders, search, playlists, favorites, history, network stream URL, themes.\n\nABHI NAHI HAI:\nBackground audio service, subtitle/audio delay, subtitle auto-detect (Android storage limit), file rename, drag-drop reorder (arrows se hota hai), equalizer, bookmarks, SMB/WebDAV, iOS version, language selection."
        }
        AlertDialog(onDismissRequest = { dialog = "" }, title = { Text(t) }, text = { Text(body, Modifier.verticalScroll(rememberScrollState())) },
            confirmButton = { TextButton(onClick = { dialog = "" }) { Text("OK") } })
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
        ScreenTitle("Storage aur permissions", onBack)
        Group("Permission") {
            ListItem(
                headlineContent = { Text("Video access") },
                supportingContent = { Text(if (granted) "Mili hui hai" else "Nahi mili. Library khaali dikhegi, par 'File kholo' se video chal sakta hai.") },
                leadingContent = { Icon(if (granted) Icons.Outlined.CheckCircle else Icons.Outlined.Block, null) },
                colors = ListItemDefaults.colors(containerColor = Color.Transparent)
            )
            if (!granted) LinkRow(Icons.Outlined.LockOpen, "Permission do") { scan() }
            LinkRow(Icons.Outlined.Settings, "System app settings kholo") {
                ctx.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", ctx.packageName, null)))
                granted = hasVideoPermission(ctx)
            }
        }
        Group("Storage usage") {
            ListItem(headlineContent = { Text("Thumbnail cache") }, supportingContent = { Text(fmtSize(cacheSize).let { if (it == "-") "0 MB" else it }) }, colors = ListItemDefaults.colors(containerColor = Color.Transparent))
            ListItem(headlineContent = { Text("App database (playlists, history)") }, supportingContent = { Text("%.1f KB".format(dbSize / 1024.0)) }, colors = ListItemDefaults.colors(containerColor = Color.Transparent))
            ListItem(headlineContent = { Text("Library mein videos") }, supportingContent = { Text("${videos.size} videos") }, colors = ListItemDefaults.colors(containerColor = Color.Transparent))
        }
        Group("Manage") {
            LinkRow(Icons.Outlined.Image, "Thumbnail cache saaf karo") {
                ctx.imageLoader.diskCache?.clear(); ctx.imageLoader.memoryCache?.clear(); tick++
                Toast.makeText(ctx, "Cache saaf", Toast.LENGTH_SHORT).show()
            }
            LinkRow(Icons.Outlined.RestartAlt, "Saved playback positions reset karo", "Sab videos shuru se chalenge") { confirmPos = true }
            LinkRow(Icons.Outlined.Visibility, "Hatayi hui videos wapas lao", "${hidden.size} video library se hatayi gayi hain") {
                vm.settings.unhideAll(); Toast.makeText(ctx, "Wapas aa gayi", Toast.LENGTH_SHORT).show()
            }
        }
    }
    if (confirmPos) ConfirmDialog("Positions reset karein?", "Resume position bhool jayegi. History list aur videos same rahenge.", "Reset", danger = true,
        onConfirm = { vm.resetPositions() }, onDismiss = { confirmPos = false })
}

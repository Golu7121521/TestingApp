package com.netplayer.app

import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

val Blue = Color(0xFF2979FF)

private val Scheme = darkColorScheme(
    primary = Blue, onPrimary = Color.White, secondary = Blue,
    background = Color(0xFF140E0D), onBackground = Color(0xFFD9C8C5),
    surface = Color(0xFF140E0D), onSurface = Color(0xFFD9C8C5),
    onSurfaceVariant = Color(0xFFB9A4A1), outline = Color(0xFF6B5753),
    outlineVariant = Color(0xFF4A3B38),
    surfaceContainer = Color(0xFF211716), surfaceContainerHigh = Color(0xFF2A1E1D),
    surfaceContainerHighest = Color(0xFF332524)
)

@Composable
fun AppTheme(content: @Composable () -> Unit) = MaterialTheme(colorScheme = Scheme, content = content)

@Composable
private fun Field(
    label: String, value: String, onChange: (String) -> Unit,
    keyboard: KeyboardType = KeyboardType.Text, trailing: (@Composable () -> Unit)? = null
) {
    OutlinedTextField(
        value = value, onValueChange = onChange, label = { Text(label) }, singleLine = true,
        shape = RoundedCornerShape(8.dp), trailingIcon = trailing,
        keyboardOptions = KeyboardOptions(keyboardType = keyboard),
        colors = OutlinedTextFieldDefaults.colors(
            unfocusedBorderColor = MaterialTheme.colorScheme.outline,
            focusedBorderColor = Blue, focusedLabelColor = Blue, cursorColor = Blue
        ),
        modifier = Modifier.fillMaxWidth().padding(bottom = 18.dp)
    )
}

@Composable
private fun Dropdown(label: String, value: String, options: List<String>, modifier: Modifier, onSelect: (String) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box(modifier) {
        OutlinedTextField(
            value = value, onValueChange = {}, readOnly = true, label = { Text(label) },
            trailingIcon = { Icon(Icons.Filled.ArrowDropDown, null) },
            shape = RoundedCornerShape(8.dp),
            colors = OutlinedTextFieldDefaults.colors(
                unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                focusedBorderColor = Blue, focusedLabelColor = Blue
            ),
            modifier = Modifier.fillMaxWidth()
        )
        Box(Modifier.matchParentSize().clickable { open = true })
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            options.forEach { o -> DropdownMenuItem(text = { Text(o) }, onClick = { onSelect(o); open = false }) }
        }
    }
}

@Composable
fun HomeScreen(
    req: StreamRequest, onReq: (StreamRequest) -> Unit,
    onHistory: () -> Unit, onSettings: () -> Unit, onPlay: () -> Unit
) {
    val ctx = LocalContext.current
    val clip = LocalClipboardManager.current
    Box(Modifier.fillMaxSize().systemBarsPadding().imePadding()) {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)) {
            Row(Modifier.fillMaxWidth().padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "Network Stream (Video) Player", fontSize = 22.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f)
                )
                IconButton(onSettings) { Icon(Icons.Filled.Settings, "Settings", tint = MaterialTheme.colorScheme.onSurfaceVariant) }
                IconButton(onHistory) { Icon(Icons.Filled.History, "History", tint = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
            Spacer(Modifier.height(12.dp))
            Field("Media Stream URL", req.url, { onReq(req.copy(url = it)) }, KeyboardType.Uri) {
                IconButton({ clip.getText()?.text?.let { onReq(req.copy(url = it.trim())) } }) {
                    Icon(Icons.Filled.ContentPaste, "Paste")
                }
            }
            Field("Cookie Value", req.cookie, { onReq(req.copy(cookie = it)) })
            Field("Referer Value", req.referer, { onReq(req.copy(referer = it)) }, KeyboardType.Uri)
            Field("Origin Value", req.origin, { onReq(req.copy(origin = it)) }, KeyboardType.Uri)
            Field("DRM License URL", req.drmUrl, { onReq(req.copy(drmUrl = it)) }, KeyboardType.Uri)
            Row(Modifier.fillMaxWidth().padding(bottom = 18.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                Dropdown("UserAgent", req.uaMode, UA_OPTIONS.keys.toList(), Modifier.weight(1f)) { onReq(req.copy(uaMode = it)) }
                Dropdown("DrmScheme", req.drmScheme, DRM_SCHEMES, Modifier.weight(1f)) { onReq(req.copy(drmScheme = it)) }
            }
            if (req.uaMode == "Custom") {
                Field("Custom User-Agent (optional)", req.customUa, { onReq(req.copy(customUa = it)) })
            }
            Spacer(Modifier.height(100.dp))
        }
        FloatingActionButton(
            onClick = {
                if (req.url.isBlank()) Toast.makeText(ctx, "Media Stream URL daalo", Toast.LENGTH_SHORT).show()
                else onPlay()
            },
            containerColor = Blue, contentColor = Color.White, shape = RoundedCornerShape(24.dp),
            modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp).size(76.dp)
        ) { Icon(Icons.Filled.PlayArrow, "Play", modifier = Modifier.size(40.dp)) }
    }
}

@Composable
fun HistoryScreen(store: HistoryStore, onBack: () -> Unit, onPick: (StreamRequest) -> Unit) {
    var items by remember { mutableStateOf(store.load()) }
    var sort by remember { mutableIntStateOf(0) }
    var sortMenu by remember { mutableStateOf(false) }
    var confirm by remember { mutableStateOf(false) }
    val sorted = when (sort) {
        0 -> items
        1 -> items.reversed()
        else -> items.sortedBy { it.req.url.lowercase() }
    }
    val tint = MaterialTheme.colorScheme.onSurfaceVariant
    Column(Modifier.fillMaxSize().systemBarsPadding().padding(horizontal = 16.dp)) {
        Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = tint) }
            Text("History", fontSize = 30.sp, color = tint, modifier = Modifier.weight(1f).padding(start = 8.dp))
            Box {
                IconButton({ sortMenu = true }) { Icon(Icons.AutoMirrored.Filled.Sort, "Sort", tint = tint) }
                DropdownMenu(sortMenu, { sortMenu = false }) {
                    listOf("Newest first", "Oldest first", "A → Z").forEachIndexed { i, s ->
                        DropdownMenuItem(text = { Text(s) }, onClick = { sort = i; sortMenu = false })
                    }
                }
            }
            IconButton({ confirm = true }) { Icon(Icons.Filled.DeleteSweep, "Clear all", tint = tint) }
        }
        if (sorted.isEmpty()) {
            Text("Abhi koi history nahi hai", color = tint, modifier = Modifier.padding(24.dp))
        }
        LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp), contentPadding = PaddingValues(top = 12.dp, bottom = 24.dp)) {
            items(sorted, key = { it.req.url }) { h ->
                val shape = RoundedCornerShape(16.dp)
                Row(
                    Modifier.fillMaxWidth().clip(shape)
                        .border(BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant), shape)
                        .clickable { onPick(h.req) }
                        .padding(start = 20.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(h.req.url, Modifier.weight(1f), maxLines = 2, overflow = TextOverflow.Ellipsis, fontSize = 17.sp, color = tint)
                    IconButton({ store.remove(h.req.url); items = store.load() }) { Icon(Icons.Filled.Delete, "Delete", tint = tint) }
                }
            }
        }
    }
    if (confirm) {
        AlertDialog(
            onDismissRequest = { confirm = false },
            title = { Text("Sab history delete karein?") },
            confirmButton = { TextButton({ store.clear(); items = emptyList(); confirm = false }) { Text("Delete") } },
            dismissButton = { TextButton({ confirm = false }) { Text("Cancel") } }
        )
    }
}

@Composable
fun SettingsDialog(prefs: GesturePrefs, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Gesture settings") },
        text = {
            Column {
                GESTURES.forEach { (k, label) ->
                    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(label, Modifier.weight(1f))
                        Switch(checked = prefs.get(k), onCheckedChange = { prefs.set(k, it) })
                    }
                }
            }
        },
        confirmButton = { TextButton(onDismiss) { Text("Done") } }
    )
}

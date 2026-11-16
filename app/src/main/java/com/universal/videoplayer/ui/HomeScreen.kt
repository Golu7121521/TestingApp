package com.universal.videoplayer.ui

import android.content.ClipboardManager
import android.widget.Toast
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.universal.videoplayer.data.Keys
import com.universal.videoplayer.data.ThemeMode
import com.universal.videoplayer.resolver.PlaybackRequest
import com.universal.videoplayer.utils.UrlValidator
import com.universal.videoplayer.utils.fmtTime

fun readClipboardText(ctx: android.content.Context): String? =
    runCatching {
        ctx.getSystemService(ClipboardManager::class.java).primaryClip
            ?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.coerceToText(ctx)?.toString()
    }.getOrNull()

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(vm: AppViewModel, actions: Actions, onSettings: () -> Unit) {
    val ctx = LocalContext.current
    val history by vm.history.collectAsStateWithLifecycleCompat()
    val settings by vm.settings.collectAsStateWithLifecycleCompat()
    val systemDark = isSystemInDarkTheme()
    var error by remember { mutableStateOf<String?>(null) }
    val effectiveDark = when (settings.theme) {
        ThemeMode.DARK -> true
        ThemeMode.LIGHT -> false
        ThemeMode.SYSTEM -> systemDark
    }

    fun paste() {
        val t = readClipboardText(ctx)
        if (t.isNullOrBlank()) Toast.makeText(ctx, "Clipboard is empty", Toast.LENGTH_SHORT).show()
        else { vm.homeUrl = t.trim(); error = null }
    }

    fun play() {
        val u = UrlValidator.normalize(vm.homeUrl)
        if (u == null) error = "Enter a valid http(s) video link."
        else { error = null; actions.play(PlaybackRequest(url = u), true) }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.PlayArrow, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(28.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Universal Video Player", style = MaterialTheme.typography.titleMedium)
                    }
                },
                actions = {
                    IconButton({ paste() }) { Icon(Icons.Filled.ContentPaste, "Paste link") }
                    IconButton({
                        vm.set(Keys.THEME, (if (effectiveDark) ThemeMode.LIGHT else ThemeMode.DARK).name)
                    }) {
                        Icon(if (effectiveDark) Icons.Filled.LightMode else Icons.Filled.DarkMode, "Toggle theme")
                    }
                    IconButton(onSettings) { Icon(Icons.Filled.Settings, "Settings") }
                },
            )
        },
    ) { pad ->
        LazyColumn(
            Modifier.fillMaxSize().padding(pad),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item {
                Card(
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                ) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        OutlinedTextField(
                            value = vm.homeUrl,
                            onValueChange = { vm.homeUrl = it; error = null },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(20.dp),
                            placeholder = { Text("Paste your video link here...") },
                            isError = error != null,
                            supportingText = { error?.let { Text(it) } },
                            maxLines = 3,
                        )
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton({ paste() }, Modifier.weight(1f)) { Text("Paste") }
                            Button({ play() }, Modifier.weight(1f), enabled = vm.homeUrl.isNotBlank()) { Text("Play") }
                            TextButton({ vm.homeUrl = ""; error = null }, Modifier.weight(1f), enabled = vm.homeUrl.isNotEmpty()) { Text("Clear") }
                        }
                    }
                }
            }
            item {
                Text("Recent videos", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 12.dp, bottom = 4.dp))
            }
            if (history.isEmpty()) {
                item {
                    EmptyState(
                        Icons.Filled.History, "No videos yet",
                        "Paste a direct video link above to start watching. Videos you play will show up here.",
                    )
                }
            } else {
                items(history.take(10), key = { it.url }) { h ->
                    val progress = if (h.durationMs > 0) h.positionMs.toFloat() / h.durationMs else null
                    VideoRow(
                        thumb = h.thumbPath,
                        title = h.title,
                        meta = buildString {
                            append(h.domain)
                            if (h.durationMs > 0) append(" • ").append(fmtTime(h.durationMs))
                            if (h.positionMs > 0) append(" • at ").append(fmtTime(h.positionMs))
                        },
                        progress = progress,
                        onClick = { actions.play(PlaybackRequest(url = h.url, title = h.title), true) },
                        trailing = {
                            if (h.positionMs > 5_000) {
                                FilledTonalButton({ actions.play(PlaybackRequest(url = h.url, title = h.title), true) }) { Text("Continue") }
                            }
                        },
                    )
                }
            }
        }
    }
}

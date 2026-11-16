package com.example.universalplayer.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ContentPaste
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.example.universalplayer.Paste
import com.example.universalplayer.extractUrl
import com.example.universalplayer.normalizeUrl

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(paste: Paste?, onPlay: (String) -> Unit, onHistory: () -> Unit) {
    var text by rememberSaveable { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    val clipboard = LocalClipboardManager.current

    LaunchedEffect(paste) { paste?.let { text = it.url; error = null } }

    fun submit() {
        val u = normalizeUrl(text)
        if (u == null) error = "Enter a valid video link" else onPlay(u)
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("Universal Player") },
                actions = {
                    IconButton(onClick = onHistory) { Icon(Icons.Rounded.History, "History") }
                }
            )
        }
    ) { pad ->
        Column(
            Modifier.padding(pad).padding(20.dp).fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text("Paste a video link to start playing", style = MaterialTheme.typography.titleMedium)
            OutlinedTextField(
                value = text,
                onValueChange = { text = it; error = null },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Video link") },
                singleLine = true,
                isError = error != null,
                supportingText = { error?.let { Text(it) } },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
                keyboardActions = KeyboardActions(onGo = { submit() }),
                trailingIcon = {
                    Row {
                        if (text.isNotEmpty()) {
                            IconButton(onClick = { text = "" }) { Icon(Icons.Rounded.Close, "Clear") }
                        }
                        IconButton(onClick = {
                            val c = clipboard.getText()?.text
                            text = extractUrl(c) ?: c ?: text
                            error = null
                        }) { Icon(Icons.Rounded.ContentPaste, "Paste") }
                    }
                }
            )
            Button(
                onClick = { submit() },
                enabled = text.isNotBlank(),
                modifier = Modifier.fillMaxWidth().height(52.dp)
            ) {
                Icon(Icons.Rounded.PlayArrow, null)
                Spacer(Modifier.width(8.dp))
                Text("Play")
            }
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Supported links", style = MaterialTheme.typography.titleSmall)
                    Text(
                        "YouTube, Vimeo, Dailymotion, Twitch, X (Twitter), Facebook, Instagram, TikTok, " +
                                "Reddit and 1000+ other sites, plus direct MP4, HLS (.m3u8) and DASH (.mpd) links.",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Text(
                        "A copied link is pasted automatically when you open the app.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

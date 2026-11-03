package com.linkplayer.app

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.font.FontWeight
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    url: String,
    onUrl: (String) -> Unit,
    headers: String,
    onHeaders: (String) -> Unit,
    settings: AppSettings,
    history: HistoryStore,
    onPlay: (String, String) -> Unit,
) {
    var showHistory by remember { mutableStateOf(false) }
    var showSettings by remember { mutableStateOf(false) }
    val clipboard = LocalClipboardManager.current

    Column(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .windowInsetsPadding(
                WindowInsets.systemBars.union(WindowInsets.displayCutout).union(WindowInsets.ime)
            )
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(start = 20.dp, end = 8.dp, top = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "Link Player",
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.weight(1f)
            )
            IconButton(onClick = { showHistory = true }) {
                Icon(Icons.Rounded.History, "History", tint = MaterialTheme.colorScheme.onBackground)
            }
            IconButton(onClick = { showSettings = true }) {
                Icon(Icons.Rounded.Settings, "Settings", tint = MaterialTheme.colorScheme.onBackground)
            }
        }

        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                Modifier
                    .size(88.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Rounded.PlayArrow, null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(52.dp)
                )
            }
            Spacer(Modifier.height(14.dp))
            Text(
                "Koi bhi video link paste karo",
                fontSize = 18.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onBackground
            )
            Text(
                "HLS, DASH, MP4, MKV, WebM, TS, MP3, RTSP aur bahut kuch",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
            )
            Spacer(Modifier.height(24.dp))

            OutlinedTextField(
                value = url,
                onValueChange = onUrl,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Video link (URL)") },
                leadingIcon = { Icon(Icons.Rounded.Link, null) },
                trailingIcon = {
                    if (url.isEmpty()) {
                        IconButton(onClick = {
                            clipboard.getText()?.text?.let { onUrl(it.trim()) }
                        }) { Icon(Icons.Rounded.ContentPaste, "Paste") }
                    } else {
                        IconButton(onClick = { onUrl("") }) { Icon(Icons.Rounded.Close, "Clear") }
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(16.dp),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Next)
            )
            Spacer(Modifier.height(14.dp))
            OutlinedTextField(
                value = headers,
                onValueChange = onHeaders,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("HTTP headers (optional)") },
                placeholder = { Text("User-Agent: Mozilla/5.0\nReferer: https://site.com\nCookie: a=b") },
                supportingText = { Text("Ek line mein ek header  →  Key: Value") },
                minLines = 4,
                maxLines = 8,
                shape = RoundedCornerShape(16.dp),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Default)
            )
            Spacer(Modifier.height(18.dp))
            Button(
                onClick = { onPlay(url, headers) },
                enabled = url.isNotBlank(),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                shape = RoundedCornerShape(16.dp)
            ) {
                Icon(Icons.Rounded.PlayArrow, null)
                Spacer(Modifier.width(8.dp))
                Text("Play", fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
            }
        }
    }

    if (showSettings) {
        ModalBottomSheet(onDismissRequest = { showSettings = false }) {
            Column(
                Modifier
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp)
            ) {
                Text("Settings", fontSize = 20.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(8.dp))
                SettingsList(
                    settings,
                    MaterialTheme.colorScheme.onSurface,
                    MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )
            }
        }
    }

    if (showHistory) {
        val fmt = remember { SimpleDateFormat("dd MMM, hh:mm a", Locale.getDefault()) }
        ModalBottomSheet(onDismissRequest = { showHistory = false }) {
            Column(Modifier.padding(horizontal = 20.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("History", fontSize = 20.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                    if (history.items.isNotEmpty()) {
                        TextButton(onClick = { history.clear() }) { Text("Clear all") }
                    }
                }
                if (history.items.isEmpty()) {
                    Text(
                        "Abhi tak kuch nahi chala.",
                        modifier = Modifier.padding(vertical = 24.dp),
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                } else {
                    LazyColumn(Modifier.heightIn(max = 480.dp)) {
                        items(history.items.toList()) { item ->
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .clickable {
                                        showHistory = false
                                        onUrl(item.url)
                                        onHeaders(item.headers)
                                        onPlay(item.url, item.headers)
                                    }
                                    .padding(vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(Modifier.weight(1f)) {
                                    Text(item.url, maxLines = 2, overflow = TextOverflow.Ellipsis, fontSize = 14.sp)
                                    Text(
                                        fmt.format(Date(item.time)) + if (item.headers.isNotBlank()) "  •  headers" else "",
                                        fontSize = 12.sp,
                                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                                    )
                                }
                                IconButton(onClick = { history.remove(item) }) {
                                    Icon(Icons.Rounded.Delete, "Delete")
                                }
                            }
                        }
                    }
                }
                Spacer(Modifier.height(24.dp))
            }
        }
    }
}

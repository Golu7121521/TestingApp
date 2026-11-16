package com.example.universalplayer.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ContentPaste
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.universalplayer.data.HistoryItem
import com.example.universalplayer.data.HistoryStore
import com.example.universalplayer.extractUrl
import com.example.universalplayer.normalizeUrl

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    text: String,
    onText: (String) -> Unit,
    onPlay: (String) -> Unit,
    onPlayItem: (HistoryItem) -> Unit
) {
    var error by remember { mutableStateOf<String?>(null) }
    val clipboard = LocalClipboardManager.current
    val recent by HistoryStore.items.collectAsState()

    fun submit() {
        val u = normalizeUrl(text)
        if (u == null) error = "Enter a valid video link" else onPlay(u)
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0)
    ) { pad ->
        Column(
            Modifier.padding(pad).statusBarsPadding().fillMaxSize().verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Hero()
            OutlinedTextField(
                value = text,
                onValueChange = { onText(it); error = null },
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
                            IconButton(onClick = { onText("") }) { Icon(Icons.Rounded.Close, "Clear") }
                        }
                        IconButton(onClick = {
                            val c = clipboard.getText()?.text
                            onText(extractUrl(c) ?: c ?: text)
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

            AnimatedVisibility(recent.isNotEmpty()) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Continue watching", style = MaterialTheme.typography.titleMedium)
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        items(recent.take(10), key = { it.url }) { h ->
                            Column(Modifier.width(170.dp).clip(MaterialTheme.shapes.medium).clickable { onPlayItem(h) }) {
                                Thumb(h.thumb, Modifier.fillMaxWidth(), h.progress())
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    h.title, maxLines = 2, overflow = TextOverflow.Ellipsis,
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                        }
                    }
                }
            }

        }
    }
}

@Composable
private fun Hero() {
    val t = rememberInfiniteTransition(label = "hero")
    val s by t.animateFloat(
        1f, 1.08f,
        infiniteRepeatable(tween(1400, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "pulse"
    )
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        Box(
            Modifier.size(64.dp).graphicsLayer { scaleX = s; scaleY = s }.clip(CircleShape)
                .background(Brush.linearGradient(listOf(MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.tertiary))),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Rounded.PlayArrow, null, Modifier.size(38.dp), tint = MaterialTheme.colorScheme.onPrimary)
        }
        Column {
            Text("Play anything", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Text(
                "Paste a link from almost any site", style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

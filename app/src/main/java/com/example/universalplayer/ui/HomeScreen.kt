package com.example.universalplayer.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.ui.graphics.Color
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
    var target by remember { mutableStateOf<HistoryItem?>(null) }

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
            GradientButton("Play", Icons.Rounded.PlayArrow, text.isNotBlank(), { submit() }, Modifier.fillMaxWidth())

            AnimatedVisibility(recent.isNotEmpty()) {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text("Continue watching", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(6.dp))
                    recent.take(6).forEach { h ->
                        Row(
                            Modifier.fillMaxWidth().clip(MaterialTheme.shapes.medium)
                                .longPressable({ onPlayItem(h) }, { target = h })
                                .padding(vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Thumb(h.thumb, Modifier.width(120.dp), h.progress())
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(
                                    h.title, maxLines = 2, overflow = TextOverflow.Ellipsis,
                                    style = MaterialTheme.typography.bodyLarge
                                )
                                Text(
                                    h.url, maxLines = 1, overflow = TextOverflow.Ellipsis,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
            target?.let { t ->
                ItemMenuHost(
                    name = t.title,
                    removeText = "This video will be removed from history.",
                    onDismiss = { target = null },
                    onRename = { HistoryStore.rename(t.url, it) },
                    onRemove = { HistoryStore.remove(t.url) }
                )
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
    val rot by t.animateFloat(
        0f, 360f,
        infiniteRepeatable(tween(6000, easing = LinearEasing)),
        label = "rotate"
    )
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        Box(
            Modifier.size(64.dp).graphicsLayer { scaleX = s; scaleY = s; rotationZ = rot }.clip(CircleShape)
                .background(Brush.linearGradient(listOf(BrandStart, BrandEnd))),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Rounded.PlayArrow, null, Modifier.size(38.dp), tint = Color.White)
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

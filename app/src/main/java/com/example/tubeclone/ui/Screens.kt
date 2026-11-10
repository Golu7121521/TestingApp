package com.example.tubeclone.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.tubeclone.data.Channel
import com.example.tubeclone.data.Repo
import com.example.tubeclone.data.VideoItem

private val RedYt = Color(0xFFFF0000)
private val Grey = Color(0xFFAAAAAA)

@Composable
private fun Loading() {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator(color = RedYt)
    }
}

// ---------------- HOME ----------------

@Composable
fun HomeScreen(onPlay: (VideoItem) -> Unit) {
    var input by rememberSaveable { mutableStateOf("") }
    var query by rememberSaveable { mutableStateOf("trending videos") }
    var videos by remember { mutableStateOf<List<VideoItem>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var retry by remember { mutableStateOf(0) }

    LaunchedEffect(query, retry) {
        loading = true
        error = null
        runCatching { Repo.search(query) }
            .onSuccess { videos = it }
            .onFailure { error = it.message ?: "Load nahi hua. Internet check karo." }
        loading = false
    }

    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Filled.PlayArrow, null, tint = RedYt, modifier = Modifier.size(30.dp))
            Text(
                "TubeClone",
                color = Color.White,
                fontWeight = FontWeight.Bold,
                fontSize = 20.sp,
                modifier = Modifier.padding(start = 2.dp)
            )
        }

        OutlinedTextField(
            value = input,
            onValueChange = { input = it },
            placeholder = { Text("Search") },
            singleLine = true,
            leadingIcon = { Icon(Icons.Filled.Search, null) },
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = {
                if (input.isNotBlank()) query = input.trim()
            }),
            shape = RoundedCornerShape(24.dp),
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp)
        )

        Box(Modifier.weight(1f).fillMaxWidth().padding(top = 8.dp)) {
            when {
                loading -> Loading()
                error != null -> Column(Modifier.padding(16.dp)) {
                    Text("Error: $error", color = Color.White)
                    TextButton(onClick = { retry++ }) { Text("Retry", color = RedYt) }
                }
                else -> LazyColumn {
                    items(videos, key = { it.url }) { v ->
                        VideoCard(v) { onPlay(v) }
                    }
                }
            }
        }
    }
}

// ---------------- SHORTS ----------------

@Composable
fun ShortsScreen(onPlay: (VideoItem) -> Unit) {
    var shorts by remember { mutableStateOf<List<VideoItem>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }

    LaunchedEffect(Unit) {
        loading = true
        val all = runCatching { Repo.search("shorts") }.getOrDefault(emptyList())
        shorts = all.filter { it.durationSec in 1..60 }
        loading = false
    }

    Column(Modifier.fillMaxSize()) {
        Text(
            "Shorts",
            color = Color.White,
            fontWeight = FontWeight.Bold,
            fontSize = 20.sp,
            modifier = Modifier.padding(12.dp)
        )
        Box(Modifier.weight(1f).fillMaxWidth()) {
            when {
                loading -> Loading()
                shorts.isEmpty() -> Text(
                    "Koi shorts nahi mile",
                    color = Grey,
                    modifier = Modifier.padding(16.dp)
                )
                else -> LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    contentPadding = PaddingValues(8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(shorts, key = { it.url }) { v ->
                        ShortCard(v) { onPlay(v) }
                    }
                }
            }
        }
    }
}

@Composable
private fun ShortCard(v: VideoItem, onClick: () -> Unit) {
    Column(Modifier.clickable(onClick = onClick)) {
        AsyncImage(
            model = v.thumb,
            contentDescription = v.title,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(9f / 16f)
                .clip(RoundedCornerShape(12.dp))
                .background(Color(0xFF272727))
        )
        Text(
            v.title,
            color = Color.White,
            fontSize = 13.sp,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 6.dp)
        )
        Text(fmtViews(v.views), color = Grey, fontSize = 11.sp)
    }
}

// ---------------- SUBSCRIPTIONS ----------------

@Composable
fun SubsScreen(onPlay: (VideoItem) -> Unit) {
    val subs = remember { Repo.subs() }
    var feed by remember { mutableStateOf<List<VideoItem>>(emptyList()) }
    var loading by remember { mutableStateOf(subs.isNotEmpty()) }

    LaunchedEffect(subs) {
        if (subs.isEmpty()) return@LaunchedEffect
        loading = true
        feed = subs.flatMap { c ->
            runCatching { Repo.channelVideos(c.name, c.url) }.getOrDefault(emptyList())
        }
        loading = false
    }

    Column(Modifier.fillMaxSize()) {
        Text(
            "Subscriptions",
            color = Color.White,
            fontWeight = FontWeight.Bold,
            fontSize = 20.sp,
            modifier = Modifier.padding(12.dp)
        )
        when {
            subs.isEmpty() -> Text(
                "Abhi koi channel subscribe nahi hai.\nVideo player mein Subscribe dabayein.",
                color = Grey,
                modifier = Modifier.padding(16.dp)
            )
            loading -> Loading()
            else -> LazyColumn(Modifier.fillMaxSize()) {
                items(feed, key = { it.url }) { v -> VideoCard(v) { onPlay(v) } }
            }
        }
    }
}

// ---------------- LIBRARY ----------------

@Composable
fun LibraryScreen(onPlay: (VideoItem) -> Unit) {
    var history by remember { mutableStateOf(Repo.history()) }
    var subs by remember { mutableStateOf(Repo.subs()) }

    LazyColumn(Modifier.fillMaxSize()) {
        item {
            Text(
                "Library",
                color = Color.White,
                fontWeight = FontWeight.Bold,
                fontSize = 20.sp,
                modifier = Modifier.padding(12.dp)
            )
            Text(
                "Subscribed channels",
                color = Color.White,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
            )
        }
        if (subs.isEmpty()) {
            item { Text("Koi channel nahi", color = Grey, modifier = Modifier.padding(12.dp)) }
        }
        items(subs, key = { it.url }) { c ->
            ChannelRow(c) {
                Repo.toggleSub(c)
                subs = Repo.subs()
            }
        }
        item {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("History", color = Color.White, fontWeight = FontWeight.SemiBold)
                TextButton(onClick = {
                    Repo.clearHistory()
                    history = Repo.history()
                }) { Text("Clear", color = RedYt) }
            }
        }
        if (history.isEmpty()) {
            item { Text("History khali hai", color = Grey, modifier = Modifier.padding(12.dp)) }
        }
        items(history, key = { it.url }) { v ->
            VideoCard(v) { onPlay(v) }
        }
    }
}

@Composable
private fun ChannelRow(c: Channel, onUnsub: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            c.name,
            color = Color.White,
            modifier = Modifier.weight(1f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        TextButton(onClick = onUnsub) { Text("Unsubscribe", color = Grey) }
    }
}

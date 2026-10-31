@file:OptIn(
    androidx.media3.common.util.UnstableApi::class,
    androidx.compose.material3.ExperimentalMaterial3Api::class
)

package com.mytube.app.ui

import android.app.Activity
import android.content.pm.ActivityInfo
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.media3.ui.PlayerView
import coil.compose.AsyncImage
import com.mytube.app.Load
import com.mytube.app.MainVM
import org.schabi.newpipe.extractor.Image
import org.schabi.newpipe.extractor.stream.StreamInfoItem
import java.util.Locale

// ---------- helpers ----------
private fun bestImage(list: List<Image>?): String? =
    list?.maxByOrNull { it.height }?.url ?: list?.firstOrNull()?.url

private fun fmtCount(n: Long): String {
    fun f(v: Double, s: String) = String.format(Locale.US, "%.1f", v).removeSuffix(".0") + s
    return when {
        n < 0 -> ""
        n >= 1_000_000_000 -> f(n / 1e9, "B")
        n >= 1_000_000 -> f(n / 1e6, "M")
        n >= 1_000 -> f(n / 1e3, "K")
        else -> n.toString()
    }
}

private fun fmtDur(s: Long): String {
    val h = s / 3600; val m = (s % 3600) / 60; val sec = s % 60
    return if (h > 0) String.format(Locale.US, "%d:%02d:%02d", h, m, sec)
    else String.format(Locale.US, "%d:%02d", m, sec)
}

private fun meta(item: StreamInfoItem): String {
    val views = if (item.viewCount >= 0) "${fmtCount(item.viewCount)} views" else ""
    return listOf(item.uploaderName ?: "", views, item.textualUploadDate ?: "")
        .filter { it.isNotBlank() }.joinToString(" • ")
}

// ---------- root ----------
@Composable
fun AppRoot(vm: MainVM) {
    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Scaffold(
            containerColor = MaterialTheme.colorScheme.background,
            topBar = { if (vm.tab == 0) HomeTopBar { vm.tab = 1 } },
            bottomBar = {
                NavigationBar(containerColor = MaterialTheme.colorScheme.background) {
                    NavigationBarItem(
                        selected = vm.tab == 0, onClick = { vm.tab = 0 },
                        icon = { Icon(Icons.Default.Home, null) }, label = { Text("Home") }
                    )
                    NavigationBarItem(
                        selected = vm.tab == 1, onClick = { vm.tab = 1 },
                        icon = { Icon(Icons.Default.Search, null) }, label = { Text("Search") }
                    )
                }
            }
        ) { pad ->
            Box(Modifier.padding(pad)) {
                if (vm.tab == 0) HomeScreen(vm) else SearchScreen(vm)
            }
        }
        if (vm.watchOpen) WatchScreen(vm)
    }
}

@Composable
private fun HomeTopBar(onSearch: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier.size(width = 30.dp, height = 21.dp).clip(RoundedCornerShape(6.dp))
                .background(Color.Red),
            contentAlignment = Alignment.Center
        ) { Text("▶", color = Color.White, fontSize = 11.sp) }
        Spacer(Modifier.width(6.dp))
        Text("MyTube", fontWeight = FontWeight.Bold, fontSize = 20.sp, letterSpacing = (-1).sp)
        Spacer(Modifier.weight(1f))
        IconButton(onClick = onSearch) { Icon(Icons.Default.Search, "Search") }
    }
}

// ---------- home ----------
@Composable
private fun HomeScreen(vm: MainVM) {
    Column(Modifier.fillMaxSize()) {
        LazyRow(
            contentPadding = PaddingValues(horizontal = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.padding(bottom = 8.dp)
        ) {
            items(vm.chips.size) { i ->
                FilterChip(
                    selected = vm.chip == i,
                    onClick = { vm.selectChip(i) },
                    label = { Text(vm.chips[i]) },
                    shape = RoundedCornerShape(8.dp),
                    colors = FilterChipDefaults.filterChipColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant,
                        labelColor = MaterialTheme.colorScheme.onSurface,
                        selectedContainerColor = MaterialTheme.colorScheme.onSurface,
                        selectedLabelColor = MaterialTheme.colorScheme.surface
                    ),
                    border = null
                )
            }
        }
        VideoList(vm.home, onRetry = { vm.loadHome() }) { vm.openVideo(it.url) }
    }
}

@Composable
private fun VideoList(state: Load<List<StreamInfoItem>>, onRetry: (() -> Unit)? = null, onClick: (StreamInfoItem) -> Unit) {
    when (state) {
        is Load.Idle -> {}
        is Load.Loading -> Box(Modifier.fillMaxSize(), Alignment.Center) { CircularProgressIndicator() }
        is Load.Err -> Column(
            Modifier.fillMaxSize().padding(24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(state.msg, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (onRetry != null) TextButton(onClick = onRetry) { Text("Retry") }
        }
        is Load.Ok -> LazyColumn(Modifier.fillMaxSize()) {
            items(state.value) { VideoCard(it) { onClick(it) } }
        }
    }
}

@Composable
private fun VideoCard(item: StreamInfoItem, onClick: () -> Unit) {
    Column(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(bottom = 18.dp)) {
        Box {
            AsyncImage(
                model = bestImage(item.thumbnails), contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxWidth().aspectRatio(16 / 9f)
                    .background(MaterialTheme.colorScheme.surfaceVariant)
            )
            if (item.duration > 0) {
                Text(
                    fmtDur(item.duration), color = Color.White, fontSize = 12.sp,
                    modifier = Modifier.align(Alignment.BottomEnd).padding(8.dp)
                        .background(Color(0xCC000000), RoundedCornerShape(4.dp))
                        .padding(horizontal = 4.dp, vertical = 1.dp)
                )
            }
        }
        Row(Modifier.padding(start = 12.dp, end = 12.dp, top = 10.dp)) {
            AsyncImage(
                model = bestImage(item.uploaderAvatars), contentDescription = null,
                modifier = Modifier.size(36.dp).clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceVariant)
            )
            Spacer(Modifier.width(12.dp))
            Column {
                Text(
                    item.name ?: "", maxLines = 2, overflow = TextOverflow.Ellipsis,
                    fontSize = 15.sp, fontWeight = FontWeight.Medium, lineHeight = 20.sp
                )
                Text(
                    meta(item), maxLines = 1, overflow = TextOverflow.Ellipsis, fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

// ---------- search ----------
@Composable
private fun SearchScreen(vm: MainVM) {
    val kb = LocalSoftwareKeyboardController.current
    Column(Modifier.fillMaxSize()) {
        OutlinedTextField(
            value = vm.query,
            onValueChange = { vm.query = it },
            placeholder = { Text("Search MyTube") },
            singleLine = true,
            shape = RoundedCornerShape(24.dp),
            leadingIcon = { Icon(Icons.Default.Search, null) },
            trailingIcon = {
                if (vm.query.isNotEmpty()) IconButton(onClick = { vm.query = "" }) {
                    Icon(Icons.Default.Close, "Clear")
                }
            },
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { kb?.hide(); vm.search() }),
            modifier = Modifier.fillMaxWidth().statusBarsPadding().padding(12.dp)
        )
        VideoList(vm.results, onRetry = { vm.search() }) { vm.openVideo(it.url) }
    }
}

// ---------- watch ----------
@Composable
private fun WatchScreen(vm: MainVM) {
    val ctx = LocalContext.current
    val activity = ctx as? Activity
    var fullscreen by remember { mutableStateOf(false) }
    var descOpen by remember { mutableStateOf(false) }
    var qMenu by remember { mutableStateOf(false) }

    fun setFullscreen(fs: Boolean) {
        fullscreen = fs
        activity?.let { a ->
            a.requestedOrientation =
                if (fs) ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
                else ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
            val c = WindowCompat.getInsetsController(a.window, a.window.decorView)
            if (fs) {
                c.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                c.hide(WindowInsetsCompat.Type.systemBars())
            } else c.show(WindowInsetsCompat.Type.systemBars())
        }
    }

    DisposableEffect(Unit) { onDispose { setFullscreen(false) } }

    BackHandler {
        if (fullscreen) setFullscreen(false) else vm.closeVideo()
    }

    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Box(
            if (fullscreen) Modifier.fillMaxSize().background(Color.Black)
            else Modifier.fillMaxWidth().background(Color.Black).statusBarsPadding().aspectRatio(16 / 9f)
        ) {
            AndroidView(
                factory = { c ->
                    PlayerView(c).apply {
                        player = vm.player
                        setFullscreenButtonClickListener { fs -> setFullscreen(fs) }
                    }
                },
                modifier = Modifier.fillMaxSize()
            )
        }

        if (!fullscreen) {
            when (val w = vm.watch) {
                is Load.Loading, is Load.Idle ->
                    Box(Modifier.fillMaxSize(), Alignment.Center) { CircularProgressIndicator() }
                is Load.Err -> Box(Modifier.fillMaxSize().padding(24.dp), Alignment.Center) {
                    Text(w.msg, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                is Load.Ok -> {
                    val info = w.value
                    val related = info.relatedItems.filterIsInstance<StreamInfoItem>()
                    LazyColumn(Modifier.fillMaxSize()) {
                        item {
                            Column(Modifier.padding(12.dp)) {
                                Text(
                                    info.name ?: "", fontSize = 18.sp, fontWeight = FontWeight.SemiBold,
                                    lineHeight = 24.sp
                                )
                                val views = if (info.viewCount >= 0) "${fmtCount(info.viewCount)} views" else ""
                                val likes = if (info.likeCount >= 0) "${fmtCount(info.likeCount)} likes" else ""
                                Text(
                                    listOf(views, likes).filter { it.isNotBlank() }.joinToString(" • "),
                                    fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(top = 4.dp)
                                )
                                Row(
                                    Modifier.fillMaxWidth().padding(top = 12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    AsyncImage(
                                        model = bestImage(info.uploaderAvatars), contentDescription = null,
                                        modifier = Modifier.size(40.dp).clip(CircleShape)
                                            .background(MaterialTheme.colorScheme.surfaceVariant)
                                    )
                                    Spacer(Modifier.width(12.dp))
                                    Text(
                                        info.uploaderName ?: "", fontWeight = FontWeight.Medium,
                                        modifier = Modifier.weight(1f), maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    if (vm.qualities.isNotEmpty()) {
                                        Box {
                                            FilledTonalButton(onClick = { qMenu = true }) { Text("${vm.quality}p") }
                                            DropdownMenu(expanded = qMenu, onDismissRequest = { qMenu = false }) {
                                                vm.qualities.forEach { h ->
                                                    DropdownMenuItem(
                                                        text = { Text("${h}p") },
                                                        onClick = { qMenu = false; vm.changeQuality(h) }
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                                val desc = info.description?.content ?: ""
                                if (desc.isNotBlank()) {
                                    Column(
                                        Modifier.fillMaxWidth().padding(top = 12.dp)
                                            .clip(RoundedCornerShape(12.dp))
                                            .background(MaterialTheme.colorScheme.surfaceVariant)
                                            .clickable { descOpen = !descOpen }
                                            .padding(12.dp)
                                    ) {
                                        Text(
                                            desc, fontSize = 13.sp,
                                            maxLines = if (descOpen) Int.MAX_VALUE else 3,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }
                            }
                        }
                        items(related) { r -> VideoCard(r) { vm.openVideo(r.url) } }
                    }
                }
            }
        }
    }
}

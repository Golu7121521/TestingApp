package com.universal.streamplayer

import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.Patterns
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.ImageLoader
import coil.compose.AsyncImage
import coil.decode.VideoFrameDecoder
import coil.request.ImageRequest
import coil.request.videoFrameMillis
import com.universal.streamplayer.data.*
import com.universal.streamplayer.player.PlayerActivity
import com.universal.streamplayer.ui.theme.UniversalStreamPlayerTheme
import com.yausername.youtubedl_android.YoutubeDL

class MainActivity : ComponentActivity() {

    private var clipboardUrlState = mutableStateOf("")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Initialize yt-dlp in background for social video link extraction
        Thread {
            try {
                YoutubeDL.getInstance().init(applicationContext)
            } catch (_: Exception) {}
        }.start()

        // Handle shared URL intent
        if (intent?.action == Intent.ACTION_SEND && intent.type == "text/plain") {
            intent.getStringExtra(Intent.EXTRA_TEXT)?.let { sharedText ->
                extractValidUrl(sharedText)?.let { clipboardUrlState.value = it }
            }
        }

        setContent {
            val systemDark = isSystemInDarkTheme()
            var isDarkMode by remember { mutableStateOf(systemDark) }

            UniversalStreamPlayerTheme(darkTheme = isDarkMode) {
                MainAppScreen(
                    autoPastedUrl = clipboardUrlState.value,
                    onConsumeAutoPaste = { clipboardUrlState.value = "" },
                    isDarkMode = isDarkMode,
                    onToggleTheme = { isDarkMode = !isDarkMode }
                )
            }
        }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) checkClipboardForPlayableLink()
    }

    private fun checkClipboardForPlayableLink() {
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager ?: return
        val item = clipboard.primaryClip?.getItemAt(0)?.text?.toString()?.trim() ?: return
        extractValidUrl(item)?.let { validUrl ->
            if (clipboardUrlState.value != validUrl) {
                clipboardUrlState.value = validUrl
            }
        }
    }

    private fun extractValidUrl(text: String): String? {
        val matcher = Patterns.WEB_URL.matcher(text)
        if (matcher.find()) {
            val url = matcher.group()
            if (url.startsWith("http://") || url.startsWith("https://") ||
                url.startsWith("rtmp://") || url.startsWith("rtsp://")) {
                return url
            }
        }
        return null
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainAppScreen(
    autoPastedUrl: String,
    onConsumeAutoPaste: () -> Unit,
    isDarkMode: Boolean,
    onToggleTheme: () -> Unit
) {
    val context = LocalContext.current
    val repository = remember { StorageRepository(context) }
    var selectedTab by remember { mutableIntStateOf(0) }

    var homeUrl by remember { mutableStateOf("") }
    var advanceUrl by remember { mutableStateOf("") }
    var lastAutoPasted by remember { mutableStateOf("") }

    // Automatically paste copied link when detected
    LaunchedEffect(autoPastedUrl) {
        if (autoPastedUrl.isNotBlank() && autoPastedUrl != lastAutoPasted) {
            homeUrl = autoPastedUrl
            advanceUrl = autoPastedUrl
            lastAutoPasted = autoPastedUrl
            Toast.makeText(context, "Link auto-pasted from clipboard", Toast.LENGTH_SHORT).show()
            onConsumeAutoPaste()
        }
    }

    fun launchPlayer(request: StreamRequest) {
        if (request.url.isBlank()) {
            Toast.makeText(context, "Please enter a valid video URL", Toast.LENGTH_SHORT).show()
            return
        }
        repository.addToHistory(request)
        val intent = Intent(context, PlayerActivity::class.java).apply {
            putExtra("STREAM_REQUEST", request)
        }
        context.startActivity(intent)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = when (selectedTab) {
                            0 -> "Universal Stream Player"
                            1 -> "Advance Stream Setup"
                            2 -> "Watch History"
                            else -> "Saved Playlists"
                        },
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 19.sp
                    )
                },
                actions = {
                    IconButton(onClick = onToggleTheme) {
                        Icon(
                            imageVector = if (isDarkMode) Icons.Default.LightMode else Icons.Default.DarkMode,
                            contentDescription = "Toggle Dark/Light Theme"
                        )
                    }
                }
            )
        },
        bottomBar = {
            // Compact navigation bar (60.dp height)
            NavigationBar(
                modifier = Modifier.height(60.dp),
                tonalElevation = 4.dp
            ) {
                val tabs = listOf(
                    Triple("Home", Icons.Filled.Home, Icons.Outlined.Home),
                    Triple("Advance", Icons.Filled.Tune, Icons.Outlined.Tune),
                    Triple("History", Icons.Filled.History, Icons.Outlined.History),
                    Triple("Playlist", Icons.Filled.PlaylistPlay, Icons.Outlined.PlaylistPlay)
                )
                tabs.forEachIndexed { index, (label, selectedIcon, unselectedIcon) ->
                    NavigationBarItem(
                        selected = selectedTab == index,
                        onClick = { selectedTab = index },
                        icon = {
                            Icon(
                                imageVector = if (selectedTab == index) selectedIcon else unselectedIcon,
                                contentDescription = label,
                                modifier = Modifier.size(21.dp)
                            )
                        },
                        label = { Text(text = label, fontSize = 11.sp, maxLines = 1) },
                        alwaysShowLabel = true
                    )
                }
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            when (selectedTab) {
                0 -> HomeTabScreen(
                    url = homeUrl,
                    onUrlChange = { homeUrl = it },
                    onPlay = { title, link ->
                        launchPlayer(
                            StreamRequest(
                                title = title.ifBlank { link.substringAfterLast("/").ifBlank { "Quick Stream" } },
                                url = link.trim()
                            )
                        )
                    },
                    onSaveToPlaylist = { title, link ->
                        if (link.isNotBlank()) {
                            repository.addToPlaylist(
                                StreamRequest(
                                    title = title.ifBlank { "Saved Stream" },
                                    url = link.trim()
                                )
                            )
                            Toast.makeText(context, "Added to Playlist", Toast.LENGTH_SHORT).show()
                        }
                    }
                )
                1 -> AdvanceTabScreen(
                    url = advanceUrl,
                    onUrlChange = { advanceUrl = it },
                    onPlay = { launchPlayer(it) },
                    onSavePlaylist = {
                        repository.addToPlaylist(it)
                        Toast.makeText(context, "Saved to Playlist", Toast.LENGTH_SHORT).show()
                    }
                )
                2 -> HistoryTabScreen(
                    repository = repository,
                    onPlay = { launchPlayer(it) }
                )
                3 -> PlaylistTabScreen(
                    repository = repository,
                    onPlay = { launchPlayer(it) }
                )
            }
        }
    }
}

@Composable
fun HomeTabScreen(
    url: String,
    onUrlChange: (String) -> Unit,
    onPlay: (String, String) -> Unit,
    onSaveToPlaylist: (String, String) -> Unit
) {
    var streamTitle by remember { mutableStateOf("") }
    val context = LocalContext.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
            shape = RoundedCornerShape(14.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Play Any Video Link", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                Text(
                    "Supports MP4, MKV, HLS (.m3u8), DASH (.mpd), RTSP, RTMP, YouTube Shorts, Instagram Reels & direct links.",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                OutlinedTextField(
                    value = streamTitle,
                    onValueChange = { streamTitle = it },
                    label = { Text("Stream Title (Optional)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = url,
                    onValueChange = onUrlChange,
                    label = { Text("Paste Video or Stream URL") },
                    placeholder = { Text("https://example.com/stream.m3u8") },
                    trailingIcon = {
                        Row {
                            if (url.isNotEmpty()) {
                                IconButton(onClick = { onUrlChange("") }) {
                                    Icon(Icons.Default.Clear, contentDescription = "Clear")
                                }
                            }
                            IconButton(onClick = {
                                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                                val pasted = clipboard?.primaryClip?.getItemAt(0)?.text?.toString() ?: ""
                                if (pasted.isNotBlank()) onUrlChange(pasted)
                            }) {
                                Icon(Icons.Default.ContentPaste, contentDescription = "Paste")
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    maxLines = 3
                )

                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                    Button(
                        onClick = { onPlay(streamTitle, url) },
                        modifier = Modifier.weight(1f),
                        contentPadding = PaddingValues(vertical = 12.dp)
                    ) {
                        Icon(Icons.Default.PlayArrow, contentDescription = null)
                        Spacer(Modifier.width(6.dp))
                        Text("Play Now")
                    }

                    OutlinedButton(
                        onClick = { onSaveToPlaylist(streamTitle, url) },
                        contentPadding = PaddingValues(vertical = 12.dp, horizontal = 16.dp)
                    ) {
                        Icon(Icons.Default.PlaylistAdd, contentDescription = null)
                        Spacer(Modifier.width(4.dp))
                        Text("Save")
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdvanceTabScreen(
    url: String,
    onUrlChange: (String) -> Unit,
    onPlay: (StreamRequest) -> Unit,
    onSavePlaylist: (StreamRequest) -> Unit
) {
    var title by remember { mutableStateOf("") }
    var cookie by remember { mutableStateOf("") }
    var referer by remember { mutableStateOf("") }
    var origin by remember { mutableStateOf("") }
    var drmLicenseUrl by remember { mutableStateOf("") }

    var selectedUa by remember { mutableStateOf(UserAgents.ANDROID_CHROME) }
    var uaExpanded by remember { mutableStateOf(false) }

    var selectedDrm by remember { mutableStateOf(DrmSchemes.NONE) }
    var drmExpanded by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        OutlinedTextField(
            value = title,
            onValueChange = { title = it },
            label = { Text("Stream Name (Optional)") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )

        OutlinedTextField(
            value = url,
            onValueChange = onUrlChange,
            label = { Text("Video Link (URL) *") },
            placeholder = { Text("https://...") },
            modifier = Modifier.fillMaxWidth(),
            maxLines = 2
        )

        OutlinedTextField(
            value = cookie,
            onValueChange = { cookie = it },
            label = { Text("Cookie Value") },
            placeholder = { Text("session_id=xyz; token=abc") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )

        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
            OutlinedTextField(
                value = referer,
                onValueChange = { referer = it },
                label = { Text("Referer Value") },
                placeholder = { Text("https://referer.com") },
                singleLine = true,
                modifier = Modifier.weight(1f)
            )
            OutlinedTextField(
                value = origin,
                onValueChange = { origin = it },
                label = { Text("Origin Value") },
                placeholder = { Text("https://origin.com") },
                singleLine = true,
                modifier = Modifier.weight(1f)
            )
        }

        // User-Agent dropdown
        ExposedDropdownMenuBox(
            expanded = uaExpanded,
            onExpandedChange = { uaExpanded = !uaExpanded }
        ) {
            OutlinedTextField(
                value = selectedUa.label,
                onValueChange = {},
                readOnly = true,
                label = { Text("User Agent") },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = uaExpanded) },
                modifier = Modifier.menuAnchor().fillMaxWidth()
            )
            ExposedDropdownMenu(
                expanded = uaExpanded,
                onDismissRequest = { uaExpanded = false }
            ) {
                UserAgents.entries.forEach { uaOption ->
                    DropdownMenuItem(
                        text = { Text(uaOption.label) },
                        onClick = {
                            selectedUa = uaOption
                            uaExpanded = false
                        }
                    )
                }
            }
        }

        // DRM scheme dropdown
        ExposedDropdownMenuBox(
            expanded = drmExpanded,
            onExpandedChange = { drmExpanded = !drmExpanded }
        ) {
            OutlinedTextField(
                value = selectedDrm.label,
                onValueChange = {},
                readOnly = true,
                label = { Text("DRM Scheme") },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = drmExpanded) },
                modifier = Modifier.menuAnchor().fillMaxWidth()
            )
            ExposedDropdownMenu(
                expanded = drmExpanded,
                onDismissRequest = { drmExpanded = false }
            ) {
                DrmSchemes.entries.forEach { scheme ->
                    DropdownMenuItem(
                        text = { Text(scheme.label) },
                        onClick = {
                            selectedDrm = scheme
                            drmExpanded = false
                        }
                    )
                }
            }
        }

        OutlinedTextField(
            value = drmLicenseUrl,
            onValueChange = { drmLicenseUrl = it },
            label = { Text("DRM License URI") },
            placeholder = { Text("https://license.server.com/widevine") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(Modifier.height(4.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
            Button(
                onClick = {
                    onPlay(
                        StreamRequest(
                            title = title.ifBlank { "Advance Stream" },
                            url = url.trim(),
                            cookie = cookie.trim(),
                            referer = referer.trim(),
                            origin = origin.trim(),
                            drmLicenseUrl = drmLicenseUrl.trim(),
                            userAgent = selectedUa.ua,
                            drmScheme = selectedDrm.label
                        )
                    )
                },
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(vertical = 12.dp)
            ) {
                Icon(Icons.Default.PlayCircleFilled, contentDescription = null)
                Spacer(Modifier.width(6.dp))
                Text("Start Advance Stream")
            }

            OutlinedButton(
                onClick = {
                    if (url.isNotBlank()) {
                        onSavePlaylist(
                            StreamRequest(
                                title = title.ifBlank { "Advance Stream" },
                                url = url.trim(),
                                cookie = cookie.trim(),
                                referer = referer.trim(),
                                origin = origin.trim(),
                                drmLicenseUrl = drmLicenseUrl.trim(),
                                userAgent = selectedUa.ua,
                                drmScheme = selectedDrm.label
                            )
                        )
                    }
                },
                contentPadding = PaddingValues(vertical = 12.dp, horizontal = 14.dp)
            ) {
                Icon(Icons.Default.BookmarkAdd, contentDescription = "Save")
            }
        }
    }
}

@Composable
fun HistoryTabScreen(
    repository: StorageRepository,
    onPlay: (StreamRequest) -> Unit
) {
    val context = LocalContext.current
    var historyItems by remember { mutableStateOf(repository.getHistory()) }

    val videoImageLoader = remember {
        ImageLoader.Builder(context)
            .components { add(VideoFrameDecoder.Factory()) }
            .crossfade(true)
            .build()
    }

    Column(modifier = Modifier.fillMaxSize().padding(14.dp)) {
        if (historyItems.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("No playback history yet.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            Row(
                modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("${historyItems.size} Recent Streams", fontSize = 13.sp, fontWeight = FontWeight.Medium)
                TextButton(onClick = {
                    repository.clearHistory()
                    historyItems = emptyList()
                }) {
                    Icon(Icons.Default.DeleteSweep, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Clear All")
                }
            }

            LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                items(historyItems, key = { it.id }) { item ->
                    Card(
                        modifier = Modifier.fillMaxWidth().clickable { onPlay(item) },
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(width = 100.dp, height = 62.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(Color.Black),
                                contentAlignment = Alignment.Center
                            ) {
                                AsyncImage(
                                    model = ImageRequest.Builder(context)
                                        .data(item.url)
                                        .videoFrameMillis(2000)
                                        .build(),
                                    imageLoader = videoImageLoader,
                                    contentDescription = "Video Thumbnail",
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize()
                                )
                                Icon(
                                    Icons.Default.PlayCircleOutline,
                                    contentDescription = null,
                                    tint = Color.White.copy(alpha = 0.85f),
                                    modifier = Modifier.size(28.dp)
                                )
                            }

                            Spacer(Modifier.width(12.dp))

                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = item.title,
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 14.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = item.url,
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                if (item.drmScheme != DrmSchemes.NONE.label) {
                                    Text(
                                        text = "DRM: ${item.drmScheme}",
                                        fontSize = 10.sp,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun PlaylistTabScreen(
    repository: StorageRepository,
    onPlay: (StreamRequest) -> Unit
) {
    var playlistItems by remember { mutableStateOf(repository.getPlaylists()) }

    Column(modifier = Modifier.fillMaxSize().padding(14.dp)) {
        if (playlistItems.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    "No saved playlists. Save links from Home or Advance tab.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                items(playlistItems, key = { it.id }) { item ->
                    Card(
                        modifier = Modifier.fillMaxWidth().clickable { onPlay(item) },
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Default.OndemandVideo,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(32.dp)
                            )
                            Spacer(Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(item.title, fontWeight = FontWeight.Bold, fontSize = 14.sp, maxLines = 1)
                                Text(
                                    item.url,
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            IconButton(onClick = {
                                repository.removeFromPlaylist(item.id)
                                playlistItems = repository.getPlaylists()
                            }) {
                                Icon(Icons.Default.DeleteOutline, contentDescription = "Remove")
                            }
                        }
                    }
                }
            }
        }
    }
}

package com.streamplay.player

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.graphics.path.PathIterator
import com.streamplay.player.data.AppDatabase
import com.streamplay.player.data.GestureSettings
import com.streamplay.player.data.SettingsManager
import com.streamplay.player.data.StreamHistoryEntity
import com.streamplay.player.ui.theme.StreamPlayTheme
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.auto(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.auto(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT)
        )
        super.onCreate(savedInstanceState)

        // Initialize androidx.graphics.path native bridge reference
        val dummyPath = android.graphics.Path().apply { moveTo(0f, 0f); lineTo(1f, 1f) }
        val iterator = PathIterator(dummyPath)
        iterator.hasNext()

        val database = AppDatabase.getInstance(this)
        val settingsManager = SettingsManager(this)

        setContent {
            StreamPlayTheme {
                HomeScreen(
                    database = database,
                    settingsManager = settingsManager,
                    onPlayStream = { url, userAgent, referer, rawHeaders ->
                        val intent = Intent(this, PlayerActivity::class.java).apply {
                            putExtra("STREAM_URL", url.trim())
                            putExtra("USER_AGENT", userAgent.trim())
                            putExtra("REFERER", referer.trim())
                            putExtra("RAW_HEADERS", rawHeaders.trim())
                        }
                        startActivity(intent)
                    }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    database: AppDatabase,
    settingsManager: SettingsManager,
    onPlayStream: (String, String, String, String) -> Unit
) {
    val scope = rememberCoroutineScope()
    val clipboard = LocalClipboardManager.current
    val historyList by database.historyDao().getAllHistory().collectAsState(initial = emptyList())
    val gestureSettings by settingsManager.settingsFlow.collectAsState(initial = GestureSettings())

    var streamUrl by remember { mutableStateOf("") }
    var streamTitle by remember { mutableStateOf("") }
    var userAgent by remember { mutableStateOf("") }
    var referer by remember { mutableStateOf("") }
    var customHeaders by remember { mutableStateOf("") }

    var showSettingsDialog by remember { mutableStateOf(false) }
    var showHistorySheet by remember { mutableStateOf(false) }
    var urlError by remember { mutableStateOf(false) }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "StreamPlay",
                        fontWeight = FontWeight.Bold
                    )
                },
                actions = {
                    IconButton(onClick = { showHistorySheet = true }) {
                        Icon(
                            imageVector = Icons.Default.History,
                            contentDescription = "History"
                        )
                    }
                    IconButton(onClick = { showSettingsDialog = true }) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = "Settings"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent
                )
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Spacer(modifier = Modifier.height(4.dp))

            ElevatedCard(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "Stream Configuration",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.SemiBold
                    )

                    OutlinedTextField(
                        value = streamUrl,
                        onValueChange = {
                            streamUrl = it
                            urlError = false
                        },
                        label = { Text("Video / Stream URL (MP4, M3U8, MPD, MKV, RTSP)") },
                        placeholder = { Text("https://example.com/live/index.m3u8") },
                        isError = urlError,
                        supportingText = if (urlError) {
                            { Text("Please enter a valid video or stream URL") }
                        } else null,
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = false,
                        maxLines = 3,
                        trailingIcon = {
                            Row {
                                if (streamUrl.isNotEmpty()) {
                                    IconButton(onClick = { streamUrl = "" }) {
                                        Icon(Icons.Default.Clear, contentDescription = "Clear")
                                    }
                                }
                                IconButton(onClick = {
                                    clipboard.getText()?.text?.let { pasted ->
                                        streamUrl = pasted
                                        urlError = false
                                    }
                                }) {
                                    Icon(Icons.Default.ContentPaste, contentDescription = "Paste")
                                }
                            }
                        }
                    )

                    OutlinedTextField(
                        value = streamTitle,
                        onValueChange = { streamTitle = it },
                        label = { Text("Stream Name (Optional for History)") },
                        placeholder = { Text("Live Match / Movie Stream") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                }
            }

            ElevatedCard(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "HTTP Request Headers",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.SemiBold
                    )

                    OutlinedTextField(
                        value = userAgent,
                        onValueChange = { userAgent = it },
                        label = { Text("User-Agent (Optional)") },
                        placeholder = { Text("Mozilla/5.0 (Windows NT 10.0; Win64; x64)...") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )

                    OutlinedTextField(
                        value = referer,
                        onValueChange = { referer = it },
                        label = { Text("Referer / Origin (Optional)") },
                        placeholder = { Text("https://referer-domain.com/") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )

                    OutlinedTextField(
                        value = customHeaders,
                        onValueChange = { customHeaders = it },
                        label = { Text("Additional Headers (Key: Value per line)") },
                        placeholder = {
                            Text("Cookie: session=abcd\nAuthorization: Bearer token\nX-Forwarded-For: 1.1.1.1")
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 110.dp),
                        maxLines = 6
                    )
                }
            }

            Button(
                onClick = {
                    if (streamUrl.isBlank()) {
                        urlError = true
                    } else {
                        scope.launch {
                            database.historyDao().insert(
                                StreamHistoryEntity(
                                    title = streamTitle.ifBlank { streamUrl.substringAfterLast("/").ifBlank { "Network Stream" } },
                                    url = streamUrl.trim(),
                                    userAgent = userAgent.trim(),
                                    referer = referer.trim(),
                                    rawHeaders = customHeaders.trim()
                                )
                            )
                        }
                        onPlayStream(streamUrl, userAgent, referer, customHeaders)
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                shape = RoundedCornerShape(14.dp)
            ) {
                Icon(Icons.Default.PlayArrow, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "PLAY STREAM",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }

    if (showSettingsDialog) {
        GestureSettingsDialog(
            settings = gestureSettings,
            onDismiss = { showSettingsDialog = false },
            onToggleSwipe = { scope.launch { settingsManager.updateSetting(SettingsManager.KEY_SWIPE, it) } },
            onTogglePinch = { scope.launch { settingsManager.updateSetting(SettingsManager.KEY_PINCH, it) } },
            onToggleDoubleTap = { scope.launch { settingsManager.updateSetting(SettingsManager.KEY_DOUBLE_TAP, it) } },
            onToggleHold2x = { scope.launch { settingsManager.updateSetting(SettingsManager.KEY_HOLD_2X, it) } },
            onToggleAutoPip = { scope.launch { settingsManager.updateSetting(SettingsManager.KEY_AUTO_PIP, it) } }
        )
    }

    if (showHistorySheet) {
        ModalBottomSheet(
            onDismissRequest = { showHistorySheet = false }
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Playback History",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    if (historyList.isNotEmpty()) {
                        TextButton(
                            onClick = { scope.launch { database.historyDao().clearAll() } }
                        ) {
                            Text("Clear All")
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                if (historyList.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 48.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "No playback history yet",
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        contentPadding = PaddingValues(bottom = 32.dp)
                    ) {
                        items(historyList, key = { it.id }) { item ->
                            ElevatedCard(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        streamTitle = item.title
                                        streamUrl = item.url
                                        userAgent = item.userAgent
                                        referer = item.referer
                                        customHeaders = item.rawHeaders
                                        showHistorySheet = false
                                    }
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(14.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = item.title,
                                            style = MaterialTheme.typography.titleSmall,
                                            fontWeight = FontWeight.Bold,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Text(
                                            text = item.url,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        val dateStr = remember(item.timestamp) {
                                            SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault())
                                                .format(Date(item.timestamp))
                                        }
                                        Text(
                                            text = dateStr,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                    IconButton(
                                        onClick = {
                                            showHistorySheet = false
                                            onPlayStream(item.url, item.userAgent, item.referer, item.rawHeaders)
                                        }
                                    ) {
                                        Icon(
                                            Icons.Default.PlayCircleFilled,
                                            contentDescription = "Play Now",
                                            tint = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                    IconButton(
                                        onClick = { scope.launch { database.historyDao().delete(item) } }
                                    ) {
                                        Icon(Icons.Default.DeleteOutline, contentDescription = "Delete")
                                    }
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
fun GestureSettingsDialog(
    settings: GestureSettings,
    onDismiss: () -> Unit,
    onToggleSwipe: (Boolean) -> Unit,
    onTogglePinch: (Boolean) -> Unit,
    onToggleDoubleTap: (Boolean) -> Unit,
    onToggleHold2x: (Boolean) -> Unit,
    onToggleAutoPip: (Boolean) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "Player & Gesture Settings",
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                SettingToggleRow(
                    title = "Volume & Brightness Swipe",
                    subtitle = "Left swipe for Brightness, Right for Volume (Status bar top zone protected)",
                    checked = settings.swipeVolumeBrightness,
                    onCheckedChange = onToggleSwipe
                )
                SettingToggleRow(
                    title = "Pinch In / Out Zoom",
                    subtitle = "Two-finger pinch gesture to scale video",
                    checked = settings.pinchToZoom,
                    onCheckedChange = onTogglePinch
                )
                SettingToggleRow(
                    title = "Double-Tap 10s Seek",
                    subtitle = "Double tap left or right side to seek 10 seconds",
                    checked = settings.doubleTapSeek,
                    onCheckedChange = onToggleDoubleTap
                )
                SettingToggleRow(
                    title = "Hold for 2x Speed",
                    subtitle = "Long press anywhere on video for temporary 2x playback",
                    checked = settings.holdFor2xSpeed,
                    onCheckedChange = onToggleHold2x
                )
                SettingToggleRow(
                    title = "Auto Picture-in-Picture",
                    subtitle = "Enter PiP mode when pressing Home while playing",
                    checked = settings.autoPip,
                    onCheckedChange = onToggleAutoPip
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Done")
            }
        }
    )
}

@Composable
fun SettingToggleRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
            Text(text = title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
            Text(text = subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

package com.streamplay.player

import android.app.PictureInPictureParams
import android.content.Context
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.media.AudioManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.util.Rational
import android.view.TextureView
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.annotation.OptIn
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.media3.common.*
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.AspectRatioFrameLayout
import com.streamplay.player.data.GestureSettings
import com.streamplay.player.data.SettingsManager
import com.streamplay.player.ui.theme.StreamPlayTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import java.util.Locale
import java.util.concurrent.TimeUnit
import kotlin.math.abs

@OptIn(UnstableApi::class)
class PlayerActivity : ComponentActivity() {
    private var exoPlayer: ExoPlayer? = null
    private var isInPipModeState by mutableStateOf(false)
    private var autoPipEnabled = true

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
        )
        super.onCreate(savedInstanceState)

        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        hideSystemBars()

        val streamUrl = intent.getStringExtra("STREAM_URL") ?: ""
        val userAgent = intent.getStringExtra("USER_AGENT") ?: ""
        val referer = intent.getStringExtra("REFERER") ?: ""
        val rawHeaders = intent.getStringExtra("RAW_HEADERS") ?: ""

        val headersMap = parseHeaders(userAgent, referer, rawHeaders)
        val okHttpClient = OkHttpClient.Builder()
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .followRedirects(true)
            .followSslRedirects(true)
            .build()

        val okHttpFactory = OkHttpDataSource.Factory(okHttpClient).apply {
            if (userAgent.isNotBlank()) {
                setUserAgent(userAgent)
            } else {
                setUserAgent("Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Mobile Safari/537.36")
            }
            if (headersMap.isNotEmpty()) {
                setDefaultRequestProperties(headersMap)
            }
        }

        val dataSourceFactory = DefaultDataSource.Factory(this, okHttpFactory)
        val player = ExoPlayer.Builder(this)
            .setMediaSourceFactory(DefaultMediaSourceFactory(dataSourceFactory))
            .build().apply {
                setMediaItem(MediaItem.fromUri(Uri.parse(streamUrl)))
                prepare()
                playWhenReady = true
            }

        exoPlayer = player
        val settingsManager = SettingsManager(this)

        setContent {
            val gestureSettings by settingsManager.settingsFlow.collectAsState(initial = GestureSettings())
            LaunchedEffect(gestureSettings.autoPip) {
                autoPipEnabled = gestureSettings.autoPip
            }

            StreamPlayTheme(darkTheme = true) {
                VideoPlayerScreen(
                    player = player,
                    settingsManager = settingsManager,
                    gestureSettings = gestureSettings,
                    isInPipMode = isInPipModeState,
                    onEnterPip = { enterPip() },
                    onToggleRotation = { toggleRotation() },
                    onBack = { finish() },
                    onAdjustBrightness = { delta -> adjustWindowBrightness(delta) }
                )
            }
        }
    }

    private fun parseHeaders(userAgent: String, referer: String, raw: String): Map<String, String> {
        val map = mutableMapOf<String, String>()
        if (referer.isNotBlank()) {
            map["Referer"] = referer
        }
        raw.lines().forEach { line ->
            val index = line.indexOf(":")
            if (index > 0) {
                val key = line.substring(0, index).trim()
                val value = line.substring(index + 1).trim()
                if (key.isNotEmpty() && value.isNotEmpty()) {
                    map[key] = value
                }
            }
        }
        return map
    }

    private fun hideSystemBars() {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        val controller = WindowInsetsControllerCompat(window, window.decorView)
        controller.hide(WindowInsetsCompat.Type.systemBars())
        controller.systemBarsBehavior =
            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
    }

    private fun adjustWindowBrightness(delta: Float): Float {
        val lp = window.attributes
        val current = if (lp.screenBrightness < 0f) 0.5f else lp.screenBrightness
        val updated = (current + delta).coerceIn(0.02f, 1.0f)
        lp.screenBrightness = updated
        window.attributes = lp
        return updated
    }

    private fun toggleRotation() {
        requestedOrientation = if (resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE) {
            ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT
        } else {
            ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        }
    }

    private fun enterPip() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val params = PictureInPictureParams.Builder()
                .setAspectRatio(Rational(16, 9))
                .build()
            enterPictureInPictureMode(params)
        }
    }

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        if (autoPipEnabled && exoPlayer?.isPlaying == true) {
            enterPip()
        }
    }

    override fun onPictureInPictureModeChanged(
        isInPictureInPictureMode: Boolean,
        newConfig: Configuration
    ) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
        isInPipModeState = isInPictureInPictureMode
        if (!isInPictureInPictureMode) {
            hideSystemBars()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        exoPlayer?.release()
        exoPlayer = null
    }
}

data class VideoTrackOption(
    val name: String,
    val group: Tracks.Group?,
    val trackIndex: Int
)

data class AudioTrackOption(
    val name: String,
    val group: Tracks.Group,
    val trackIndex: Int
)

@OptIn(UnstableApi::class)
@Composable
fun VideoPlayerScreen(
    player: ExoPlayer,
    settingsManager: SettingsManager,
    gestureSettings: GestureSettings,
    isInPipMode: Boolean,
    onEnterPip: () -> Unit,
    onToggleRotation: () -> Unit,
    onBack: () -> Unit,
    onAdjustBrightness: (Float) -> Float
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val audioManager = remember { context.getSystemService(Context.AUDIO_SERVICE) as AudioManager }
    val maxVolume = remember { audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1) }

    var isPlaying by remember { mutableStateOf(player.isPlaying) }
    var playbackState by remember { mutableIntStateOf(player.playbackState) }
    var currentPosition by remember { mutableLongStateOf(0L) }
    var duration by remember { mutableLongStateOf(0L) }
    var isLive by remember { mutableStateOf(false) }
    var isMuted by remember { mutableStateOf(false) }
    var playbackSpeed by remember { mutableFloatStateOf(1.0f) }

    var controlsVisible by remember { mutableStateOf(true) }
    var isLocked by remember { mutableStateOf(false) }
    var zoomScale by remember { mutableFloatStateOf(1f) }
    var panOffsetX by remember { mutableFloatStateOf(0f) }
    var panOffsetY by remember { mutableFloatStateOf(0f) }

    var statusOverlayText by remember { mutableStateOf<String?>(null) }
    var isHolding2x by remember { mutableStateOf(false) }

    var showQualityDialog by remember { mutableStateOf(false) }
    var showAudioDialog by remember { mutableStateOf(false) }
    var showSpeedDialog by remember { mutableStateOf(false) }
    var showSettingsDialog by remember { mutableStateOf(false) }

    var videoTracks by remember { mutableStateOf<List<VideoTrackOption>>(emptyList()) }
    var audioTracks by remember { mutableStateOf<List<AudioTrackOption>>(emptyList()) }

    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(playing: Boolean) {
                isPlaying = playing
            }

            override fun onPlaybackStateChanged(state: Int) {
                playbackState = state
                duration = player.duration.coerceAtLeast(0L)
                isLive = player.isCurrentMediaItemLive
            }

            override fun onTracksChanged(tracks: Tracks) {
                val vList = mutableListOf(VideoTrackOption("Auto Quality", null, -1))
                val aList = mutableListOf<AudioTrackOption>()

                for (group in tracks.groups) {
                    if (group.type == C.TRACK_TYPE_VIDEO) {
                        for (i in 0 until group.length) {
                            if (group.isTrackSupported(i)) {
                                val format = group.getTrackFormat(i)
                                val height = format.height
                                val bitrateKbps = if (format.bitrate > 0) "${format.bitrate / 1000} kbps" else ""
                                val label = if (height > 0) "${height}p $bitrateKbps".trim() else "Video Track ${i + 1}"
                                vList.add(VideoTrackOption(label, group, i))
                            }
                        }
                    } else if (group.type == C.TRACK_TYPE_AUDIO) {
                        for (i in 0 until group.length) {
                            if (group.isTrackSupported(i)) {
                                val format = group.getTrackFormat(i)
                                val lang = format.language?.let { Locale(it).displayLanguage } ?: "Track ${i + 1}"
                                val label = listOfNotNull(lang, format.label).joinToString(" - ").ifBlank { "Audio ${i + 1}" }
                                aList.add(AudioTrackOption(label, group, i))
                            }
                        }
                    }
                }
                videoTracks = vList
                audioTracks = aList
            }
        }
        player.addListener(listener)
        onDispose { player.removeListener(listener) }
    }

    LaunchedEffect(player) {
        while (true) {
            currentPosition = player.currentPosition.coerceAtLeast(0L)
            duration = player.duration.coerceAtLeast(0L)
            isLive = player.isCurrentMediaItemLive
            delay(500)
        }
    }

    LaunchedEffect(controlsVisible, isPlaying, isLocked) {
        if (controlsVisible && isPlaying) {
            delay(4000)
            controlsVisible = false
        }
    }

    LaunchedEffect(statusOverlayText) {
        if (statusOverlayText != null && !isHolding2x) {
            delay(900)
            statusOverlayText = null
        }
    }

    var volumeAccumulator by remember { mutableFloatStateOf(audioManager.getStreamVolume(AudioManager.STREAM_MUSIC).toFloat()) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .pointerInput(gestureSettings, isLocked, isInPipMode) {
                if (isInPipMode) return@pointerInput
                detectTapGestures(
                    onTap = {
                        controlsVisible = !controlsVisible
                    },
                    onDoubleTap = { offset ->
                        if (!isLocked && gestureSettings.doubleTapSeek) {
                            val width = size.width
                            if (offset.x < width * 0.45f) {
                                val target = (player.currentPosition - 10_000L).coerceAtLeast(0L)
                                player.seekTo(target)
                                statusOverlayText = "Rewind 10s"
                            } else if (offset.x > width * 0.55f) {
                                val target = (player.currentPosition + 10_000L).coerceAtMost(player.duration.coerceAtLeast(player.currentPosition + 10_000L))
                                player.seekTo(target)
                                statusOverlayText = "Forward 10s"
                            }
                        }
                    },
                    onLongPress = {
                        if (!isLocked && gestureSettings.holdFor2xSpeed) {
                            isHolding2x = true
                            player.setPlaybackSpeed(2.0f)
                            statusOverlayText = "2x Speed Active"
                        }
                    }
                )
            }
            .pointerInput(gestureSettings, isLocked, isInPipMode) {
                if (isInPipMode) return@pointerInput
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    val screenHeight = size.height.toFloat().coerceAtLeast(1f)
                    val screenWidth = size.width.toFloat().coerceAtLeast(1f)

                    // TOP 12% DEAD ZONE: Ignore vertical swipe if user pulls down from status bar area
                    val startedInStatusBarZone = down.position.y < (screenHeight * 0.12f)
                    val isLeftSide = down.position.x < (screenWidth * 0.5f)
                    var verticalDragMode = false
                    var totalVerticalDelta = 0f

                    volumeAccumulator = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC).toFloat()

                    do {
                        val event = awaitPointerEvent()
                        val pointerCount = event.changes.count { it.pressed }

                        if (!isLocked && pointerCount >= 2 && gestureSettings.pinchToZoom) {
                            val zoomChange = event.calculateZoom()
                            val panChange = event.calculatePan()
                            if (zoomChange != 1f) {
                                zoomScale = (zoomScale * zoomChange).coerceIn(0.5f, 4.0f)
                                if (zoomScale <= 1.02f) {
                                    panOffsetX = 0f
                                    panOffsetY = 0f
                                } else {
                                    panOffsetX += panChange.x
                                    panOffsetY += panChange.y
                                }
                                statusOverlayText = "Zoom ${(zoomScale * 100).toInt()}%"
                                event.changes.forEach { if (it.positionChanged()) it.consume() }
                            }
                        } else if (!isLocked && pointerCount == 1 && !startedInStatusBarZone && gestureSettings.swipeVolumeBrightness && !isHolding2x) {
                            val change = event.changes.firstOrNull()
                            if (change != null && change.positionChanged()) {
                                val dy = change.position.y - change.previousPosition.y
                                val dx = change.position.x - change.previousPosition.x

                                if (!verticalDragMode && abs(dy) > 12f && abs(dy) > abs(dx) * 1.5f) {
                                    verticalDragMode = true
                                }

                                if (verticalDragMode) {
                                    totalVerticalDelta += dy
                                    val sensitivity = screenHeight * 0.65f
                                    val deltaRatio = -dy / sensitivity

                                    if (isLeftSide) {
                                        val newBright = onAdjustBrightness(deltaRatio)
                                        statusOverlayText = "Brightness ${(newBright * 100).toInt()}%"
                                    } else {
                                        volumeAccumulator = (volumeAccumulator + deltaRatio * maxVolume).coerceIn(0f, maxVolume.toFloat())
                                        val targetVol = volumeAccumulator.toInt()
                                        audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, targetVol, 0)
                                        val pct = ((volumeAccumulator / maxVolume) * 100).toInt()
                                        statusOverlayText = "Volume $pct%"
                                    }
                                    change.consume()
                                }
                            }
                        }
                    } while (event.changes.any { it.pressed })

                    if (isHolding2x) {
                        isHolding2x = false
                        player.setPlaybackSpeed(playbackSpeed)
                        statusOverlayText = null
                    }
                }
            }
    ) {
        // Video Surface using TextureView for smooth pinch-to-zoom scaling
        AndroidView(
            factory = { ctx ->
                AspectRatioFrameLayout(ctx).apply {
                    resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                    val textureView = TextureView(ctx)
                    addView(textureView)
                    player.setVideoTextureView(textureView)
                }
            },
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer(
                    scaleX = zoomScale,
                    scaleY = zoomScale,
                    translationX = panOffsetX,
                    translationY = panOffsetY
                )
        )

        // Buffering indicator
        if (playbackState == Player.STATE_BUFFERING) {
            CircularProgressIndicator(
                modifier = Modifier.align(Alignment.Center),
                color = MaterialTheme.colorScheme.primary
            )
        }

        // Gesture feedback badge (Volume / Brightness / 2x / Zoom / Seek)
        statusOverlayText?.let { text ->
            Surface(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 48.dp),
                shape = RoundedCornerShape(24.dp),
                color = Color.Black.copy(alpha = 0.75f)
            ) {
                Text(
                    text = text,
                    color = Color.White,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp)
                )
            }
        }

        // Full Custom Controls Overlay
        AnimatedVisibility(
            visible = controlsVisible && !isInPipMode,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.fillMaxSize()
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.45f))
            ) {
                if (isLocked) {
                    // Lock Mode UI
                    IconButton(
                        onClick = { isLocked = false },
                        modifier = Modifier
                            .align(Alignment.CenterStart)
                            .padding(start = 24.dp)
                            .background(MaterialTheme.colorScheme.primary, CircleShape)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Lock,
                            contentDescription = "Unlock Controls",
                            tint = Color.White
                        )
                    }
                } else {
                    // Top Controls Bar
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .align(Alignment.TopCenter)
                            .padding(horizontal = 16.dp, vertical = 16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        IconButton(onClick = onBack) {
                            Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = Color.White)
                        }

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            // Go Live Button
                            TextButton(
                                onClick = {
                                    if (player.isCurrentMediaItemLive) {
                                        player.seekToDefaultPosition()
                                    } else if (duration > 0) {
                                        player.seekTo(duration)
                                    }
                                },
                                colors = ButtonDefaults.textButtonColors(
                                    contentColor = if (isLive) MaterialTheme.colorScheme.primary else Color.White
                                )
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Sensors,
                                    contentDescription = "Go Live",
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("GO LIVE", fontWeight = FontWeight.Bold)
                            }

                            // Video Quality Selection
                            IconButton(onClick = { showQualityDialog = true }) {
                                Icon(Icons.Default.HighQuality, contentDescription = "Video Quality", tint = Color.White)
                            }

                            // Audio Language Selection
                            IconButton(onClick = { showAudioDialog = true }) {
                                Icon(Icons.Default.Audiotrack, contentDescription = "Audio Track", tint = Color.White)
                            }

                            // Speed Control Button
                            TextButton(onClick = { showSpeedDialog = true }) {
                                Text(
                                    text = "${playbackSpeed}x",
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold
                                )
                            }

                            // Gesture Settings Button inside Player
                            IconButton(onClick = { showSettingsDialog = true }) {
                                Icon(Icons.Default.Tune, contentDescription = "Gesture Settings", tint = Color.White)
                            }
                        }
                    }

                    // Center Play/Pause & 10s Skip Controls
                    Row(
                        modifier = Modifier.align(Alignment.Center),
                        horizontalArrangement = Arrangement.spacedBy(36.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(
                            onClick = {
                                player.seekTo((player.currentPosition - 10_000L).coerceAtLeast(0L))
                            },
                            modifier = Modifier
                                .size(52.dp)
                                .background(Color.Black.copy(alpha = 0.5f), CircleShape)
                        ) {
                            Icon(
                                Icons.Default.Replay10,
                                contentDescription = "Rewind 10s",
                                tint = Color.White,
                                modifier = Modifier.size(30.dp)
                            )
                        }

                        IconButton(
                            onClick = {
                                if (player.playbackState == Player.STATE_ENDED) {
                                    player.seekTo(0)
                                    player.play()
                                } else if (player.isPlaying) {
                                    player.pause()
                                } else {
                                    player.play()
                                }
                            },
                            modifier = Modifier
                                .size(68.dp)
                                .background(MaterialTheme.colorScheme.primary, CircleShape)
                        ) {
                            Icon(
                                imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                contentDescription = "Play or Pause",
                                tint = Color.White,
                                modifier = Modifier.size(40.dp)
                            )
                        }

                        IconButton(
                            onClick = {
                                player.seekTo(player.currentPosition + 10_000L)
                            },
                            modifier = Modifier
                                .size(52.dp)
                                .background(Color.Black.copy(alpha = 0.5f), CircleShape)
                        ) {
                            Icon(
                                Icons.Default.Forward10,
                                contentDescription = "Forward 10s",
                                tint = Color.White,
                                modifier = Modifier.size(30.dp)
                            )
                        }
                    }

                    // Bottom Bar: Seekbar + Lock, Mute, Rotation, PiP, Reset Zoom
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .align(Alignment.BottomCenter)
                            .padding(horizontal = 16.dp, vertical = 14.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = formatDuration(currentPosition),
                                color = Color.White,
                                style = MaterialTheme.typography.bodySmall
                            )
                            Slider(
                                value = if (duration > 0) currentPosition.toFloat().coerceIn(0f, duration.toFloat()) else 0f,
                                onValueChange = { newPos ->
                                    currentPosition = newPos.toLong()
                                    player.seekTo(currentPosition)
                                },
                                valueRange = 0f..(duration.toFloat().coerceAtLeast(1f)),
                                modifier = Modifier
                                    .weight(1f)
                                    .padding(horizontal = 12.dp),
                                colors = SliderDefaults.colors(
                                    thumbColor = MaterialTheme.colorScheme.primary,
                                    activeTrackColor = MaterialTheme.colorScheme.primary
                                )
                            )
                            Text(
                                text = if (isLive && duration <= 0) "LIVE" else formatDuration(duration),
                                color = Color.White,
                                style = MaterialTheme.typography.bodySmall
                            )
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                // Lock Button
                                IconButton(onClick = { isLocked = true }) {
                                    Icon(Icons.Default.LockOpen, contentDescription = "Lock Screen", tint = Color.White)
                                }

                                // Speaker Mute / Unmute Button
                                IconButton(
                                    onClick = {
                                        isMuted = !isMuted
                                        player.volume = if (isMuted) 0f else 1f
                                    }
                                ) {
                                    Icon(
                                        imageVector = if (isMuted) Icons.Default.VolumeOff else Icons.Default.VolumeUp,
                                        contentDescription = "Mute or Unmute",
                                        tint = Color.White
                                    )
                                }

                                if (zoomScale != 1f) {
                                    TextButton(
                                        onClick = {
                                            zoomScale = 1f
                                            panOffsetX = 0f
                                            panOffsetY = 0f
                                        }
                                    ) {
                                        Text("Reset Zoom", color = MaterialTheme.colorScheme.primary)
                                    }
                                }
                            }

                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                // Picture in Picture Button
                                IconButton(onClick = onEnterPip) {
                                    Icon(
                                        Icons.Default.PictureInPictureAlt,
                                        contentDescription = "Picture in Picture",
                                        tint = Color.White
                                    )
                                }

                                // Screen Rotation Button
                                IconButton(onClick = onToggleRotation) {
                                    Icon(
                                        Icons.Default.ScreenRotation,
                                        contentDescription = "Rotate Screen",
                                        tint = Color.White
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // Quality Selection Dialog
    if (showQualityDialog) {
        AlertDialog(
            onDismissRequest = { showQualityDialog = false },
            title = { Text("Select Video Quality", fontWeight = FontWeight.Bold) },
            text = {
                LazyColumn {
                    items(videoTracks) { item ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    val builder = player.trackSelectionParameters.buildUpon()
                                    if (item.group == null) {
                                        builder.clearOverridesOfType(C.TRACK_TYPE_VIDEO)
                                    } else {
                                        builder.clearOverridesOfType(C.TRACK_TYPE_VIDEO)
                                        builder.addOverride(
                                            TrackSelectionOverride(
                                                item.group.mediaTrackGroup,
                                                listOf(item.trackIndex)
                                            )
                                        )
                                    }
                                    player.trackSelectionParameters = builder.build()
                                    showQualityDialog = false
                                }
                                .padding(vertical = 12.dp, horizontal = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.HighQuality, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(text = item.name, style = MaterialTheme.typography.bodyLarge)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showQualityDialog = false }) { Text("Close") }
            }
        )
    }

    // Audio Language Selection Dialog
    if (showAudioDialog) {
        AlertDialog(
            onDismissRequest = { showAudioDialog = false },
            title = { Text("Select Audio Language", fontWeight = FontWeight.Bold) },
            text = {
                if (audioTracks.isEmpty()) {
                    Text("Default Audio Track (No multi-audio tracks detected)")
                } else {
                    LazyColumn {
                        items(audioTracks) { item ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        val builder = player.trackSelectionParameters.buildUpon()
                                        builder.clearOverridesOfType(C.TRACK_TYPE_AUDIO)
                                        builder.addOverride(
                                            TrackSelectionOverride(
                                                item.group.mediaTrackGroup,
                                                listOf(item.trackIndex)
                                            )
                                        )
                                        player.trackSelectionParameters = builder.build()
                                        showAudioDialog = false
                                    }
                                    .padding(vertical = 12.dp, horizontal = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Default.Audiotrack, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                Spacer(modifier = Modifier.width(12.dp))
                                Text(text = item.name, style = MaterialTheme.typography.bodyLarge)
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showAudioDialog = false }) { Text("Close") }
            }
        )
    }

    // Playback Speed Dialog
    if (showSpeedDialog) {
        val speeds = listOf(0.25f, 0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 1.75f, 2.0f, 2.5f, 3.0f)
        AlertDialog(
            onDismissRequest = { showSpeedDialog = false },
            title = { Text("Playback Speed", fontWeight = FontWeight.Bold) },
            text = {
                LazyColumn {
                    items(speeds) { spd ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    playbackSpeed = spd
                                    player.setPlaybackSpeed(spd)
                                    showSpeedDialog = false
                                }
                                .padding(vertical = 10.dp, horizontal = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(text = "${spd}x", style = MaterialTheme.typography.bodyLarge)
                            if (playbackSpeed == spd) {
                                Icon(Icons.Default.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showSpeedDialog = false }) { Text("Close") }
            }
        )
    }

    // In-Player Gesture Settings Dialog
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
}

private fun formatDuration(millis: Long): String {
    if (millis <= 0L) return "00:00"
    val totalSeconds = millis / 1000
    val seconds = totalSeconds % 60
    val minutes = (totalSeconds / 60) % 60
    val hours = totalSeconds / 3600
    return if (hours > 0) {
        String.format(Locale.US, "%d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format(Locale.US, "%02d:%02d", minutes, seconds)
    }
}

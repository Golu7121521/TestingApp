package com.universal.streamplayer.player

import android.app.Activity
import android.app.PictureInPictureParams
import android.content.pm.ActivityInfo
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.util.Rational
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.annotation.OptIn
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.media3.common.*
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.universal.streamplayer.data.DrmSchemes
import com.universal.streamplayer.data.StreamRequest
import com.universal.streamplayer.ui.theme.UniversalStreamPlayerTheme
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDLRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.util.Locale

@OptIn(UnstableApi::class)
class PlayerActivity : ComponentActivity() {

    private var exoPlayer: ExoPlayer? = null
    private var isInPipState = mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Full edge-to-edge immersive screen
        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        hideSystemBars()

        val streamRequest = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getSerializableExtra("STREAM_REQUEST", StreamRequest::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getSerializableExtra("STREAM_REQUEST") as? StreamRequest
        } ?: StreamRequest(url = "")

        setContent {
            UniversalStreamPlayerTheme(darkTheme = true) {
                EdgeToEdgeVideoPlayer(
                    request = streamRequest,
                    isInPip = isInPipState.value,
                    onEnterPip = { triggerPipMode() },
                    onExit = { finish() },
                    onPlayerCreated = { exoPlayer = it }
                )
            }
        }
    }

    private fun hideSystemBars() {
        val controller = WindowInsetsControllerCompat(window, window.decorView)
        controller.hide(WindowInsetsCompat.Type.systemBars())
        controller.systemBarsBehavior =
            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
    }

    private fun triggerPipMode() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val videoSize = exoPlayer?.videoSize
            val rational = if (videoSize != null && videoSize.width > 0 && videoSize.height > 0) {
                Rational(videoSize.width, videoSize.height)
            } else {
                Rational(16, 9)
            }
            val params = PictureInPictureParams.Builder()
                .setAspectRatio(rational)
                .build()
            enterPictureInPictureMode(params)
        }
    }

    override fun onPictureInPictureModeChanged(isInPictureInPictureMode: Boolean) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode)
        isInPipState.value = isInPictureInPictureMode
    }

    override fun onStop() {
        super.onStop()
        if (!isInPictureInPictureMode) {
            exoPlayer?.pause()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        exoPlayer?.release()
        exoPlayer = null
    }
}

@OptIn(UnstableApi::class)
@Composable
fun EdgeToEdgeVideoPlayer(
    request: StreamRequest,
    isInPip: Boolean,
    onEnterPip: () -> Unit,
    onExit: () -> Unit,
    onPlayerCreated: (ExoPlayer) -> Unit
) {
    val context = LocalContext.current
    val activity = context as? Activity

    // Build custom headers (Cookie, Referer, Origin, User-Agent)
    val player = remember {
        val headers = mutableMapOf<String, String>()
        if (request.cookie.isNotBlank()) headers["Cookie"] = request.cookie
        if (request.referer.isNotBlank()) headers["Referer"] = request.referer
        if (request.origin.isNotBlank()) headers["Origin"] = request.origin

        val httpDataSourceFactory = DefaultHttpDataSource.Factory()
            .setUserAgent(request.userAgent)
            .setDefaultRequestProperties(headers)
            .setAllowCrossProtocolRedirects(true)

        val dataSourceFactory = DefaultDataSource.Factory(context, httpDataSourceFactory)

        ExoPlayer.Builder(context)
            .setMediaSourceFactory(DefaultMediaSourceFactory(dataSourceFactory))
            .build()
            .also { onPlayerCreated(it) }
    }

    var isPlaying by remember { mutableStateOf(true) }
    var currentPos by remember { mutableLongStateOf(0L) }
    var duration by remember { mutableLongStateOf(0L) }
    var isLiveStream by remember { mutableStateOf(false) }
    var isMuted by remember { mutableStateOf(false) }
    var isLocked by remember { mutableStateOf(false) }
    var controlsVisible by remember { mutableStateOf(true) }
    var is2xHoldActive by remember { mutableStateOf(false) }
    var currentSpeed by remember { mutableFloatStateOf(1.0f) }
    var subtitlesEnabled by remember { mutableStateOf(true) }
    var seekFeedbackText by remember { mutableStateOf<String?>(null) }

    var showQualityDialog by remember { mutableStateOf(false) }
    var showAudioDialog by remember { mutableStateOf(false) }
    var showSpeedDialog by remember { mutableStateOf(false) }

    // Build MediaItem with DRM, or resolve social links via yt-dlp
    LaunchedEffect(request.url) {
        fun buildConfiguredMediaItem(targetUrl: String): MediaItem {
            val builder = MediaItem.Builder().setUri(Uri.parse(targetUrl))
            if (request.drmScheme != DrmSchemes.NONE.label && request.drmLicenseUrl.isNotBlank()) {
                val uuid = when (request.drmScheme) {
                    DrmSchemes.WIDEVINE.label -> C.WIDEVINE_UUID
                    DrmSchemes.PLAYREADY.label -> C.PLAYREADY_UUID
                    DrmSchemes.CLEARKEY.label -> C.CLEARKEY_UUID
                    else -> null
                }
                if (uuid != null) {
                    builder.setDrmConfiguration(
                        MediaItem.DrmConfiguration.Builder(uuid)
                            .setLicenseUri(request.drmLicenseUrl)
                            .build()
                    )
                }
            }
            return builder.build()
        }

        val isSocialLink = listOf("youtube.com", "youtu.be", "instagram.com", "tiktok.com", "facebook.com", "twitter.com", "x.com")
            .any { request.url.contains(it, ignoreCase = true) }

        if (isSocialLink) {
            withContext(Dispatchers.IO) {
                try {
                    val ytReq = YoutubeDLRequest(request.url).apply {
                        addOption("-f", "best")
                    }
                    val info = YoutubeDL.getInstance().getInfo(ytReq)
                    val resolvedUrl = info.url ?: request.url
                    withContext(Dispatchers.Main) {
                        player.setMediaItem(buildConfiguredMediaItem(resolvedUrl))
                        player.prepare()
                        player.playWhenReady = true
                    }
                } catch (_: Exception) {
                    withContext(Dispatchers.Main) {
                        player.setMediaItem(buildConfiguredMediaItem(request.url))
                        player.prepare()
                        player.playWhenReady = true
                    }
                }
            }
        } else {
            player.setMediaItem(buildConfiguredMediaItem(request.url))
            player.prepare()
            player.playWhenReady = true
        }
    }

    // Auto-orientation: portrait for Shorts/Reels, landscape for 16:9
    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(playing: Boolean) {
                isPlaying = playing
            }

            override fun onPlaybackStateChanged(state: Int) {
                duration = player.duration.coerceAtLeast(0L)
                isLiveStream = player.isCurrentMediaItemLive
            }

            override fun onVideoSizeChanged(videoSize: VideoSize) {
                if (videoSize.width > 0 && videoSize.height > 0) {
                    activity?.requestedOrientation = if (videoSize.height >= videoSize.width) {
                        ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT
                    } else {
                        ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
                    }
                }
            }

            override fun onPlayerError(error: PlaybackException) {
                Toast.makeText(context, "Playback error: ${error.errorCodeName}", Toast.LENGTH_LONG).show()
            }
        }
        player.addListener(listener)
        onDispose {
            player.removeListener(listener)
        }
    }

    // Progress loop
    LaunchedEffect(isPlaying) {
        while (true) {
            currentPos = player.currentPosition.coerceAtLeast(0L)
            duration = player.duration.coerceAtLeast(0L)
            isLiveStream = player.isCurrentMediaItemLive
            delay(500)
        }
    }

    // Clear seek feedback indicator
    LaunchedEffect(seekFeedbackText) {
        if (seekFeedbackText != null) {
            delay(750)
            seekFeedbackText = null
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .pointerInput(isLocked) {
                detectTapGestures(
                    onTap = { controlsVisible = !controlsVisible },
                    onDoubleTap = { offset ->
                        if (!isLocked) {
                            val screenWidth = size.width
                            if (offset.x < screenWidth / 2) {
                                player.seekTo((player.currentPosition - 10_000L).coerceAtLeast(0L))
                                seekFeedbackText = "⏪ -10s"
                            } else {
                                player.seekTo((player.currentPosition + 10_000L).coerceAtMost(player.duration.coerceAtLeast(0L)))
                                seekFeedbackText = "+10s ⏩"
                            }
                        }
                    },
                    onLongPress = {
                        if (!isLocked) {
                            is2xHoldActive = true
                            player.setPlaybackSpeed(2.0f)
                        }
                    },
                    onPress = {
                        tryAwaitRelease()
                        if (is2xHoldActive) {
                            is2xHoldActive = false
                            player.setPlaybackSpeed(currentSpeed)
                        }
                    }
                )
            }
    ) {
        // Edge-to-edge video surface
        AndroidView(
            factory = { ctx ->
                PlayerView(ctx).apply {
                    this.player = player
                    useController = false
                    resizeMode = AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                    layoutParams = FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                }
            },
            modifier = Modifier.fillMaxSize()
        )

        // 2x hold pill
        if (is2xHoldActive) {
            Surface(
                color = Color.Black.copy(alpha = 0.7f),
                shape = RoundedCornerShape(20.dp),
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 28.dp)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.FastForward, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("2x Speed Active", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                }
            }
        }

        // Double-tap ±10s indicator
        seekFeedbackText?.let { feedback ->
            Surface(
                color = Color.Black.copy(alpha = 0.65f),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.align(Alignment.Center)
            ) {
                Text(
                    text = feedback,
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp)
                )
            }
        }

        // Hide UI overlays in Picture-in-Picture mode
        if (!isInPip) {
            AnimatedVisibility(
                visible = controlsVisible,
                enter = fadeIn(),
                exit = fadeOut(),
                modifier = Modifier.fillMaxSize()
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.42f))
                ) {
                    if (isLocked) {
                        IconButton(
                            onClick = { isLocked = false },
                            modifier = Modifier
                                .align(Alignment.BottomStart)
                                .padding(24.dp)
                                .background(Color.Black.copy(alpha = 0.6f), CircleShape)
                        ) {
                            Icon(Icons.Default.Lock, contentDescription = "Unlock Controls", tint = Color.Yellow)
                        }
                    } else {
                        // Top bar: back, title, quality, audio, subtitles
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .align(Alignment.TopCenter)
                                .padding(horizontal = 16.dp, vertical = 14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                                IconButton(onClick = onExit) {
                                    Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = Color.White)
                                }
                                Text(
                                    text = request.title,
                                    color = Color.White,
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 15.sp,
                                    maxLines = 1
                                )
                            }

                            Row(verticalAlignment = Alignment.CenterVertically) {
                                IconButton(onClick = { showQualityDialog = true }) {
                                    Icon(Icons.Default.Hd, contentDescription = "Video Quality", tint = Color.White)
                                }
                                IconButton(onClick = { showAudioDialog = true }) {
                                    Icon(Icons.Default.Audiotrack, contentDescription = "Audio Language", tint = Color.White)
                                }
                                IconButton(onClick = {
                                    subtitlesEnabled = !subtitlesEnabled
                                    player.trackSelectionParameters = player.trackSelectionParameters
                                        .buildUpon()
                                        .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, !subtitlesEnabled)
                                        .build()
                                    Toast.makeText(
                                        context,
                                        if (subtitlesEnabled) "Subtitles enabled" else "Subtitles off",
                                        Toast.LENGTH_SHORT
                                    ).show()
                                }) {
                                    Icon(
                                        imageVector = if (subtitlesEnabled) Icons.Default.Subtitles else Icons.Default.SubtitlesOff,
                                        contentDescription = "Subtitles",
                                        tint = if (subtitlesEnabled) Color.White else Color.Gray
                                    )
                                }
                            }
                        }

                        // Bottom panel: time + go live, thin seekbar, controls
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .align(Alignment.BottomCenter)
                                .padding(horizontal = 16.dp, vertical = 10.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "${formatDuration(currentPos)} / ${formatDuration(duration)}",
                                    color = Color.White,
                                    fontSize = 12.sp
                                )

                                TextButton(
                                    onClick = {
                                        if (duration > 0) player.seekToDefaultPosition()
                                    },
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(8.dp)
                                            .background(if (isLiveStream) Color.Red else Color.Gray, CircleShape)
                                    )
                                    Spacer(Modifier.width(5.dp))
                                    Text("GO LIVE", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                }
                            }

                            Slider(
                                value = if (duration > 0) currentPos.toFloat() / duration.toFloat() else 0f,
                                onValueChange = { fraction ->
                                    if (duration > 0) {
                                        player.seekTo((fraction * duration).toLong())
                                    }
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(22.dp),
                                colors = SliderDefaults.colors(
                                    thumbColor = Color(0xFF8AB4F8),
                                    activeTrackColor = Color(0xFF8AB4F8),
                                    inactiveTrackColor = Color.White.copy(alpha = 0.3f)
                                )
                            )

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    IconButton(
                                        onClick = {
                                            if (player.isPlaying) player.pause() else player.play()
                                        }
                                    ) {
                                        Icon(
                                            imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                            contentDescription = "Play/Pause",
                                            tint = Color.White,
                                            modifier = Modifier.size(30.dp)
                                        )
                                    }

                                    IconButton(onClick = {
                                        isMuted = !isMuted
                                        player.volume = if (isMuted) 0f else 1f
                                    }) {
                                        Icon(
                                            imageVector = if (isMuted) Icons.Default.VolumeOff else Icons.Default.VolumeUp,
                                            contentDescription = "Mute/Unmute",
                                            tint = Color.White
                                        )
                                    }

                                    IconButton(onClick = { isLocked = true }) {
                                        Icon(Icons.Default.LockOpen, contentDescription = "Lock Screen", tint = Color.White)
                                    }
                                }

                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    TextButton(onClick = { showSpeedDialog = true }) {
                                        Icon(Icons.Default.Speed, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
                                        Spacer(Modifier.width(4.dp))
                                        Text("${currentSpeed}x", color = Color.White, fontWeight = FontWeight.Bold)
                                    }

                                    IconButton(onClick = onEnterPip) {
                                        Icon(Icons.Default.PictureInPictureAlt, contentDescription = "PiP Mode", tint = Color.White)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // Speed dialog
        if (showSpeedDialog) {
            val speeds = listOf(0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 2.0f)
            AlertDialog(
                onDismissRequest = { showSpeedDialog = false },
                title = { Text("Playback Speed") },
                text = {
                    Column {
                        speeds.forEach { spd ->
                            TextButton(
                                onClick = {
                                    currentSpeed = spd
                                    player.setPlaybackSpeed(spd)
                                    showSpeedDialog = false
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("${spd}x")
                            }
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = { showSpeedDialog = false }) { Text("Close") }
                }
            )
        }

        // Quality dialog
        if (showQualityDialog) {
            val heights = listOf(
                "Auto Quality" to Int.MAX_VALUE,
                "1080p Full HD" to 1080,
                "720p HD" to 720,
                "480p SD" to 480,
                "360p Data Saver" to 360
            )
            AlertDialog(
                onDismissRequest = { showQualityDialog = false },
                title = { Text("Select Video Quality") },
                text = {
                    Column {
                        heights.forEach { (label, maxH) ->
                            TextButton(
                                onClick = {
                                    player.trackSelectionParameters = player.trackSelectionParameters
                                        .buildUpon()
                                        .setMaxVideoSize(Int.MAX_VALUE, maxH)
                                        .build()
                                    showQualityDialog = false
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(label)
                            }
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = { showQualityDialog = false }) { Text("Cancel") }
                }
            )
        }

        // Audio track dialog
        if (showAudioDialog) {
            val audioGroups = player.currentTracks.groups.filter { it.type == C.TRACK_TYPE_AUDIO }
            AlertDialog(
                onDismissRequest = { showAudioDialog = false },
                title = { Text("Select Audio Language") },
                text = {
                    Column {
                        if (audioGroups.isEmpty()) {
                            Text("Default audio stream active")
                        } else {
                            audioGroups.forEachIndexed { index, group ->
                                val format = group.getTrackFormat(0)
                                val langName = format.language?.let { Locale(it).displayLanguage } ?: "Audio Track ${index + 1}"
                                TextButton(
                                    onClick = {
                                        player.trackSelectionParameters = player.trackSelectionParameters
                                            .buildUpon()
                                            .setOverrideForType(TrackSelectionOverride(group.mediaTrackGroup, 0))
                                            .build()
                                        showAudioDialog = false
                                    },
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text(langName)
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

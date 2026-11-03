@file:OptIn(UnstableApi::class)

package com.linkplayer.app

import android.content.Context
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.media.AudioManager
import android.net.Uri
import android.provider.Settings
import android.util.Rational
import android.view.WindowManager
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.common.Timeline
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.TrackSelectionParameters
import androidx.media3.common.Tracks
import androidx.media3.common.VideoSize
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import kotlinx.coroutines.delay
import okhttp3.OkHttpClient
import java.util.Locale
import java.util.concurrent.TimeUnit
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.roundToInt

private val SPEEDS = listOf(0.25f, 0.5f, 0.75f, 1f, 1.25f, 1.5f, 1.75f, 2f, 2.5f, 3f)

private data class Hud(
    val id: Long,
    val icon: ImageVector,
    val text: String,
    val progress: Float? = null,
    val align: Int = 0
)

private data class Opt(val label: String, val selected: Boolean, val onSelect: () -> Unit)

// ------------------------------------------------------------------ player setup

private fun buildPlayer(ctx: Context, req: PlayRequest): ExoPlayer {
    val client = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .build()
    val http = OkHttpDataSource.Factory(client).setDefaultRequestProperties(req.headers)
    if (req.headers.keys.none { it.equals("user-agent", ignoreCase = true) }) {
        http.setUserAgent("Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0 Mobile Safari/537.36")
    }
    val dataSource = DefaultDataSource.Factory(ctx, http)
    val renderers = DefaultRenderersFactory(ctx)
        .setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_PREFER)
        .setEnableDecoderFallback(true)
    return ExoPlayer.Builder(ctx, renderers)
        .setMediaSourceFactory(DefaultMediaSourceFactory(dataSource))
        .setAudioAttributes(AudioAttributes.DEFAULT, true)
        .setHandleAudioBecomingNoisy(true)
        .setSeekBackIncrementMs(10_000)
        .setSeekForwardIncrementMs(10_000)
        .build()
}

/** Extension na ho to ek-ek karke HLS / DASH / SS try hota hai. */
private fun guessMimes(url: String): List<String?> {
    val u = url.lowercase().substringBefore('#')
    return when {
        u.startsWith("rtsp") -> listOf(null)
        u.contains(".m3u8") -> listOf(MimeTypes.APPLICATION_M3U8, null)
        u.contains(".mpd") -> listOf(MimeTypes.APPLICATION_MPD, null)
        u.contains(".ism") -> listOf(MimeTypes.APPLICATION_SS, null)
        else -> listOf(null, MimeTypes.APPLICATION_M3U8, MimeTypes.APPLICATION_MPD, MimeTypes.APPLICATION_SS)
    }
}

private fun isFormatError(code: Int) = code == PlaybackException.ERROR_CODE_PARSING_CONTAINER_UNSUPPORTED ||
        code == PlaybackException.ERROR_CODE_PARSING_CONTAINER_MALFORMED ||
        code == PlaybackException.ERROR_CODE_PARSING_MANIFEST_MALFORMED ||
        code == PlaybackException.ERROR_CODE_PARSING_MANIFEST_UNSUPPORTED

private fun fmtTime(ms: Long): String {
    val s = (ms / 1000).coerceAtLeast(0)
    val h = s / 3600
    val m = (s % 3600) / 60
    val sec = s % 60
    return if (h > 0) String.format(Locale.US, "%d:%02d:%02d", h, m, sec)
    else String.format(Locale.US, "%02d:%02d", m, sec)
}

private fun trackLabel(type: Int, f: Format, n: Int): String {
    if (type == C.TRACK_TYPE_VIDEO) {
        val res = if (f.height > 0) "${f.height}p" else "Track $n"
        val br = if (f.bitrate > 0) "  •  ${f.bitrate / 1000} kbps" else ""
        return res + br
    }
    val lang = f.language?.takeIf { it != "und" }
        ?.let { l -> Locale.forLanguageTag(l).displayLanguage.ifBlank { l } }
    val parts = ArrayList<String>()
    f.label?.let { parts.add(it) }
    if (lang != null && lang != f.label) parts.add(lang)
    var s = if (parts.isEmpty()) "Track $n" else parts.joinToString(" • ")
    if (type == C.TRACK_TYPE_AUDIO && f.channelCount > 0) s += "  •  ${f.channelCount}ch"
    return s
}

private fun trackOptions(player: ExoPlayer, type: Int): List<Opt> {
    val p = player.trackSelectionParameters
    val groups = player.currentTracks.groups.filter { it.type == type }
    val hasOverride = p.overrides.values.any { it.type == type }
    val disabled = p.disabledTrackTypes.contains(type)

    fun update(f: TrackSelectionParameters.Builder.() -> Unit) {
        player.trackSelectionParameters = p.buildUpon().apply(f).build()
    }

    val head = ArrayList<Opt>()
    if (type == C.TRACK_TYPE_VIDEO) head.add(Opt("Auto", !hasOverride) { update { clearOverridesOfType(type) } })
    if (type == C.TRACK_TYPE_TEXT) head.add(Opt("Off", disabled) { update { setTrackTypeDisabled(type, true) } })

    val items = ArrayList<Pair<Int, Opt>>()
    var n = 0
    groups.forEach { g ->
        for (i in 0 until g.length) {
            if (!g.isTrackSupported(i)) continue
            n++
            val f = g.getTrackFormat(i)
            val sel = when (type) {
                C.TRACK_TYPE_VIDEO -> hasOverride && g.isTrackSelected(i)
                C.TRACK_TYPE_TEXT -> !disabled && g.isTrackSelected(i)
                else -> g.isTrackSelected(i)
            }
            items.add(
                f.height to Opt(trackLabel(type, f, n), sel) {
                    update {
                        setOverrideForType(TrackSelectionOverride(g.mediaTrackGroup, i))
                        if (type == C.TRACK_TYPE_TEXT) setTrackTypeDisabled(type, false)
                    }
                }
            )
        }
    }
    val sorted = if (type == C.TRACK_TYPE_VIDEO) items.sortedByDescending { it.first } else items
    return head + sorted.map { it.second }
}

// ------------------------------------------------------------------ screen

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun PlayerScreen(request: PlayRequest, settings: AppSettings, onClose: () -> Unit) {
    MaterialTheme(colorScheme = PlayerDarkScheme) {
        PlayerContent(request, settings, onClose)
    }
}

@Composable
private fun PlayerContent(request: PlayRequest, settings: AppSettings, onClose: () -> Unit) {
    val context = LocalContext.current
    val activity = context as MainActivity
    val density = LocalDensity.current
    val config = LocalConfiguration.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val inPip = activity.inPip

    val player = remember { buildPlayer(context, request) }
    val candidates = remember { guessMimes(request.url) }
    val attempt = remember { intArrayOf(0) }
    val orientDone = remember { booleanArrayOf(false) }
    val audio = remember { context.getSystemService(Context.AUDIO_SERVICE) as AudioManager }

    // player state mirrors
    var pwr by remember { mutableStateOf(true) }
    var pstate by remember { mutableIntStateOf(Player.STATE_IDLE) }
    var tracksVersion by remember { mutableIntStateOf(0) }
    var isLive by remember { mutableStateOf(false) }
    var speed by remember { mutableFloatStateOf(1f) }
    var muted by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var pos by remember { mutableLongStateOf(0L) }
    var dur by remember { mutableLongStateOf(0L) }
    var liveOffset by remember { mutableLongStateOf(C.TIME_UNSET) }

    // ui state
    var controls by remember { mutableStateOf(true) }
    var locked by remember { mutableStateOf(false) }
    var panel by remember { mutableStateOf<String?>(null) }
    var interaction by remember { mutableIntStateOf(0) }
    var seeking by remember { mutableStateOf(false) }
    var seekPos by remember { mutableFloatStateOf(0f) }
    var zoom by remember { mutableFloatStateOf(1f) }
    var hud by remember { mutableStateOf<Hud?>(null) }
    var boost by remember { mutableStateOf(false) }
    var prevSpeed by remember { mutableFloatStateOf(1f) }

    fun showHud(icon: ImageVector, text: String, progress: Float? = null, align: Int = 0) {
        hud = Hud(System.nanoTime(), icon, text, progress, align)
    }

    fun load(i: Int) {
        val b = MediaItem.Builder().setUri(request.url)
        candidates[i]?.let { b.setMimeType(it) }
        player.setMediaItem(b.build())
        player.prepare()
        player.playWhenReady = true
    }

    fun seekBy(ms: Long) {
        var t = player.currentPosition + ms
        val d = player.duration
        if (d != C.TIME_UNSET && d > 0) t = t.coerceAtMost(d)
        player.seekTo(t.coerceAtLeast(0L))
    }

    fun currentBrightness(): Float {
        val b = activity.window.attributes.screenBrightness
        if (b >= 0f) return b
        return try {
            Settings.System.getInt(activity.contentResolver, Settings.System.SCREEN_BRIGHTNESS) / 255f
        } catch (e: Exception) {
            0.5f
        }
    }

    fun setBrightness(v: Float) {
        val lp = activity.window.attributes
        lp.screenBrightness = v
        activity.window.attributes = lp
    }

    // --- listener + lifecycle of the player
    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
                pwr = playWhenReady
            }

            override fun onIsPlayingChanged(isPlaying: Boolean) {
                activity.pipPlaying = isPlaying
                activity.updatePipParams()
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                pstate = playbackState
                isLive = player.isCurrentMediaItemLive
                if (playbackState == Player.STATE_READY) error = null
            }

            override fun onTimelineChanged(timeline: Timeline, reason: Int) {
                isLive = player.isCurrentMediaItemLive
            }

            override fun onTracksChanged(tracks: Tracks) {
                tracksVersion++
            }

            override fun onTrackSelectionParametersChanged(parameters: TrackSelectionParameters) {
                tracksVersion++
            }

            override fun onPlaybackParametersChanged(playbackParameters: PlaybackParameters) {
                speed = playbackParameters.speed
            }

            override fun onVideoSizeChanged(videoSize: VideoSize) {
                if (videoSize.width > 0 && videoSize.height > 0) {
                    val ratio = videoSize.width * videoSize.pixelWidthHeightRatio / videoSize.height
                    val r = ratio.coerceIn(0.42f, 2.38f)
                    activity.pipRatio = Rational((r * 1000).toInt(), 1000)
                    activity.updatePipParams()
                    if (!orientDone[0] && settings[K.AUTO_LANDSCAPE]) {
                        orientDone[0] = true
                        activity.requestedOrientation = if (ratio >= 1f)
                            ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
                        else ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT
                    }
                }
            }

            override fun onPlayerError(e: PlaybackException) {
                val next = attempt[0] + 1
                if (isFormatError(e.errorCode) && next < candidates.size) {
                    attempt[0] = next
                    load(next)
                } else {
                    error = "${e.errorCodeName}\n${e.cause?.message ?: e.message ?: ""}"
                }
            }
        }
        player.addListener(listener)
        activity.pipActive = true
        activity.updatePipParams()
        load(0)
        onDispose {
            player.removeListener(listener)
            player.release()
            activity.pipActive = false
            activity.pipPlaying = false
            activity.updatePipParams()
        }
    }

    // --- pause when app goes to background (but not in PiP)
    DisposableEffect(lifecycleOwner) {
        val obs = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP && !activity.isInPictureInPictureMode) player.pause()
        }
        lifecycleOwner.lifecycle.addObserver(obs)
        onDispose { lifecycleOwner.lifecycle.removeObserver(obs) }
    }

    // --- immersive full screen
    DisposableEffect(Unit) {
        val w = activity.window
        val ctl = WindowCompat.getInsetsController(w, w.decorView)
        ctl.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        ctl.hide(WindowInsetsCompat.Type.systemBars())
        w.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        onDispose {
            ctl.show(WindowInsetsCompat.Type.systemBars())
            w.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            val lp = w.attributes
            lp.screenBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
            w.attributes = lp
            activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        }
    }
    LaunchedEffect(inPip) {
        if (!inPip) {
            val w = activity.window
            WindowCompat.getInsetsController(w, w.decorView).hide(WindowInsetsCompat.Type.systemBars())
        }
    }

    // --- position polling
    LaunchedEffect(Unit) {
        while (true) {
            if (controls && !inPip) {
                pos = player.currentPosition
                val d = player.duration
                dur = if (d == C.TIME_UNSET) 0L else d
                liveOffset = player.currentLiveOffset
                isLive = player.isCurrentMediaItemLive
            }
            delay(250)
        }
    }

    // --- auto hide controls
    LaunchedEffect(controls, pwr, interaction, panel, seeking, locked) {
        if (controls && pwr && panel == null && !seeking) {
            delay(4000)
            controls = false
        }
    }
    LaunchedEffect(hud?.id) {
        if (hud != null) {
            delay(800)
            hud = null
        }
    }

    BackHandler(enabled = !inPip) {
        when {
            panel != null -> panel = null
            locked -> controls = true
            else -> onClose()
        }
    }

    // gesture dead zones (status bar / nav bar / back-gesture edges)
    val barTop = WindowInsets.systemBarsIgnoringVisibility.getTop(density)
    val barBottom = WindowInsets.systemBarsIgnoringVisibility.getBottom(density)
    val deadTop = barTop + with(density) { 28.dp.roundToPx() }
    val deadBottom = barBottom + with(density) { 28.dp.roundToPx() }
    val deadSide = with(density) { 32.dp.roundToPx() }

    val tv = tracksVersion // subscribe
    val hasAudioTracks = remember(tv) { player.currentTracks.groups.any { it.type == C.TRACK_TYPE_AUDIO } }
    val hasTextTracks = remember(tv) { player.currentTracks.groups.any { it.type == C.TRACK_TYPE_TEXT } }

    val title = remember(request.url) {
        val u = Uri.parse(request.url)
        u.lastPathSegment?.takeIf { it.isNotBlank() } ?: u.host ?: request.url
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        // ---- video surface
        AndroidView(
            factory = { ctx ->
                PlayerView(ctx).apply {
                    useController = false
                    this.player = player
                    resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                    setShutterBackgroundColor(Color.Black.toArgb())
                    keepScreenOn = true
                }
            },
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    scaleX = zoom
                    scaleY = zoom
                }
        )

        // ---- gesture layer
        Box(
            Modifier
                .fillMaxSize()
                // taps (outer -> runs after drag layer)
                .pointerInput(Unit) {
                    detectTapGestures(
                        onPress = {
                            tryAwaitRelease()
                            if (boost) {
                                boost = false
                                player.setPlaybackSpeed(prevSpeed)
                            }
                        },
                        onTap = {
                            if (!inPip) controls = !controls
                        },
                        onDoubleTap = { offset ->
                            if (!locked && !inPip && settings[K.DOUBLE_TAP]) {
                                val w = size.width
                                when {
                                    offset.x < w * 0.4f -> {
                                        seekBy(-10_000)
                                        showHud(Icons.Rounded.Replay10, "-10s", null, -1)
                                    }
                                    offset.x > w * 0.6f -> {
                                        seekBy(10_000)
                                        showHud(Icons.Rounded.Forward10, "+10s", null, 1)
                                    }
                                    else -> if (player.playWhenReady) player.pause() else player.play()
                                }
                            }
                        },
                        onLongPress = {
                            if (!locked && !inPip && settings[K.LONG_PRESS] && player.playWhenReady) {
                                prevSpeed = player.playbackParameters.speed
                                player.setPlaybackSpeed(2f)
                                boost = true
                            }
                        }
                    )
                }
                // drags + pinch (inner -> sees events first)
                .pointerInput(deadTop, deadBottom, deadSide) {
                    val slop = viewConfiguration.touchSlop
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        if (locked || inPip) return@awaitEachGesture
                        val w = size.width.toFloat()
                        val h = size.height.toFloat()
                        val inDead = down.position.y < deadTop ||
                                down.position.y > h - deadBottom ||
                                down.position.x < deadSide ||
                                down.position.x > w - deadSide

                        // 0 = undecided, 1 = seek, 2 = brightness, 3 = volume, 4 = pinch, 5 = ignore
                        var mode = if (inDead) 5 else 0
                        var total = Offset.Zero
                        var startPos = 0L
                        var targetSeek = 0L
                        var startBright = 0f
                        var startVol = 0f
                        val maxVol = audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1)

                        while (true) {
                            val ev = awaitPointerEvent()
                            val pressed = ev.changes.filter { it.pressed }
                            if (pressed.isEmpty()) break
                            if (boost) continue

                            if (pressed.size >= 2) {
                                if (settings[K.ZOOM] && mode != 5) {
                                    mode = 4
                                    val z = ev.calculateZoom()
                                    zoom = (zoom * z).coerceIn(1f, 4f)
                                    showHud(Icons.Rounded.ZoomIn, "${(zoom * 100).roundToInt()}%")
                                    ev.changes.forEach { it.consume() }
                                }
                                continue
                            }
                            if (mode == 4 || mode == 5) continue

                            val c = pressed.first()
                            total += c.positionChange()

                            if (mode == 0 && (abs(total.x) > slop || abs(total.y) > slop)) {
                                if (abs(total.x) > abs(total.y)) {
                                    val d = player.duration
                                    mode = if (settings[K.SEEK] && d != C.TIME_UNSET && d > 0) 1 else 5
                                    startPos = player.currentPosition
                                } else if (down.position.x < w / 2f) {
                                    mode = if (settings[K.BRIGHTNESS]) 2 else 5
                                    startBright = currentBrightness()
                                } else {
                                    mode = if (settings[K.VOLUME]) 3 else 5
                                    startVol = audio.getStreamVolume(AudioManager.STREAM_MUSIC).toFloat() / maxVol
                                }
                            }

                            when (mode) {
                                1 -> {
                                    c.consume()
                                    val d = player.duration
                                    targetSeek = (startPos + (total.x / w * 120_000f).toLong()).coerceIn(0L, max(d, 0L))
                                    val diff = targetSeek - startPos
                                    val sign = if (diff >= 0) "+" else "-"
                                    showHud(
                                        if (diff >= 0) Icons.Rounded.FastForward else Icons.Rounded.FastRewind,
                                        "${fmtTime(targetSeek)}  ($sign${fmtTime(abs(diff))})"
                                    )
                                }
                                2 -> {
                                    c.consume()
                                    val v = (startBright - total.y / h * 1.3f).coerceIn(0.01f, 1f)
                                    setBrightness(v)
                                    showHud(Icons.Rounded.BrightnessMedium, "${(v * 100).roundToInt()}%", v)
                                }
                                3 -> {
                                    c.consume()
                                    val v = (startVol - total.y / h * 1.3f).coerceIn(0f, 1f)
                                    audio.setStreamVolume(AudioManager.STREAM_MUSIC, (v * maxVol).roundToInt(), 0)
                                    showHud(
                                        if (v <= 0f) Icons.Rounded.VolumeOff else Icons.Rounded.VolumeUp,
                                        "${(v * 100).roundToInt()}%", v
                                    )
                                }
                            }
                        }
                        if (mode == 1) player.seekTo(targetSeek)
                    }
                }
        )

        // ---- buffering
        if (pstate == Player.STATE_BUFFERING && error == null && !inPip) {
            CircularProgressIndicator(Modifier.align(Alignment.Center), color = Accent)
        }

        // ---- error
        if (error != null && !inPip) {
            Column(
                Modifier
                    .align(Alignment.Center)
                    .padding(32.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color(0xCC000000))
                    .padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text("Video play nahi ho paya", color = Color.White, fontSize = 16.sp)
                Spacer(Modifier.height(6.dp))
                Text(error ?: "", color = Color.White.copy(alpha = 0.7f), fontSize = 12.sp, maxLines = 6)
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { error = null; attempt[0] = 0; load(0) }) { Text("Retry") }
                    OutlinedButton(onClick = onClose) { Text("Back", color = Color.White) }
                }
            }
        }

        // ---- HUD (brightness / volume / seek)
        if (!inPip) {
            hud?.let { h ->
                Box(
                    Modifier
                        .fillMaxSize()
                        .windowInsetsPadding(WindowInsets.safeDrawing)
                        .padding(horizontal = 32.dp, vertical = 48.dp),
                    contentAlignment = when (h.align) {
                        -1 -> Alignment.CenterStart
                        1 -> Alignment.CenterEnd
                        else -> Alignment.TopCenter
                    }
                ) {
                    Row(
                        Modifier
                            .clip(RoundedCornerShape(24.dp))
                            .background(Color.Black.copy(alpha = 0.65f))
                            .padding(horizontal = 16.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(h.icon, null, tint = Color.White, modifier = Modifier.size(22.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(h.text, color = Color.White, fontSize = 14.sp)
                        if (h.progress != null) {
                            Spacer(Modifier.width(10.dp))
                            LinearProgressIndicator(
                                progress = { h.progress },
                                modifier = Modifier
                                    .width(90.dp)
                                    .height(4.dp),
                                color = Accent,
                                trackColor = Color.White.copy(alpha = 0.3f)
                            )
                        }
                    }
                }
            }
            if (boost) {
                Box(
                    Modifier
                        .fillMaxSize()
                        .windowInsetsPadding(WindowInsets.safeDrawing)
                        .padding(top = 48.dp),
                    contentAlignment = Alignment.TopCenter
                ) {
                    Row(
                        Modifier
                            .clip(RoundedCornerShape(24.dp))
                            .background(Color.Black.copy(alpha = 0.65f))
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Rounded.FastForward, null, tint = Accent, modifier = Modifier.size(22.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("2x", color = Color.White, fontSize = 14.sp)
                    }
                }
            }
        }

        // ---- unlock button
        AnimatedVisibility(
            visible = locked && controls && !inPip,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.CenterStart)
        ) {
            FilledIconButton(
                onClick = { locked = false; controls = true },
                modifier = Modifier
                    .windowInsetsPadding(WindowInsets.safeDrawing)
                    .padding(16.dp)
                    .size(52.dp),
                colors = IconButtonDefaults.filledIconButtonColors(containerColor = Color.Black.copy(alpha = 0.6f))
            ) { Icon(Icons.Rounded.LockOpen, "Unlock", tint = Color.White) }
        }

        // ---- main controls
        AnimatedVisibility(
            visible = controls && !locked && !inPip,
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.40f))
            ) {
                // top bar
                Row(
                    Modifier
                        .align(Alignment.TopStart)
                        .fillMaxWidth()
                        .windowInsetsPadding(WindowInsets.safeDrawing)
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onClose) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back", tint = Color.White)
                    }
                    Text(
                        title, color = Color.White, fontSize = 16.sp, maxLines = 1,
                        overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f)
                    )
                    if (activity.supportsPip) {
                        IconButton(onClick = { activity.enterPip() }) {
                            Icon(Icons.Rounded.PictureInPictureAlt, "PiP", tint = Color.White)
                        }
                    }
                    IconButton(onClick = { panel = "settings" }) {
                        Icon(Icons.Rounded.Settings, "Settings", tint = Color.White)
                    }
                }

                // center buttons
                Row(
                    Modifier.align(Alignment.Center),
                    horizontalArrangement = Arrangement.spacedBy(32.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = { seekBy(-10_000); interaction++ }, modifier = Modifier.size(56.dp)) {
                        Icon(Icons.Rounded.Replay10, "-10s", tint = Color.White, modifier = Modifier.size(38.dp))
                    }
                    FilledIconButton(
                        onClick = {
                            interaction++
                            if (pstate == Player.STATE_ENDED) {
                                player.seekTo(0); player.play()
                            } else if (player.playWhenReady) player.pause() else player.play()
                        },
                        modifier = Modifier.size(72.dp),
                        colors = IconButtonDefaults.filledIconButtonColors(containerColor = Accent)
                    ) {
                        Icon(
                            when {
                                pstate == Player.STATE_ENDED -> Icons.Rounded.Replay
                                pwr -> Icons.Rounded.Pause
                                else -> Icons.Rounded.PlayArrow
                            },
                            "Play/Pause", tint = Color.White, modifier = Modifier.size(44.dp)
                        )
                    }
                    IconButton(onClick = { seekBy(10_000); interaction++ }, modifier = Modifier.size(56.dp)) {
                        Icon(Icons.Rounded.Forward10, "+10s", tint = Color.White, modifier = Modifier.size(38.dp))
                    }
                }

                // bottom: seek bar + tool buttons
                Column(
                    Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .windowInsetsPadding(WindowInsets.safeDrawing)
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(fmtTime(if (seeking) seekPos.toLong() else pos), color = Color.White, fontSize = 12.sp)
                        val maxV = max(dur, 1L).toFloat()
                        Slider(
                            value = (if (seeking) seekPos else pos.toFloat()).coerceIn(0f, maxV),
                            onValueChange = { seeking = true; seekPos = it; interaction++ },
                            onValueChangeFinished = { player.seekTo(seekPos.toLong()); seeking = false },
                            valueRange = 0f..maxV,
                            enabled = dur > 0,
                            colors = SliderDefaults.colors(
                                thumbColor = Accent,
                                activeTrackColor = Accent,
                                inactiveTrackColor = Color.White.copy(alpha = 0.3f),
                                disabledThumbColor = Color.White.copy(alpha = 0.5f),
                                disabledActiveTrackColor = Color.White.copy(alpha = 0.3f),
                                disabledInactiveTrackColor = Color.White.copy(alpha = 0.2f),
                            ),
                            modifier = Modifier
                                .weight(1f)
                                .padding(horizontal = 8.dp)
                        )
                        Text(
                            if (isLive && dur <= 0) "LIVE" else fmtTime(dur),
                            color = Color.White, fontSize = 12.sp
                        )
                    }
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        ToolButton(Icons.Rounded.Lock, "Lock") { locked = true; controls = true }
                        ToolButton(
                            if (muted) Icons.Rounded.VolumeOff else Icons.Rounded.VolumeUp,
                            if (muted) "Unmute" else "Mute"
                        ) {
                            muted = !muted
                            player.volume = if (muted) 0f else 1f
                        }
                        ToolButton(
                            Icons.Rounded.Speed,
                            speed.toString().removeSuffix(".0") + "x"
                        ) { panel = "speed" }
                        ToolButton(Icons.Rounded.HighQuality, "Quality") { panel = "quality" }
                        ToolButton(Icons.Rounded.Audiotrack, "Audio") { panel = "audio" }
                        if (hasTextTracks) ToolButton(Icons.Rounded.ClosedCaption, "Subtitles") { panel = "text" }
                        if (isLive) {
                            val atLive = liveOffset != C.TIME_UNSET && liveOffset < 6000
                            ToolButton(Icons.Rounded.Sensors, "Go Live", active = atLive) {
                                player.seekToDefaultPosition()
                                player.play()
                            }
                        }
                        ToolButton(Icons.Rounded.ScreenRotation, "Rotate") {
                            activity.requestedOrientation =
                                if (config.orientation == Configuration.ORIENTATION_LANDSCAPE)
                                    ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT
                                else ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
                        }
                    }
                }
            }
        }

        // ---- panels
        if (!inPip) {
            when (panel) {
                "speed" -> Panel("Playback speed", { panel = null }) {
                    SPEEDS.forEach { s ->
                        OptionRow(
                            if (s == 1f) "Normal (1x)" else s.toString().removeSuffix(".0") + "x",
                            speed == s
                        ) { player.setPlaybackSpeed(s); panel = null }
                    }
                }
                "quality" -> Panel("Video quality", { panel = null }) {
                    val opts = trackOptions(player, C.TRACK_TYPE_VIDEO)
                    opts.forEach { o -> OptionRow(o.label, o.selected) { o.onSelect(); panel = null } }
                    if (opts.size <= 1) NoteText("Is stream mein sirf ek quality hai.")
                }
                "audio" -> Panel("Audio language", { panel = null }) {
                    val opts = trackOptions(player, C.TRACK_TYPE_AUDIO)
                    opts.forEach { o -> OptionRow(o.label, o.selected) { o.onSelect(); panel = null } }
                    if (!hasAudioTracks) NoteText("Koi audio track nahi mila.")
                    else if (opts.size <= 1) NoteText("Sirf ek audio track available hai.")
                }
                "text" -> Panel("Subtitles", { panel = null }) {
                    val opts = trackOptions(player, C.TRACK_TYPE_TEXT)
                    opts.forEach { o -> OptionRow(o.label, o.selected) { o.onSelect(); panel = null } }
                }
                "settings" -> Panel("Player settings", { panel = null }) {
                    SettingsList(settings, Color.White, Color.White.copy(alpha = 0.6f))
                }
            }
        }
    }
}

// ------------------------------------------------------------------ small composables

@Composable
private fun ToolButton(icon: ImageVector, label: String, active: Boolean = false, onClick: () -> Unit) {
    Column(
        Modifier
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(icon, label, tint = if (active) Accent else Color.White, modifier = Modifier.size(24.dp))
        Text(label, color = if (active) Accent else Color.White, fontSize = 11.sp, maxLines = 1)
    }
}

@Composable
private fun Panel(title: String, onDismiss: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    val maxH = (LocalConfiguration.current.screenHeightDp * 0.6f).dp
    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.55f))
            .pointerInput(Unit) { detectTapGestures { onDismiss() } },
        contentAlignment = Alignment.Center
    ) {
        Surface(
            modifier = Modifier
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(16.dp)
                .fillMaxWidth()
                .widthIn(max = 380.dp)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) { },
            shape = RoundedCornerShape(20.dp),
            color = Color(0xFF1B1D22)
        ) {
            Column(Modifier.padding(16.dp)) {
                Text(title, color = Color.White, fontSize = 18.sp)
                Spacer(Modifier.height(8.dp))
                Column(
                    Modifier
                        .heightIn(max = maxH)
                        .verticalScroll(rememberScrollState()),
                    content = content
                )
            }
        }
    }
}

@Composable
private fun OptionRow(label: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            label,
            color = if (selected) Accent else Color.White,
            fontSize = 15.sp,
            modifier = Modifier.weight(1f)
        )
        if (selected) Icon(Icons.Rounded.Check, null, tint = Accent, modifier = Modifier.size(20.dp))
    }
}

@Composable
private fun NoteText(text: String) {
    Text(text, color = Color.White.copy(alpha = 0.55f), fontSize = 12.sp, modifier = Modifier.padding(8.dp))
}

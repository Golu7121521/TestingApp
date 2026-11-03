@file:OptIn(UnstableApi::class)

package com.netplayer.app

import android.content.Context
import android.media.AudioManager
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.Tracks
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Locale
import kotlin.math.roundToInt

private data class Hud(val text: String, val progress: Float? = null, val sticky: Boolean = false, val id: Long = System.nanoTime())
private data class TrackOpt(val label: String, val group: Tracks.Group?, val index: Int, val selected: Boolean, val off: Boolean = false)

private val SPEEDS = listOf(0.25f, 0.5f, 0.75f, 1f, 1.25f, 1.5f, 1.75f, 2f, 3f)

private fun fmt(ms: Long): String {
    val s = (ms / 1000).coerceAtLeast(0)
    val h = s / 3600; val m = (s % 3600) / 60; val sec = s % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, sec) else "%02d:%02d".format(m, sec)
}

private fun trackLabel(f: Format, type: Int, i: Int): String = when (type) {
    C.TRACK_TYPE_VIDEO -> {
        val q = if (f.height > 0) "${f.height}p" else "Track ${i + 1}"
        val br = if (f.bitrate > 0) " • %.1f Mbps".format(f.bitrate / 1_000_000f) else ""
        q + br
    }
    else -> {
        val lang = f.language?.takeIf { it != "und" }?.let { Locale.forLanguageTag(it).displayLanguage.ifBlank { it } }
        listOfNotNull(
            f.label, lang?.takeIf { it != f.label },
            if (type == C.TRACK_TYPE_AUDIO && f.channelCount > 0) "${f.channelCount}ch" else null
        ).joinToString(" • ").ifBlank { "Track ${i + 1}" }
    }
}

private fun buildOpts(p: ExoPlayer, tracks: Tracks, type: Int): List<TrackOpt> {
    val params = p.trackSelectionParameters
    val hasOverride = params.overrides.values.any { it.type == type }
    val disabled = params.disabledTrackTypes.contains(type)
    val first = when (type) {
        C.TRACK_TYPE_VIDEO -> TrackOpt("Auto", null, -1, !hasOverride)
        C.TRACK_TYPE_TEXT -> TrackOpt("Off", null, -1, disabled, off = true)
        else -> TrackOpt("Default (auto)", null, -1, !hasOverride)
    }
    val rest = mutableListOf<TrackOpt>()
    tracks.groups.filter { it.type == type }.forEach { g ->
        for (i in 0 until g.length) {
            if (!g.isTrackSupported(i)) continue
            rest += TrackOpt(trackLabel(g.getTrackFormat(i), type, i), g, i, hasOverride && !disabled && g.isTrackSelected(i))
        }
    }
    val sortedRest = if (type == C.TRACK_TYPE_VIDEO)
        rest.sortedByDescending { it.group!!.getTrackFormat(it.index).height } else rest
    return listOf(first) + sortedRest
}

private fun applyOpt(p: ExoPlayer, type: Int, o: TrackOpt) {
    val b = p.trackSelectionParameters.buildUpon()
    val g = o.group
    if (o.off) b.setTrackTypeDisabled(type, true)
    else if (g == null) b.setTrackTypeDisabled(type, false).clearOverridesOfType(type)
    else b.setTrackTypeDisabled(type, false).setOverrideForType(TrackSelectionOverride(g.mediaTrackGroup, o.index))
    p.trackSelectionParameters = b.build()
}

@Composable
private fun IconBtn(icon: ImageVector, desc: String, tint: Color = Color.White, onClick: () -> Unit) {
    IconButton(onClick, Modifier.size(42.dp)) { Icon(icon, desc, tint = tint) }
}

@Composable
private fun BigBtn(icon: ImageVector, desc: String, size: Int = 56, onClick: () -> Unit) {
    Box(
        Modifier.size(size.dp).clip(CircleShape).background(Color(0x55000000)).clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) { Icon(icon, desc, tint = Color.White, modifier = Modifier.size((size * 0.58f).dp)) }
}

@Composable
private fun TrackDialog(title: String, opts: List<TrackOpt>, onPick: (TrackOpt) -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss, title = { Text(title) },
        confirmButton = { TextButton(onDismiss) { Text("Close") } },
        text = {
            if (opts.size <= 1 && title != "Subtitles") Text("Koi extra option available nahi hai")
            LazyColumn {
                items(opts) { o ->
                    Row(
                        Modifier.fillMaxWidth().clickable { onPick(o); onDismiss() }.padding(vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(selected = o.selected, onClick = null)
                        Spacer(Modifier.width(12.dp))
                        Text(o.label)
                    }
                }
            }
        }
    )
}

@Composable
fun PlayerScreen(act: PlayerActivity, exo: ExoPlayer, title: String) {
    val ctx = LocalContext.current
    val density = LocalDensity.current
    val prefs = remember { GesturePrefs(ctx) }
    val scope = rememberCoroutineScope()
    val audio = remember { ctx.getSystemService(Context.AUDIO_SERVICE) as AudioManager }
    val inPip by act.inPip

    var controls by remember { mutableStateOf(true) }
    var locked by remember { mutableStateOf(false) }
    var lockHint by remember { mutableStateOf(false) }
    var playing by remember { mutableStateOf(exo.isPlaying) }
    var wantPlay by remember { mutableStateOf(exo.playWhenReady) }
    var pstate by remember { mutableIntStateOf(exo.playbackState) }
    var pos by remember { mutableLongStateOf(0L) }
    var dur by remember { mutableLongStateOf(0L) }
    var live by remember { mutableStateOf(false) }
    var atLive by remember { mutableStateOf(false) }
    var speed by remember { mutableFloatStateOf(1f) }
    var muted by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var dialog by remember { mutableStateOf<String?>(null) }
    var tracks by remember { mutableStateOf(exo.currentTracks) }
    var fill by remember { mutableStateOf(false) }
    var hud by remember { mutableStateOf<Hud?>(null) }
    var scrubbing by remember { mutableStateOf(false) }
    var pinching by remember { mutableStateOf(false) }

    // System bars (status bar swipe) detection: gestures are ignored while bars are showing
    val barsShown = WindowInsets.statusBars.getTop(density) > 0 || WindowInsets.navigationBars.getBottom(density) > 0
    val barsNow by rememberUpdatedState(barsShown)
    var lastBars by remember { mutableLongStateOf(0L) }
    LaunchedEffect(barsShown) { lastBars = System.currentTimeMillis() }

    fun showHud(t: String, p: Float? = null, sticky: Boolean = false) { hud = Hud(t, p, sticky) }
    fun seekBy(ms: Long) {
        val d = if (exo.duration == C.TIME_UNSET) 0L else exo.duration
        val t = exo.currentPosition + ms
        exo.seekTo(if (d > 0) t.coerceIn(0, d) else t.coerceAtLeast(0))
    }

    DisposableEffect(exo) {
        val l = object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) { playing = isPlaying }
            override fun onPlayWhenReadyChanged(p: Boolean, reason: Int) { wantPlay = p }
            override fun onPlaybackStateChanged(s: Int) { pstate = s; if (s == Player.STATE_READY) error = null }
            override fun onTracksChanged(t: Tracks) { tracks = t }
            override fun onPlayerError(e: PlaybackException) {
                if (!act.handleError(e)) error = e.errorCodeName + "\n" + (e.cause?.message ?: e.message ?: "")
            }
        }
        exo.addListener(l)
        onDispose { exo.removeListener(l) }
    }
    LaunchedEffect(exo) {
        while (true) {
            if (!scrubbing) pos = exo.currentPosition
            dur = if (exo.duration == C.TIME_UNSET) 0L else exo.duration
            live = exo.isCurrentMediaItemLive
            val off = exo.currentLiveOffset
            atLive = live && off != C.TIME_UNSET && off < 20_000
            delay(300)
        }
    }
    LaunchedEffect(hud) {
        val h = hud
        if (h != null && !h.sticky) { delay(900); if (hud === h) hud = null }
    }
    LaunchedEffect(controls, playing, dialog, scrubbing) {
        if (controls && playing && dialog == null && !scrubbing) { delay(4000); controls = false }
    }
    LaunchedEffect(lockHint) { if (lockHint) { delay(3000); lockHint = false } }
    BackHandler(locked) { lockHint = true }

    val dtOn = prefs.get("doubleTap")

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        AndroidView(
            factory = { c ->
                PlayerView(c).apply {
                    useController = false
                    this.player = exo
                    resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                    setShutterBackgroundColor(android.graphics.Color.BLACK)
                }
            },
            update = {
                it.resizeMode = if (fill) AspectRatioFrameLayout.RESIZE_MODE_ZOOM else AspectRatioFrameLayout.RESIZE_MODE_FIT
            },
            modifier = Modifier.fillMaxSize()
        )

        // ---------- Gesture layer ----------
        Box(
            Modifier.fillMaxSize()
                // pinch zoom in/out
                .pointerInput(Unit) {
                    awaitEachGesture {
                        awaitFirstDown(requireUnconsumed = false)
                        var scale = 1f
                        var multi = false
                        do {
                            val ev = awaitPointerEvent()
                            if (ev.changes.size >= 2 && !locked && !act.inPip.value && prefs.get("pinch")) {
                                multi = true; pinching = true
                                scale *= ev.calculateZoom()
                                ev.changes.forEach { it.consume() }
                            }
                        } while (ev.changes.any { it.pressed })
                        pinching = false
                        if (multi) {
                            if (scale > 1.05f) { fill = true; showHud("Zoom to fill") }
                            else if (scale < 0.95f) { fill = false; showHud("Fit to screen") }
                        }
                    }
                }
                // tap, double tap, press & hold 2x
                .pointerInput(dtOn) {
                    var holding = false
                    var suppress = false
                    detectTapGestures(
                        onPress = {
                            suppress = false
                            if (!locked && !act.inPip.value && prefs.get("hold2x")) {
                                val job = scope.launch {
                                    delay(500)
                                    holding = true; suppress = true
                                    exo.setPlaybackSpeed(2f)
                                    showHud("2x speed", sticky = true)
                                }
                                tryAwaitRelease()
                                job.cancel()
                                if (holding) { holding = false; exo.setPlaybackSpeed(speed); hud = null }
                            }
                        },
                        onDoubleTap = if (dtOn) { o ->
                            if (!locked && !act.inPip.value) {
                                if (o.x < size.width / 2f) { seekBy(-10_000); showHud("⏪ 10 sec") }
                                else { seekBy(10_000); showHud("10 sec ⏩") }
                            }
                        } else null,
                        onTap = {
                            if (!suppress) { if (locked) lockHint = !lockHint else controls = !controls }
                        }
                    )
                }
                // vertical swipe: left = brightness, right = volume
                .pointerInput(Unit) {
                    var mode = 0
                    var frac = 0f
                    val edgeTop = 44.dp.toPx()
                    val edgeBottom = 36.dp.toPx()
                    val edgeSide = 24.dp.toPx()
                    val maxVol = audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1)
                    detectVerticalDragGestures(
                        onDragStart = { o ->
                            val blocked = locked || act.inPip.value || barsNow ||
                                System.currentTimeMillis() - lastBars < 700 ||
                                o.y < edgeTop || o.y > size.height - edgeBottom ||
                                o.x < edgeSide || o.x > size.width - edgeSide
                            mode = when {
                                blocked -> 0
                                o.x < size.width / 2f -> if (prefs.get("brightness")) 1 else 0
                                else -> if (prefs.get("volume")) 2 else 0
                            }
                            if (mode == 1) frac = act.currentBrightness()
                            if (mode == 2) frac = audio.getStreamVolume(AudioManager.STREAM_MUSIC).toFloat() / maxVol
                        },
                        onDragEnd = { mode = 0 },
                        onDragCancel = { mode = 0 },
                        onVerticalDrag = { change, amount ->
                            if (mode != 0 && !pinching && !barsNow) {
                                change.consume()
                                frac = (frac - amount / size.height * 1.3f).coerceIn(0f, 1f)
                                if (mode == 1) {
                                    act.setBrightness(frac)
                                    showHud("Brightness ${(frac * 100).roundToInt()}%", frac)
                                } else {
                                    audio.setStreamVolume(AudioManager.STREAM_MUSIC, (frac * maxVol).roundToInt(), 0)
                                    showHud("Volume ${(frac * 100).roundToInt()}%", frac)
                                }
                            }
                        }
                    )
                }
        )

        // ---------- HUD ----------
        hud?.let { h ->
            Column(
                Modifier.align(Alignment(0f, -0.55f)).clip(RoundedCornerShape(12.dp))
                    .background(Color(0xB3000000)).padding(horizontal = 18.dp, vertical = 10.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(h.text, color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Medium)
                if (h.progress != null) {
                    Spacer(Modifier.height(6.dp))
                    LinearProgressIndicator(
                        progress = { h.progress }, modifier = Modifier.width(120.dp),
                        color = Blue, trackColor = Color(0x55FFFFFF)
                    )
                }
            }
        }

        // ---------- Buffering ----------
        val buffering = pstate == Player.STATE_BUFFERING && error == null
        if (buffering && (!controls || locked) && !inPip) {
            CircularProgressIndicator(Modifier.align(Alignment.Center), color = Blue)
        }

        // ---------- Controls ----------
        AnimatedVisibility(
            visible = controls && !locked && !inPip, enter = fadeIn(), exit = fadeOut(),
            modifier = Modifier.fillMaxSize()
        ) {
            Box(
                Modifier.fillMaxSize()
                    .background(Brush.verticalGradient(listOf(Color(0xAA000000), Color.Transparent, Color(0xCC000000))))
                    .windowInsetsPadding(WindowInsets.safeDrawing)
            ) {
                Row(
                    Modifier.align(Alignment.TopCenter).fillMaxWidth().padding(horizontal = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconBtn(Icons.AutoMirrored.Filled.ArrowBack, "Back") { act.finish() }
                    Text(
                        title, Modifier.weight(1f).padding(horizontal = 4.dp), color = Color.White,
                        maxLines = 1, overflow = TextOverflow.Ellipsis, fontSize = 15.sp
                    )
                    IconBtn(if (muted) Icons.Filled.VolumeOff else Icons.Filled.VolumeUp, "Mute") {
                        muted = !muted; exo.volume = if (muted) 0f else 1f
                    }
                    IconBtn(Icons.Filled.Speed, "Speed") { dialog = "speed" }
                    IconBtn(Icons.Filled.HighQuality, "Quality") { dialog = "quality" }
                    IconBtn(Icons.Filled.Audiotrack, "Audio") { dialog = "audio" }
                    if (tracks.groups.any { it.type == C.TRACK_TYPE_TEXT }) {
                        IconBtn(Icons.Filled.ClosedCaption, "Subtitles") { dialog = "text" }
                    }
                    IconBtn(Icons.Filled.PictureInPictureAlt, "PiP") { act.enterPip() }
                    IconBtn(Icons.Filled.Settings, "Settings") { dialog = "settings" }
                }

                Row(
                    Modifier.align(Alignment.Center), horizontalArrangement = Arrangement.spacedBy(36.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    BigBtn(Icons.Filled.Replay10, "Back 10s") { seekBy(-10_000) }
                    if (buffering) {
                        Box(Modifier.size(72.dp), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(color = Blue)
                        }
                    } else {
                        BigBtn(if (wantPlay && pstate != Player.STATE_ENDED) Icons.Filled.Pause else Icons.Filled.PlayArrow, "Play/Pause", 72) {
                            if (wantPlay && pstate != Player.STATE_ENDED) exo.pause()
                            else { if (pstate == Player.STATE_ENDED) exo.seekTo(0); exo.play() }
                        }
                    }
                    BigBtn(Icons.Filled.Forward10, "Forward 10s") { seekBy(10_000) }
                }

                Column(Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(horizontal = 12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(fmt(pos), color = Color.White, fontSize = 12.sp)
                        Slider(
                            value = if (dur > 0) (pos.toFloat() / dur).coerceIn(0f, 1f) else 0f,
                            onValueChange = { scrubbing = true; pos = (it * dur).toLong() },
                            onValueChangeFinished = { exo.seekTo(pos); scrubbing = false },
                            enabled = dur > 0,
                            colors = SliderDefaults.colors(
                                thumbColor = Blue, activeTrackColor = Blue, inactiveTrackColor = Color(0x55FFFFFF),
                                disabledThumbColor = Color(0x55FFFFFF), disabledActiveTrackColor = Color(0x55FFFFFF),
                                disabledInactiveTrackColor = Color(0x33FFFFFF)
                            ),
                            modifier = Modifier.weight(1f).padding(horizontal = 8.dp)
                        )
                        Text(if (dur > 0) fmt(dur) else "LIVE", color = Color.White, fontSize = 12.sp)
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconBtn(Icons.Filled.Lock, "Lock") { locked = true; controls = false; lockHint = true }
                        IconBtn(Icons.Filled.ScreenRotation, "Rotate") { act.toggleRotation() }
                        IconBtn(Icons.Filled.AspectRatio, "Aspect") {
                            fill = !fill; showHud(if (fill) "Zoom to fill" else "Fit to screen")
                        }
                        Spacer(Modifier.weight(1f))
                        if (live) {
                            Row(
                                Modifier.clip(RoundedCornerShape(20.dp))
                                    .background(if (atLive) Color(0x33FFFFFF) else Blue)
                                    .clickable { exo.seekToDefaultPosition(); exo.play() }
                                    .padding(horizontal = 14.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(Modifier.size(8.dp).clip(CircleShape).background(if (atLive) Color.Red else Color.White))
                                Spacer(Modifier.width(8.dp))
                                Text(if (atLive) "LIVE" else "GO LIVE", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }

        // ---------- Lock overlay ----------
        if (locked && lockHint && !inPip) {
            Box(
                Modifier.align(Alignment.CenterStart).windowInsetsPadding(WindowInsets.safeDrawing).padding(start = 24.dp)
            ) { BigBtn(Icons.Filled.LockOpen, "Unlock", 60) { locked = false; lockHint = false; controls = true } }
        }

        // ---------- Error ----------
        error?.let { msg ->
            Column(
                Modifier.align(Alignment.Center).padding(24.dp).clip(RoundedCornerShape(16.dp))
                    .background(Color(0xCC000000)).padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text("Playback error", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 17.sp)
                Spacer(Modifier.height(6.dp))
                Text(msg, color = Color(0xFFCCCCCC), fontSize = 12.sp, maxLines = 6, overflow = TextOverflow.Ellipsis)
                Row {
                    TextButton({ error = null; act.retry() }) { Text("Retry") }
                    TextButton({ act.finish() }) { Text("Back") }
                }
            }
        }
    }

    // ---------- Dialogs ----------
    when (dialog) {
        "quality" -> TrackDialog("Video quality", buildOpts(exo, tracks, C.TRACK_TYPE_VIDEO),
            { applyOpt(exo, C.TRACK_TYPE_VIDEO, it) }) { dialog = null }
        "audio" -> TrackDialog("Audio language", buildOpts(exo, tracks, C.TRACK_TYPE_AUDIO),
            { applyOpt(exo, C.TRACK_TYPE_AUDIO, it) }) { dialog = null }
        "text" -> TrackDialog("Subtitles", buildOpts(exo, tracks, C.TRACK_TYPE_TEXT),
            { applyOpt(exo, C.TRACK_TYPE_TEXT, it) }) { dialog = null }
        "speed" -> AlertDialog(
            onDismissRequest = { dialog = null }, title = { Text("Playback speed") },
            confirmButton = { TextButton({ dialog = null }) { Text("Close") } },
            text = {
                LazyColumn {
                    items(SPEEDS) { s ->
                        Row(
                            Modifier.fillMaxWidth().clickable { exo.setPlaybackSpeed(s); speed = s; dialog = null }
                                .padding(vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(selected = speed == s, onClick = null)
                            Spacer(Modifier.width(12.dp))
                            Text(if (s == 1f) "Normal (1x)" else "${s}x")
                        }
                    }
                }
            }
        )
        "settings" -> SettingsDialog(prefs) { dialog = null }
    }
}

@file:OptIn(ExperimentalMaterial3Api::class)

package com.playnova.app.ui

import android.app.Activity
import android.app.PictureInPictureParams
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.ActivityInfo
import android.media.AudioManager
import android.os.Build
import android.util.Rational
import android.view.WindowManager
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.CaptionStyleCompat
import androidx.media3.ui.PlayerView
import com.playnova.app.AppViewModel
import com.playnova.app.data.fmtTime
import com.playnova.app.i18n.tr
import androidx.compose.foundation.clickable
import com.playnova.app.player.PipState
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs

fun Context.findActivity(): Activity? {
    var c = this
    while (c is ContextWrapper) { if (c is Activity) return c; c = c.baseContext }
    return null
}

fun enterPip(a: Activity, w: Int, h: Int) {
    if (Build.VERSION.SDK_INT < 26 || !a.packageManager.hasSystemFeature(android.content.pm.PackageManager.FEATURE_PICTURE_IN_PICTURE)) return
    val ratio = if (w > 0 && h > 0) Rational(w.coerceIn(h * 10 / 24 + 1, h * 24 / 10), h) else Rational(16, 9)
    runCatching { a.enterPictureInPictureMode(PictureInPictureParams.Builder().setAspectRatio(ratio).build()) }
}

@Composable
fun PlayerScreen(vm: AppViewModel, onBack: () -> Unit) {
    val ctx = LocalContext.current
    val activity = ctx.findActivity()
    val holder = vm.holder
    val player = holder.player
    val prefs by vm.settings.state.collectAsState()
    val prefsNow by rememberUpdatedState(prefs)
    val queue by holder.queue.collectAsState()
    val idx by holder.index.collectAsState()
    val playing by holder.playing.collectAsState()
    val error by holder.error.collectAsState()
    val a by holder.abA.collectAsState()
    val b by holder.abB.collectAsState()
    val speed by holder.speed.collectAsState()
    val cur = queue.getOrNull(idx)
    val scope = rememberCoroutineScope()

    val st = remember { PlayerUiState(prefs.aspect, prefs.orientation) }
    var controls by remember { mutableStateOf(true) }
    var locked by remember { mutableStateOf(false) }
    var showSheet by remember { mutableStateOf(false) }
    var sheetPage by remember { mutableStateOf("main") }
    var scrubbing by remember { mutableStateOf(false) }
    var hud by remember { mutableStateOf<String?>(null) }
    var hudTick by remember { mutableIntStateOf(0) }
    var pos by remember { mutableLongStateOf(0L) }
    var dur by remember { mutableLongStateOf(0L) }
    var buf by remember { mutableLongStateOf(0L) }
    var seekPreview by remember { mutableStateOf<Long?>(null) }
    var view by remember { mutableStateOf<PlayerView?>(null) }
    val inPip = PipState.inPip

    fun showHud(t: String) { hud = t; hudTick++ }
    LaunchedEffect(hudTick) { if (hud != null) { delay(900); hud = null } }

    // Poll position
    LaunchedEffect(Unit) {
        while (true) {
            pos = player.currentPosition
            dur = player.duration.takeIf { it != C.TIME_UNSET && it > 0 } ?: (holder.current?.durationMs ?: 0L)
            buf = player.bufferedPosition
            delay(250)
        }
    }
    // Auto-hide controls
    LaunchedEffect(controls, playing, scrubbing, showSheet, hudTick) {
        if (controls && playing && !scrubbing && !showSheet) { delay(3500); controls = false }
    }

    // Immersive full-screen, orientation, brightness restore
    DisposableEffect(Unit) {
        PipState.playerVisible = true
        val w = activity?.window
        val ctrl = w?.let { WindowInsetsControllerCompat(it, it.decorView) }
        ctrl?.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        ctrl?.hide(WindowInsetsCompat.Type.systemBars())
        w?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        onDispose {
            PipState.playerVisible = false
            ctrl?.show(WindowInsetsCompat.Type.systemBars())
            w?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
            w?.let { win -> win.attributes = win.attributes.also { it.screenBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE } }
        }
    }
    LaunchedEffect(st.orient) {
        activity?.requestedOrientation = when (st.orient) {
            1 -> ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
            2 -> ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
            else -> ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        }
    }
    LaunchedEffect(st.audioOnly) {
        player.trackSelectionParameters = player.trackSelectionParameters.buildUpon()
            .setTrackTypeDisabled(C.TRACK_TYPE_VIDEO, st.audioOnly).build()
    }
    // Leave screen when queue closed
    LaunchedEffect(queue.isEmpty()) { if (queue.isEmpty()) onBack() }

    BackHandler {
        when {
            showSheet -> showSheet = false
            locked -> { controls = true; showHud(tr("Screen locked")) }
            else -> onBack()
        }
    }

    // Gesture helpers
    val audio = remember { ctx.getSystemService(Context.AUDIO_SERVICE) as AudioManager }
    fun skip(sign: Int) {
        val s = prefsNow.skipSec * 1000L * sign
        player.seekTo((player.currentPosition + s).coerceIn(0L, if (dur > 0) dur else Long.MAX_VALUE))
        showHud(if (sign > 0) "+${prefsNow.skipSec}s" else "-${prefsNow.skipSec}s")
    }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        AndroidView(
            factory = { c ->
                PlayerView(c).apply {
                    useController = false
                    setShowBuffering(PlayerView.SHOW_BUFFERING_WHEN_PLAYING)
                    this.player = player
                    keepScreenOn = true
                    view = this
                }
            },
            update = { pv ->
                pv.resizeMode = when (st.aspect) {
                    1 -> AspectRatioFrameLayout.RESIZE_MODE_FILL
                    2 -> AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                    3 -> AspectRatioFrameLayout.RESIZE_MODE_FIXED_WIDTH
                    4 -> AspectRatioFrameLayout.RESIZE_MODE_FIXED_HEIGHT
                    else -> AspectRatioFrameLayout.RESIZE_MODE_FIT
                }
                pv.subtitleView?.apply {
                    setApplyEmbeddedStyles(false)
                    setApplyEmbeddedFontSizes(false)
                    setFractionalTextSize(0.0533f * prefs.subSize)
                    setStyle(
                        CaptionStyleCompat(
                            subColorOf(prefs.subColor).toArgb(),
                            android.graphics.Color.argb((prefs.subBg * 255).toInt(), 0, 0, 0),
                            android.graphics.Color.TRANSPARENT,
                            CaptionStyleCompat.EDGE_TYPE_OUTLINE, android.graphics.Color.BLACK, null
                        )
                    )
                }
            },
            modifier = Modifier.fillMaxSize()
        )
        DisposableEffect(Unit) { onDispose { view?.player = null } }

        // Gesture layer
        Box(
            Modifier.fillMaxSize()
                .pointerInput(Unit) {
                    detectTapGestures(
                        onTap = { controls = !controls },
                        onDoubleTap = { o ->
                            if (locked) return@detectTapGestures
                            val p = prefsNow
                            if (!p.gDoubleTap) { holder.toggle(); return@detectTapGestures }
                            when {
                                o.x < size.width / 3f -> skip(-1)
                                o.x > size.width * 2f / 3f -> skip(1)
                                else -> holder.toggle()
                            }
                        },
                        onPress = {
                            if (locked || !prefsNow.gLongPress) return@detectTapGestures
                            var fast = false
                            val job = scope.launch {
                                delay(450)
                                fast = true
                                holder.tempSpeed(prefsNow.longPressSpeed)
                                showHud(tr("%s speed", "${prefsNow.longPressSpeed}x"))
                            }
                            tryAwaitRelease()
                            job.cancel()
                            if (fast) holder.tempSpeed(null)
                        }
                    )
                }
                .pointerInput(Unit) {
                    var mode = 0
                    var acc = androidx.compose.ui.geometry.Offset.Zero
                    var startX = 0f
                    var startPos = 0L
                    var bright = 0.5f
                    var vol = 0f
                    detectDragGestures(
                        onDragStart = { o ->
                            mode = 0; acc = androidx.compose.ui.geometry.Offset.Zero; startX = o.x
                            startPos = player.currentPosition
                            val lp = activity?.window?.attributes
                            bright = if (lp != null && lp.screenBrightness >= 0) lp.screenBrightness else 0.5f
                            vol = audio.getStreamVolume(AudioManager.STREAM_MUSIC).toFloat() / audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
                        },
                        onDragEnd = {
                            if (mode == 3) seekPreview?.let { player.seekTo(it) }
                            seekPreview = null; mode = 0
                        },
                        onDragCancel = { seekPreview = null; mode = 0 },
                        onDrag = { change, d ->
                            if (locked) return@detectDragGestures
                            val p = prefsNow
                            change.consume()
                            acc += d
                            if (mode == 0 && (abs(acc.x) > 28f || abs(acc.y) > 28f)) {
                                mode = if (abs(acc.y) > abs(acc.x)) {
                                    if (startX < size.width / 2f) { if (p.gBrightness) 1 else -1 } else { if (p.gVolume) 2 else -1 }
                                } else { if (p.gSeek && dur > 0) 3 else -1 }
                            }
                            when (mode) {
                                1 -> {
                                    bright = (bright - d.y / size.height * 1.3f * p.sensitivity).coerceIn(0.02f, 1f)
                                    activity?.window?.let { w -> w.attributes = w.attributes.also { it.screenBrightness = bright } }
                                    showHud(tr("Brightness %d%%", (bright * 100).toInt()))
                                }
                                2 -> {
                                    vol = (vol - d.y / size.height * 1.3f * p.sensitivity).coerceIn(0f, 1f)
                                    val max = audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
                                    audio.setStreamVolume(AudioManager.STREAM_MUSIC, (vol * max).toInt(), 0)
                                    showHud(tr("Volume %d%%", (vol * 100).toInt()))
                                }
                                3 -> {
                                    val delta = (acc.x / size.width * 120_000f * p.sensitivity).toLong()
                                    val t = (startPos + delta).coerceIn(0L, dur)
                                    seekPreview = t
                                    showHud("${fmtTime(t)} / ${fmtTime(dur)}  (${if (delta >= 0) "+" else "-"}${fmtTime(abs(delta))})")
                                }
                            }
                        }
                    )
                }
        )

        // HUD
        hud?.let {
            Text(it, Modifier.align(Alignment.Center).clip(RoundedCornerShape(14.dp)).background(Color(0xCC000000)).padding(horizontal = 18.dp, vertical = 10.dp),
                color = Color.White, fontSize = 18.sp)
        }

        // Error / unsupported-format state
        if (error != null) Column(
            Modifier.align(Alignment.Center).padding(24.dp).clip(CardShape).background(Color(0xEE14151B)).padding(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            ErrorState(error ?: "", onRetry = { holder.retry() })
            Row {
                if (player.hasNextMediaItem()) TextButton(onClick = { holder.retry(); player.seekToNextMediaItem() }) { Text(tr("Next video")) }
                TextButton(onClick = onBack) { Text(tr("Back")) }
            }
        }

        // Lock indicator
        if (locked && controls && !inPip) IconButton(
            onClick = { locked = false; showHud(tr("Unlocked")) },
            Modifier.align(Alignment.CenterStart).padding(16.dp).size(56.dp).clip(CircleShape).background(Color(0x99000000))
        ) { Icon(Icons.Outlined.Lock, tr("Unlock screen"), tint = Color.White) }

        // Controls overlay (YouTube style: top bar, three center buttons, time + seek bar at the bottom)
        AnimatedVisibility(visible = controls && !locked && !inPip && error == null, enter = fadeIn(), exit = fadeOut()) {
            Box(Modifier.fillMaxSize().displayCutoutPadding()) {
                Box(Modifier.fillMaxWidth().height(88.dp).background(Brush.verticalGradient(listOf(Color(0xB3000000), Color.Transparent))))
                Box(Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(110.dp).background(Brush.verticalGradient(listOf(Color.Transparent, Color(0xB3000000)))))

                // Top bar
                Row(Modifier.align(Alignment.TopStart).fillMaxWidth().padding(horizontal = 4.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onBack) { Icon(Icons.Outlined.ArrowBack, tr("Back to library"), tint = Color.White) }
                    Text(cur?.title ?: "", Modifier.weight(1f), color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis, fontSize = 15.sp)
                    IconButton(onClick = { sheetPage = "subs"; showSheet = true }) { Icon(Icons.Outlined.Subtitles, tr("Subtitles"), tint = Color.White) }
                    if (Build.VERSION.SDK_INT >= 26) IconButton(onClick = {
                        activity?.let { enterPip(it, view?.player?.videoSize?.width ?: 0, view?.player?.videoSize?.height ?: 0) }
                    }) { Icon(Icons.Outlined.PictureInPictureAlt, tr("Picture in picture"), tint = Color.White) }
                    IconButton(onClick = { sheetPage = "main"; showSheet = true }) { Icon(Icons.Outlined.Settings, tr("Playback settings"), tint = Color.White) }
                }

                // Center controls
                Row(Modifier.align(Alignment.Center), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(40.dp)) {
                    if (queue.size > 1) RoundButton(Icons.Outlined.SkipPrevious, tr("Previous video"), 48.dp, 30.dp) { player.seekToPreviousMediaItem() }
                    RoundButton(if (playing) Icons.Outlined.Pause else Icons.Outlined.PlayArrow, if (playing) tr("Pause") else tr("Play"), 64.dp, 40.dp) { holder.toggle() }
                    if (queue.size > 1) RoundButton(Icons.Outlined.SkipNext, tr("Next video"), 48.dp, 30.dp) { player.seekToNextMediaItem() }
                }

                // Bottom: time row + seek bar
                Column(Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(horizontal = 12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("${fmtTime(seekPreview ?: pos)} / ${fmtTime(dur)}", color = Color.White, fontSize = 12.sp, modifier = Modifier.weight(1f))
                        if (speed != 1f) Text("${speed}x", color = Color.White, fontSize = 12.sp,
                            modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable { sheetPage = "main"; showSheet = true }.padding(horizontal = 8.dp, vertical = 6.dp))
                        IconButton(onClick = { locked = true; controls = false; showHud(tr("Screen locked")) }, Modifier.size(40.dp)) { Icon(Icons.Outlined.LockOpen, tr("Lock screen controls"), tint = Color.White) }
                        IconButton(onClick = { st.orient = if (st.orient == 1) 2 else 1 }, Modifier.size(40.dp)) { Icon(Icons.Outlined.ScreenRotation, tr("Rotate screen"), tint = Color.White) }
                    }
                    SeekBar(seekPreview ?: pos, dur, buf, a, b,
                        onScrub = { f -> scrubbing = true; seekPreview = (f * dur).toLong() },
                        onDone = { f -> player.seekTo((f * dur).toLong()); seekPreview = null; scrubbing = false })
                }
            }
        }
    }
    if (showSheet) PlayerSheet(vm, st, sheetPage) { showSheet = false }
}

@Composable
private fun RoundButton(icon: androidx.compose.ui.graphics.vector.ImageVector, desc: String, size: androidx.compose.ui.unit.Dp, iconSize: androidx.compose.ui.unit.Dp, onClick: () -> Unit) {
    IconButton(onClick = onClick, Modifier.size(size).clip(CircleShape).background(Color(0x66000000))) {
        Icon(icon, desc, tint = Color.White, modifier = Modifier.size(iconSize))
    }
}

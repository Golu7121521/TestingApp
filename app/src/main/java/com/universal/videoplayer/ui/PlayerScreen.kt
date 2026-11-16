package com.universal.videoplayer.ui

import android.content.Context
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.media.AudioManager
import android.os.Build
import android.view.WindowManager
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Audiotrack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Forward10
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.FullscreenExit
import androidx.compose.material.icons.filled.HighQuality
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PictureInPictureAlt
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay10
import androidx.compose.material.icons.filled.ScreenRotation
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Subtitles
import androidx.compose.material.icons.filled.VolumeOff
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.universal.videoplayer.data.AppSettings
import com.universal.videoplayer.data.Scaling
import com.universal.videoplayer.player.PlayerViewModel
import com.universal.videoplayer.player.TrackOpt
import com.universal.videoplayer.utils.findActivity
import com.universal.videoplayer.utils.fmtTime
import kotlinx.coroutines.delay

private enum class Panel { SPEED, QUALITY, AUDIO, SUBTITLES, SCALING, MORE }
private class PItem(val label: String, val selected: Boolean, val onClick: () -> Unit)

private val speeds = listOf(0.25f, 0.5f, 0.75f, 1f, 1.25f, 1.5f, 1.75f, 2f, 2.5f, 3f)

@Composable
fun PlayerScreen(vm: PlayerViewModel, settings: AppSettings, onPip: () -> Unit) {
    val ui by vm.ui.collectAsStateWithLifecycle()
    val ctx = LocalContext.current
    val activity = remember { ctx.findActivity() }
    var controls by remember { mutableStateOf(true) }
    var locked by remember { mutableStateOf(false) }
    var panel by remember { mutableStateOf<Panel?>(null) }
    var hud by remember { mutableStateOf<String?>(null) }
    var hudTick by remember { mutableIntStateOf(0) }
    var immersive by remember { mutableStateOf(true) }
    var manualRotation by remember { mutableStateOf(false) }
    var interact by remember { mutableIntStateOf(0) }
    val inPip = vm.inPip

    fun showHud(t: String) { hud = t; hudTick++ }
    fun touch() { interact++ }

    BackHandler { if (panel != null) panel = null else if (!locked) vm.close() }

    // System bars: edge-to-edge immersive while playing.
    DisposableEffect(immersive) {
        val w = activity?.window
        if (w != null) {
            val c = WindowCompat.getInsetsController(w, w.decorView)
            c.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            if (immersive) c.hide(WindowInsetsCompat.Type.systemBars()) else c.show(WindowInsetsCompat.Type.systemBars())
        }
        onDispose { }
    }
    DisposableEffect(Unit) {
        onDispose {
            activity?.let { a ->
                WindowCompat.getInsetsController(a.window, a.window.decorView).show(WindowInsetsCompat.Type.systemBars())
                a.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
                a.window.attributes = a.window.attributes.also { it.screenBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE }
            }
        }
    }

    // Preserve the video's own orientation: portrait clips stay portrait, landscape clips go landscape.
    LaunchedEffect(ui.videoWidth, ui.videoHeight) {
        if (!manualRotation && ui.videoWidth > 0 && ui.videoHeight > 0) {
            activity?.requestedOrientation =
                if (ui.videoHeight > ui.videoWidth) ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT
                else ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        }
    }

    LaunchedEffect(controls, ui.playing, interact, settings.autoHide, panel) {
        if (controls && ui.playing && settings.autoHide && panel == null) { delay(4000); controls = false }
    }
    LaunchedEffect(hudTick) { if (hud != null) { delay(900); hud = null } }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        AndroidView(
            factory = { c -> PlayerView(c).apply { useController = false; keepScreenOn = true; setShutterBackgroundColor(android.graphics.Color.BLACK) } },
            update = {
                it.player = vm.player
                it.resizeMode = when (ui.scaling) {
                    Scaling.FIT -> AspectRatioFrameLayout.RESIZE_MODE_FIT
                    Scaling.FILL -> AspectRatioFrameLayout.RESIZE_MODE_FILL
                    Scaling.ZOOM -> AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                    Scaling.ORIGINAL -> AspectRatioFrameLayout.RESIZE_MODE_FIXED_WIDTH
                }
            },
            onRelease = { it.player = null },
            modifier = Modifier.fillMaxSize(),
        )

        if (!inPip) {
            // Gesture layer: tap, double-tap seek, hold for 2x.
            var boosted by remember { mutableStateOf(false) }
            Box(
                Modifier.fillMaxSize()
                    .pointerInput(locked) {
                        detectTapGestures(
                            onTap = { controls = !controls; touch() },
                            onDoubleTap = if (locked) null else { off ->
                                if (off.x < size.width / 2) { vm.seekBy(-10_000); showHud("-10s") }
                                else { vm.seekBy(10_000); showHud("+10s") }
                            },
                            onLongPress = if (locked) null else { _ -> boosted = true; vm.boost(true); showHud("2x speed") },
                            onPress = { tryAwaitRelease(); if (boosted) { boosted = false; vm.boost(false) } },
                        )
                    }
                    .pointerInput(locked) {
                        if (locked) return@pointerInput
                        val am = ctx.getSystemService(Context.AUDIO_SERVICE) as AudioManager
                        var left = true
                        var volAcc = 0f
                        detectVerticalDragGestures(
                            onDragStart = { left = it.x < size.width / 2; volAcc = am.getStreamVolume(AudioManager.STREAM_MUSIC).toFloat() },
                            onVerticalDrag = { _, dy ->
                                val frac = dy / size.height
                                if (left) {
                                    val w = activity?.window ?: return@detectVerticalDragGestures
                                    val a = w.attributes
                                    val cur = if (a.screenBrightness < 0) 0.5f else a.screenBrightness
                                    a.screenBrightness = (cur - frac * 1.2f).coerceIn(0.02f, 1f)
                                    w.attributes = a
                                    showHud("Brightness ${(a.screenBrightness * 100).toInt()}%")
                                } else {
                                    val max = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
                                    volAcc = (volAcc - frac * max * 1.2f).coerceIn(0f, max.toFloat())
                                    am.setStreamVolume(AudioManager.STREAM_MUSIC, volAcc.toInt(), 0)
                                    showHud("Volume ${(volAcc / max * 100).toInt()}%")
                                }
                            },
                        )
                    }
            )

            if (ui.loading || ui.buffering) {
                CircularProgressIndicator(Modifier.align(Alignment.Center), color = Color.White)
            }

            hud?.let {
                Text(
                    it, color = Color.White,
                    modifier = Modifier.align(Alignment.TopCenter).safeDrawingPadding().padding(top = 56.dp)
                        .clip(RoundedCornerShape(20.dp)).background(Color(0x99000000)).padding(horizontal = 16.dp, vertical = 8.dp),
                )
            }

            AnimatedVisibility(controls && !locked && ui.error == null, enter = fadeIn(), exit = fadeOut()) {
                ControlsOverlay(
                    vm, ui, onPip, onPanel = { panel = it; touch() },
                    onLock = { locked = true; controls = false; showHud("Screen locked") },
                    immersive = immersive, onFullscreen = { immersive = !immersive },
                    onRotate = {
                        manualRotation = true
                        val a = activity ?: return@ControlsOverlay
                        val land = ctx.resources.configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE
                        a.requestedOrientation =
                            if (land) ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT else ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
                    },
                    onBack = { vm.close() },
                    touch = ::touch,
                )
            }

            if (locked && controls) {
                IconButton(
                    { locked = false; showHud("Screen unlocked") },
                    Modifier.align(Alignment.CenterStart).safeDrawingPadding().padding(16.dp)
                        .clip(CircleShape).background(Color(0x99000000)),
                ) { Icon(Icons.Filled.Lock, "Unlock screen", tint = Color.White) }
            }

            ui.error?.let { msg ->
                Column(
                    Modifier.align(Alignment.Center).safeDrawingPadding().padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text("Can't play this video", color = Color.White, style = MaterialTheme.typography.titleMedium)
                    Text(msg, color = Color(0xFFDDDDDD), textAlign = TextAlign.Center)
                    if (settings.errorDetails) ui.errorDetail?.let { Text(it, color = Color(0xFF999999), style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.Center) }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button({ vm.retry() }) { Text("Retry") }
                        OutlinedButton({ vm.close() }) { Text("Close") }
                    }
                }
            }

            panel?.let { p ->
                val (title, list) = panelItems(p, vm, ui, immersive, { immersive = !immersive }, { panel = it })
                Box(Modifier.fillMaxSize().background(Color(0x66000000)).clickable(indication = null, interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }) { panel = null })
                Surface(
                    Modifier.align(Alignment.CenterEnd).safeDrawingPadding().padding(12.dp).widthIn(max = 320.dp).fillMaxHeight(0.8f),
                    shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.surface,
                ) {
                    Column(Modifier.padding(12.dp)) {
                        Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(8.dp))
                        if (list.isEmpty()) Text("Nothing available for this video.", Modifier.padding(8.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
                        LazyColumn {
                            items(list) { it0 ->
                                Row(
                                    Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable {
                                        it0.onClick()
                                        if (p != Panel.MORE) panel = null
                                    }.padding(horizontal = 12.dp, vertical = 14.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Text(it0.label, Modifier.weight(1f))
                                    if (it0.selected) Icon(Icons.Filled.Check, null, tint = MaterialTheme.colorScheme.primary)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun panelItems(
    p: Panel, vm: PlayerViewModel, ui: com.universal.videoplayer.player.PlayerUi,
    immersive: Boolean, toggleImmersive: () -> Unit, open: (Panel?) -> Unit,
): Pair<String, List<PItem>> = when (p) {
    Panel.SPEED -> "Playback speed" to speeds.map { PItem(speedLabel(it), ui.speed == it) { vm.setSpeed(it) } }
    Panel.QUALITY -> "Quality" to (if (ui.qualities.isEmpty()) emptyList() else
        listOf(PItem("Auto", ui.qualityAuto) { vm.selectQuality(null) }) +
            ui.qualities.map { o: TrackOpt -> PItem(o.label, !ui.qualityAuto && o.selected) { vm.selectQuality(o) } })
    Panel.AUDIO -> "Audio track" to ui.audios.map { o -> PItem(o.label, o.selected) { vm.selectAudio(o) } }
    Panel.SUBTITLES -> "Subtitles" to (if (ui.texts.isEmpty()) emptyList() else
        listOf(PItem("Off", ui.texts.none { it.selected }) { vm.selectText(null) }) +
            ui.texts.map { o -> PItem(o.label, o.selected) { vm.selectText(o) } })
    Panel.SCALING -> "Scaling mode" to Scaling.values().map { s -> PItem(s.label, ui.scaling == s) { vm.setScaling(s) } }
    Panel.MORE -> "More" to listOf(
        PItem("Scaling mode: ${ui.scaling.label}", false) { open(Panel.SCALING) },
        PItem("Hide system bars", immersive) { toggleImmersive() },
        PItem("Playback speed: ${speedLabel(ui.speed)}", false) { open(Panel.SPEED) },
    )
}

@Composable
private fun CtrlIcon(icon: ImageVector, desc: String, enabled: Boolean = true, size: Int = 24, onClick: () -> Unit) {
    IconButton(onClick, enabled = enabled) {
        Icon(icon, desc, Modifier.size(size.dp), tint = if (enabled) Color.White else Color(0x66FFFFFF))
    }
}

@Composable
private fun ControlsOverlay(
    vm: PlayerViewModel, ui: com.universal.videoplayer.player.PlayerUi, onPip: () -> Unit,
    onPanel: (Panel) -> Unit, onLock: () -> Unit, immersive: Boolean, onFullscreen: () -> Unit,
    onRotate: () -> Unit, onBack: () -> Unit, touch: () -> Unit,
) {
    val ctx = LocalContext.current
    val pipOk = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
        ctx.packageManager.hasSystemFeature(PackageManager.FEATURE_PICTURE_IN_PICTURE)
    Box(
        Modifier.fillMaxSize().background(
            Brush.verticalGradient(listOf(Color(0xAA000000), Color.Transparent, Color.Transparent, Color(0xCC000000)))
        ).safeDrawingPadding()
    ) {
        Row(Modifier.fillMaxWidth().align(Alignment.TopStart), verticalAlignment = Alignment.CenterVertically) {
            CtrlIcon(Icons.Filled.ArrowBack, "Close player") { onBack() }
            Text(ui.title, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
            CtrlIcon(Icons.Filled.PictureInPictureAlt, "Picture in picture", enabled = pipOk) { onPip() }
            CtrlIcon(Icons.Filled.ScreenRotation, "Rotate screen") { onRotate() }
            CtrlIcon(if (immersive) Icons.Filled.FullscreenExit else Icons.Filled.Fullscreen, "Toggle full screen") { onFullscreen() }
            CtrlIcon(Icons.Filled.MoreVert, "More settings") { onPanel(Panel.MORE) }
        }

        Row(Modifier.align(Alignment.Center), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            if (ui.queueSize > 1) CtrlIcon(Icons.Filled.SkipPrevious, "Previous video", ui.queueIndex > 0, 32) { vm.skipPrev() }
            CtrlIcon(Icons.Filled.Replay10, "Back 10 seconds", size = 32) { vm.seekBy(-10_000); touch() }
            IconButton({ vm.togglePlay(); touch() }, Modifier.size(72.dp)) {
                Icon(if (ui.playing) Icons.Filled.Pause else Icons.Filled.PlayArrow, if (ui.playing) "Pause" else "Play", Modifier.size(52.dp), tint = Color.White)
            }
            CtrlIcon(Icons.Filled.Forward10, "Forward 10 seconds", size = 32) { vm.seekBy(10_000); touch() }
            if (ui.queueSize > 1) CtrlIcon(Icons.Filled.SkipNext, "Next video", ui.queueIndex < ui.queueSize - 1, 32) { vm.skipNext() }
        }

        Column(Modifier.fillMaxWidth().align(Alignment.BottomStart).padding(horizontal = 12.dp)) {
            if (ui.durationMs > 0) {
                ThinSeekBar(ui.positionMs, ui.bufferedMs, ui.durationMs, onSeek = { vm.seekTo(it); touch() })
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(fmtTime(ui.positionMs), color = Color.White, style = MaterialTheme.typography.labelMedium)
                Text(if (ui.isLive) "LIVE" else fmtTime(ui.durationMs), color = Color.White, style = MaterialTheme.typography.labelMedium)
            }
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), verticalAlignment = Alignment.CenterVertically) {
                CtrlIcon(if (ui.muted) Icons.Filled.VolumeOff else Icons.Filled.VolumeUp, if (ui.muted) "Unmute" else "Mute") { vm.toggleMute() }
                CtrlIcon(Icons.Filled.Speed, "Playback speed") { onPanel(Panel.SPEED) }
                Text(speedLabel(ui.speed), color = Color.White, style = MaterialTheme.typography.labelMedium)
                CtrlIcon(Icons.Filled.HighQuality, "Video quality", ui.qualities.isNotEmpty()) { onPanel(Panel.QUALITY) }
                CtrlIcon(Icons.Filled.Audiotrack, "Audio track", ui.audios.isNotEmpty()) { onPanel(Panel.AUDIO) }
                CtrlIcon(Icons.Filled.Subtitles, "Subtitles", ui.texts.isNotEmpty()) { onPanel(Panel.SUBTITLES) }
                CtrlIcon(Icons.Filled.LockOpen, "Lock screen") { onLock() }
                if (ui.isLive) TextButton({ vm.goLive() }) { Text("GO LIVE", color = Color.White) }
            }
        }
    }
}

@Composable
private fun ThinSeekBar(pos: Long, buffered: Long, dur: Long, onSeek: (Long) -> Unit) {
    var drag by remember { mutableStateOf<Float?>(null) }
    val frac = drag ?: if (dur > 0) (pos.toFloat() / dur).coerceIn(0f, 1f) else 0f
    val bufFrac = if (dur > 0) (buffered.toFloat() / dur).coerceIn(0f, 1f) else 0f
    Box(
        Modifier.fillMaxWidth().height(28.dp)
            .pointerInput(dur) { detectTapGestures { off -> onSeek((off.x / size.width * dur).toLong()) } }
            .pointerInput(dur) {
                detectHorizontalDragGestures(
                    onDragStart = { drag = (it.x / size.width).coerceIn(0f, 1f) },
                    onDragEnd = { drag?.let { f -> onSeek((f * dur).toLong()) }; drag = null },
                    onDragCancel = { drag = null },
                    onHorizontalDrag = { c, _ -> drag = (c.position.x / size.width).coerceIn(0f, 1f) },
                )
            }
    ) {
        val accent = MaterialTheme.colorScheme.primary
        Canvas(Modifier.fillMaxSize()) {
            val cy = size.height / 2
            val sw = 3.dp.toPx()
            drawLine(Color(0x55FFFFFF), Offset(0f, cy), Offset(size.width, cy), sw)
            drawLine(Color(0x88FFFFFF), Offset(0f, cy), Offset(size.width * bufFrac, cy), sw)
            drawLine(accent, Offset(0f, cy), Offset(size.width * frac, cy), sw)
            drawCircle(accent, 6.dp.toPx(), Offset(size.width * frac, cy))
        }
    }
}

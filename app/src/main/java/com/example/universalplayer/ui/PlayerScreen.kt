@file:OptIn(UnstableApi::class, androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.example.universalplayer.ui

import android.content.pm.ActivityInfo
import android.os.Build
import android.view.WindowManager
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.focusable
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import com.example.universalplayer.data.EpgStore
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.example.universalplayer.findActivity
import com.example.universalplayer.formatTime
import com.example.universalplayer.player.LoadState
import com.example.universalplayer.player.PlayerViewModel
import kotlinx.coroutines.delay

private enum class MenuType { None, Speed, Quality, Audio, Subs }

private val speeds = listOf(0.25f, 0.5f, 0.75f, 1f, 1.25f, 1.5f, 1.75f, 2f)
private fun speedLabel(s: Float) = if (s == s.toInt().toFloat()) "${s.toInt()}x" else "${s}x"

@Composable
fun PlayerScreen(vm: PlayerViewModel, inPip: Boolean, onBack: () -> Unit, onEnterPip: () -> Unit) {
    val ui by vm.ui.collectAsState()
    val load by vm.load.collectAsState()
    val title by vm.title.collectAsState()
    val qualities by vm.qualities.collectAsState()
    val audios by vm.audios.collectAsState()
    val portrait by vm.portrait.collectAsState()
    val subs by vm.subs.collectAsState()
    val resumedAt by vm.resumedAt.collectAsState()
    val reconnect by vm.reconnect.collectAsState()
    val castName by vm.castName.collectAsState()
    val castNote by vm.castNote.collectAsState()
    val epgId by vm.epgId.collectAsState()
    val epgData by EpgStore.data.collectAsState()
    var showCast by remember { mutableStateOf(false) }
    var showDetails by remember { mutableStateOf(false) }
    val clipboard = LocalClipboardManager.current
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }

    Immersive(portrait)

    var locked by remember { mutableStateOf(false) }
    var controls by remember { mutableStateOf(true) }
    var touch by remember { mutableIntStateOf(0) }
    var menu by remember { mutableStateOf(MenuType.None) }
    var resize by remember { mutableIntStateOf(AspectRatioFrameLayout.RESIZE_MODE_FIT) }
    var boosting by remember { mutableStateOf(false) }
    var flashSide by remember { mutableIntStateOf(0) }
    var flashTotal by remember { mutableIntStateOf(0) }
    var flashKey by remember { mutableIntStateOf(0) }
    var scrub by remember { mutableStateOf<Float?>(null) }
    var showResume by remember { mutableStateOf(false) }
    var lastMenu by remember { mutableStateOf(MenuType.Speed) }
    if (menu != MenuType.None) lastMenu = menu
    val haptic = LocalHapticFeedback.current
    val view = LocalView.current

    BackHandler {
        when {
            menu != MenuType.None -> menu = MenuType.None
            locked -> { controls = true; touch++ }
            else -> onBack()
        }
    }
    // keep the status / navigation bars hidden (e.g. after a menu choice or a quality change)
    LaunchedEffect(menu, load, portrait, qualities) {
        view.context.findActivity()?.window?.let {
            WindowCompat.getInsetsController(it, view).hide(WindowInsetsCompat.Type.systemBars())
        }
    }
    LaunchedEffect(resumedAt) {
        if (resumedAt > 0) { showResume = true; delay(4500); showResume = false } else showResume = false
    }
    LaunchedEffect(flashKey) {
        if (flashKey > 0) { delay(700); flashSide = 0; flashTotal = 0 }
    }
    LaunchedEffect(controls, touch, ui.isPlaying, menu, scrub) {
        if (controls && ui.isPlaying && menu == MenuType.None && scrub == null) {
            delay(3500); controls = false
        }
    }

    Box(
        Modifier.fillMaxSize().background(Color.Black)
            .focusRequester(focus).focusable()
            .onPreviewKeyEvent { ev ->
                if (menu != MenuType.None || locked || ev.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                when (ev.key) {
                    Key.DirectionCenter, Key.Enter, Key.NumPadEnter, Key.Spacebar, Key.MediaPlayPause -> {
                        vm.togglePlay(); controls = true; touch++; true
                    }
                    Key.DirectionLeft, Key.MediaRewind -> { vm.seekBy(-10_000L); controls = true; touch++; true }
                    Key.DirectionRight, Key.MediaFastForward -> { vm.seekBy(10_000L); controls = true; touch++; true }
                    Key.DirectionUp -> { controls = true; touch++; true }
                    Key.DirectionDown -> { menu = if (qualities.isNotEmpty()) MenuType.Quality else MenuType.Speed; true }
                    Key.ChannelUp, Key.MediaNext -> { vm.skipNext(); true }
                    Key.ChannelDown, Key.MediaPrevious -> { vm.skipPrev(); true }
                    else -> false
                }
            }
    ) {
        AndroidView(
            factory = { c ->
                PlayerView(c).apply {
                    useController = false
                    setShutterBackgroundColor(android.graphics.Color.BLACK)
                    player = vm.player
                    subtitleView?.setBottomPaddingFraction(0.12f)
                }
            },
            update = { it.resizeMode = resize },
            modifier = Modifier.fillMaxSize()
        )

        if (!inPip) {
            // ---- gesture layer: tap, double-tap seek, press & hold 2x
            Box(
                Modifier.fillMaxSize().pointerInput(locked) {
                    detectTapGestures(
                        onTap = { controls = !controls; touch++; menu = MenuType.None },
                        onDoubleTap = { off ->
                            if (!locked && load is LoadState.Ready) {
                                val side = if (off.x < size.width / 2) -1 else 1
                                vm.seekBy(side * 10_000L)
                                flashTotal = if (flashSide == side) flashTotal + 10 else 10
                                flashSide = side
                                flashKey++
                            }
                        },
                        onLongPress = {
                            if (!locked && load is LoadState.Ready) {
                                boosting = true; vm.boost(true)
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            }
                        },
                        onPress = {
                            tryAwaitRelease()
                            if (boosting) { boosting = false; vm.boost(false) }
                        }
                    )
                }
            )

            // ---- double-tap feedback
            if (flashSide != 0) {
                Box(
                    Modifier.align(if (flashSide < 0) Alignment.CenterStart else Alignment.CenterEnd)
                        .padding(horizontal = 72.dp)
                ) {
                    Text(
                        if (flashSide < 0) "« ${flashTotal}s" else "${flashTotal}s »",
                        color = Color.White, fontWeight = FontWeight.Bold, fontSize = 20.sp,
                        modifier = Modifier.background(Color.Black.copy(alpha = 0.45f), CircleShape).padding(18.dp)
                    )
                }
            }

            // ---- 2x indicator
            if (boosting) {
                Text(
                    "2x  ▶▶", color = Color.White, fontWeight = FontWeight.Bold,
                    modifier = Modifier.align(Alignment.TopCenter)
                        .windowInsetsPadding(WindowInsets.safeDrawing).padding(top = 12.dp)
                        .background(Color.Black.copy(alpha = 0.55f), RoundedCornerShape(50))
                        .padding(horizontal = 14.dp, vertical = 6.dp)
                )
            }

            // ---- "Resumed from" pill
            AnimatedVisibility(
                visible = showResume && !locked && load is LoadState.Ready,
                enter = slideInVertically { it } + fadeIn(), exit = fadeOut(),
                modifier = Modifier.align(Alignment.BottomStart)
                    .windowInsetsPadding(WindowInsets.safeDrawing).padding(start = 16.dp, bottom = 96.dp)
            ) {
                Row(
                    Modifier.background(Color.Black.copy(alpha = 0.75f), RoundedCornerShape(50))
                        .padding(start = 16.dp, end = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Resumed from ${formatTime(resumedAt)}", color = Color.White, fontSize = 13.sp)
                    TextButton(onClick = { vm.startOver(); showResume = false }) { Text("Start over") }
                }
            }

            // ---- loading / error
            when (val l = load) {
                is LoadState.Loading -> {
                    CircularProgressIndicator(Modifier.align(Alignment.Center), color = Color.White)
                    BackOnly(onBack)
                }
                is LoadState.Error -> {
                    Column(
                        Modifier.align(Alignment.Center).padding(32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Icon(Icons.Rounded.ErrorOutline, null, tint = Color.White, modifier = Modifier.size(40.dp))
                        Text(l.message, color = Color.White)
                        if (l.details.isNotEmpty()) {
                            TextButton(onClick = { showDetails = !showDetails }) {
                                Text(if (showDetails) "Hide details" else "Details", color = Color.White)
                            }
                            if (showDetails) {
                                Text(
                                    l.details, color = Color.White.copy(alpha = 0.85f), fontSize = 12.sp,
                                    modifier = Modifier.widthIn(max = 460.dp).heightIn(max = 150.dp)
                                        .background(Color.White.copy(alpha = 0.08f), RoundedCornerShape(8.dp))
                                        .padding(10.dp).verticalScroll(rememberScrollState())
                                )
                                TextButton(onClick = { clipboard.setText(AnnotatedString(l.details)) }) {
                                    Text("Copy details", color = Color.White)
                                }
                            }
                        }
                        Button(onClick = { vm.retry() }) { Text("Retry") }
                    }
                    BackOnly(onBack)
                }
                else -> if (ui.buffering) {
                    CircularProgressIndicator(Modifier.align(Alignment.Center), color = Color.White)
                }
            }

            // ---- auto-reconnect status
            reconnect?.let {
                Text(
                    it, color = Color.White, fontSize = 13.sp,
                    modifier = Modifier.align(Alignment.TopCenter)
                        .windowInsetsPadding(WindowInsets.safeDrawing).padding(top = 12.dp)
                        .background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(50))
                        .padding(horizontal = 14.dp, vertical = 6.dp)
                )
            }

            // ---- Chromecast banner
            castName?.let { name ->
                Column(
                    Modifier.align(Alignment.Center).background(Color.Black.copy(alpha = 0.8f), RoundedCornerShape(16.dp)).padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(Icons.Rounded.CastConnected, null, tint = Color.White, modifier = Modifier.size(36.dp))
                    Text("Casting to $name", color = Color.White)
                    Row {
                        TextButton(onClick = { vm.castTogglePlay() }) { Text("Play / Pause", color = Color.White) }
                        TextButton(onClick = { vm.castStop() }) { Text("Stop casting", color = Color.White) }
                    }
                }
            }
            castNote?.let {
                Text(
                    it, color = Color.White, fontSize = 13.sp,
                    modifier = Modifier.align(Alignment.BottomCenter).windowInsetsPadding(WindowInsets.safeDrawing)
                        .padding(bottom = 90.dp).background(Color.Black.copy(alpha = 0.75f), RoundedCornerShape(12.dp))
                        .clickable { vm.clearCastNote() }.padding(horizontal = 14.dp, vertical = 8.dp)
                )
            }
            if (showCast) CastDialog(vm.currentLink()) { showCast = false }

            // ---- lock mode: only unlock button
            if (locked) {
                AnimatedVisibility(
                    visible = controls, enter = fadeIn(), exit = fadeOut(),
                    modifier = Modifier.align(Alignment.CenterStart)
                ) {
                    CtrlIcon(
                        Icons.Rounded.LockOpen, "Unlock",
                        Modifier.windowInsetsPadding(WindowInsets.safeDrawing).padding(16.dp)
                    ) { locked = false; touch++ }
                }
            }

            // ---- full controls
            AnimatedVisibility(
                visible = controls && !locked && load is LoadState.Ready,
                enter = fadeIn(), exit = fadeOut()
            ) {
                Box(Modifier.fillMaxSize()) {
                    Box(
                        Modifier.align(Alignment.TopCenter).fillMaxWidth().height(96.dp)
                            .background(Brush.verticalGradient(listOf(Color.Black.copy(0.7f), Color.Transparent)))
                    )
                    Box(
                        Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(170.dp)
                            .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(0.75f))))
                    )

                    // top bar
                    Row(
                        Modifier.align(Alignment.TopCenter).fillMaxWidth()
                            .windowInsetsPadding(WindowInsets.safeDrawing).padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        CtrlIcon(Icons.AutoMirrored.Rounded.ArrowBack, "Back", onClick = onBack)
                        Spacer(Modifier.width(4.dp))
                        Column(Modifier.weight(1f)) {
                            Text(
                                title, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis,
                                style = MaterialTheme.typography.titleMedium
                            )
                            if (epgId.isNotBlank() && epgData.isNotEmpty()) {
                                val (cur, nxt) = EpgStore.nowNext(epgId)
                                val line = listOfNotNull(
                                    cur?.let { "Now: ${it.title}" }, nxt?.let { "Next: ${it.title}" }
                                ).joinToString("   ")
                                if (line.isNotEmpty()) {
                                    Text(
                                        line, color = Color.White.copy(alpha = 0.75f), maxLines = 1,
                                        overflow = TextOverflow.Ellipsis, fontSize = 12.sp
                                    )
                                }
                            }
                        }
                    }

                    // bottom bar
                    val playGroup: @Composable () -> Unit = {
                        if (ui.hasPrev) CtrlIcon(Icons.Rounded.SkipPrevious, "Previous") { vm.skipPrev() }
                        PlayPauseButton(ui.isPlaying, ui.ended) { vm.togglePlay(); touch++ }
                        if (ui.hasNext) CtrlIcon(Icons.Rounded.SkipNext, "Next") { vm.skipNext() }
                        CtrlIcon(
                            if (ui.muted) Icons.Rounded.VolumeOff else Icons.Rounded.VolumeUp,
                            if (ui.muted) "Unmute" else "Mute"
                        ) { vm.toggleMute(); touch++ }
                        if (ui.isLive) {
                            val atLive = ui.liveOffsetMs in 0..6000
                            Surface(
                                onClick = { vm.goLive(); touch++ },
                                shape = RoundedCornerShape(50),
                                color = if (atLive) Color(0xFFE53935) else Color.White.copy(alpha = 0.2f),
                                modifier = Modifier.padding(horizontal = 6.dp)
                            ) {
                                Text(
                                    if (atLive) "● LIVE" else "GO LIVE", color = Color.White,
                                    fontSize = 12.sp, fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                                )
                            }
                        }
                    }
                    val speedBtn: @Composable () -> Unit = {
                        TextButton(onClick = { menu = MenuType.Speed }) {
                            Text(speedLabel(ui.speed), color = Color.White, fontWeight = FontWeight.Bold)
                        }
                    }
                    val otherBtns: @Composable () -> Unit = {
                        if (qualities.isNotEmpty()) {
                            CtrlIcon(Icons.Rounded.HighQuality, "Quality") { menu = MenuType.Quality }
                        }
                        if (audios.isNotEmpty()) {
                            CtrlIcon(Icons.Rounded.Language, "Audio language") { menu = MenuType.Audio }
                        }
                        if (subs.isNotEmpty()) {
                            CtrlIcon(
                                if (ui.subsOn) Icons.Rounded.ClosedCaption else Icons.Rounded.ClosedCaptionOff,
                                "Subtitles"
                            ) { menu = MenuType.Subs }
                        }
                        CtrlIcon(Icons.Rounded.AspectRatio, "Resize mode") {
                            resize = when (resize) {
                                AspectRatioFrameLayout.RESIZE_MODE_FIT -> AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                                AspectRatioFrameLayout.RESIZE_MODE_ZOOM -> AspectRatioFrameLayout.RESIZE_MODE_FILL
                                else -> AspectRatioFrameLayout.RESIZE_MODE_FIT
                            }
                            touch++
                        }
                        CtrlIcon(Icons.Rounded.Cast, "Cast") { showCast = true; touch++ }
                        if (Build.VERSION.SDK_INT >= 26) {
                            CtrlIcon(Icons.Rounded.PictureInPictureAlt, "Picture in picture", onClick = onEnterPip)
                        }
                        CtrlIcon(Icons.Rounded.Lock, "Lock controls") { locked = true; controls = true; touch++ }
                    }

                    Column(
                        Modifier.align(Alignment.BottomCenter).fillMaxWidth()
                            .windowInsetsPadding(WindowInsets.safeDrawing).padding(horizontal = 12.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            val shownPos = scrub?.let { (it * ui.durationMs).toLong() } ?: ui.positionMs
                            Text(formatTime(shownPos), color = Color.White, fontSize = 12.sp)
                            ThinSeekBar(
                                ui.positionMs, ui.durationMs, ui.bufferedMs,
                                onScrub = { scrub = it; touch++ },
                                onSeek = { vm.seekToFraction(it); touch++ },
                                modifier = Modifier.weight(1f).padding(horizontal = 10.dp)
                            )
                            Text(
                                if (ui.isLive && ui.durationMs <= 0) "LIVE" else formatTime(ui.durationMs),
                                color = Color.White, fontSize = 12.sp
                            )
                        }
                        if (portrait == true) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                playGroup(); Spacer(Modifier.weight(1f)); speedBtn()
                            }
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Spacer(Modifier.weight(1f)); otherBtns()
                            }
                        } else {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                playGroup(); Spacer(Modifier.weight(1f)); speedBtn(); otherBtns()
                            }
                        }
                    }
                }
            }

            // ---- small glass menu (drawn inside the player, so system bars never show up)
            AnimatedVisibility(
                visible = menu != MenuType.None && !locked,
                enter = fadeIn(tween(150)) + scaleIn(tween(150), initialScale = 0.9f, transformOrigin = TransformOrigin(1f, 1f)),
                exit = fadeOut(tween(120))
            ) {
                Box(Modifier.fillMaxSize().pointerInput(Unit) { detectTapGestures { menu = MenuType.None } }) {
                    GlassMenu(
                        Modifier.align(Alignment.BottomEnd).windowInsetsPadding(WindowInsets.safeDrawing)
                            .padding(end = 12.dp, bottom = if (portrait == true) 128.dp else 84.dp)
                    ) {
                        when (lastMenu) {
                            MenuType.Speed -> speeds.forEach { sp ->
                                GlassItem(speedLabel(sp), sp == ui.speed) { vm.setSpeed(sp); menu = MenuType.None }
                            }
                            MenuType.Quality -> qualities.forEach { o ->
                                GlassItem(o.label, o.selected) { vm.selectQuality(o.id); menu = MenuType.None }
                            }
                            MenuType.Audio -> audios.forEach { o ->
                                GlassItem(o.label, o.selected) { vm.selectAudio(o.id); menu = MenuType.None }
                            }
                            MenuType.Subs -> {
                                GlassItem("Off", !ui.subsOn) { vm.selectSub("off"); menu = MenuType.None }
                                subs.forEach { o ->
                                    GlassItem(o.label, ui.subsOn && o.selected) { vm.selectSub(o.id); menu = MenuType.None }
                                }
                            }
                            MenuType.None -> {}
                        }
                    }
                }
            }
        }
    }
}

/** Compact translucent "water glass" panel. */
@Composable
private fun GlassMenu(modifier: Modifier, content: @Composable ColumnScope.() -> Unit) {
    val shape = RoundedCornerShape(16.dp)
    Column(
        modifier.widthIn(max = 150.dp).width(IntrinsicSize.Max).heightIn(max = 220.dp)
            .clip(shape)
            .background(
                Brush.linearGradient(
                    listOf(Color(0xB30B3C5D), Color(0x99086A9A), Color(0xB30288D1))
                )
            )
            .border(1.dp, Color.White.copy(alpha = 0.28f), shape)
            .pointerInput(Unit) { detectTapGestures { } }
            .verticalScroll(rememberScrollState())
            .padding(vertical = 6.dp),
        content = content
    )
}

@Composable
private fun GlassItem(label: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 4.dp).clip(RoundedCornerShape(10.dp))
            .background(if (selected) Color.White.copy(alpha = 0.22f) else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            label, color = Color.White, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal
        )
    }
}

@Composable
private fun PlayPauseButton(playing: Boolean, ended: Boolean, onClick: () -> Unit) {
    IconButton(onClick = onClick, modifier = Modifier.size(42.dp)) {
        Crossfade(targetState = if (playing) 1 else if (ended) 2 else 0, label = "playpause") { st ->
            Icon(
                when (st) { 1 -> Icons.Rounded.Pause; 2 -> Icons.Rounded.Replay; else -> Icons.Rounded.PlayArrow },
                if (st == 1) "Pause" else "Play", tint = Color.White, modifier = Modifier.size(26.dp)
            )
        }
    }
}

@Composable
private fun BoxScope.BackOnly(onBack: () -> Unit) {
    CtrlIcon(
        Icons.AutoMirrored.Rounded.ArrowBack, "Back",
        Modifier.align(Alignment.TopStart).windowInsetsPadding(WindowInsets.safeDrawing).padding(8.dp),
        onBack
    )
}

@Composable
private fun CtrlIcon(icon: ImageVector, desc: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    IconButton(onClick = onClick, modifier = modifier.size(42.dp)) {
        Icon(icon, desc, tint = Color.White, modifier = Modifier.size(24.dp))
    }
}

/** Thin seek bar with buffered indicator and drag/tap seeking. */
@Composable
private fun ThinSeekBar(
    positionMs: Long, durationMs: Long, bufferedMs: Long,
    onScrub: (Float?) -> Unit, onSeek: (Float) -> Unit, modifier: Modifier = Modifier
) {
    var drag by remember { mutableStateOf<Float?>(null) }
    val enabled = durationMs > 0
    val frac = drag ?: if (enabled) (positionMs.toFloat() / durationMs).coerceIn(0f, 1f) else 0f
    val buf = if (enabled) (bufferedMs.toFloat() / durationMs).coerceIn(0f, 1f) else 0f
    val primary = MaterialTheme.colorScheme.primary

    Box(
        modifier.height(28.dp)
            .pointerInput(enabled) {
                if (!enabled) return@pointerInput
                detectTapGestures { o -> onSeek((o.x / size.width).coerceIn(0f, 1f)) }
            }
            .pointerInput(enabled) {
                if (!enabled) return@pointerInput
                detectHorizontalDragGestures(
                    onDragStart = { o ->
                        val f = (o.x / size.width).coerceIn(0f, 1f); drag = f; onScrub(f)
                    },
                    onHorizontalDrag = { c, _ ->
                        val f = (c.position.x / size.width).coerceIn(0f, 1f); drag = f; onScrub(f)
                    },
                    onDragEnd = { drag?.let(onSeek); drag = null; onScrub(null) },
                    onDragCancel = { drag = null; onScrub(null) }
                )
            }
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val cy = size.height / 2
            val h = 3.dp.toPx()
            val top = cy - h / 2
            drawRect(Color.White.copy(alpha = 0.3f), Offset(0f, top), Size(size.width, h))
            drawRect(Color.White.copy(alpha = 0.5f), Offset(0f, top), Size(size.width * buf, h))
            drawRect(primary, Offset(0f, top), Size(size.width * frac, h))
            if (enabled) {
                drawCircle(
                    primary,
                    radius = if (drag != null) 8.dp.toPx() else 5.dp.toPx(),
                    center = Offset(size.width * frac, cy)
                )
            }
        }
    }
}

/** Hides system bars, draws under the display cutout; orientation follows the video (portrait / landscape). */
@Composable
private fun Immersive(portrait: Boolean?) {
    val view = LocalView.current
    DisposableEffect(Unit) {
        val act = view.context.findActivity()
        val window = act?.window
        val ctrl = window?.let { WindowCompat.getInsetsController(it, view) }
        ctrl?.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        ctrl?.hide(WindowInsetsCompat.Type.systemBars())
        if (window != null && Build.VERSION.SDK_INT >= 28) {
            val lp = window.attributes
            lp.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            window.attributes = lp
        }
        window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        onDispose {
            ctrl?.show(WindowInsetsCompat.Type.systemBars())
            if (window != null && Build.VERSION.SDK_INT >= 28) {
                val lp = window.attributes
                lp.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_DEFAULT
                window.attributes = lp
            }
            window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            act?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        }
    }
    DisposableEffect(portrait) {
        view.context.findActivity()?.requestedOrientation = when (portrait) {
            true -> ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT
            false -> ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
            null -> ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        }
        onDispose { }
    }
}

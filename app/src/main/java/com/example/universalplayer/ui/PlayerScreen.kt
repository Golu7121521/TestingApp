@file:OptIn(UnstableApi::class, androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.example.universalplayer.ui

import android.content.pm.ActivityInfo
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
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
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

private enum class MenuType { None, Speed, Quality, Audio }

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

    BackHandler {
        if (locked) { controls = true; touch++ } else onBack()
    }
    LaunchedEffect(flashKey) {
        if (flashKey > 0) { delay(700); flashSide = 0; flashTotal = 0 }
    }
    LaunchedEffect(controls, touch, ui.isPlaying, menu, scrub) {
        if (controls && ui.isPlaying && menu == MenuType.None && scrub == null) {
            delay(3500); controls = false
        }
    }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
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
                                val left = off.x < size.width / 2
                                val side = if (left) -1 else 1
                                vm.seekBy(side * 10_000L)
                                flashTotal = if (flashSide == side) flashTotal + 10 else 10
                                flashSide = side
                                flashKey++
                            }
                        },
                        onLongPress = {
                            if (!locked && load is LoadState.Ready) { boosting = true; vm.boost(true) }
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
                        Text(l.message, color = Color.White)
                        Button(onClick = { vm.retry() }) { Text("Retry") }
                    }
                    BackOnly(onBack)
                }
                else -> if (ui.buffering) {
                    CircularProgressIndicator(Modifier.align(Alignment.Center), color = Color.White)
                }
            }

            // ---- lock mode: only unlock button
            if (locked) {
                AnimatedVisibility(
                    visible = controls, enter = fadeIn(), exit = fadeOut(),
                    modifier = Modifier.align(Alignment.CenterStart)
                ) {
                    CtrlIcon(Icons.Rounded.LockOpen, "Unlock", Modifier.windowInsetsPadding(WindowInsets.safeDrawing).padding(16.dp)) {
                        locked = false; touch++
                    }
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
                        Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(140.dp)
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
                        Text(
                            title, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis,
                            style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f)
                        )
                    }

                    // bottom bar
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
                        // portrait: the control row is too wide for the screen, so let it scroll
                        Row(
                            if (portrait == true) Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()) else Modifier,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            CtrlIcon(
                                if (ui.isPlaying) Icons.Rounded.Pause else if (ui.ended) Icons.Rounded.Replay else Icons.Rounded.PlayArrow,
                                if (ui.isPlaying) "Pause" else "Play"
                            ) { vm.togglePlay(); touch++ }
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

                            if (portrait != true) Spacer(Modifier.weight(1f))

                            // speed
                            MenuBox(menu == MenuType.Speed, { menu = MenuType.None }, anchor = {
                                TextButton(onClick = { menu = MenuType.Speed }) {
                                    Text(speedLabel(ui.speed), color = Color.White, fontWeight = FontWeight.Bold)
                                }
                            }) {
                                speeds.forEach { s ->
                                    DropdownMenuItem(
                                        text = { Text(speedLabel(s)) },
                                        leadingIcon = { if (s == ui.speed) Icon(Icons.Rounded.Check, null) },
                                        onClick = { vm.setSpeed(s); menu = MenuType.None }
                                    )
                                }
                            }
                            // quality
                            if (qualities.isNotEmpty()) {
                                MenuBox(menu == MenuType.Quality, { menu = MenuType.None }, anchor = {
                                    CtrlIcon(Icons.Rounded.HighQuality, "Quality") { menu = MenuType.Quality }
                                }) {
                                    qualities.forEach { o ->
                                        DropdownMenuItem(
                                            text = { Text(o.label) },
                                            leadingIcon = { if (o.selected) Icon(Icons.Rounded.Check, null) },
                                            onClick = { vm.selectQuality(o.id); menu = MenuType.None }
                                        )
                                    }
                                }
                            }
                            // audio language
                            if (audios.isNotEmpty()) {
                                MenuBox(menu == MenuType.Audio, { menu = MenuType.None }, anchor = {
                                    CtrlIcon(Icons.Rounded.Language, "Audio language") { menu = MenuType.Audio }
                                }) {
                                    audios.forEach { o ->
                                        DropdownMenuItem(
                                            text = { Text(o.label) },
                                            leadingIcon = { if (o.selected) Icon(Icons.Rounded.Check, null) },
                                            onClick = { vm.selectAudio(o.id); menu = MenuType.None }
                                        )
                                    }
                                }
                            }
                            // subtitles
                            if (ui.hasSubs) {
                                CtrlIcon(
                                    if (ui.subsOn) Icons.Rounded.ClosedCaption else Icons.Rounded.ClosedCaptionOff,
                                    "Subtitles"
                                ) { vm.toggleSubs(); touch++ }
                            }
                            // aspect / zoom
                            CtrlIcon(Icons.Rounded.AspectRatio, "Resize mode") {
                                resize = when (resize) {
                                    AspectRatioFrameLayout.RESIZE_MODE_FIT -> AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                                    AspectRatioFrameLayout.RESIZE_MODE_ZOOM -> AspectRatioFrameLayout.RESIZE_MODE_FILL
                                    else -> AspectRatioFrameLayout.RESIZE_MODE_FIT
                                }
                                touch++
                            }
                            // picture in picture
                            if (Build.VERSION.SDK_INT >= 26) {
                                CtrlIcon(Icons.Rounded.PictureInPictureAlt, "Picture in picture", onClick = onEnterPip)
                            }
                            // lock
                            CtrlIcon(Icons.Rounded.Lock, "Lock controls") { locked = true; controls = true; touch++ }
                        }
                    }
                }
            }
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
private fun CtrlIcon(icon: androidx.compose.ui.graphics.vector.ImageVector, desc: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    IconButton(onClick = onClick, modifier = modifier.size(42.dp)) {
        Icon(icon, desc, tint = Color.White, modifier = Modifier.size(24.dp))
    }
}

@Composable
private fun MenuBox(
    expanded: Boolean,
    onDismiss: () -> Unit,
    anchor: @Composable () -> Unit,
    content: @Composable ColumnScope.() -> Unit
) {
    Box {
        anchor()
        DropdownMenu(expanded = expanded, onDismissRequest = onDismiss, content = content)
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

/** Hides system bars, draws under the display cutout and matches the screen orientation to the video. */
@Composable
private fun Immersive(portrait: Boolean?) {
    val view = LocalView.current
    val act = view.context.findActivity()
    // Portrait videos (reels, Shorts) play upright; landscape videos play sideways.
    // Until the video size is known, keep the current orientation.
    LaunchedEffect(portrait) {
        act?.requestedOrientation = when (portrait) {
            true -> ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT
            false -> ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
            null -> ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        }
    }
    DisposableEffect(Unit) {
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
}

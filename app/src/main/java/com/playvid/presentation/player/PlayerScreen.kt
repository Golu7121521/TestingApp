package com.playvid.presentation.player

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.media.AudioManager
import android.provider.Settings
import android.view.WindowManager
import android.view.LayoutInflater
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.filled.BrightnessMedium
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Forward10
import androidx.compose.material.icons.filled.Replay10
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.playvid.PlayVidApp
import com.playvid.R
import com.playvid.domain.model.AppSettings
import com.playvid.presentation.navigation.PlaybackPayload
import com.playvid.presentation.util.formatTime
import kotlinx.coroutines.delay

private const val SEEK_MS = 10_000L

private fun Context.findActivity(): Activity {
    var c: Context = this
    while (c is ContextWrapper) {
        if (c is Activity) return c
        c = c.baseContext
    }
    error("No Activity found in context")
}

/**
 * Player screen. Everything (picker, resume prompt) is drawn inside this one window,
 * so no dialog can pull the system bars back while a video is playing.
 * Box > [video surface, tap layer, seek feedback, loading/buffering, error,
 *        controls (fade), more menu panel, resume prompt]
 */
@Composable
fun PlayerScreen(payload: String, onBack: () -> Unit) {
    val context = LocalContext.current
    val controller = remember { (context.applicationContext as PlayVidApp).container.playback }
    val request = remember(payload) { PlaybackPayload.decode(payload) }
    // Idempotent: the same source keeps playing (coming back from the mini player).
    remember(payload) { if (request != null) controller.open(request) else controller.fail() }

    val state by controller.state.collectAsStateWithLifecycle()

    val activity = remember(context) { context.findActivity() }
    val pip = LocalPipController.current
    val view = LocalView.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val clipboard = LocalClipboardManager.current
    val isLandscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE

    var controlsVisible by remember { mutableStateOf(true) }
    var interaction by remember { mutableIntStateOf(0) }
    var picker by remember { mutableStateOf<PickerKind?>(null) }
    var locked by remember { mutableStateOf(false) }
    var unlockHintVisible by remember { mutableStateOf(false) }

    val settings by remember { (context.applicationContext as PlayVidApp).container.settingsRepository.settings }
        .collectAsStateWithLifecycle(initialValue = AppSettings())
    val audioManager = remember { context.getSystemService(Context.AUDIO_SERVICE) as AudioManager }

    // Brightness / volume swipe: 0 = idle, 1 = brightness, 2 = volume. Level 0f..1f.
    var gestureKind by remember { mutableIntStateOf(0) }
    var gestureLevel by remember { mutableFloatStateOf(0f) }

    // Double-tap seek feedback: side (-1 / 1), accumulated seconds, and a tick that restarts the hide timer.
    var seekSide by remember { mutableIntStateOf(0) }
    var seekTotal by remember { mutableIntStateOf(0) }
    var seekTick by remember { mutableIntStateOf(0) }

    // Press-and-hold anywhere to fast-forward at settings.holdSpeedMultiplier; released = back to normal.
    var holdSpeedActive by remember { mutableStateOf(false) }
    var speedBeforeHold by remember { mutableFloatStateOf(1f) }

    val leave: () -> Unit = {
        controller.onLeavePlayer()
        if (!settings.backgroundPlayback) controller.pause()
        onBack()
    }

    // Hide status + navigation bars for the whole time the player is open (portrait and landscape).
    // On a cold app start the window can still be settling (layout/focus not done yet) well
    // beyond a normal warm launch, so a hide() call right away is often dropped; keep retrying
    // for a couple of seconds to reliably catch that instead of leaving a bar showing until the
    // user happens to touch something (like the brightness gesture) that forces a re-layout.
    LaunchedEffect(Unit) {
        val controllerCompat = WindowCompat.getInsetsController(activity.window, view)
        controllerCompat.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        val delaysMs = longArrayOf(0, 120, 250, 450, 800, 1300, 2000)
        delaysMs.forEach { d ->
            if (d > 0) delay(d)
            controllerCompat.hide(WindowInsetsCompat.Type.systemBars())
        }
    }
    DisposableEffect(Unit) {
        val controllerCompat = WindowCompat.getInsetsController(activity.window, view)
        view.keepScreenOn = true
        pip.enabled = true
        onDispose {
            controllerCompat.show(WindowInsetsCompat.Type.systemBars())
            view.keepScreenOn = false
            pip.enabled = false
            // Give brightness back to the system setting.
            activity.window.attributes = activity.window.attributes.also {
                it.screenBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
            }
            activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        }
    }

    // Backgrounding-pause is handled once, app-wide, in MainActivity.onStop() (it already
    // respects Settings > "Keep Playing in Background"). Nothing needed here anymore.

    // Auto-hide controls 3.5 s after the last interaction while playing.
    LaunchedEffect(controlsVisible, state.isPlaying, picker, state.resumePrompt, interaction) {
        if (controlsVisible && state.isPlaying && picker == null && state.resumePrompt == null) {
            delay(3_500)
            controlsVisible = false
        }
    }

    // Same auto-hide for the small unlock button while the screen is locked.
    LaunchedEffect(unlockHintVisible, interaction) {
        if (unlockHintVisible) {
            delay(3_500)
            unlockHintVisible = false
        }
    }

    LaunchedEffect(seekTick) {
        if (seekSide != 0) {
            delay(800)
            seekSide = 0
            seekTotal = 0
        }
    }

    BackHandler { leave() }

    val doSeek: (Int) -> Unit = { direction ->
        controller.seekBy(direction * SEEK_MS)
        seekTotal = if (seekSide == direction) seekTotal + 10 else 10
        seekSide = direction
        seekTick++
    }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        // Video surface. No built-in controller: our Compose controls draw on top.
        var playerViewRef by remember { mutableStateOf<PlayerView?>(null) }
        AndroidView(
            factory = { ctx ->
                (LayoutInflater.from(ctx).inflate(R.layout.view_player, null, false) as PlayerView).also { pv ->
                    pv.player = controller.player
                    playerViewRef = pv
                    // Compose can detach/reattach this View's window without the composable itself
                    // leaving composition (layout animations, certain recompositions); when that
                    // happens the video surface can come back black while audio keeps playing.
                    // Re-pointing the player at it as soon as it's back in a window is a cheap,
                    // reliable fix for that — covers more cases than only watching for resume.
                    pv.addOnAttachStateChangeListener(object : android.view.View.OnAttachStateChangeListener {
                        override fun onViewAttachedToWindow(v: android.view.View) {
                            pv.player = null
                            pv.player = controller.player
                        }
                        override fun onViewDetachedFromWindow(v: android.view.View) {}
                    })
                }
            },
            update = {
                it.resizeMode = if (state.resizeFill) {
                    AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                } else {
                    AspectRatioFrameLayout.RESIZE_MODE_FIT
                }
            },
            modifier = Modifier.fillMaxSize(),
            onRelease = {
                it.player = null
                if (playerViewRef === it) playerViewRef = null
            }
        )

        // Rarely, the video surface goes black (a lost ExoPlayer/TextureView attachment) while
        // audio keeps playing — re-attaching the player is a cheap, reliable fix for that, so do
        // it automatically whenever the app comes back to the foreground rather than making the
        // person switch to the mini player and back to force it themselves.
        DisposableEffect(lifecycleOwner) {
            val observer = LifecycleEventObserver { _, event ->
                if (event == Lifecycle.Event.ON_RESUME) {
                    playerViewRef?.let { pv ->
                        pv.player = null
                        pv.player = controller.player
                    }
                }
            }
            lifecycleOwner.lifecycle.addObserver(observer)
            onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
        }

        // Also re-attach whenever the orientation flips: a rotation can just as easily leave the
        // video surface detached as backgrounding/returning does. Skip the very first composition
        // (the factory above already attached the player) so there's no redundant reattach/flicker.
        var isFirstOrientation by remember { mutableStateOf(true) }
        LaunchedEffect(isLandscape) {
            if (isFirstOrientation) {
                isFirstOrientation = false
            } else {
                playerViewRef?.let { pv ->
                    pv.player = null
                    pv.player = controller.player
                }
            }
        }

        // And whenever a different video actually starts loading on this same screen (e.g.
        // "Choose another video", switching quality/audio track) — one more point where the
        // surface can come up black instead of showing the new video's first frame.
        val currentSource by controller.current.collectAsStateWithLifecycle()
        var isFirstSource by remember { mutableStateOf(true) }
        LaunchedEffect(currentSource?.source) {
            if (isFirstSource) {
                isFirstSource = false
            } else {
                playerViewRef?.let { pv ->
                    pv.player = null
                    pv.player = controller.player
                }
            }
        }

        // Tap layer: single tap = show/hide controls, double tap left/right half = seek 10 s,
        // press and hold = fast-forward at settings.holdSpeedMultiplier while held.
        if (!locked) Box(
            Modifier.fillMaxSize().pointerInput(Unit) {
                detectTapGestures(
                    onTap = {
                        controlsVisible = !controlsVisible
                        interaction++
                    },
                    onDoubleTap = { offset ->
                        if (offset.x < size.width / 2f) doSeek(-1) else doSeek(1)
                    },
                    onLongPress = {
                        if (settings.holdSpeedGesture && !holdSpeedActive) {
                            speedBeforeHold = state.speed
                            holdSpeedActive = true
                            controller.setSpeed(settings.holdSpeedMultiplier.toFloat())
                        }
                    },
                    onPress = {
                        if (settings.holdSpeedGesture) {
                            tryAwaitRelease()
                            if (holdSpeedActive) {
                                holdSpeedActive = false
                                controller.setSpeed(speedBeforeHold)
                            }
                        }
                    }
                )
            }.pointerInput(settings.brightnessGesture, settings.volumeGesture) {
                // Left half: brightness. Right half: volume. Each can be turned off in Settings.
                // A drag starting near the very top is left alone so the status-bar swipe-down
                // (clock, notifications, quick settings) keeps working.
                if (settings.brightnessGesture || settings.volumeGesture) {
                    val topExclusionPx = 48.dp.toPx()
                    var side = 0
                    var level = 0f
                    detectVerticalDragGestures(
                        onDragStart = { start ->
                            side = when {
                                start.y < topExclusionPx -> 0
                                start.x < size.width / 2f && settings.brightnessGesture -> 1
                                start.x >= size.width / 2f && settings.volumeGesture -> 2
                                else -> 0
                            }
                            level = when (side) {
                                1 -> {
                                    val b = activity.window.attributes.screenBrightness
                                    if (b >= 0f) b else Settings.System.getInt(
                                        context.contentResolver, Settings.System.SCREEN_BRIGHTNESS, 128
                                    ) / 255f
                                }
                                2 -> {
                                    val max = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1)
                                    audioManager.getStreamVolume(AudioManager.STREAM_MUSIC).toFloat() / max
                                }
                                else -> 0f
                            }
                        },
                        onDragEnd = { gestureKind = 0 },
                        onDragCancel = { gestureKind = 0 }
                    ) { change, dragAmount ->
                        if (side == 0) return@detectVerticalDragGestures
                        change.consume()
                        level = (level - dragAmount / (size.height * 0.7f)).coerceIn(0f, 1f)
                        if (side == 1) {
                            activity.window.attributes = activity.window.attributes.also {
                                it.screenBrightness = level.coerceAtLeast(0.01f)
                            }
                        } else {
                            val max = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
                            audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, Math.round(level * max), 0)
                        }
                        gestureLevel = level
                        gestureKind = side
                    }
                }
            }
        )

        // Locked: the tap/gesture layer above is skipped entirely. A tap here only reveals a
        // small unlock button (auto-hides again), so the screen can't be touched by accident.
        if (locked && !pip.inPip) {
            Box(
                Modifier.fillMaxSize().pointerInput(Unit) {
                    detectTapGestures(onTap = { unlockHintVisible = !unlockHintVisible; interaction++ })
                }
            )
            AnimatedVisibility(
                visible = unlockHintVisible,
                enter = fadeIn(tween(150)),
                exit = fadeOut(tween(150)),
                modifier = Modifier.align(Alignment.BottomStart).windowInsetsPadding(WindowInsets.safeDrawing).padding(20.dp)
            ) {
                Box(
                    Modifier
                        .background(Color.Black.copy(alpha = 0.55f), androidx.compose.foundation.shape.CircleShape)
                        .clickable { locked = false; unlockHintVisible = false; interaction++ }
                        .padding(12.dp)
                ) {
                    Icon(Icons.Filled.LockOpen, contentDescription = "Unlock", tint = Color.White)
                }
            }
        }

        if (pip.inPip) {
            // Tiny PiP window: video only.
            if (state.isLoading || state.isBuffering) {
                CircularProgressIndicator(Modifier.align(Alignment.Center).size(28.dp), color = Color.White)
            }
        } else {
            // Brightness / volume level while swiping (icon + thin bar, disappears on release)
            if (gestureKind != 0) {
                Row(
                    Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = 24.dp)
                        .background(Color.Black.copy(alpha = 0.55f), RoundedCornerShape(20.dp))
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        if (gestureKind == 1) Icons.Filled.BrightnessMedium else Icons.Filled.VolumeUp,
                        contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp)
                    )
                    Box(Modifier.width(96.dp).height(2.dp).background(Color.White.copy(alpha = 0.3f))) {
                        Box(
                            Modifier.fillMaxWidth(gestureLevel).fillMaxHeight()
                                .background(MaterialTheme.colorScheme.primary)
                        )
                    }
                }
            }

            // Double-tap seek feedback
            if (seekSide != 0) {
                Row(
                    Modifier.align(if (seekSide < 0) Alignment.CenterStart else Alignment.CenterEnd).padding(48.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        if (seekSide < 0) Icons.Filled.Replay10 else Icons.Filled.Forward10,
                        contentDescription = null, tint = Color.White
                    )
                    Text("${seekTotal}s", color = Color.White, style = MaterialTheme.typography.titleMedium)
                }
            }

            // Loading (before first frame) and buffering (mid-playback) indicators
            if (state.error == null && (state.isLoading || state.isBuffering)) {
                Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(Modifier.size(44.dp), color = Color.White, strokeWidth = 3.dp)
                    if (state.isLoading) {
                        Text("Loading video...", color = Color.White, modifier = Modifier.padding(top = 12.dp))
                    }
                }
            }

            // Error state
            if (state.error != null) {
                Column(
                    Modifier.align(Alignment.Center).padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text("Can't play this video", color = Color.White, style = MaterialTheme.typography.titleMedium)
                    Text(
                        state.error.orEmpty(),
                        color = Color.White.copy(alpha = 0.7f),
                        style = MaterialTheme.typography.bodyMedium,
                        textAlign = TextAlign.Center
                    )
                    Button(onClick = controller::retry, modifier = Modifier.padding(top = 8.dp)) { Text("Retry") }
                    TextButton(onClick = leave) {
                        Text(if (state.isLocal) "Choose another video" else "Change URL")
                    }
                }
            }

            AnimatedVisibility(
                visible = controlsVisible && state.error == null && state.resumePrompt == null && !locked,
                enter = fadeIn(tween(150)),
                exit = fadeOut(tween(150))
            ) {
                PlayerControls(
                    state = state,
                    progress = controller.progress,
                    isLandscape = isLandscape,
                    onBack = leave,
                    onTogglePlay = controller::togglePlay,
                    onSeekBack = { doSeek(-1) },
                    onSeekForward = { doSeek(1) },
                    onSeekTo = controller::seekTo,
                    onToggleMute = controller::toggleMute,
                    showQuality = !state.isLocal && state.qualities.size > 1,
                    showAudio = state.audioTracks.size > 1,
                    showSubtitles = state.textTracks.isNotEmpty(),
                    showPip = pip.isSupported,
                    showCopyUrl = request != null && !request.isLocal,
                    showDownload = state.downloadOptions.isNotEmpty() || state.audioDownloadOptions.isNotEmpty(),
                    onPicker = { picker = it },
                    onToggleResize = { controller.setResizeFill(!state.resizeFill) },
                    onPip = { pip.enter() },
                    onCopyUrl = { request?.let { clipboard.setText(AnnotatedString(it.source)) } },
                    onDownload = {
                        picker = when {
                            state.downloadOptions.isNotEmpty() && state.audioDownloadOptions.isNotEmpty() -> PickerKind.DOWNLOAD_TYPE
                            state.audioDownloadOptions.isNotEmpty() -> PickerKind.DOWNLOAD_AUDIO
                            else -> PickerKind.DOWNLOAD
                        }
                    },
                    onToggleOrientation = {
                        activity.requestedOrientation =
                            if (isLandscape) ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
                            else ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
                    },
                    onLock = { locked = true; controlsVisible = false; unlockHintVisible = false },
                    onGoLive = { controller.seekToLive() },
                    onInteract = { interaction++ }
                )
            }

            var lastPicker by remember { mutableStateOf<PickerKind?>(null) }
            LaunchedEffect(picker) { picker?.let { lastPicker = it } }
            AnimatedVisibility(
                visible = picker != null,
                enter = fadeIn(tween(180)) + slideInVertically(tween(180)) { it / 3 },
                exit = fadeOut(tween(180)) + slideOutVertically(tween(180)) { it / 3 }
            ) {
                lastPicker?.let { kind ->
                    PlayerPickerPanel(
                        kind = kind,
                        state = state,
                        onDismiss = { picker = null },
                        onAutoQuality = { controller.selectAutoQuality(); picker = null },
                        onQuality = { controller.selectQuality(it); picker = null },
                        onAudio = { controller.selectAudio(it); picker = null },
                        onText = { controller.selectText(it); picker = null },
                        onSpeed = { controller.setSpeed(it); picker = null },
                        onDownload = { controller.downloadVideo(it); picker = null },
                        onDownloadAudio = { controller.downloadAudio(it); picker = null },
                        onPickDownloadType = { isAudio -> picker = if (isAudio) PickerKind.DOWNLOAD_AUDIO else PickerKind.DOWNLOAD }
                    )
                }
            }

            // Resume prompt: "Continue from 25:40?"
            val prompt = state.resumePrompt
            if (prompt != null && state.error == null) {
                ResumePrompt(position = prompt, onResume = controller::resume, onStartOver = controller::startOver)
            }
        }
    }
}

@Composable
private fun ResumePrompt(position: Long, onResume: () -> Unit, onStartOver: () -> Unit) {
    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.6f))
            .pointerInput(Unit) { detectTapGestures { } },
        contentAlignment = Alignment.Center
    ) {
        Surface(
            shape = MaterialTheme.shapes.large,
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            modifier = Modifier.padding(32.dp).widthIn(max = 320.dp)
        ) {
            Column(Modifier.padding(20.dp)) {
                Text("Continue from ${formatTime(position)}?", style = MaterialTheme.typography.titleMedium)
                Row(Modifier.fillMaxWidth().padding(top = 16.dp), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onStartOver) { Text("Start Over") }
                    TextButton(onClick = onResume) { Text("Resume") }
                }
            }
        }
    }
}

package com.example.offlineplayer

import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.View
import android.widget.SeekBar
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.example.offlineplayer.databinding.ActivityPlayerBinding
import org.videolan.libvlc.LibVLC
import org.videolan.libvlc.Media
import org.videolan.libvlc.MediaPlayer
import java.util.Locale
import java.util.concurrent.TimeUnit
import kotlin.math.abs

/**
 * Fully custom, edge-to-edge video player screen built on libVLC - the exact same
 * playback engine VLC itself uses. This means it plays local files AND almost any
 * kind of link (http/https, hls .m3u8, dash .mpd, rtsp, rtmp, mms, udp, smb, ftp...)
 * without needing separate extension modules, just like VLC does.
 *
 * Features:
 *  - Immersive edge-to-edge playback (content flows under the status/nav bars)
 *  - Tap to show/hide controls, auto-hide after a few seconds
 *  - Double-tap left/right to seek -10s / +10s with animated feedback
 *  - Vertical swipe on the left half = brightness, right half = volume, with a live indicator
 *  - Play/pause, rewind10/forward10, seek bar with live time, playback speed cycling, lock, aspect ratio
 */
class PlayerActivity : AppCompatActivity() {

    private lateinit var binding: ActivityPlayerBinding

    private var libVLC: LibVLC? = null
    private var mediaPlayer: MediaPlayer? = null

    private val mainHandler = Handler(Looper.getMainLooper())
    private var controlsVisible = true
    private var isLocked = false
    private var isUserSeeking = false
    private var knownDurationMs = 0L

    private val speedSteps = floatArrayOf(0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 2.0f)
    private var speedIndex = 2 // 1.0x

    // null = best-fit (libVLC default), then a couple of fixed ratios
    private val aspectRatios = arrayOf<String?>(null, "16:9", "4:3", "1:1")
    private val aspectLabels = arrayOf("Fit", "16:9", "4:3", "1:1")
    private var aspectIndex = 0

    private lateinit var gestureDetector: GestureDetector
    private lateinit var audioManager: AudioManager

    // Drag-gesture state
    private var dragMode = DragMode.NONE
    private var dragStartY = 0f
    private var dragStartX = 0f
    private var dragStartVolume = 0
    private var dragStartBrightness = 0f
    private var maxVolume = 1

    private enum class DragMode { NONE, BRIGHTNESS, VOLUME }

    companion object {
        const val EXTRA_VIDEO_URI = "extra_video_uri"
        const val EXTRA_VIDEO_TITLE = "extra_video_title"
        private const val CONTROLS_TIMEOUT_MS = 3500L
        private const val PROGRESS_UPDATE_MS = 400L
        private const val SEEK_STEP_MS = 10_000L
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        binding = ActivityPlayerBinding.inflate(layoutInflater)
        setContentView(binding.root)

        audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        maxVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)

        binding.textVideoTitle.text = resolveTitle()

        setupGestures()
        setupClickListeners()
        setupSeekBar()

        enterImmersiveMode()
        scheduleAutoHideControls()
    }

    override fun onStart() {
        super.onStart()
        initializePlayer()
    }

    override fun onStop() {
        super.onStop()
        mainHandler.removeCallbacksAndMessages(null)
        releasePlayer()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) enterImmersiveMode()
    }

    // ---------------------------------------------------------------------
    // Player setup (libVLC)
    // ---------------------------------------------------------------------

    /** Works whether we launched this screen ourselves, or another app (file manager,
     *  browser, WhatsApp, etc.) opened/shared a video - or a direct link - with us. */
    private fun resolveVideoUri(): Uri? {
        intent.getStringExtra(EXTRA_VIDEO_URI)?.let {
            return if (it.contains("://")) Uri.parse(it) else Uri.parse(it)
        }

        return when (intent.action) {
            Intent.ACTION_VIEW -> intent.data
            Intent.ACTION_SEND -> {
                @Suppress("DEPRECATION")
                intent.getParcelableExtra(Intent.EXTRA_STREAM) as? Uri
            }
            else -> null
        }
    }

    private fun resolveTitle(): String {
        intent.getStringExtra(EXTRA_VIDEO_TITLE)?.let { return it }
        val uri = resolveVideoUri()
        return uri?.lastPathSegment?.substringAfterLast('/') ?: getString(R.string.app_name)
    }

    private fun initializePlayer() {
        val uri = resolveVideoUri() ?: return

        // Minimal, safe options - lets libVLC auto-detect everything about the stream,
        // same way the VLC app itself does.
        val vlc = LibVLC(this, arrayListOf("--no-drop-late-frames", "--no-skip-frames"))
        val vlcMediaPlayer = MediaPlayer(vlc)
        vlcMediaPlayer.attachViews(binding.playerView, null, false, false)

        val media = Media(vlc, uri)
        media.setHWDecoderEnabled(true, false)
        vlcMediaPlayer.media = media
        media.release()

        vlcMediaPlayer.setEventListener { event ->
            when (event.type) {
                MediaPlayer.Event.Opening, MediaPlayer.Event.Buffering -> {
                    runOnUiThread {
                        val stillBuffering = event.type != MediaPlayer.Event.Buffering || event.buffering < 100f
                        binding.bufferingSpinner.visibility = if (stillBuffering) View.VISIBLE else View.GONE
                    }
                }
                MediaPlayer.Event.Playing -> runOnUiThread {
                    binding.bufferingSpinner.visibility = View.GONE
                    updatePlayPauseIcon(true)
                }
                MediaPlayer.Event.Paused, MediaPlayer.Event.Stopped -> runOnUiThread {
                    updatePlayPauseIcon(false)
                }
                MediaPlayer.Event.EndReached -> runOnUiThread {
                    updatePlayPauseIcon(false)
                }
                MediaPlayer.Event.EncounteredError -> runOnUiThread {
                    binding.bufferingSpinner.visibility = View.GONE
                }
                MediaPlayer.Event.LengthChanged -> runOnUiThread {
                    knownDurationMs = event.lengthChanged.coerceAtLeast(0)
                    binding.textTotalTime.text = formatTime(knownDurationMs)
                }
                else -> {}
            }
        }

        vlcMediaPlayer.play()

        libVLC = vlc
        mediaPlayer = vlcMediaPlayer
        startProgressUpdates()
    }

    private fun releasePlayer() {
        mediaPlayer?.let {
            it.stop()
            it.detachViews()
            it.release()
        }
        mediaPlayer = null
        libVLC?.release()
        libVLC = null
    }

    // ---------------------------------------------------------------------
    // Immersive edge-to-edge mode
    // ---------------------------------------------------------------------

    private fun enterImmersiveMode() {
        val controller = WindowInsetsControllerCompat(window, binding.root)
        controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        if (controlsVisible) {
            controller.show(WindowInsetsCompat.Type.systemBars())
        } else {
            controller.hide(WindowInsetsCompat.Type.systemBars())
        }
    }

    // ---------------------------------------------------------------------
    // Controls visibility
    // ---------------------------------------------------------------------

    private fun toggleControls() {
        if (isLocked) return
        if (controlsVisible) hideControls() else showControls()
    }

    private fun showControls() {
        controlsVisible = true
        binding.controlsContainer.animate().alpha(1f).setDuration(150).withStartAction {
            binding.controlsContainer.visibility = View.VISIBLE
        }.start()
        enterImmersiveMode()
        scheduleAutoHideControls()
    }

    private fun hideControls() {
        controlsVisible = false
        binding.controlsContainer.animate().alpha(0f).setDuration(150).withEndAction {
            binding.controlsContainer.visibility = View.INVISIBLE
        }.start()
        enterImmersiveMode()
        mainHandler.removeCallbacks(autoHideRunnable)
    }

    private val autoHideRunnable = Runnable {
        if (mediaPlayer?.isPlaying == true) hideControls()
    }

    private fun scheduleAutoHideControls() {
        mainHandler.removeCallbacks(autoHideRunnable)
        mainHandler.postDelayed(autoHideRunnable, CONTROLS_TIMEOUT_MS)
    }

    // ---------------------------------------------------------------------
    // Click listeners
    // ---------------------------------------------------------------------

    private fun setupClickListeners() {
        binding.btnBack.setOnClickListener { finish() }

        binding.btnPlayPause.setOnClickListener { togglePlayPause() }

        binding.btnRewind.setOnClickListener {
            seekBy(-SEEK_STEP_MS)
            scheduleAutoHideControls()
        }
        binding.btnForward.setOnClickListener {
            seekBy(SEEK_STEP_MS)
            scheduleAutoHideControls()
        }

        binding.btnSpeed.setOnClickListener {
            speedIndex = (speedIndex + 1) % speedSteps.size
            val speed = speedSteps[speedIndex]
            mediaPlayer?.setRate(speed)
            binding.btnSpeed.text = formatSpeedLabel(speed)
            scheduleAutoHideControls()
        }

        binding.btnAspectRatio.setOnClickListener {
            aspectIndex = (aspectIndex + 1) % aspectRatios.size
            mediaPlayer?.setAspectRatio(aspectRatios[aspectIndex])
            mediaPlayer?.setScale(0f)
            scheduleAutoHideControls()
        }

        binding.btnLock.setOnClickListener { setLocked(true) }
        binding.btnUnlock.setOnClickListener { setLocked(false) }
    }

    private fun setLocked(locked: Boolean) {
        isLocked = locked
        if (locked) {
            binding.controlsContainer.visibility = View.INVISIBLE
            binding.lockOverlay.visibility = View.VISIBLE
            mainHandler.removeCallbacks(autoHideRunnable)
        } else {
            binding.lockOverlay.visibility = View.GONE
            showControls()
        }
    }

    private fun togglePlayPause() {
        val player = mediaPlayer ?: return
        if (player.isPlaying) player.pause() else player.play()
        scheduleAutoHideControls()
    }

    private fun updatePlayPauseIcon(isPlaying: Boolean) {
        binding.iconPlayPause.setImageResource(if (isPlaying) R.drawable.ic_pause else R.drawable.ic_play)
    }

    private fun seekBy(deltaMs: Long) {
        val player = mediaPlayer ?: return
        val duration = if (knownDurationMs > 0) knownDurationMs else player.length.coerceAtLeast(0)
        val newPosition = (player.time + deltaMs).coerceIn(0, duration)
        player.time = newPosition
    }

    // ---------------------------------------------------------------------
    // Seek bar + progress
    // ---------------------------------------------------------------------

    private fun setupSeekBar() {
        binding.seekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) {
                if (fromUser) {
                    val duration = currentDuration()
                    val position = duration * progress / seekBar.max
                    binding.textCurrentTime.text = formatTime(position)
                }
            }

            override fun onStartTrackingTouch(seekBar: SeekBar) {
                isUserSeeking = true
                mainHandler.removeCallbacks(autoHideRunnable)
            }

            override fun onStopTrackingTouch(seekBar: SeekBar) {
                val duration = currentDuration()
                val position = duration * seekBar.progress / seekBar.max
                mediaPlayer?.time = position
                isUserSeeking = false
                scheduleAutoHideControls()
            }
        })
    }

    private fun currentDuration(): Long {
        val player = mediaPlayer ?: return knownDurationMs
        return if (knownDurationMs > 0) knownDurationMs else player.length.coerceAtLeast(0)
    }

    private val progressRunnable = object : Runnable {
        override fun run() {
            val player = mediaPlayer
            if (player != null && !isUserSeeking) {
                val duration = currentDuration()
                val position = player.time.coerceIn(0, if (duration > 0) duration else Long.MAX_VALUE)
                if (duration > 0) {
                    binding.seekBar.progress = (position * binding.seekBar.max / duration).toInt()
                }
                binding.textCurrentTime.text = formatTime(position)
                if (duration > 0) binding.textTotalTime.text = formatTime(duration)
            }
            mainHandler.postDelayed(this, PROGRESS_UPDATE_MS)
        }
    }

    private fun startProgressUpdates() {
        mainHandler.removeCallbacks(progressRunnable)
        mainHandler.post(progressRunnable)
    }

    private fun formatSpeedLabel(speed: Float): String {
        val rounded = Math.round(speed * 100) / 100f
        val text = if (rounded == rounded.toLong().toFloat()) {
            rounded.toLong().toString()
        } else {
            rounded.toString().trimEnd('0').trimEnd('.')
        }
        return "${text}x"
    }

    private fun formatTime(ms: Long): String {
        val totalSeconds = TimeUnit.MILLISECONDS.toSeconds(ms.coerceAtLeast(0))
        val hours = totalSeconds / 3600
        val minutes = (totalSeconds % 3600) / 60
        val seconds = totalSeconds % 60
        return if (hours > 0) {
            String.format(Locale.US, "%d:%02d:%02d", hours, minutes, seconds)
        } else {
            String.format(Locale.US, "%02d:%02d", minutes, seconds)
        }
    }

    // ---------------------------------------------------------------------
    // Gestures: single tap toggles controls, double tap seeks, vertical drag
    // adjusts brightness (left half) or volume (right half)
    // ---------------------------------------------------------------------

    private fun setupGestures() {
        gestureDetector = GestureDetector(this, object : GestureDetector.SimpleOnGestureListener() {
            override fun onSingleTapConfirmed(e: MotionEvent): Boolean {
                if (!isLocked) toggleControls()
                return true
            }

            override fun onDoubleTap(e: MotionEvent): Boolean {
                if (isLocked) return true
                val screenWidth = binding.root.width
                if (e.x < screenWidth / 2f) {
                    seekBy(-SEEK_STEP_MS)
                    showSeekFeedback(isForward = false)
                } else {
                    seekBy(SEEK_STEP_MS)
                    showSeekFeedback(isForward = true)
                }
                return true
            }
        })

        binding.gestureOverlay.setOnTouchListener { _, event ->
            gestureDetector.onTouchEvent(event)
            if (!isLocked) handleDragGesture(event)
            true
        }
    }

    private fun handleDragGesture(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                dragStartX = event.x
                dragStartY = event.y
                dragMode = DragMode.NONE
            }

            MotionEvent.ACTION_MOVE -> {
                val deltaX = event.x - dragStartX
                val deltaY = event.y - dragStartY

                if (dragMode == DragMode.NONE && abs(deltaY) > 40 && abs(deltaY) > abs(deltaX)) {
                    dragMode = if (dragStartX < binding.root.width / 2f) {
                        dragStartBrightness = getCurrentBrightness()
                        DragMode.BRIGHTNESS
                    } else {
                        dragStartVolume = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
                        DragMode.VOLUME
                    }
                }

                when (dragMode) {
                    DragMode.BRIGHTNESS -> adjustBrightness(deltaY)
                    DragMode.VOLUME -> adjustVolume(deltaY)
                    DragMode.NONE -> {}
                }
            }

            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                if (dragMode != DragMode.NONE) hideGestureIndicatorDelayed()
                dragMode = DragMode.NONE
            }
        }
        return true
    }

    private fun getCurrentBrightness(): Float {
        val current = window.attributes.screenBrightness
        return if (current in 0f..1f) current else 0.5f
    }

    private fun adjustBrightness(deltaY: Float) {
        val range = binding.root.height.toFloat()
        val change = -deltaY / range
        val newBrightness = (dragStartBrightness + change).coerceIn(0.02f, 1f)

        val layoutParams = window.attributes
        layoutParams.screenBrightness = newBrightness
        window.attributes = layoutParams

        showGestureIndicator(
            iconRes = R.drawable.ic_brightness,
            percent = (newBrightness * 100).toInt()
        )
    }

    private fun adjustVolume(deltaY: Float) {
        val range = binding.root.height.toFloat()
        val change = -deltaY / range * maxVolume
        val newVolume = (dragStartVolume + change).toInt().coerceIn(0, maxVolume)

        audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, newVolume, 0)

        val percent = if (maxVolume > 0) (newVolume * 100 / maxVolume) else 0
        showGestureIndicator(
            iconRes = if (newVolume == 0) R.drawable.ic_volume_mute else R.drawable.ic_volume,
            percent = percent
        )
    }

    private fun showGestureIndicator(iconRes: Int, percent: Int) {
        binding.iconGesture.setImageResource(iconRes)
        binding.progressGesture.progress = percent.coerceIn(0, 100)
        if (binding.gestureIndicator.visibility != View.VISIBLE) {
            binding.gestureIndicator.visibility = View.VISIBLE
            binding.gestureIndicator.animate().alpha(1f).setDuration(100).start()
        }
    }

    private fun hideGestureIndicatorDelayed() {
        binding.gestureIndicator.animate().alpha(0f).setDuration(200).setStartDelay(300).withEndAction {
            binding.gestureIndicator.visibility = View.INVISIBLE
        }.start()
    }

    private fun showSeekFeedback(isForward: Boolean) {
        val target = if (isForward) binding.seekFeedbackRight else binding.seekFeedbackLeft
        target.visibility = View.VISIBLE
        target.animate().cancel()
        target.alpha = 1f
        target.animate().alpha(0f).setDuration(500).setStartDelay(300).withEndAction {
            target.visibility = View.INVISIBLE
        }.start()
    }
}

package com.business.musicapp

import android.Manifest
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.SeekBar
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import com.business.musicapp.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var trackAdapter: TrackAdapter

    private var mediaPlayer: MediaPlayer? = null
    private var allTracks: List<Track> = emptyList()
    private var displayedTracks: List<Track> = emptyList()
    private var currentTrackIndex: Int = -1
    private var isPrepared: Boolean = false
    private var isLooping: Boolean = false

    private val progressHandler = Handler(Looper.getMainLooper())
    private val progressRunnable = object : Runnable {
        override fun run() {
            mediaPlayer?.let { player ->
                if (isPrepared && player.isPlaying) {
                    val pos = player.currentPosition
                    binding.seekBar.progress = pos
                    binding.tvCurrentTime.text = TrackRepository.formatDuration(pos.toLong())
                }
            }
            progressHandler.postDelayed(this, 500)
        }
    }

    private val audioPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            loadAllTracks(includeDeviceAudio = true)
            Toast.makeText(this, "Device music loaded", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(this, "Showing Business Stream playlists", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupRecyclerView()
        setupControls()
        setupSearch()
        loadAllTracks(includeDeviceAudio = hasAudioPermission())
    }

    private fun setupRecyclerView() {
        trackAdapter = TrackAdapter { _, position ->
            playTrackAtIndex(position)
        }
        binding.rvTracks.apply {
            layoutManager = LinearLayoutManager(this@MainActivity)
            adapter = trackAdapter
        }
    }

    private fun setupControls() {
        binding.btnPlayPause.setOnClickListener {
            togglePlayPause()
        }

        binding.btnNext.setOnClickListener {
            playNext()
        }

        binding.btnPrev.setOnClickListener {
            playPrevious()
        }

        binding.btnRepeat.setOnClickListener {
            isLooping = !isLooping
            mediaPlayer?.isLooping = isLooping
            val tint = if (isLooping) R.color.accent_primary else R.color.text_secondary
            binding.btnRepeat.setColorFilter(ContextCompat.getColor(this, tint))
            val msg = if (isLooping) "Repeat: ON" else "Repeat: OFF"
            Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
        }

        binding.btnLoadDeviceMusic.setOnClickListener {
            requestDeviceAudioIfNeeded()
        }

        binding.seekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser && isPrepared) {
                    binding.tvCurrentTime.text = TrackRepository.formatDuration(progress.toLong())
                }
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) {}

            override fun onStopTrackingTouch(seekBar: SeekBar?) {
                if (isPrepared) {
                    seekBar?.progress?.let { mediaPlayer?.seekTo(it) }
                }
            }
        })
    }

    private fun setupSearch() {
        binding.etSearch.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                filterTracks(s?.toString().orEmpty())
            }
            override fun afterTextChanged(s: Editable?) {}
        })
    }

    private fun loadAllTracks(includeDeviceAudio: Boolean) {
        val combined = mutableListOf<Track>()
        combined.addAll(TrackRepository.getBusinessTracks())
        if (includeDeviceAudio) {
            combined.addAll(TrackRepository.getDeviceAudioTracks(this))
        }
        allTracks = combined
        filterTracks(binding.etSearch.text?.toString().orEmpty())
    }

    private fun filterTracks(query: String) {
        val q = query.trim().lowercase()
        displayedTracks = if (q.isEmpty()) {
            allTracks
        } else {
            allTracks.filter {
                it.title.lowercase().contains(q) ||
                it.artist.lowercase().contains(q) ||
                it.category.lowercase().contains(q)
            }
        }
        trackAdapter.submitList(displayedTracks)
        binding.tvTrackCount.text = "${displayedTracks.size} tracks available"
    }

    private fun playTrackAtIndex(index: Int) {
        if (displayedTracks.isEmpty() || index !in displayedTracks.indices) return

        currentTrackIndex = index
        val track = displayedTracks[index]

        trackAdapter.setActiveTrack(track.id)
        binding.tvNowPlayingTitle.text = track.title
        binding.tvNowPlayingArtist.text = "${track.artist} • ${track.category}"
        binding.tvTotalTime.text = track.durationLabel
        binding.tvCurrentTime.text = "00:00"
        binding.seekBar.progress = 0
        binding.progressBuffering.visibility = View.VISIBLE
        binding.btnPlayPause.setImageResource(R.drawable.ic_pause)

        releasePlayer()

        try {
            val player = MediaPlayer().apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .build()
                )
                if (track.isLocal) {
                    setDataSource(this@MainActivity, Uri.parse(track.sourceUrl))
                } else {
                    setDataSource(track.sourceUrl)
                }
                isLooping = this@MainActivity.isLooping

                setOnPreparedListener { mp ->
                    isPrepared = true
                    binding.progressBuffering.visibility = View.GONE
                    binding.seekBar.max = mp.duration
                    binding.tvTotalTime.text = TrackRepository.formatDuration(mp.duration.toLong())
                    mp.start()
                    binding.btnPlayPause.setImageResource(R.drawable.ic_pause)
                    progressHandler.post(progressRunnable)
                }

                setOnCompletionListener {
                    if (!isLooping) {
                        playNext()
                    }
                }

                setOnErrorListener { _, _, _ ->
                    isPrepared = false
                    binding.progressBuffering.visibility = View.GONE
                    binding.btnPlayPause.setImageResource(R.drawable.ic_play)
                    Toast.makeText(
                        this@MainActivity,
                        "Unable to play track. Check internet connection.",
                        Toast.LENGTH_SHORT
                    ).show()
                    true
                }

                prepareAsync()
            }
            mediaPlayer = player
        } catch (e: Exception) {
            binding.progressBuffering.visibility = View.GONE
            Toast.makeText(this, "Playback error: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun togglePlayPause() {
        val player = mediaPlayer
        if (player == null || !isPrepared) {
            if (displayedTracks.isNotEmpty()) {
                playTrackAtIndex(if (currentTrackIndex >= 0) currentTrackIndex else 0)
            }
            return
        }

        if (player.isPlaying) {
            player.pause()
            binding.btnPlayPause.setImageResource(R.drawable.ic_play)
        } else {
            player.start()
            binding.btnPlayPause.setImageResource(R.drawable.ic_pause)
            progressHandler.post(progressRunnable)
        }
    }

    private fun playNext() {
        if (displayedTracks.isEmpty()) return
        val nextIndex = if (currentTrackIndex + 1 < displayedTracks.size) {
            currentTrackIndex + 1
        } else {
            0
        }
        playTrackAtIndex(nextIndex)
    }

    private fun playPrevious() {
        if (displayedTracks.isEmpty()) return
        val prevIndex = if (currentTrackIndex - 1 >= 0) {
            currentTrackIndex - 1
        } else {
            displayedTracks.size - 1
        }
        playTrackAtIndex(prevIndex)
    }

    private fun hasAudioPermission(): Boolean {
        val permission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Manifest.permission.READ_MEDIA_AUDIO
        } else {
            Manifest.permission.READ_EXTERNAL_STORAGE
        }
        return ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED
    }

    private fun requestDeviceAudioIfNeeded() {
        val permission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Manifest.permission.READ_MEDIA_AUDIO
        } else {
            Manifest.permission.READ_EXTERNAL_STORAGE
        }
        if (hasAudioPermission()) {
            loadAllTracks(includeDeviceAudio = true)
            Toast.makeText(this, "Device library refreshed", Toast.LENGTH_SHORT).show()
        } else {
            audioPermissionLauncher.launch(permission)
        }
    }

    private fun releasePlayer() {
        progressHandler.removeCallbacks(progressRunnable)
        isPrepared = false
        try {
            mediaPlayer?.stop()
        } catch (_: Exception) {
        }
        mediaPlayer?.release()
        mediaPlayer = null
    }

    override fun onDestroy() {
        super.onDestroy()
        releasePlayer()
    }
}

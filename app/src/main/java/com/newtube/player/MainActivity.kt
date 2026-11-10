package com.newtube.player

import android.content.pm.ActivityInfo
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.annotation.OptIn
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.dash.DashMediaSource
import androidx.media3.exoplayer.hls.HlsMediaSource
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.source.MergingMediaSource
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import androidx.recyclerview.widget.LinearLayoutManager
import coil.load
import com.newtube.player.databinding.ActivityMainBinding
import kotlinx.coroutines.launch

@OptIn(UnstableApi::class)
class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var feedAdapter: VideoFeedAdapter
    private lateinit var relatedAdapter: VideoFeedAdapter
    private var exoPlayer: ExoPlayer? = null
    private lateinit var okHttpDataSourceFactory: OkHttpDataSource.Factory

    private var isFullscreen = false
    private val progressHandler = Handler(Looper.getMainLooper())

    private val watchHistory = mutableListOf<VideoItem>()
    private val subscribedChannels = mutableSetOf<String>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupExoPlayer()
        setupRecyclerViews()
        setupBottomNavigation()
        setupSearchAndMiniPlayerControls()
        setupBackNavigation()

        // Load initial Home feed
        loadCategoryFeed("Trending India music gaming tech")
    }

    private fun setupExoPlayer() {
        // Use OkHttpDataSource with proper User-Agent so googlevideo.com CDN never blocks stream playback (HTTP 403 fix)
        okHttpDataSourceFactory = OkHttpDataSource.Factory(NewPipeOkHttpDownloader.instance.client)
            .setUserAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:128.0) Gecko/20100101 Firefox/128.0")

        val mediaSourceFactory = DefaultMediaSourceFactory(this)
            .setDataSourceFactory(okHttpDataSourceFactory)

        exoPlayer = ExoPlayer.Builder(this)
            .setMediaSourceFactory(mediaSourceFactory)
            .build()
            .also { player ->
                binding.exoPlayerView.player = player
                player.addListener(object : Player.Listener {
                    override fun onIsPlayingChanged(isPlaying: Boolean) {
                        val iconRes = if (isPlaying) R.drawable.ic_pause else R.drawable.ic_play
                        binding.miniPlayerPlayPause.setImageResource(iconRes)
                    }

                    override fun onPlayerError(error: PlaybackException) {
                        Toast.makeText(
                            this@MainActivity,
                            "Playback error: ${error.errorCodeName}",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                })
            }

        val btnMinimize = binding.exoPlayerView.findViewById<ImageButton>(R.id.btn_minimize_player)
        val btnFullscreen = binding.exoPlayerView.findViewById<ImageButton>(R.id.btn_fullscreen)

        btnMinimize?.setOnClickListener {
            minimizeToBottomMiniPlayer()
        }

        btnFullscreen?.setOnClickListener {
            toggleFullscreen()
        }

        progressHandler.post(object : Runnable {
            override fun run() {
                exoPlayer?.let { player ->
                    if (player.duration > 0) {
                        val progress = ((player.currentPosition * 100) / player.duration).toInt()
                        binding.miniPlayerProgress.progress = progress
                    }
                }
                progressHandler.postDelayed(this, 1000)
            }
        })
    }

    private fun setupRecyclerViews() {
        feedAdapter = VideoFeedAdapter { video ->
            playVideo(video)
        }
        relatedAdapter = VideoFeedAdapter { video ->
            playVideo(video)
        }

        binding.recyclerFeed.layoutManager = LinearLayoutManager(this)
        binding.recyclerFeed.adapter = feedAdapter

        binding.recyclerRelated.layoutManager = LinearLayoutManager(this)
        binding.recyclerRelated.adapter = relatedAdapter

        binding.swipeRefresh.setColorSchemeResources(R.color.yt_red)
        binding.swipeRefresh.setOnRefreshListener {
            loadCategoryFeed("Trending India videos")
        }
    }

    private fun setupBottomNavigation() {
        binding.bottomNavigation.setOnItemSelectedListener { item ->
            if (binding.expandedPlayerSheet.visibility == View.VISIBLE) {
                minimizeToBottomMiniPlayer()
            }

            when (item.itemId) {
                R.id.nav_home -> {
                    loadCategoryFeed("Trending India videos")
                    true
                }
                R.id.nav_shorts -> {
                    loadCategoryFeed("#shorts viral india")
                    true
                }
                R.id.nav_subscriptions -> {
                    val query = if (subscribedChannels.isNotEmpty()) {
                        subscribedChannels.joinToString(" ")
                    } else {
                        "Popular Indian Creators latest videos"
                    }
                    loadCategoryFeed(query)
                    true
                }
                R.id.nav_library -> {
                    if (watchHistory.isNotEmpty()) {
                        binding.feedLoadingSpinner.visibility = View.GONE
                        feedAdapter.submitList(watchHistory.toList())
                        Toast.makeText(this, "Watch History (${watchHistory.size} videos)", Toast.LENGTH_SHORT).show()
                    } else {
                        loadCategoryFeed("Hindi songs lofi mix")
                    }
                    true
                }
                else -> false
            }
        }
    }

    private fun setupSearchAndMiniPlayerControls() {
        binding.btnSearch.setOnClickListener {
            val q = binding.editSearch.text.toString().trim()
            if (q.isNotEmpty()) {
                if (binding.expandedPlayerSheet.visibility == View.VISIBLE) {
                    minimizeToBottomMiniPlayer()
                }
                loadCategoryFeed(q)
            }
        }

        binding.editSearch.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                binding.btnSearch.performClick()
                true
            } else false
        }

        binding.miniPlayerBar.setOnClickListener {
            expandFromMiniPlayer()
        }

        binding.miniPlayerPlayPause.setOnClickListener {
            exoPlayer?.let { player ->
                if (player.isPlaying) player.pause() else player.play()
            }
        }

        binding.miniPlayerClose.setOnClickListener {
            exoPlayer?.stop()
            exoPlayer?.clearMediaItems()
            binding.miniPlayerBar.visibility = View.GONE
        }

        binding.btnSubscribe.setOnClickListener {
            val channel = binding.expandedChannelName.text.toString()
            if (subscribedChannels.contains(channel)) {
                subscribedChannels.remove(channel)
                binding.btnSubscribe.text = "Subscribe"
            } else {
                subscribedChannels.add(channel)
                binding.btnSubscribe.text = "Subscribed"
                Toast.makeText(this, "Subscribed to $channel", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun loadCategoryFeed(query: String) {
        binding.feedLoadingSpinner.visibility = View.VISIBLE
        lifecycleScope.launch {
            val videos = NewPipeRepository.fetchVideos(query)
            binding.feedLoadingSpinner.visibility = View.GONE
            binding.swipeRefresh.isRefreshing = false
            if (videos.isNotEmpty()) {
                feedAdapter.submitList(videos)
            } else {
                Toast.makeText(this@MainActivity, "Could not load feed. Check internet connection.", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun playVideo(video: VideoItem) {
        watchHistory.removeAll { it.videoUrl == video.videoUrl }
        watchHistory.add(0, video)

        binding.miniPlayerBar.visibility = View.GONE
        binding.expandedPlayerSheet.visibility = View.VISIBLE

        binding.expandedVideoTitle.text = video.title
        binding.expandedChannelName.text = video.uploaderName
        binding.expandedVideoMeta.text = "Extracting stream..."
        binding.exoPlayerView.findViewById<TextView>(R.id.player_overlay_title)?.text = video.title

        binding.miniPlayerTitle.text = video.title
        binding.miniPlayerChannel.text = video.uploaderName
        binding.miniPlayerThumb.load(video.thumbnailUrl)

        exoPlayer?.stop()

        lifecycleScope.launch {
            when (val result = NewPipeRepository.extractStreamDetails(video.videoUrl)) {
                is StreamExtractionResult.Success -> {
                    val details = result.data
                    binding.expandedVideoTitle.text = details.title
                    binding.expandedChannelName.text = details.uploaderName
                    binding.expandedVideoMeta.text = details.viewsAndDate
                    relatedAdapter.submitList(details.relatedItems)

                    startExoPlayerStream(details)
                }
                is StreamExtractionResult.Error -> {
                    binding.expandedVideoMeta.text = "Error: ${result.message}"
                    Toast.makeText(
                        this@MainActivity,
                        "Stream Error: ${result.message}",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        }
    }

    private fun startExoPlayerStream(details: PlayedVideoStream) {
        val player = exoPlayer ?: return
        val videoUri = Uri.parse(details.videoStreamUrl)

        val mediaSource = when {
            details.isHls -> {
                HlsMediaSource.Factory(okHttpDataSourceFactory)
                    .createMediaSource(MediaItem.fromUri(videoUri))
            }
            details.isDashManifest -> {
                DashMediaSource.Factory(okHttpDataSourceFactory)
                    .createMediaSource(MediaItem.fromUri(videoUri))
            }
            !details.audioStreamUrl.isNullOrEmpty() -> {
                // Merge Adaptive Video-Only stream + Audio-Only stream seamlessly
                val videoSource = ProgressiveMediaSource.Factory(okHttpDataSourceFactory)
                    .createMediaSource(MediaItem.fromUri(videoUri))
                val audioUri = Uri.parse(details.audioStreamUrl)
                val audioSource = ProgressiveMediaSource.Factory(okHttpDataSourceFactory)
                    .createMediaSource(MediaItem.fromUri(audioUri))
                MergingMediaSource(videoSource, audioSource)
            }
            else -> {
                ProgressiveMediaSource.Factory(okHttpDataSourceFactory)
                    .createMediaSource(MediaItem.fromUri(videoUri))
            }
        }

        player.setMediaSource(mediaSource)
        player.prepare()
        player.playWhenReady = true
    }

    private fun minimizeToBottomMiniPlayer() {
        if (isFullscreen) {
            toggleFullscreen()
        }
        binding.expandedPlayerSheet.visibility = View.GONE
        binding.miniPlayerBar.visibility = View.VISIBLE
    }

    private fun expandFromMiniPlayer() {
        binding.miniPlayerBar.visibility = View.GONE
        binding.expandedPlayerSheet.visibility = View.VISIBLE
    }

    private fun toggleFullscreen() {
        isFullscreen = !isFullscreen
        if (isFullscreen) {
            requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
            binding.bottomNavigation.visibility = View.GONE
            binding.bottomNavDivider.visibility = View.GONE
            val params = binding.playerSurfaceContainer.layoutParams
            params.height = android.view.ViewGroup.LayoutParams.MATCH_PARENT
            binding.playerSurfaceContainer.layoutParams = params
        } else {
            requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
            binding.bottomNavigation.visibility = View.VISIBLE
            binding.bottomNavDivider.visibility = View.VISIBLE
            val density = resources.displayMetrics.density
            val params = binding.playerSurfaceContainer.layoutParams
            params.height = (225 * density).toInt()
            binding.playerSurfaceContainer.layoutParams = params
        }
    }

    private fun setupBackNavigation() {
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                when {
                    isFullscreen -> toggleFullscreen()
                    binding.expandedPlayerSheet.visibility == View.VISIBLE -> minimizeToBottomMiniPlayer()
                    else -> {
                        isEnabled = false
                        onBackPressedDispatcher.onBackPressed()
                    }
                }
            }
        })
    }

    override fun onDestroy() {
        super.onDestroy()
        progressHandler.removeCallbacksAndMessages(null)
        exoPlayer?.release()
        exoPlayer = null
    }
}

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
import androidx.appcompat.app.AlertDialog
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
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import coil.load
import com.newtube.player.databinding.ActivityMainBinding
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/** Bottom tabs aur search ke liye feed ka mode */
private enum class FeedMode { HOME, SHORTS, SUBSCRIPTIONS, LIBRARY, SEARCH }

@OptIn(UnstableApi::class)
class MainActivity : AppCompatActivity() {

    companion object {
        private const val TRENDING_QUERY = "Trending India music gaming tech"
        private const val SHORTS_QUERY = "#shorts viral india"
    }

    private lateinit var binding: ActivityMainBinding
    private lateinit var feedAdapter: VideoFeedAdapter
    private lateinit var shortsAdapter: ShortFeedAdapter
    private lateinit var relatedAdapter: VideoFeedAdapter
    private var exoPlayer: ExoPlayer? = null
    private lateinit var okHttpDataSourceFactory: OkHttpDataSource.Factory

    private var isFullscreen = false
    private val progressHandler = Handler(Looper.getMainLooper())

    private var feedMode = FeedMode.HOME
    private var feedJob: Job? = null
    private var searchQuery = ""

    /** Abhi khule video ka channel (Subscribe button ke liye) */
    private var currentChannel: Subscription? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupExoPlayer()
        setupRecyclerViews()
        setupBottomNavigation()
        setupSearchAndMiniPlayerControls()
        setupSectionHeader()
        setupBackNavigation()

        loadFeed(FeedMode.HOME)
    }

    private fun setupExoPlayer() {
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

        btnMinimize?.setOnClickListener { minimizeToBottomMiniPlayer() }
        btnFullscreen?.setOnClickListener { toggleFullscreen() }

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
        feedAdapter = VideoFeedAdapter { video -> playVideo(video) }
        shortsAdapter = ShortFeedAdapter { video -> playVideo(video) }
        relatedAdapter = VideoFeedAdapter { video -> playVideo(video) }

        binding.recyclerFeed.layoutManager = LinearLayoutManager(this)
        binding.recyclerFeed.adapter = feedAdapter

        binding.recyclerRelated.layoutManager = LinearLayoutManager(this)
        binding.recyclerRelated.adapter = relatedAdapter

        binding.swipeRefresh.setColorSchemeResources(R.color.yt_red)
        binding.swipeRefresh.setOnRefreshListener {
            loadFeed(feedMode, searchQuery)
        }
    }

    private fun setupBottomNavigation() {
        binding.bottomNavigation.setOnItemSelectedListener { item ->
            if (binding.expandedPlayerSheet.visibility == View.VISIBLE) {
                minimizeToBottomMiniPlayer()
            }
            when (item.itemId) {
                R.id.nav_home -> loadFeed(FeedMode.HOME)
                R.id.nav_shorts -> loadFeed(FeedMode.SHORTS)
                R.id.nav_subscriptions -> loadFeed(FeedMode.SUBSCRIPTIONS)
                R.id.nav_library -> loadFeed(FeedMode.LIBRARY)
                else -> return@setOnItemSelectedListener false
            }
            true
        }
    }

    private fun setupSearchAndMiniPlayerControls() {
        binding.btnSearch.setOnClickListener {
            val q = binding.editSearch.text.toString().trim()
            if (q.isNotEmpty()) {
                if (binding.expandedPlayerSheet.visibility == View.VISIBLE) {
                    minimizeToBottomMiniPlayer()
                }
                loadFeed(FeedMode.SEARCH, q)
            }
        }

        binding.editSearch.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                binding.btnSearch.performClick()
                true
            } else false
        }

        binding.miniPlayerBar.setOnClickListener { expandFromMiniPlayer() }

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

        // Subscribe / Unsubscribe ab SharedPreferences mein save hota hai
        binding.btnSubscribe.setOnClickListener {
            val channel = currentChannel ?: return@setOnClickListener
            val nowSubscribed = LocalStore.toggleSubscription(channel)
            updateSubscribeButton()
            val msg = if (nowSubscribed) {
                getString(R.string.toast_subscribed, channel.name)
            } else {
                getString(R.string.toast_unsubscribed, channel.name)
            }
            Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
        }
    }

    private fun setupSectionHeader() {
        binding.btnSectionAction.setOnClickListener {
            when (feedMode) {
                FeedMode.SUBSCRIPTIONS -> showManageSubscriptionsDialog()
                FeedMode.LIBRARY -> {
                    LocalStore.clearHistory()
                    Toast.makeText(this, getString(R.string.toast_history_cleared), Toast.LENGTH_SHORT).show()
                    loadFeed(FeedMode.LIBRARY)
                }
                else -> Unit
            }
        }
    }

    /**
     * Feed load karta hai. Har tab ke liye alag data source hai:
     * Home = trending, Shorts = short videos grid, Subscriptions = channels ki latest videos,
     * Library = local watch history, Search = search results.
     */
    private fun loadFeed(mode: FeedMode, query: String = "") {
        feedMode = mode
        if (mode == FeedMode.SEARCH) searchQuery = query
        feedJob?.cancel()
        updateSectionHeader()
        setEmptyState(null)

        when (mode) {
            FeedMode.LIBRARY -> {
                binding.feedLoadingSpinner.visibility = View.GONE
                binding.swipeRefresh.isRefreshing = false
                showList(shorts = false)
                val history = LocalStore.getHistory()
                feedAdapter.submitList(history)
                if (history.isEmpty()) setEmptyState(getString(R.string.empty_history))
                return
            }
            FeedMode.SUBSCRIPTIONS -> {
                if (LocalStore.getSubscriptions().isEmpty()) {
                    binding.feedLoadingSpinner.visibility = View.GONE
                    binding.swipeRefresh.isRefreshing = false
                    showList(shorts = false)
                    feedAdapter.submitList(emptyList())
                    setEmptyState(getString(R.string.empty_subscriptions))
                    return
                }
            }
            else -> Unit
        }

        binding.feedLoadingSpinner.visibility = View.VISIBLE
        feedJob = lifecycleScope.launch {
            val videos = when (mode) {
                FeedMode.HOME -> NewPipeRepository.fetchVideos(TRENDING_QUERY)
                FeedMode.SHORTS -> NewPipeRepository.fetchVideos(SHORTS_QUERY).filter { it.isShort }
                FeedMode.SUBSCRIPTIONS -> NewPipeRepository.fetchChannelFeed(LocalStore.getSubscriptions())
                FeedMode.SEARCH -> NewPipeRepository.fetchVideos(searchQuery)
                FeedMode.LIBRARY -> emptyList()
            }

            binding.feedLoadingSpinner.visibility = View.GONE
            binding.swipeRefresh.isRefreshing = false

            if (mode == FeedMode.SHORTS) {
                showList(shorts = true)
                shortsAdapter.submitList(videos)
            } else {
                showList(shorts = false)
                feedAdapter.submitList(videos)
            }

            if (videos.isEmpty()) {
                val msgRes = when (mode) {
                    FeedMode.SHORTS -> R.string.empty_shorts
                    FeedMode.SUBSCRIPTIONS -> R.string.empty_subscriptions_feed
                    else -> R.string.empty_feed
                }
                setEmptyState(getString(msgRes))
            }
        }
    }

    /** Shorts ke liye 2-column grid, baaki ke liye normal list */
    private fun showList(shorts: Boolean) {
        val adapter = if (shorts) shortsAdapter else feedAdapter
        if (binding.recyclerFeed.adapter !== adapter) {
            binding.recyclerFeed.layoutManager =
                if (shorts) GridLayoutManager(this, 2) else LinearLayoutManager(this)
            binding.recyclerFeed.adapter = adapter
        }
    }

    private fun setEmptyState(message: String?) {
        if (message.isNullOrEmpty()) {
            binding.txtEmptyState.visibility = View.GONE
        } else {
            binding.txtEmptyState.text = message
            binding.txtEmptyState.visibility = View.VISIBLE
        }
    }

    private fun updateSectionHeader() {
        val header = binding.sectionHeader
        when (feedMode) {
            FeedMode.SUBSCRIPTIONS -> {
                header.visibility = View.VISIBLE
                binding.sectionTitle.text = getString(R.string.section_subscriptions)
                binding.btnSectionAction.visibility = View.VISIBLE
                binding.btnSectionAction.text = getString(R.string.action_manage)
            }
            FeedMode.LIBRARY -> {
                header.visibility = View.VISIBLE
                binding.sectionTitle.text = getString(R.string.section_library)
                binding.btnSectionAction.visibility = View.VISIBLE
                binding.btnSectionAction.text = getString(R.string.action_clear)
            }
            FeedMode.SEARCH -> {
                header.visibility = View.VISIBLE
                binding.sectionTitle.text = getString(R.string.search_results_title, searchQuery)
                binding.btnSectionAction.visibility = View.GONE
            }
            else -> header.visibility = View.GONE
        }
    }

    private fun showManageSubscriptionsDialog() {
        val subs = LocalStore.getSubscriptions()
        if (subs.isEmpty()) return
        AlertDialog.Builder(this)
            .setTitle(R.string.manage_channels_title)
            .setItems(subs.map { it.name }.toTypedArray()) { _, which ->
                val sub = subs[which]
                LocalStore.unsubscribe(sub)
                Toast.makeText(
                    this,
                    getString(R.string.toast_unsubscribed, sub.name),
                    Toast.LENGTH_SHORT
                ).show()
                loadFeed(FeedMode.SUBSCRIPTIONS)
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun updateSubscribeButton() {
        val channel = currentChannel
        binding.btnSubscribe.text =
            if (channel != null && LocalStore.isSubscribed(channel)) "Subscribed" else "Subscribe"
    }

    private fun playVideo(video: VideoItem) {
        LocalStore.addToHistory(video)

        currentChannel = Subscription(video.uploaderName, video.uploaderUrl)

        binding.miniPlayerBar.visibility = View.GONE
        binding.expandedPlayerSheet.visibility = View.VISIBLE

        binding.expandedVideoTitle.text = video.title
        binding.expandedChannelName.text = video.uploaderName
        binding.expandedVideoMeta.text = "Extracting stream..."
        binding.exoPlayerView.findViewById<TextView>(R.id.player_overlay_title)?.text = video.title

        binding.miniPlayerTitle.text = video.title
        binding.miniPlayerChannel.text = video.uploaderName
        binding.miniPlayerThumb.load(video.thumbnailUrl)
        updateSubscribeButton()

        exoPlayer?.stop()

        lifecycleScope.launch {
            when (val result = NewPipeRepository.extractStreamDetails(video.videoUrl)) {
                is StreamExtractionResult.Success -> {
                    val details = result.data
                    currentChannel = Subscription(details.uploaderName, details.uploaderUrl)
                    binding.expandedVideoTitle.text = details.title
                    binding.expandedChannelName.text = details.uploaderName
                    binding.expandedVideoMeta.text = details.viewsAndDate
                    updateSubscribeButton()
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

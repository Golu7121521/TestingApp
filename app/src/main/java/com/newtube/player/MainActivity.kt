package com.newtube.player

import android.content.Intent
import android.content.res.ColorStateList
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
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.VideoSize
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.source.MergingMediaSource
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import coil.load
import coil.transform.CircleCropTransformation
import com.newtube.player.databinding.ActivityMainBinding
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

/** Tabs aur search ke liye feed ka mode */
private enum class FeedMode { HOME, SHORTS, SUBSCRIPTIONS, LIBRARY, SEARCH, CHANNEL }

@OptIn(UnstableApi::class)
class MainActivity : AppCompatActivity() {

    companion object {
        private const val SHORTS_QUERY = "#shorts viral india"
        private const val USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:128.0) Gecko/20100101 Firefox/128.0"
        private val HOME_CHIPS = listOf("All", "Gaming", "Hyped", "Live", "Music")
        private val HOME_QUERIES = listOf(
            "Trending India music gaming tech",
            "Gaming India trending",
            "Hyped viral India",
            "Live streams India",
            "Hindi music trending"
        )
        private val SUB_CHIPS = listOf("All", "Videos", "Shorts", "Live")
        private val SPEED_LABELS = listOf("0.5", "0.75", "1.0", "1.25", "1.5", "2.0")
        private val SPEED_VALUES = listOf(0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 2.0f)
    }

    private lateinit var binding: ActivityMainBinding
    private lateinit var feedAdapter: FeedAdapter
    private lateinit var relatedAdapter: FeedAdapter
    private lateinit var shortsGridAdapter: ShortFeedAdapter
    private var exoPlayer: ExoPlayer? = null
    private lateinit var okHttpDataSourceFactory: OkHttpDataSource.Factory

    private var isFullscreen = false
    private val progressHandler = Handler(Looper.getMainLooper())

    private var feedMode = FeedMode.HOME
    private var feedJob: Job? = null
    private var searchQuery = ""
    private var channelSub: Subscription? = null
    private var homeChip = 0
    private var subsChip = 0
    private var subsCache: List<VideoItem> = emptyList()
    private var liked = false
    private var disliked = false

    private var currentVideo: VideoItem? = null
    private var currentDetails: PlayedVideoStream? = null
    private var currentQuality: QualityOption? = null
    private var currentChannel: Subscription? = null
    private var captionsOn = false
    private var currentSpeed = 1f

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupExoPlayer()
        setupPlayerControls()
        setupRecyclerViews()
        setupBottomNavigation()
        setupTopBarAndSearch()
        setupExpandedPlayerActions()
        setupSectionHeader()
        setupBackNavigation()
        setPlayerHeight()

        loadFeed(FeedMode.HOME)
    }

    // ---------------- PLAYER SETUP ----------------

    private fun setupExoPlayer() {
        okHttpDataSourceFactory = OkHttpDataSource.Factory(NewPipeOkHttpDownloader.instance.client)
            .setUserAgent(USER_AGENT)

        val mediaSourceFactory = DefaultMediaSourceFactory(this)
            .setDataSourceFactory(okHttpDataSourceFactory)

        // Buffering fix: zyada buffer + jaldi playback start (1.5s) + rebuffer ke baad 4s
        val loadControl = DefaultLoadControl.Builder()
            .setBufferDurationsMs(30_000, 120_000, 1_500, 4_000)
            .build()

        exoPlayer = ExoPlayer.Builder(this)
            .setMediaSourceFactory(mediaSourceFactory)
            .setLoadControl(loadControl)
            .build()
            .also { player ->
                binding.exoPlayerView.player = player
                // Captions default OFF (YouTube jaisa)
                player.trackSelectionParameters = player.trackSelectionParameters.buildUpon()
                    .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true)
                    .build()
                player.addListener(object : Player.Listener {
                    override fun onIsPlayingChanged(isPlaying: Boolean) {
                        binding.miniPlayerPlayPause.setImageResource(
                            if (isPlaying) R.drawable.ic_pause else R.drawable.ic_play
                        )
                    }

                    override fun onVideoSizeChanged(videoSize: VideoSize) {
                        if (videoSize.height > 0) {
                            qualityButton()?.text = "${videoSize.height}p"
                        }
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

    /** Player overlay ke buttons (YouTube jaise) */
    private fun setupPlayerControls() {
        val view = binding.exoPlayerView
        view.findViewById<ImageButton>(R.id.btn_minimize_player)?.setOnClickListener {
            minimizeToBottomMiniPlayer()
        }
        view.findViewById<ImageButton>(R.id.btn_fullscreen)?.setOnClickListener { toggleFullscreen() }
        view.findViewById<ImageButton>(R.id.btn_cc)?.setOnClickListener { toggleCaptions() }
        view.findViewById<ImageButton>(R.id.btn_settings)?.setOnClickListener { showSettingsDialog() }
        view.findViewById<ImageButton>(R.id.btn_prev_video)?.setOnClickListener { playPrevious() }
        view.findViewById<ImageButton>(R.id.btn_next_video)?.setOnClickListener { playNext() }
        view.findViewById<TextView>(R.id.btn_quality)?.setOnClickListener { showQualityDialog() }
        view.findViewById<TextView>(R.id.btn_speed)?.setOnClickListener { showSpeedDialog() }
    }

    private fun qualityButton(): TextView? =
        binding.exoPlayerView.findViewById(R.id.btn_quality)

    private fun speedButton(): TextView? =
        binding.exoPlayerView.findViewById(R.id.btn_speed)

    private fun setPlayerHeight() {
        binding.playerSurfaceContainer.layoutParams.height =
            resources.displayMetrics.widthPixels * 9 / 16
        binding.playerSurfaceContainer.requestLayout()
    }

    // ---------------- RECYCLERS / NAV / TOP BAR ----------------

    private fun setupRecyclerViews() {
        feedAdapter = FeedAdapter(
            onVideoClick = { playVideo(it) },
            onShortClick = { playVideo(it) },
            onChannelClick = { openChannel(it) },
            onChannelLongClick = { confirmUnsubscribe(it) }
        )
        relatedAdapter = FeedAdapter(
            onVideoClick = { playVideo(it) },
            onShortClick = { playVideo(it) },
            onChannelClick = {},
            onChannelLongClick = {}
        )
        shortsGridAdapter = ShortFeedAdapter(horizontal = false, onShortClick = { playVideo(it) })

        binding.recyclerFeed.layoutManager = LinearLayoutManager(this)
        binding.recyclerFeed.adapter = feedAdapter

        binding.recyclerRelated.layoutManager = LinearLayoutManager(this)
        binding.recyclerRelated.adapter = relatedAdapter

        binding.swipeRefresh.setColorSchemeResources(R.color.yt_red)
        binding.swipeRefresh.setOnRefreshListener {
            loadFeed(feedMode, searchQuery, channelSub)
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

    private fun setupTopBarAndSearch() {
        binding.btnTopSearch.setOnClickListener {
            val show = binding.searchContainer.visibility != View.VISIBLE
            binding.searchContainer.visibility = if (show) View.VISIBLE else View.GONE
            if (show) binding.editSearch.requestFocus()
        }
        binding.btnTopCamera.setOnClickListener {
            Toast.makeText(this, R.string.toast_coming_soon, Toast.LENGTH_SHORT).show()
        }
        binding.btnSearch.setOnClickListener { runSearch() }
        binding.editSearch.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                runSearch()
                true
            } else false
        }
        binding.miniPlayerBar.setOnClickListener { expandFromMiniPlayer() }
        binding.miniPlayerPlayPause.setOnClickListener {
            exoPlayer?.let { player -> if (player.isPlaying) player.pause() else player.play() }
        }
        binding.miniPlayerClose.setOnClickListener {
            exoPlayer?.stop()
            exoPlayer?.clearMediaItems()
            binding.miniPlayerBar.visibility = View.GONE
        }
    }

    private fun runSearch() {
        val q = binding.editSearch.text.toString().trim()
        if (q.isEmpty()) return
        binding.searchContainer.visibility = View.GONE
        if (binding.expandedPlayerSheet.visibility == View.VISIBLE) minimizeToBottomMiniPlayer()
        loadFeed(FeedMode.SEARCH, q)
    }

    private fun setupExpandedPlayerActions() {
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
        binding.btnLike.setOnClickListener {
            liked = !liked
            if (liked) disliked = false
            updateLikeButtons()
        }
        binding.btnDislike.setOnClickListener {
            disliked = !disliked
            if (disliked) liked = false
            updateLikeButtons()
        }
        binding.btnShare.setOnClickListener {
            val v = currentVideo ?: return@setOnClickListener
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, v.videoUrl)
            }
            startActivity(Intent.createChooser(intent, getString(R.string.share_title)))
        }
    }

    private fun updateLikeButtons() {
        val red = ContextCompat.getColor(this, R.color.yt_red)
        val normal = ContextCompat.getColor(this, R.color.yt_text)
        binding.btnLike.imageTintList = ColorStateList.valueOf(if (liked) red else normal)
        binding.btnDislike.imageTintList = ColorStateList.valueOf(if (disliked) red else normal)
    }

    private fun setupSectionHeader() {
        binding.btnSectionAction.setOnClickListener {
            when (feedMode) {
                FeedMode.LIBRARY -> {
                    LocalStore.clearHistory()
                    Toast.makeText(this, R.string.toast_history_cleared, Toast.LENGTH_SHORT).show()
                    loadFeed(FeedMode.LIBRARY)
                }
                FeedMode.CHANNEL -> {
                    channelSub?.let { LocalStore.toggleSubscription(it) }
                    updateHeaderAndChips()
                }
                else -> Unit
            }
        }
    }

    // ---------------- FEED ----------------

    /**
     * Har tab ka data source alag hai:
     * Home = trending + Shorts shelf, Shorts = grid, Subscriptions = channel row + latest videos,
     * Library = local history, Search = results, Channel = ek channel ki videos.
     */
    private fun loadFeed(mode: FeedMode, query: String = "", channel: Subscription? = null) {
        feedMode = mode
        if (mode == FeedMode.SEARCH) searchQuery = query
        if (mode == FeedMode.CHANNEL) channelSub = channel
        feedJob?.cancel()
        updateHeaderAndChips()
        setEmptyState(null)

        if (mode == FeedMode.LIBRARY) {
            stopLoading()
            showList(false)
            val history = LocalStore.getHistory()
            feedAdapter.submitRows(history.map { FeedRow.Video(it) })
            if (history.isEmpty()) setEmptyState(getString(R.string.empty_history))
            return
        }

        if (mode == FeedMode.SUBSCRIPTIONS && LocalStore.getSubscriptions().isEmpty()) {
            stopLoading()
            showList(false)
            feedAdapter.submitRows(emptyList())
            setEmptyState(getString(R.string.empty_subscriptions))
            return
        }

        binding.feedLoadingSpinner.visibility = View.VISIBLE
        feedJob = lifecycleScope.launch {
            when (mode) {
                FeedMode.SHORTS -> {
                    val shorts = NewPipeRepository.fetchVideos(SHORTS_QUERY).filter { it.isShort }
                    stopLoading()
                    showList(true)
                    shortsGridAdapter.submitList(shorts)
                    if (shorts.isEmpty()) setEmptyState(getString(R.string.empty_shorts))
                    return@launch
                }
                FeedMode.SUBSCRIPTIONS -> {
                    subsCache = NewPipeRepository.fetchChannelFeed(LocalStore.getSubscriptions())
                    stopLoading()
                    showList(false)
                    renderSubscriptionRows()
                    if (subsCache.isEmpty()) setEmptyState(getString(R.string.empty_subscriptions_feed))
                    return@launch
                }
                else -> Unit
            }

            val rows: List<FeedRow> = when (mode) {
                FeedMode.HOME -> {
                    val (videos, shorts) = coroutineScope {
                        // Dono requests parallel: buffering/loading time kam
                        val videosDeferred = async { NewPipeRepository.fetchVideos(HOME_QUERIES[homeChip]) }
                        val shortsDeferred = async {
                            NewPipeRepository.fetchVideos(SHORTS_QUERY).filter { it.isShort }
                        }
                        videosDeferred.await() to shortsDeferred.await()
                    }
                    buildHomeRows(videos, shorts)
                }
                FeedMode.SEARCH -> NewPipeRepository.fetchVideos(searchQuery).map { FeedRow.Video(it) }
                FeedMode.CHANNEL -> NewPipeRepository.fetchChannelFeed(listOfNotNull(channelSub))
                    .map { FeedRow.Video(it) }
                else -> emptyList()
            }

            stopLoading()
            showList(false)
            feedAdapter.submitRows(rows)
            if (rows.isEmpty()) setEmptyState(getString(R.string.empty_feed))
        }
    }

    /** Home: 3 videos ke baad Shorts shelf (YouTube jaisa) */
    private fun buildHomeRows(videos: List<VideoItem>, shorts: List<VideoItem>): List<FeedRow> {
        val rows = mutableListOf<FeedRow>()
        videos.forEachIndexed { index, v ->
            rows.add(FeedRow.Video(v))
            if (index == 2 && shorts.isNotEmpty()) rows.add(FeedRow.ShortsShelf(shorts))
        }
        if (videos.size < 3 && shorts.isNotEmpty()) rows.add(FeedRow.ShortsShelf(shorts))
        return rows
    }

    private fun renderSubscriptionRows() {
        if (feedMode != FeedMode.SUBSCRIPTIONS) return
        val subs = LocalStore.getSubscriptions()
        val videos = when (subsChip) {
            1 -> subsCache.filter { !it.isShort }
            2 -> subsCache.filter { it.isShort }
            3 -> subsCache.filter { it.durationText == "LIVE" }
            else -> subsCache
        }
        showList(false)
        feedAdapter.submitRows(listOf(FeedRow.Channels(subs)) + videos.map { FeedRow.Video(it) })
    }

    private fun updateHeaderAndChips() {
        val showChips = feedMode == FeedMode.HOME || feedMode == FeedMode.SUBSCRIPTIONS
        binding.chipScroll.visibility = if (showChips) View.VISIBLE else View.GONE
        when (feedMode) {
            FeedMode.HOME -> renderChips(HOME_CHIPS, homeChip) { i ->
                homeChip = i
                loadFeed(FeedMode.HOME)
            }
            FeedMode.SUBSCRIPTIONS -> renderChips(SUB_CHIPS, subsChip) { i ->
                subsChip = i
                renderSubscriptionRows()
            }
            else -> Unit
        }

        val title: String?
        val action: String?
        when (feedMode) {
            FeedMode.LIBRARY -> {
                title = getString(R.string.section_library)
                action = getString(R.string.action_clear)
            }
            FeedMode.SEARCH -> {
                title = getString(R.string.search_results_title, searchQuery)
                action = null
            }
            FeedMode.CHANNEL -> {
                title = channelSub?.name ?: ""
                action = channelSub?.let {
                    getString(if (LocalStore.isSubscribed(it)) R.string.action_subscribed else R.string.action_subscribe)
                }
            }
            else -> {
                title = null
                action = null
            }
        }

        if (title == null) {
            binding.sectionHeader.visibility = View.GONE
        } else {
            binding.sectionHeader.visibility = View.VISIBLE
            binding.sectionTitle.text = title
            if (action == null) {
                binding.btnSectionAction.visibility = View.GONE
            } else {
                binding.btnSectionAction.visibility = View.VISIBLE
                binding.btnSectionAction.text = action
            }
        }
    }

    /** YouTube jaise chips (rounded, selected = black) */
    private fun renderChips(labels: List<String>, selected: Int, onSelect: (Int) -> Unit) {
        val container = binding.chipContainer
        container.removeAllViews()
        labels.forEachIndexed { i, label ->
            val chip = TextView(this).apply {
                text = label
                textSize = 14f
                val isSelected = i == selected
                setTextColor(
                    ContextCompat.getColor(
                        this@MainActivity,
                        if (isSelected) R.color.yt_white else R.color.yt_text
                    )
                )
                setBackgroundResource(if (isSelected) R.drawable.bg_chip_selected else R.drawable.bg_chip)
                setPadding(dp(14), dp(7), dp(14), dp(7))
                layoutParams = android.widget.LinearLayout.LayoutParams(
                    android.widget.LinearLayout.LayoutParams.WRAP_CONTENT,
                    android.widget.LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { marginEnd = dp(8) }
                setOnClickListener { onSelect(i) }
            }
            container.addView(chip)
        }
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    private fun showList(shorts: Boolean) {
        val adapter = if (shorts) shortsGridAdapter else feedAdapter
        if (binding.recyclerFeed.adapter !== adapter) {
            binding.recyclerFeed.layoutManager =
                if (shorts) GridLayoutManager(this, 2) else LinearLayoutManager(this)
            binding.recyclerFeed.adapter = adapter
        }
    }

    private fun stopLoading() {
        binding.feedLoadingSpinner.visibility = View.GONE
        binding.swipeRefresh.isRefreshing = false
    }

    private fun setEmptyState(message: String?) {
        if (message.isNullOrEmpty()) {
            binding.txtEmptyState.visibility = View.GONE
        } else {
            binding.txtEmptyState.text = message
            binding.txtEmptyState.visibility = View.VISIBLE
        }
    }

    private fun openChannel(sub: Subscription) {
        loadFeed(FeedMode.CHANNEL, channel = sub)
    }

    private fun confirmUnsubscribe(sub: Subscription) {
        AlertDialog.Builder(this)
            .setMessage(getString(R.string.unsubscribe_confirm, sub.name))
            .setPositiveButton(R.string.action_unsubscribe) { _, _ ->
                LocalStore.unsubscribe(sub)
                Toast.makeText(this, getString(R.string.toast_unsubscribed, sub.name), Toast.LENGTH_SHORT).show()
                if (feedMode == FeedMode.SUBSCRIPTIONS) {
                    renderSubscriptionRows()
                    if (LocalStore.getSubscriptions().isEmpty()) {
                        setEmptyState(getString(R.string.empty_subscriptions))
                    }
                }
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    // ---------------- PLAYBACK ----------------

    private fun playVideo(video: VideoItem) {
        LocalStore.addToHistory(video)
        currentVideo = video
        currentDetails = null
        currentQuality = null
        currentChannel = Subscription(video.uploaderName, video.uploaderUrl)

        binding.miniPlayerBar.visibility = View.GONE
        binding.expandedPlayerSheet.visibility = View.VISIBLE

        binding.expandedVideoTitle.text = video.title
        binding.expandedChannelName.text = video.uploaderName
        binding.expandedVideoMeta.text = video.viewCountText
        setAvatar(video.uploaderName, "")
        binding.exoPlayerView.findViewById<TextView>(R.id.player_overlay_title)?.text = video.title

        binding.miniPlayerTitle.text = video.title
        binding.miniPlayerChannel.text = video.uploaderName
        binding.miniPlayerThumb.load(video.thumbnailUrl)

        updateSubscribeButton()
        updateLikeButtons()
        updateCaptionsButton()
        relatedAdapter.submitRows(emptyList())
        exoPlayer?.stop()

        lifecycleScope.launch {
            when (val result = NewPipeRepository.extractStreamDetails(video.videoUrl)) {
                is StreamExtractionResult.Success -> {
                    val details = result.data
                    currentDetails = details
                    currentChannel = Subscription(details.uploaderName, details.uploaderUrl, details.uploaderAvatarUrl)
                    binding.expandedVideoTitle.text = details.title
                    binding.expandedChannelName.text = details.uploaderName
                    binding.expandedVideoMeta.text = details.viewsAndDate
                    setAvatar(details.uploaderName, details.uploaderAvatarUrl)
                    updateSubscribeButton()
                    updateCaptionsButton()
                    relatedAdapter.submitRows(details.relatedItems.map { FeedRow.Video(it) })
                    applyPlayback(details, keepPosition = false)
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

    /**
     * Auto = DASH/HLS (ExoPlayer network ke hisaab se quality badalta hai).
     * Manual quality = ek specific stream.
     */
    private fun applyPlayback(details: PlayedVideoStream, keepPosition: Boolean) {
        val player = exoPlayer ?: return
        val startPos = if (keepPosition) player.currentPosition else 0L
        val quality = currentQuality

        if (quality == null && details.dashUrl != null) {
            setSingleItem(details.dashUrl, MimeTypes.APPLICATION_MPD, details, startPos)
        } else if (quality == null && details.hlsUrl != null) {
            setSingleItem(details.hlsUrl, MimeTypes.APPLICATION_M3U8, details, startPos)
        } else {
            val q = quality
                ?: details.qualities.firstOrNull { it.isProgressive && it.height <= 720 }
                ?: details.qualities.lastOrNull()
            if (q == null) {
                Toast.makeText(this, "Koi stream nahi mili", Toast.LENGTH_SHORT).show()
                return
            }
            val audio = q.audioUrl
            if (q.isProgressive || audio.isNullOrBlank()) {
                setSingleItem(q.videoUrl, MimeTypes.VIDEO_MP4, details, startPos)
            } else {
                setMergedSources(q.videoUrl, audio, startPos)
            }
        }

        player.setPlaybackSpeed(currentSpeed)
        player.prepare()
        player.playWhenReady = true
    }

    /** Single URL (DASH / HLS / progressive). Subtitles yahin attach hote hain */
    private fun setSingleItem(url: String, mime: String, details: PlayedVideoStream, startPos: Long) {
        val subs = details.subtitles.map { s ->
            MediaItem.SubtitleConfiguration.Builder(Uri.parse(s.url))
                .setMimeType(s.mimeType)
                .setLanguage(s.languageTag)
                .setSelectionFlags(C.SELECTION_FLAG_DEFAULT)
                .build()
        }
        val item = MediaItem.Builder()
            .setUri(Uri.parse(url))
            .setMimeType(mime)
            .setSubtitleConfigurations(subs)
            .build()
        exoPlayer?.setMediaItem(item, startPos)
    }

    /** Video-only + audio merge (sirf 480p se upar ke liye) */
    private fun setMergedSources(videoUrl: String, audioUrl: String, startPos: Long) {
        val video = ProgressiveMediaSource.Factory(okHttpDataSourceFactory)
            .createMediaSource(MediaItem.fromUri(videoUrl))
        val audio = ProgressiveMediaSource.Factory(okHttpDataSourceFactory)
            .createMediaSource(MediaItem.fromUri(audioUrl))
        exoPlayer?.setMediaSource(MergingMediaSource(video, audio), startPos)
    }

    private fun playNext() {
        val next = currentDetails?.relatedItems?.firstOrNull() ?: return
        playVideo(next)
    }

    /** 3 sec se zyada chala ho to video ko start se, warna history ka pichhla video */
    private fun playPrevious() {
        val player = exoPlayer ?: return
        if (player.currentPosition > 3000) {
            player.seekTo(0)
            return
        }
        val previous = LocalStore.getHistory().getOrNull(1)
        if (previous != null) playVideo(previous) else player.seekTo(0)
    }

    private fun toggleCaptions() {
        val player = exoPlayer ?: return
        captionsOn = !captionsOn
        player.trackSelectionParameters = player.trackSelectionParameters.buildUpon()
            .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, !captionsOn)
            .build()
        updateCaptionsButton()
    }

    private fun updateCaptionsButton() {
        val btn = binding.exoPlayerView.findViewById<ImageButton>(R.id.btn_cc) ?: return
        val hasSubs = !currentDetails?.subtitles.isNullOrEmpty()
        btn.visibility = if (hasSubs) View.VISIBLE else View.GONE
        btn.alpha = if (captionsOn) 1f else 0.55f
    }

    private fun showSettingsDialog() {
        val options = arrayOf<CharSequence>(
            getString(R.string.dialog_quality),
            getString(R.string.dialog_speed),
            getString(R.string.dialog_captions)
        )
        AlertDialog.Builder(this)
            .setTitle(R.string.dialog_settings)
            .setItems(options) { _, which ->
                when (which) {
                    0 -> showQualityDialog()
                    1 -> showSpeedDialog()
                    2 -> toggleCaptions()
                }
            }
            .show()
    }

    private fun showQualityDialog() {
        val details = currentDetails ?: return
        val labels = mutableListOf(getString(R.string.quality_auto))
        details.qualities.forEach { labels.add(it.label) }
        val current = currentQuality
        val checked = if (current == null) 0 else {
            val idx = details.qualities.indexOfFirst { it.videoUrl == current.videoUrl }
            if (idx >= 0) idx + 1 else 0
        }
        AlertDialog.Builder(this)
            .setTitle(R.string.dialog_quality)
            .setSingleChoiceItems(labels.toTypedArray<CharSequence>(), checked) { dialog, which ->
                dialog.dismiss()
                val chosen = if (which == 0) null else details.qualities[which - 1]
                if (chosen?.videoUrl != currentQuality?.videoUrl) {
                    currentQuality = chosen
                    applyPlayback(details, keepPosition = true)
                }
            }
            .show()
    }

    private fun showSpeedDialog() {
        val checked = SPEED_VALUES.indexOfFirst { it == currentSpeed }.coerceAtLeast(0)
        AlertDialog.Builder(this)
            .setTitle(R.string.dialog_speed)
            .setSingleChoiceItems(SPEED_LABELS.toTypedArray<CharSequence>(), checked) { dialog, which ->
                currentSpeed = SPEED_VALUES[which]
                exoPlayer?.setPlaybackSpeed(currentSpeed)
                speedButton()?.text = SPEED_LABELS[which]
                dialog.dismiss()
            }
            .show()
    }

    private fun setAvatar(name: String, url: String) {
        binding.txtExpandedAvatarInitial.text = name.firstOrNull()?.uppercaseChar()?.toString() ?: "?"
        if (url.isNotBlank()) {
            binding.imgExpandedAvatar.visibility = View.VISIBLE
            binding.imgExpandedAvatar.load(url) {
                crossfade(true)
                transformations(CircleCropTransformation())
            }
        } else {
            binding.imgExpandedAvatar.visibility = View.GONE
        }
    }

    private fun updateSubscribeButton() {
        val channel = currentChannel
        val subscribed = channel != null && LocalStore.isSubscribed(channel)
        binding.btnSubscribe.text = getString(
            if (subscribed) R.string.action_subscribed else R.string.action_subscribe
        )
        binding.btnSubscribe.setBackgroundResource(
            if (subscribed) R.drawable.bg_subscribed_pill else R.drawable.bg_subscribe_pill
        )
        binding.btnSubscribe.setTextColor(
            ContextCompat.getColor(this, if (subscribed) R.color.yt_text else R.color.yt_white)
        )
    }

    private fun minimizeToBottomMiniPlayer() {
        if (isFullscreen) toggleFullscreen()
        binding.expandedPlayerSheet.visibility = View.GONE
        binding.miniPlayerBar.visibility = View.VISIBLE
    }

    private fun expandFromMiniPlayer() {
        binding.miniPlayerBar.visibility = View.GONE
        binding.expandedPlayerSheet.visibility = View.VISIBLE
    }

    private fun toggleFullscreen() {
        isFullscreen = !isFullscreen
        val infoVisibility = if (isFullscreen) View.GONE else View.VISIBLE
        binding.playerInfo.visibility = infoVisibility
        binding.upNextHeader.visibility = infoVisibility
        binding.recyclerRelated.visibility = infoVisibility

        if (isFullscreen) {
            requestedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
            binding.bottomNavigation.visibility = View.GONE
            binding.bottomNavDivider.visibility = View.GONE
            binding.playerSurfaceContainer.layoutParams.height =
                android.view.ViewGroup.LayoutParams.MATCH_PARENT
            binding.playerSurfaceContainer.requestLayout()
        } else {
            requestedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
            binding.bottomNavigation.visibility = View.VISIBLE
            binding.bottomNavDivider.visibility = View.VISIBLE
            setPlayerHeight()
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

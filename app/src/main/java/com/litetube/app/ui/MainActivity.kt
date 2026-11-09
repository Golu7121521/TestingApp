package com.litetube.app.ui

import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import com.litetube.app.R
import com.litetube.app.databinding.ActivityMainBinding
import com.litetube.app.player.PlayerHolder

class MainActivity : AppCompatActivity() {

    private lateinit var b: ActivityMainBinding

    private val playerListener = object : Player.Listener {
        override fun onIsPlayingChanged(isPlaying: Boolean) = updateMini()
        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) = updateMini()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        b = ActivityMainBinding.inflate(layoutInflater)
        setContentView(b.root)

        PlayerHolder.get(this).addListener(playerListener)

        b.bottomNav.setOnItemSelectedListener { item ->
            showMode(
                when (item.itemId) {
                    R.id.nav_shorts -> FeedFragment.MODE_SHORTS
                    R.id.nav_subs -> FeedFragment.MODE_SUBS
                    R.id.nav_library -> FeedFragment.MODE_LIBRARY
                    else -> FeedFragment.MODE_HOME
                }
            )
            true
        }
        if (savedInstanceState == null) showMode(FeedFragment.MODE_HOME)

        b.miniPlayer.setOnClickListener { openFullPlayer() }
        b.miniPlayPause.setOnClickListener {
            val p = PlayerHolder.get(this)
            if (p.isPlaying) p.pause() else p.play()
        }
        b.miniClose.setOnClickListener {
            PlayerHolder.stopAndClear()
            updateMini()
        }
        updateMini()
    }

    override fun onResume() {
        super.onResume()
        b.miniPlayerView.player = PlayerHolder.get(this)
        updateMini()
    }

    override fun onPause() {
        super.onPause()
        // Full player khulne par mini player ka surface chhod do; audio chalta rahega
        b.miniPlayerView.player = null
    }

    private fun showMode(mode: String) {
        supportFragmentManager.beginTransaction()
            .replace(R.id.container, FeedFragment.newInstance(mode))
            .commit()
    }

    private fun openFullPlayer() {
        if (PlayerHolder.currentWatchUrl == null) return
        PlayerActivity.openCurrent(this)
    }

    private fun updateMini() {
        val hasMedia = PlayerHolder.currentWatchUrl != null
        b.miniPlayer.visibility = if (hasMedia) View.VISIBLE else View.GONE
        b.miniTitle.text = PlayerHolder.currentTitle
        b.miniChannel.text = PlayerHolder.currentChannel
        val playing = PlayerHolder.get(this).isPlaying
        b.miniPlayPause.setImageResource(if (playing) R.drawable.ic_pause else R.drawable.ic_play)
    }
}

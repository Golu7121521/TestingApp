package com.litetube.app.ui

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.MediaItem
import androidx.recyclerview.widget.LinearLayoutManager
import com.litetube.app.data.Repo
import com.litetube.app.databinding.ActivityPlayerBinding
import com.litetube.app.player.PlayerHolder
import kotlinx.coroutines.launch

class PlayerActivity : AppCompatActivity() {

    private lateinit var b: ActivityPlayerBinding
    private val relatedAdapter = VideoAdapter { item -> load(item.url, restart = true) }

    companion object {
        private const val EXTRA_URL = "url"

        /** Naya video chalao */
        fun start(ctx: Context, url: String) {
            ctx.startActivity(Intent(ctx, PlayerActivity::class.java).putExtra(EXTRA_URL, url))
        }

        /** Jo video already chal raha hai usi ka full player kholo (mini player se) */
        fun openCurrent(ctx: Context) {
            ctx.startActivity(Intent(ctx, PlayerActivity::class.java))
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        b = ActivityPlayerBinding.inflate(layoutInflater)
        setContentView(b.root)

        b.related.layoutManager = LinearLayoutManager(this)
        b.related.adapter = relatedAdapter
        b.playerView.player = PlayerHolder.get(this)

        b.title.text = PlayerHolder.currentTitle
        b.channel.text = PlayerHolder.currentChannel

        val url = intent.getStringExtra(EXTRA_URL)
        val current = PlayerHolder.currentWatchUrl
        when {
            url != null -> load(url, restart = true)
            current != null -> load(current, restart = false)
            else -> finish()
        }
    }

    private fun load(url: String, restart: Boolean) {
        b.progress.visibility = View.VISIBLE
        lifecycleScope.launch {
            try {
                val data = Repo.streamUrl(url)
                PlayerHolder.currentWatchUrl = url
                PlayerHolder.currentTitle = data.title
                PlayerHolder.currentChannel = data.channel

                b.title.text = data.title
                b.channel.text = data.channel
                relatedAdapter.submit(data.related)

                if (restart) {
                    val p = PlayerHolder.get(this@PlayerActivity)
                    p.setMediaItem(MediaItem.fromUri(data.playUrl))
                    p.prepare()
                    p.playWhenReady = true
                }
            } catch (e: Exception) {
                Toast.makeText(this@PlayerActivity, "Load nahi hua: ${e.message}", Toast.LENGTH_LONG).show()
            } finally {
                b.progress.visibility = View.GONE
            }
        }
    }

    override fun onDestroy() {
        b.playerView.player = null
        super.onDestroy()
    }
}

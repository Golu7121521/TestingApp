package com.newtube.player

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import coil.load
import com.newtube.player.databinding.ItemChannelsRowBinding
import com.newtube.player.databinding.ItemShortsShelfBinding
import com.newtube.player.databinding.ItemVideoCardBinding

/** Feed ki ek row: video card, Shorts shelf, ya subscribed channels ki row */
sealed class FeedRow {
    data class Video(val video: VideoItem) : FeedRow()
    data class ShortsShelf(val shorts: List<VideoItem>) : FeedRow()
    data class Channels(val subs: List<Subscription>) : FeedRow()
}

class FeedAdapter(
    private val onVideoClick: (VideoItem) -> Unit,
    private val onShortClick: (VideoItem) -> Unit,
    private val onChannelClick: (Subscription) -> Unit,
    private val onChannelLongClick: (Subscription) -> Unit
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    companion object {
        private const val TYPE_VIDEO = 0
        private const val TYPE_SHELF = 1
        private const val TYPE_CHANNELS = 2
    }

    private val rows = mutableListOf<FeedRow>()

    fun submitRows(newRows: List<FeedRow>) {
        rows.clear()
        rows.addAll(newRows)
        notifyDataSetChanged()
    }

    override fun getItemViewType(position: Int): Int = when (rows[position]) {
        is FeedRow.Video -> TYPE_VIDEO
        is FeedRow.ShortsShelf -> TYPE_SHELF
        is FeedRow.Channels -> TYPE_CHANNELS
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return when (viewType) {
            TYPE_SHELF -> ShelfHolder(ItemShortsShelfBinding.inflate(inflater, parent, false))
            TYPE_CHANNELS -> ChannelsHolder(ItemChannelsRowBinding.inflate(inflater, parent, false))
            else -> VideoHolder(ItemVideoCardBinding.inflate(inflater, parent, false))
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (holder) {
            is VideoHolder -> holder.bind((rows[position] as FeedRow.Video).video)
            is ShelfHolder -> holder.bind((rows[position] as FeedRow.ShortsShelf).shorts)
            is ChannelsHolder -> holder.bind((rows[position] as FeedRow.Channels).subs)
        }
    }

    override fun getItemCount(): Int = rows.size

    inner class VideoHolder(private val b: ItemVideoCardBinding) : RecyclerView.ViewHolder(b.root) {
        fun bind(v: VideoItem) {
            b.txtTitle.text = v.title
            b.txtMetadata.text = "${v.uploaderName} • ${v.viewCountText}"
            b.txtDuration.text = v.durationText
            b.txtAvatarInitial.text = v.uploaderName.firstOrNull()?.uppercaseChar()?.toString() ?: "?"
            b.imgThumbnail.load(v.thumbnailUrl) { crossfade(true) }
            b.root.setOnClickListener { onVideoClick(v) }
        }
    }

    inner class ShelfHolder(private val b: ItemShortsShelfBinding) : RecyclerView.ViewHolder(b.root) {
        private val shortsAdapter = ShortFeedAdapter(horizontal = true, onShortClick = onShortClick)

        init {
            b.recyclerShorts.layoutManager =
                LinearLayoutManager(b.root.context, LinearLayoutManager.HORIZONTAL, false)
            b.recyclerShorts.adapter = shortsAdapter
        }

        fun bind(shorts: List<VideoItem>) {
            shortsAdapter.submitList(shorts)
        }
    }

    inner class ChannelsHolder(private val b: ItemChannelsRowBinding) : RecyclerView.ViewHolder(b.root) {
        private val channelAdapter = ChannelAdapter(onChannelClick, onChannelLongClick)

        init {
            b.recyclerChannels.layoutManager =
                LinearLayoutManager(b.root.context, LinearLayoutManager.HORIZONTAL, false)
            b.recyclerChannels.adapter = channelAdapter
        }

        fun bind(subs: List<Subscription>) {
            channelAdapter.submitList(subs)
            b.root.visibility = if (subs.isEmpty()) View.GONE else View.VISIBLE
        }
    }
}

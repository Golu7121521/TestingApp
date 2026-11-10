package com.newtube.player

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import coil.load
import coil.transform.CircleCropTransformation
import com.newtube.player.databinding.ItemVideoCardBinding

class VideoFeedAdapter(
    private val onVideoClick: (VideoItem) -> Unit
) : RecyclerView.Adapter<VideoFeedAdapter.VideoViewHolder>() {

    private val items = mutableListOf<VideoItem>()

    fun submitList(newItems: List<VideoItem>) {
        items.clear()
        items.addAll(newItems)
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VideoViewHolder {
        val binding = ItemVideoCardBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return VideoViewHolder(binding)
    }

    override fun onBindViewHolder(holder: VideoViewHolder, position: Int) {
        holder.bind(items[position])
    }

    override fun getItemCount(): Int = items.size

    inner class VideoViewHolder(private val binding: ItemVideoCardBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(item: VideoItem) {
            binding.txtTitle.text = item.title
            binding.txtMetadata.text = "${item.uploaderName} • ${item.viewCountText}"
            binding.txtDuration.text = item.durationText

            binding.imgThumbnail.load(item.thumbnailUrl) {
                crossfade(true)
            }
            binding.imgChannelAvatar.load(item.thumbnailUrl) {
                transformations(CircleCropTransformation())
            }

            binding.root.setOnClickListener {
                onVideoClick(item)
            }
        }
    }
}

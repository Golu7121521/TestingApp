package com.newtube.player

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import coil.load
import com.newtube.player.databinding.ItemShortCardBinding

/** Shorts tab ke liye 2-column portrait grid adapter */
class ShortFeedAdapter(
    private val onShortClick: (VideoItem) -> Unit
) : RecyclerView.Adapter<ShortFeedAdapter.ShortViewHolder>() {

    private val items = mutableListOf<VideoItem>()

    fun submitList(newItems: List<VideoItem>) {
        items.clear()
        items.addAll(newItems)
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ShortViewHolder {
        val binding = ItemShortCardBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ShortViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ShortViewHolder, position: Int) {
        holder.bind(items[position])
    }

    override fun getItemCount(): Int = items.size

    inner class ShortViewHolder(private val binding: ItemShortCardBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(item: VideoItem) {
            binding.txtShortTitle.text = item.title
            binding.txtShortViews.text = item.viewCountText
            binding.imgShortThumb.load(item.thumbnailUrl) {
                crossfade(true)
            }
            binding.root.setOnClickListener { onShortClick(item) }
        }
    }
}

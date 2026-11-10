package com.newtube.player

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import coil.load
import com.newtube.player.databinding.ItemShortCardBinding

/**
 * Shorts card. horizontal = true -> Home ka Shorts shelf (fixed width)
 * horizontal = false -> Shorts tab ka 2-column grid
 */
class ShortFeedAdapter(
    private val horizontal: Boolean,
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

    inner class ShortViewHolder(private val b: ItemShortCardBinding) : RecyclerView.ViewHolder(b.root) {

        fun bind(item: VideoItem) {
            val density = b.root.resources.displayMetrics.density
            if (horizontal) {
                b.root.layoutParams.width = (150 * density).toInt()
                b.imgShortThumb.layoutParams.height = (266 * density).toInt()
            } else {
                b.root.layoutParams.width = ViewGroup.LayoutParams.MATCH_PARENT
                b.imgShortThumb.layoutParams.height = (300 * density).toInt()
            }
            b.txtShortTitle.text = item.title
            b.txtShortViews.text = item.viewCountText.substringBefore(" • ")
            b.imgShortThumb.load(item.thumbnailUrl) { crossfade(true) }
            b.root.setOnClickListener { onShortClick(item) }
        }
    }
}

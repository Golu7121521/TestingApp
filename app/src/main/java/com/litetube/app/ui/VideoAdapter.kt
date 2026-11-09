package com.litetube.app.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.litetube.app.data.VideoItem
import com.litetube.app.databinding.ItemVideoBinding

class VideoAdapter(private val onClick: (VideoItem) -> Unit) :
    RecyclerView.Adapter<VideoAdapter.VH>() {

    private val items = mutableListOf<VideoItem>()

    fun submit(list: List<VideoItem>) {
        items.clear()
        items.addAll(list)
        notifyDataSetChanged()
    }

    class VH(val b: ItemVideoBinding) : RecyclerView.ViewHolder(b.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH =
        VH(ItemVideoBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun getItemCount(): Int = items.size

    override fun onBindViewHolder(holder: VH, position: Int) {
        val item = items[position]
        val b = holder.b
        b.title.text = item.title
        b.meta.text = item.channel
        b.duration.text = formatDuration(item.durationSec)
        b.duration.visibility = if (item.durationSec > 0) View.VISIBLE else View.GONE
        Glide.with(b.thumb).load(item.thumbnail).centerCrop().into(b.thumb)
        b.root.setOnClickListener { onClick(item) }
    }
}

fun formatDuration(sec: Long): String = when {
    sec <= 0 -> ""
    sec >= 3600 -> "%d:%02d:%02d".format(sec / 3600, (sec % 3600) / 60, sec % 60)
    else -> "%d:%02d".format(sec / 60, sec % 60)
}

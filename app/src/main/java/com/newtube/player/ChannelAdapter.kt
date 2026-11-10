package com.newtube.player

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import coil.load
import coil.transform.CircleCropTransformation
import com.newtube.player.databinding.ItemChannelAvatarBinding

/** Subscriptions ke upar wali channel avatar row */
class ChannelAdapter(
    private val onClick: (Subscription) -> Unit,
    private val onLongClick: (Subscription) -> Unit
) : RecyclerView.Adapter<ChannelAdapter.ChannelViewHolder>() {

    private val items = mutableListOf<Subscription>()

    fun submitList(list: List<Subscription>) {
        items.clear()
        items.addAll(list)
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ChannelViewHolder {
        val binding = ItemChannelAvatarBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ChannelViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ChannelViewHolder, position: Int) {
        holder.bind(items[position])
    }

    override fun getItemCount(): Int = items.size

    inner class ChannelViewHolder(private val b: ItemChannelAvatarBinding) :
        RecyclerView.ViewHolder(b.root) {

        fun bind(sub: Subscription) {
            b.txtChannelName.text = sub.name
            b.txtChannelInitial.text = sub.name.firstOrNull()?.uppercaseChar()?.toString() ?: "?"
            if (sub.avatarUrl.isNotBlank()) {
                b.imgChannelAvatar.visibility = View.VISIBLE
                b.imgChannelAvatar.load(sub.avatarUrl) {
                    crossfade(true)
                    transformations(CircleCropTransformation())
                }
            } else {
                b.imgChannelAvatar.visibility = View.GONE
            }
            b.root.setOnClickListener { onClick(sub) }
            b.root.setOnLongClickListener {
                onLongClick(sub)
                true
            }
        }
    }
}

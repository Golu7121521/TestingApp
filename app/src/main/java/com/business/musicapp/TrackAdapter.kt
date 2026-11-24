package com.business.musicapp

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.business.musicapp.databinding.ItemTrackBinding

class TrackAdapter(
    private val onTrackClick: (Track, Int) -> Unit
) : RecyclerView.Adapter<TrackAdapter.TrackViewHolder>() {

    private val items = mutableListOf<Track>()
    private var activeTrackId: String? = null

    fun submitList(newTracks: List<Track>) {
        items.clear()
        items.addAll(newTracks)
        notifyDataSetChanged()
    }

    fun setActiveTrack(trackId: String?) {
        activeTrackId = trackId
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TrackViewHolder {
        val binding = ItemTrackBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return TrackViewHolder(binding)
    }

    override fun onBindViewHolder(holder: TrackViewHolder, position: Int) {
        holder.bind(items[position], position)
    }

    override fun getItemCount(): Int = items.size

    inner class TrackViewHolder(
        private val binding: ItemTrackBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(track: Track, position: Int) {
            val isCurrent = track.id == activeTrackId
            val ctx = binding.root.context

            binding.tvTrackTitle.text = track.title
            binding.tvTrackSubtitle.text = "${track.artist}  •  ${track.category}"
            binding.tvTrackDuration.text = track.durationLabel

            if (isCurrent) {
                binding.cardTrack.setCardBackgroundColor(
                    ContextCompat.getColor(ctx, R.color.surface_active)
                )
                binding.tvTrackTitle.setTextColor(
                    ContextCompat.getColor(ctx, R.color.accent_primary)
                )
                binding.ivPlayingIndicator.visibility = View.VISIBLE
            } else {
                binding.cardTrack.setCardBackgroundColor(
                    ContextCompat.getColor(ctx, R.color.surface_card)
                )
                binding.tvTrackTitle.setTextColor(
                    ContextCompat.getColor(ctx, R.color.text_primary)
                )
                binding.ivPlayingIndicator.visibility = View.GONE
            }

            binding.root.setOnClickListener {
                onTrackClick(track, position)
            }
        }
    }
}

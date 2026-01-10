package com.samsung.remote.adapter

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.samsung.remote.databinding.ItemTvBinding
import com.samsung.remote.model.SamsungTV

class TVListAdapter(
    private val onConnectClick: (SamsungTV) -> Unit
) : ListAdapter<SamsungTV, TVListAdapter.TVViewHolder>(TVDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TVViewHolder {
        val binding = ItemTvBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return TVViewHolder(binding)
    }

    override fun onBindViewHolder(holder: TVViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class TVViewHolder(
        private val binding: ItemTvBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(tv: SamsungTV) {
            binding.tvNameTextView.text = tv.name
            binding.tvIpTextView.text = tv.ip
            binding.connectButton.setOnClickListener {
                onConnectClick(tv)
            }
        }
    }

    private class TVDiffCallback : DiffUtil.ItemCallback<SamsungTV>() {
        override fun areItemsTheSame(oldItem: SamsungTV, newItem: SamsungTV): Boolean {
            return oldItem.ip == newItem.ip
        }

        override fun areContentsTheSame(oldItem: SamsungTV, newItem: SamsungTV): Boolean {
            return oldItem == newItem
        }
    }
}

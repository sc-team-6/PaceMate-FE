package com.gdg.scrollmanager.adapters

import android.content.Context
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.gdg.scrollmanager.databinding.ItemScrollAppBinding
import com.gdg.scrollmanager.fragments.ScrollAppInfo

class ScrollAppAdapter(private val context: Context) : 
    ListAdapter<ScrollAppInfo, ScrollAppAdapter.ScrollAppViewHolder>(ScrollAppDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ScrollAppViewHolder {
        val binding = ItemScrollAppBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ScrollAppViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ScrollAppViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    class ScrollAppViewHolder(private val binding: ItemScrollAppBinding) : 
        RecyclerView.ViewHolder(binding.root) {
        
        fun bind(scrollAppInfo: ScrollAppInfo) {
            binding.appNameText.text = scrollAppInfo.appName
            binding.packageNameText.text = scrollAppInfo.packageName
            binding.scrollDistanceText.text = formatScrollDistance(scrollAppInfo.scrollDistance)
            binding.percentageText.text = "${scrollAppInfo.percentage}%"
        }

        private fun formatScrollDistance(distance: Float): String {
            return when {
                distance >= 100000 -> "${String.format("%.1f", distance / 100000)}km"
                distance >= 100 -> "${String.format("%.1f", distance / 100)}m"
                else -> "${distance.toInt()}cm"
            }
        }
    }

    class ScrollAppDiffCallback : DiffUtil.ItemCallback<ScrollAppInfo>() {
        override fun areItemsTheSame(oldItem: ScrollAppInfo, newItem: ScrollAppInfo): Boolean {
            return oldItem.packageName == newItem.packageName
        }

        override fun areContentsTheSame(oldItem: ScrollAppInfo, newItem: ScrollAppInfo): Boolean {
            return oldItem == newItem
        }
    }
}
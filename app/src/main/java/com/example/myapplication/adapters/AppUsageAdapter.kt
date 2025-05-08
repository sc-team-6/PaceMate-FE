package com.example.myapplication.adapters

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.myapplication.databinding.ItemAppUsageBinding
import com.example.myapplication.models.AppUsageInfo
import java.util.concurrent.TimeUnit

class AppUsageAdapter : ListAdapter<AppUsageInfo, AppUsageAdapter.AppUsageViewHolder>(AppUsageDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): AppUsageViewHolder {
        val binding = ItemAppUsageBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return AppUsageViewHolder(binding)
    }

    override fun onBindViewHolder(holder: AppUsageViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    class AppUsageViewHolder(private val binding: ItemAppUsageBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(appUsageInfo: AppUsageInfo) {
            binding.appNameText.text = appUsageInfo.appName
            binding.packageNameText.text = appUsageInfo.packageName
            binding.usageTimeText.text = formatTime(appUsageInfo.timeInForeground)
            binding.colorIndicator.setBackgroundColor(appUsageInfo.color)
        }

        private fun formatTime(timeInMillis: Long): String {
            val hours = TimeUnit.MILLISECONDS.toHours(timeInMillis)
            val minutes = TimeUnit.MILLISECONDS.toMinutes(timeInMillis) % 60

            return when {
                hours > 0 -> String.format("%d시간 %d분", hours, minutes)
                minutes > 0 -> String.format("%d분", minutes)
                else -> "1분 미만"
            }
        }
    }

    class AppUsageDiffCallback : DiffUtil.ItemCallback<AppUsageInfo>() {
        override fun areItemsTheSame(oldItem: AppUsageInfo, newItem: AppUsageInfo): Boolean {
            return oldItem.packageName == newItem.packageName
        }

        override fun areContentsTheSame(oldItem: AppUsageInfo, newItem: AppUsageInfo): Boolean {
            return oldItem == newItem
        }
    }
}
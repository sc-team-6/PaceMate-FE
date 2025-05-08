package com.example.myapplication.fragments

import android.app.AppOpsManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.myapplication.databinding.FragmentAppUsageBinding
import com.example.myapplication.adapters.AppUsageAdapter
import com.example.myapplication.models.AppUsageInfo
import com.example.myapplication.utils.UsageStatsUtils

class AppUsageFragment : Fragment() {
    private var _binding: FragmentAppUsageBinding? = null
    private val binding get() = _binding!!
    private lateinit var adapter: AppUsageAdapter

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentAppUsageBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupRecyclerView()
        checkPermissionAndLoadData()
    }

    private fun setupRecyclerView() {
        adapter = AppUsageAdapter()
        binding.recyclerView.layoutManager = LinearLayoutManager(requireContext())
        binding.recyclerView.adapter = adapter
    }

    private fun checkPermissionAndLoadData() {
        if (hasUsageStatsPermission()) {
            loadUsageStats()
            binding.permissionLayout.visibility = View.GONE
            binding.contentLayout.visibility = View.VISIBLE
        } else {
            binding.permissionLayout.visibility = View.VISIBLE
            binding.contentLayout.visibility = View.GONE
            binding.requestPermissionButton.setOnClickListener {
                startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
            }
        }
    }

    private fun hasUsageStatsPermission(): Boolean {
        val appOps = requireContext().getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        val mode = appOps.checkOpNoThrow(
            AppOpsManager.OPSTR_GET_USAGE_STATS,
            android.os.Process.myUid(),
            requireContext().packageName
        )
        return mode == AppOpsManager.MODE_ALLOWED
    }

    private fun loadUsageStats() {
        val stats = UsageStatsUtils.getAppUsageStats(requireContext())
        adapter.submitList(stats)
        
        // 파이 차트에 데이터 설정 (필요에 따라 활성화/비활성화 할 수 있음)
        if (stats.isNotEmpty()) {
            val topApps = stats.sortedByDescending { it.timeInForeground }.take(5)
            binding.pieChart.setData(topApps)
        }
    }

    override fun onResume() {
        super.onResume()
        checkPermissionAndLoadData()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
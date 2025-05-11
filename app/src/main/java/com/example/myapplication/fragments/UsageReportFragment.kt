package com.example.myapplication.fragments

import android.app.AppOpsManager
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.cardview.widget.CardView
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.myapplication.R
import com.example.myapplication.adapters.AppUsageAdapter
import com.example.myapplication.databinding.FragmentUsageReportBinding
import com.example.myapplication.models.UsageReport
import com.example.myapplication.utils.DataStoreUtils
import com.example.myapplication.utils.UsageStatsUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.*
import java.util.concurrent.TimeUnit

class UsageReportFragment : Fragment() {
    private var _binding: FragmentUsageReportBinding? = null
    private val binding get() = _binding!!
    
    private lateinit var adapter: AppUsageAdapter
    private var usageReport: UsageReport? = null
    private var appSwitches: List<UsageStatsUtils.AppSwitchEvent> = emptyList()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentUsageReportBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupRecyclerView()
        setupNumberSummary() // 파이차트 대신 숫자 요약 화면 설정
        setupRefreshButton()
        checkPermissionAndLoadData()
    }

    private fun setupRecyclerView() {
        adapter = AppUsageAdapter()
        binding.recyclerView.layoutManager = LinearLayoutManager(requireContext())
        binding.recyclerView.adapter = adapter
    }
    
    private fun setupNumberSummary() {
        // 차트 대신 숫자 요약 뷰 초기화
        binding.appUsageSummaryTitle.text = "앱 사용 통계 요약"
        binding.appUsageSummaryDescription.text = "최근 5개 우선순위 앱 사용 통계"
        binding.appUsageSummaryContainer.visibility = View.VISIBLE
    }
    
    private fun setupRefreshButton() {
        binding.btnRefresh.setOnClickListener {
            checkPermissionAndLoadData()
        }
    }

    private fun checkPermissionAndLoadData() {
        if (hasUsageStatsPermission()) {
            binding.permissionLayout.visibility = View.GONE
            loadData()
        } else {
            binding.permissionLayout.visibility = View.VISIBLE
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

    private fun loadData() {
        binding.progressBar.visibility = View.VISIBLE
        
        viewLifecycleOwner.lifecycleScope.launch {
            val reportResult = generateUsageReport()
            usageReport = reportResult.first
            appSwitches = reportResult.second
            
            withContext(Dispatchers.Main) {
                updateUI()
                binding.progressBar.visibility = View.GONE
            }
        }
    }
    
    private suspend fun generateUsageReport(): Pair<UsageReport, List<UsageStatsUtils.AppSwitchEvent>> = withContext(Dispatchers.IO) {
        val context = requireContext()
        val appUsageList = UsageStatsUtils.getAppUsageStats(context)
        
        // 앱 전환 디버그 로그 추가
        val appSwitches = UsageStatsUtils.debugAppSwitchesIn15Min(context)
        Log.d("UsageReport", "Total app switches in last 15 minutes: ${appSwitches.size}")
        
        // 앱 전환 상세 로그 (CSV 형식)
        Log.d("UsageReport", "TIME,FROM_APP,TO_APP,DURATION_SEC")
        appSwitches.forEach { event ->
            Log.d("UsageReport", "${event.timestamp},${event.fromApp},${event.toApp},${event.durationSec}")
        }
        
        val usageTime15Min = UsageStatsUtils.getTotalUsageTimeInMinutes(context, 15)
        val usageTime30Min = UsageStatsUtils.getTotalUsageTimeInMinutes(context, 30)
        val usageTime60Min = UsageStatsUtils.getTotalUsageTimeInMinutes(context, 60)
        
        val unlockCount = UsageStatsUtils.getUnlockCount(context, 15)
        val appSwitchCount = UsageStatsUtils.getAppSwitchCount(context, 15)
        
        val mainAppCategory = UsageStatsUtils.getMainAppCategory(context)
        val socialAppCount = UsageStatsUtils.getSocialMediaAppUsageCount(context, 60)
        
        val averageSessionLength = UsageStatsUtils.getAverageSessionDuration(context)
        val dateTime = UsageStatsUtils.getCurrentDateTime()
        
        // 스크롤 거리 가져오기
        val scrollDistance = DataStoreUtils.getTotalScrollDistance(context)
        
        val report = UsageReport(
            usageTime15Min = usageTime15Min,
            usageTime30Min = usageTime30Min,
            usageTime60Min = usageTime60Min,
            unlockCount15Min = unlockCount,
            appSwitchCount15Min = appSwitchCount,
            mainAppCategory = mainAppCategory,
            socialAppCount = socialAppCount,
            averageSessionLength = averageSessionLength,
            dateTime = dateTime,
            scrollDistance = scrollDistance,
            appUsageList = appUsageList
        )
        
        return@withContext Pair(report, appSwitches)
    }
    
    private fun updateUI() {
        usageReport?.let { report ->
            // 날짜/시간 업데이트
            binding.tvDateTime.text = report.dateTime
            
            // 사용 시간 업데이트
            binding.tvUsageTime15.text = "${report.usageTime15Min}분"
            binding.tvUsageTime30.text = "${report.usageTime30Min}분"
            binding.tvUsageTime60.text = "${report.usageTime60Min}분"
            
            // 상호작용 업데이트
            binding.tvUnlockCount.text = "${report.unlockCount15Min}회"
            binding.tvAppSwitchCount.text = "${report.appSwitchCount15Min}회"
            binding.tvSocialAppCount.text = "${report.socialAppCount}회"
            
            // 사용 패턴 업데이트
            binding.tvMainCategory.text = report.mainAppCategory
            
            val sessionTime = formatSessionTime(report.averageSessionLength)
            binding.tvAvgSession.text = sessionTime
            
            // 스크롤 거리 업데이트
            val scrollDistance = DataStoreUtils.formatScrollDistance(report.scrollDistance)
            binding.tvScrollDistance.text = scrollDistance
            
            // 접근성 서비스가 활성화되어 있지 않으면 스크롤 거리에 알림 표시
            if (!isAccessibilityServiceEnabled() && report.scrollDistance == 0f) {
                binding.tvScrollDistance.text = "접근성 설정 필요"
                binding.tvScrollDistance.setTextColor(Color.RED)
                binding.tvScrollDistance.setOnClickListener {
                    // 접근성 설정 화면으로 이동
                    val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
                    startActivity(intent)
                }
            } else {
                binding.tvScrollDistance.setTextColor(Color.BLACK)
                binding.tvScrollDistance.setOnClickListener(null)
            }
            
            // 앱별 사용 현황 업데이트
            updateTopAppsInfo(report) // 파이차트 대신 숫자 정보 표시
            adapter.submitList(report.appUsageList)
            
            // 앱 전환 디버그 정보 업데이트
            updateAppSwitchesDebug()
        }
    }
    
    // 접근성 서비스 활성화 여부 확인
    private fun isAccessibilityServiceEnabled(): Boolean {
        val accessibilityManager = requireContext().getSystemService(Context.ACCESSIBILITY_SERVICE) as android.view.accessibility.AccessibilityManager
        val enabledServices = Settings.Secure.getString(
            requireContext().contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: return false
        
        val serviceName = requireContext().packageName + "/.ScrollAccessibilityService"
        return enabledServices.contains(serviceName)
    }
    
    private fun updateAppSwitchesDebug() {
        // 이전 정보 삭제
        binding.debugAppSwitchesContainer.removeAllViews()
        
        if (appSwitches.isEmpty()) {
            val textView = TextView(requireContext())
            textView.text = "최근 15분 동안 앱 전환 기록이 없습니다."
            textView.textSize = 14f
            textView.setTextColor(Color.GRAY)
            binding.debugAppSwitchesContainer.addView(textView)
            return
        }
        
        // 헤더 추가
        val headerView = layoutInflater.inflate(
            R.layout.item_debug_app_switch,
            binding.debugAppSwitchesContainer,
            false
        )
        
        headerView.findViewById<TextView>(R.id.tv_time).text = "시간"
        headerView.findViewById<TextView>(R.id.tv_from_app).text = "이전 앱"
        headerView.findViewById<TextView>(R.id.tv_to_app).text = "전환 앱"
        headerView.findViewById<TextView>(R.id.tv_duration).text = "사용 시간"
        
        // 헤더 텍스트 스타일 설정
        headerView.findViewById<TextView>(R.id.tv_time).setTypeface(null, Typeface.BOLD)
        headerView.findViewById<TextView>(R.id.tv_from_app).setTypeface(null, Typeface.BOLD)
        headerView.findViewById<TextView>(R.id.tv_to_app).setTypeface(null, Typeface.BOLD)
        headerView.findViewById<TextView>(R.id.tv_duration).setTypeface(null, Typeface.BOLD)
        
        binding.debugAppSwitchesContainer.addView(headerView)
        
        // 구분선 추가
        val divider = View(requireContext())
        divider.layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, 
            resources.getDimensionPixelSize(R.dimen.divider_height)
        )
        divider.setBackgroundColor(Color.LTGRAY)
        binding.debugAppSwitchesContainer.addView(divider)
        
        // 최대 15개만 표시
        val displaySwitches = appSwitches.take(15)
        
        // 앱 전환 항목 추가
        for (appSwitch in displaySwitches) {
            val itemView = layoutInflater.inflate(
                R.layout.item_debug_app_switch,
                binding.debugAppSwitchesContainer,
                false
            )
            
            itemView.findViewById<TextView>(R.id.tv_time).text = appSwitch.timestamp
            itemView.findViewById<TextView>(R.id.tv_from_app).text = getDisplayAppName(appSwitch.fromApp)
            itemView.findViewById<TextView>(R.id.tv_to_app).text = getDisplayAppName(appSwitch.toApp)
            itemView.findViewById<TextView>(R.id.tv_duration).text = formatDuration(appSwitch.durationSec)
            
            binding.debugAppSwitchesContainer.addView(itemView)
        }
        
        // 더 많은 항목이 있으면 메시지 표시
        if (appSwitches.size > 15) {
            val moreItemsView = TextView(requireContext())
            moreItemsView.text = "... 외 ${appSwitches.size - 15}개의 항목이 더 있습니다."
            moreItemsView.textSize = 12f
            moreItemsView.setTextColor(Color.GRAY)
            moreItemsView.gravity = Gravity.CENTER
            moreItemsView.setPadding(0, 8, 0, 0)
            binding.debugAppSwitchesContainer.addView(moreItemsView)
        }
    }
    
    private fun getDisplayAppName(appName: String): String {
        // 앱 이름이 너무 길면 줄임
        return if (appName.length > 15) {
            "${appName.substring(0, 13)}..."
        } else {
            appName
        }
    }
    
    private fun formatDuration(seconds: Long): String {
        return when {
            seconds >= 3600 -> {
                val hours = seconds / 3600
                val minutes = (seconds % 3600) / 60
                "${hours}시간 ${minutes}분"
            }
            seconds >= 60 -> {
                val minutes = seconds / 60
                "${minutes}분"
            }
            else -> {
                "${seconds}초"
            }
        }
    }
    
    private fun formatSessionTime(seconds: Float): String {
        return when {
            seconds >= 3600 -> {
                val hours = (seconds / 3600).toInt()
                val minutes = ((seconds % 3600) / 60).toInt()
                "${hours}시간 ${minutes}분"
            }
            seconds >= 60 -> {
                val minutes = (seconds / 60).toInt()
                "${minutes}분"
            }
            else -> "${seconds.toInt()}초"
        }
    }
    
    private fun updateTopAppsInfo(report: UsageReport) {
        // 상위 5개 앱만 선택
        val topApps = report.appUsageList
            .sortedByDescending { it.timeInForeground }
            .take(5)
        
        // 기존 뷰 제거
        binding.appUsageTopContainer.removeAllViews()
        
        // 앱별 사용 통계 추가
        for (app in topApps) {
            val appInfoView = layoutInflater.inflate(
                R.layout.item_top_app_usage, 
                binding.appUsageTopContainer, 
                false
            )
            
            // 앱 정보 설정
            val appNameView = appInfoView.findViewById<TextView>(R.id.app_name)
            val appTimeView = appInfoView.findViewById<TextView>(R.id.app_time)
            val colorIndicator = appInfoView.findViewById<CardView>(R.id.color_indicator)
            
            appNameView.text = app.appName
            appTimeView.text = formatTimeInForeground(app.timeInForeground)
            colorIndicator.setCardBackgroundColor(app.color)
            
            binding.appUsageTopContainer.addView(appInfoView)
        }
    }
    
    private fun formatTimeInForeground(timeInMillis: Long): String {
        val hours = TimeUnit.MILLISECONDS.toHours(timeInMillis)
        val minutes = TimeUnit.MILLISECONDS.toMinutes(timeInMillis) % 60
        val seconds = TimeUnit.MILLISECONDS.toSeconds(timeInMillis) % 60

        return when {
            hours > 0 -> String.format("%d시간 %d분", hours, minutes)
            minutes > 0 -> String.format("%d분 %d초", minutes, seconds)
            else -> String.format("%d초", seconds)
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
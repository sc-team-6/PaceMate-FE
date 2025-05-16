package com.gdg.scrollmanager.fragments

import android.app.AppOpsManager
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.Log
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.cardview.widget.CardView
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.gdg.scrollmanager.R
import com.gdg.scrollmanager.adapters.AppUsageAdapter
import com.gdg.scrollmanager.databinding.FragmentUsageReportBinding
import com.gdg.scrollmanager.models.UsageReport
import com.gdg.scrollmanager.utils.DataStoreUtils
import com.gdg.scrollmanager.utils.UsageStatsUtils
import com.gdg.scrollmanager.ml.PhoneUsagePredictor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
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
    
    // 휴대폰 사용 중독 예측기
    private lateinit var phoneUsagePredictor: PhoneUsagePredictor
    
    // 5초마다 예측 실행을 위한 핸들러
    private val handler = Handler(Looper.getMainLooper())
    private var predictionRunnable: Runnable? = null

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
        setupDataObservers() // Flow 관찰자 설정
        initPhoneUsagePredictor() // ONNX 모델 초기화
        checkPermissionAndLoadData()
        
        // 5초마다 예측 실행
        startPeriodicPrediction()
    }
    
    private fun setupDataObservers() {
        viewLifecycleOwner.lifecycleScope.launch {
            // 데이터 수집 진행 상황 관찰
            DataStoreUtils.getDataCollectionProgressFlow(requireContext()).collect { progress ->
                withContext(Dispatchers.Main) {
                    // 데이터 수집 진행 상황 표시
                    val progressText = if (progress < 100) {
                        "데이터 수집 중: $progress%"
                    } else {
                        "데이터 분석 완료"
                    }
                    binding.tvDataProgress.text = progressText
                    binding.tvDataProgress.visibility = View.VISIBLE
                    
                    // 항상 중독 상태 표시 (부분 데이터로도 예측 결과 보여줌)
                    binding.tvAddictionStatus.visibility = View.VISIBLE
                    binding.tvAddictionProbability.visibility = View.VISIBLE
                    
                    // 진행도에 상관없이 항상 예측 결과 표시 (제한 없음)
                    // 예측 결과는 setupDataObservers에서 처리
                    
                }
            }
        }
        
        viewLifecycleOwner.lifecycleScope.launch {
            // 예측 결과 관찰
            DataStoreUtils.getPredictionResultFlow(requireContext()).collect { predictionPair ->
                withContext(Dispatchers.Main) {
                    updateAddictionUI(predictionPair)
                }
            }
        }
    }
    
    private fun initPhoneUsagePredictor() {
        phoneUsagePredictor = PhoneUsagePredictor(requireContext())
        phoneUsagePredictor.initialize()
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

    /**
     * 현재 서비스에서 수집한 데이터를 사용하여 UI를 업데이트합니다.
     * 직접 데이터를 수집하는 대신 서비스의 데이터를 사용합니다.
     */
    private fun loadData() {
        binding.progressBar.visibility = View.VISIBLE
        
        viewLifecycleOwner.lifecycleScope.launch {
            // 서비스에서 처리중인 처리를 조금 일찌시키기 위해 15분 내 상위 앱 정보를 검색
            val topPackages = UsageStatsUtils.getTopUsedPackages(requireContext(), 15, 5)
            Log.d("UsageReportFragment", "최근 15분 동안 가장 많이 사용된 상위 앱 패키지: ${topPackages.joinToString()}")
            
            // 임베딩 계산을 일치시키기 위해 임베딩을 계산하고 DataStore에 저장
            if (topPackages.isNotEmpty() && ::phoneUsagePredictor.isInitialized) {
                try {
                    // 서비스와 동일한 방식으로 임베딩 계산
                    val appEmbedding = phoneUsagePredictor.getAverageEmbeddingForPackages(topPackages)
                    
                    // 기존 모델 입력값 가져오기
                    val modelInputJson = withContext(Dispatchers.IO) {
                        DataStoreUtils.getModelInputFlow(requireContext()).first()
                    }
                    
                    if (modelInputJson != null) {
                        // 임베딩 값만 업데이트하여 모델 입력 재사용
                        // 이 부분은 장식적으로 서비스의 다음 예측 시 사용됨
                        Log.d("UsageReportFragment", "기존 모델 입력값 임베딩 업데이트")
                    }
                    
                    // 서비스의 다음 예측 실행 공유를 위해 5초 스레드를 일찌
                    // 최신 예측 결과를 가져오기 위해 서비스의 runPrediction을 직접 호출하는 대신 시간 지연
                    // 실제 runPrediction은 서비스 내부에서 5초마다 자시 호출됨
                    delay(300) // 0.3초 지연
                } catch (e: Exception) {
                    Log.e("UsageReportFragment", "임베딩 계산 오류: ${e.message}")
                }
            }
            
            // DataStore에서 최신 데이터 가져오기
            val latestUsageReport = DataStoreUtils.getLatestUsageReportFlow(requireContext()).first()
            if (latestUsageReport != null) {
                usageReport = latestUsageReport
            }
            
            // 앱 전환 디버그 로그 가져오기 (추가 정보용)
            appSwitches = UsageStatsUtils.debugAppSwitchesIn15Min(requireContext())
            
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
        
        // 사용한 앱 패키지 목록 추출
        val recentAppPackages = appUsageList.map { it.packageName }.take(5)
        Log.d("UsageReport", "Recent app packages: $recentAppPackages")
        
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
            appUsageList = appUsageList,
            recentAppPackages = recentAppPackages
        )
        
        return@withContext Pair(report, appSwitches)
    }
    
    private fun updateUI() {
        usageReport?.let { report ->
            // 날짜/시간 업데이트
            binding.tvDateTime.text = report.dateTime
            
            // 데이터 수집 진행 상황 표시
            binding.tvDataProgress.visibility = View.VISIBLE
            
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
            
            // 중독 예측 결과는 직접 계산하지 않고, DataStore의 값을 사용
            // analyzeAddiction(report) 제거
            
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
    
    /**
     * ONNX 모델을 사용하여 휴대폰 사용 중독 여부 분석
     */
    private fun analyzeAddiction(report: UsageReport) {
        viewLifecycleOwner.lifecycleScope.launch(Dispatchers.Default) {
            // 현재 시간 관련 데이터 가져오기
            val calendar = Calendar.getInstance()
            val hour = calendar.get(Calendar.HOUR_OF_DAY)
            val dayOfWeek = calendar.get(Calendar.DAY_OF_WEEK) - 1 // Calendar.SUNDAY(1)부터 시작하므로 0-6으로 변환
            
            // 스크롤 관련 데이터 계산
            val scrollLength = report.scrollDistance.toInt() // 총 스크롤 길이
            
            // 비율 계산 (변화율)
            val unlockRate: Float = if (report.usageTime15Min > 0) {
                report.unlockCount15Min.toFloat() / report.usageTime15Min.toFloat()
            } else 0f
            
            val switchRate: Float = if (report.usageTime15Min > 0) {
                report.appSwitchCount15Min.toFloat() / report.usageTime15Min.toFloat()
            } else 0f
            
            val scrollRate: Float = if (report.usageTime60Min > 0 && scrollLength > 0) {
                scrollLength.toFloat() / (report.usageTime60Min.toFloat() * 60f) // 초당 스크롤 길이
            } else 0f
            
            // 로그로 값 확인
            Log.d("AddictionPredictor", "Input values:")
            Log.d("AddictionPredictor", "recent15minUsage: ${report.usageTime15Min}")
            Log.d("AddictionPredictor", "recent30minUsage: ${report.usageTime30Min}")
            Log.d("AddictionPredictor", "recent60minUsage: ${report.usageTime60Min}")
            Log.d("AddictionPredictor", "unlocks15min: ${report.unlockCount15Min}")
            Log.d("AddictionPredictor", "appSwitches15min: ${report.appSwitchCount15Min}")
            Log.d("AddictionPredictor", "snsAppUsage: ${report.socialAppCount}")
            Log.d("AddictionPredictor", "avgSessionLength: ${report.averageSessionLength}")
            Log.d("AddictionPredictor", "hour: ${hour}")
            Log.d("AddictionPredictor", "dayOfWeek: ${dayOfWeek}")
            Log.d("AddictionPredictor", "scrollLength: ${scrollLength}")
            Log.d("AddictionPredictor", "unlockRate: ${unlockRate}")
            Log.d("AddictionPredictor", "switchRate: ${switchRate}")
            Log.d("AddictionPredictor", "scrollRate: ${scrollRate}")
            Log.d("AddictionPredictor", "topAppCategory: ${report.mainAppCategory}")
            
            // 테스트용 고정 값을 사용하여 예측할 경우 아래 주석을 해제하세요
            /*
            // ONNX 모델 예측 실행 (테스트용 고정 값)
            val result = phoneUsagePredictor.predict(
                recent15minUsage = 3,
                recent30minUsage = 7,
                recent60minUsage = 20,
                unlocks15min = 4,
                appSwitches15min = 8,
                snsAppUsage = 3,
                avgSessionLength = 1.0f,
                hour = 10,
                dayOfWeek = 2,
                scrollLength = 400,
                unlockRate = 0.5f,
                switchRate = 0.25f,
                scrollRate = 0.1f,
                topAppCategory = "Utility"
            )
            */
            
            // 실제 측정 데이터 기반 예측
            val result = phoneUsagePredictor.predict(
                recent15minUsage = report.usageTime15Min,
                recent30minUsage = report.usageTime30Min,
                recent60minUsage = report.usageTime60Min,
                unlocks15min = report.unlockCount15Min,
                appSwitches15min = report.appSwitchCount15Min,
                snsAppUsage = report.socialAppCount,
                avgSessionLength = report.averageSessionLength,
                hour = hour,
                dayOfWeek = dayOfWeek,
                scrollLength = scrollLength,
                unlockRate = unlockRate,
                switchRate = switchRate,
                scrollRate = scrollRate,
                topAppCategory = report.mainAppCategory,
                recentApps = report.recentAppPackages
            )
            
            // ONNX 모델의 Pair<FloatArray, Int>를 DataStore가 저장하는 형식인 Pair<Int, Float>로 변환
            val prediction = result.second
            val probability = result.first[1] * 100f // 확률 백분율로 변환
            
            // 예측 결과를 DataStore에 저장
            try {
                DataStoreUtils.savePredictionResult(requireContext(), prediction, probability)
                Log.d("AddictionPredictor", "예측 결과 저장 성공: 예측=$prediction, 확률=$probability%")
            } catch (e: Exception) {
                Log.e("AddictionPredictor", "예측 결과 저장 오류: ${e.message}")
            }
            
            // UI 업데이트는 메인 스레드에서 실행
            withContext(Dispatchers.Main) {
                updateAddictionUI(Pair(prediction, probability))
            }
        }
    }
    
    /**
     * 중독 분석 결과 UI 업데이트
     * @param result Pair(예측결과, 확률) - 첫 번째는 분류 결과 (0: 정상, 1: 중독), 두 번째는 중독 확률(0-100%)
     */
    private fun updateAddictionUI(result: Pair<Int, Float>) {
        // 데이터 수집 진행 상황 확인
        viewLifecycleOwner.lifecycleScope.launch(Dispatchers.Main) {
            val progress = DataStoreUtils.getDataCollectionProgress(requireContext())
            
            // 모든 데이터 진행도에서 예측 표시 (제한 없음)
            updateAddictionUIWithProgress(result, progress)
        }
    }
    
    private fun updateAddictionUIWithProgress(result: Pair<Int, Float>, dataProgress: Int) {
        val prediction = result.first
        val probability = result.second.toInt()
        
        // 중독 상태 표시
        when (prediction) {
            0 -> {
                binding.tvAddictionStatus.text = "정상 사용 패턴"
                binding.tvAddictionStatus.setTextColor(Color.parseColor("#4CAF50")) // 녹색
            }
            1 -> {
                binding.tvAddictionStatus.text = "중독 위험 패턴"
                binding.tvAddictionStatus.setTextColor(Color.parseColor("#F44336")) // 빨간색
            }
        }
        
        // 확률 표시
        binding.tvAddictionProbability.text = "(${probability}%)"
        
        // 조언 표시 (데이터 진행도에 따라 신뢰도 정보 추가)
        val advice = when {
            probability >= 80 -> "심각한 중독 수준입니다. 스마트폰 사용을 제한하고 전문가의 도움을 받아보세요."
            probability >= 60 -> "중독 위험이 높습니다. 사용 시간을 줄이고 정기적인 휴식이 필요합니다."
            probability >= 40 -> "주의가 필요한 단계입니다. 사용 패턴을 모니터링하고 SNS 앱 사용을 줄여보세요."
            probability >= 20 -> "약간의 주의가 필요합니다. 취침 전 스마트폰 사용을 자제하세요."
            else -> "건강한 사용 패턴입니다. 현재 수준을 유지하세요."
        }
        
        // 데이터 수집 정도에 따른 신뢰도 정보 추가 (지표용, 모든 데이터 진행도에서 결과 표시)
        val reliabilityInfo = when {
            dataProgress < 10 -> "\n\n(신뢰도: 매우 낮음 - 데이터 수집 초기 단계)"
            dataProgress < 50 -> "\n\n(신뢰도: 낮음 - 데이터 수집 진행 중)"
            dataProgress < 90 -> "\n\n(신뢰도: 중간 - 현재 데이터를 기반으로 한 분석입니다)"
            else -> "\n\n(신뢰도: 높음 - 충분한 데이터 기반 분석)"  // 90% 이상일 때도 신뢰도 표시
        }
        
        binding.tvAddictionAdvice.text = advice + reliabilityInfo
    }

    /**
     * 5초마다 예측을 실행하는 기능을 시작합니다.
     */
    private fun startPeriodicPrediction() {
        // 이전 예약을 먼저 취소
        stopPeriodicPrediction()
        
        predictionRunnable = Runnable {
            // 최신 사용 데이터를 가져와 중독 예측 실행
            viewLifecycleOwner.lifecycleScope.launch {
                val report = DataStoreUtils.getLatestUsageReportFlow(requireContext()).first()
                if (report != null) {
                    // 예측 실행
                    analyzeAddiction(report)
                    Log.d("UsageReportFragment", "5초 주기 예측 실행: 시간 ${System.currentTimeMillis()}")
                }
                
                // 다음 업데이트 예약 (5초 후)
                handler.postDelayed(predictionRunnable!!, 5000)
            }
        }
        
        // 첫 번째 업데이트 예약
        handler.postDelayed(predictionRunnable!!, 1000) // 1초 후 바로 실행
    }
    
    /**
     * 예측 주기 실행을 중지합니다.
     */
    private fun stopPeriodicPrediction() {
        predictionRunnable?.let {
            handler.removeCallbacks(it)
            predictionRunnable = null
        }
    }
    
    override fun onResume() {
        super.onResume()
        checkPermissionAndLoadData()
        
        // 화면이 다시 보일 때 주기 예측 재시작
        startPeriodicPrediction()
    }
    
    override fun onPause() {
        super.onPause()
        
        // 화면이 안 보일 때 주기 예측 중지
        stopPeriodicPrediction()
    }
    
    override fun onDestroyView() {
        super.onDestroyView()
        
        // 주기 예측 중지
        stopPeriodicPrediction()
        
        if (::phoneUsagePredictor.isInitialized) {
            phoneUsagePredictor.close()
        }
        _binding = null
    }
}
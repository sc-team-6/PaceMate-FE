package com.gdg.scrollmanager.fragments

import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.fragment.app.Fragment
import com.gdg.scrollmanager.databinding.FragmentHomeBinding
import com.gdg.scrollmanager.utils.PreferenceManager
import com.gdg.scrollmanager.views.ArcProgressView
import java.text.SimpleDateFormat
import java.util.*
import androidx.core.graphics.toColorInt
import android.animation.ValueAnimator
import android.app.usage.UsageEvents
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.animation.DecelerateInterpolator
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.lifecycle.lifecycleScope
import com.gdg.scrollmanager.models.AppUsageInfo
import com.gdg.scrollmanager.utils.DataStoreUtils
import com.gdg.scrollmanager.utils.UsageStatsUtils
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class HomeFragment : Fragment() {
    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!

    // 설문으로부터 계산된 임계값(Alert Level)
    private var alertThresholdValue: Int = 60
    
    // 현재 Overrun Score (중독 확률)
    private var currentScoreValue: Int = 0
    
    // 게이지의 최소값과 최대값
    private var minScore: Int = 0
    private var maxScore: Int = 0
    
    // 프리퍼런스 매니저 참조
    private lateinit var prefManager: PreferenceManager
    
    // 5초마다 데이터를 갱신하기 위한 핸들러
    private val handler = Handler(Looper.getMainLooper())
    private var updateRunnable: Runnable? = null
    
    // ArcProgressView 참조 저장
    private var arcProgressView: ArcProgressView? = null
    
    // 앱 사용 데이터 저장
    private var appUsageList: List<AppUsageInfo> = emptyList()
    
    // 아이콘 캐시
    private val appIconCache = mutableMapOf<String, Drawable>()
    
    // 영속성 저장을 위한 컴파닌 클래스
    companion object {
        private const val PREF_MAX_SCORE = "max_score"
        private const val PREF_MIN_SCORE = "min_score"
        // 앱 사용 데이터 로드
        loadAppUsageData()
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentHomeBinding.inflate(inflater, container, false)
        return binding.root as ConstraintLayout
        // 앱 사용 데이터 로드
        loadAppUsageData()
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        // 설문에서 계산된 임계값 가져오기
        loadAlertThreshold()
        
        // 최신 중독 확률 가져오기
        loadAddictionProbability()
        
        setupUI()
        
        // 5초마다 데이터 갱신 시작
        startDataUpdates()
        // 앱 사용 데이터 로드
        loadAppUsageData()
    }
    
    /**
     * PreferenceManager에서 임계값을 가져와 사용합니다.
     */
    private fun loadAlertThreshold() {
        // PreferenceManager 인스턴스 생성
        prefManager = PreferenceManager(requireContext())
        
        // 저장된 알림 임계값을 가져옴
        alertThresholdValue = prefManager.getAlertThreshold()
        
        Log.d("HomeFragment", "Loaded alert threshold: $alertThresholdValue%")
        // 앱 사용 데이터 로드
        loadAppUsageData()
    }
    
    /**
     * DataStore에서 최신 중독 확률을 가져옵니다.
     */
    private fun loadAddictionProbability() {
        if (view == null || !isAdded) return  // View가 없거나 Fragment가 경로에 추가되지 않은 경우 실행하지 않음
        
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                // DataStore에서 최신 예측 결과 가져오기
                val predictionResult = DataStoreUtils.getPredictionResultFlow(requireContext()).first()
                
                // 확률 값 추출 (0-100 사이의 값으로 변환)
                val probability = predictionResult.second.toInt()
                
                Log.d("HomeFragment", "Loaded addiction probability: $probability%")
                
                // 항상 최소/최대값 업데이트 먼저 수행
                // 현재 스코어가 변경되기 전에 먼저 최소/최대값 업데이트
                prefManager.updateMinScore24h(probability)
                prefManager.updateMaxScore24h(probability)
                
                // 현재 스코어 값 업데이트
                if (probability != currentScoreValue) {
                    val oldValue = currentScoreValue
                    currentScoreValue = probability
                    
                    // UI가 이미 초기화되었고 Fragment가 여전히 활성 상태인지 확인
                    if (_binding != null && isAdded && view != null) {
                        updateUIWithNewScore(oldValue, probability)
                        // 앱 사용 데이터 로드
        loadAppUsageData()
    }
                    // 앱 사용 데이터 로드
        loadAppUsageData()
    } else {
                    // 값이 변경되지 않았더라도 MIN/MAX 표시는 업데이트
                    updateMinMaxDisplay()
                    // 앱 사용 데이터 로드
        loadAppUsageData()
    }
                // 앱 사용 데이터 로드
        loadAppUsageData()
    } catch (e: Exception) {
                Log.e("HomeFragment", "Error loading addiction probability: ${e.message}")
                
                // 문제가 있을 경우 0으로 설정
                currentScoreValue = 0
                // 앱 사용 데이터 로드
        loadAppUsageData()
    }
            // 앱 사용 데이터 로드
        loadAppUsageData()
    }
        // 앱 사용 데이터 로드
        loadAppUsageData()
    }
    
    /**
     * 최소/최대 표시만 업데이트합니다 (현재 값 변경 없이)
     */
    private fun updateMinMaxDisplay() {
        if (_binding == null) return
        
        // 저장된 최소/최대 값 가져오기
        val newMinScore = prefManager.getMinScore24h(currentScoreValue)
        val newMaxScore = prefManager.getMaxScore24h(currentScoreValue)
        
        // 값이 변경되었으면 UI 업데이트
        if (newMinScore != minScore) {
            try {
                val currentMinText = binding.tvMin.text.toString().replace("%", "")
                val currentMinValue = if (currentMinText.isEmpty()) 0 else currentMinText.toInt()
                animateTextValue(binding.tvMin, currentMinValue, newMinScore, "%")
                minScore = newMinScore
                Log.d("HomeFragment", "Updated minScore display: $minScore")
                // 앱 사용 데이터 로드
        loadAppUsageData()
    } catch (e: Exception) {
                binding.tvMin.text = "$newMinScore%"
                // 앱 사용 데이터 로드
        loadAppUsageData()
    }
            // 앱 사용 데이터 로드
        loadAppUsageData()
    }
        
        if (newMaxScore != maxScore) {
            try {
                val currentMaxText = binding.tvMax.text.toString().replace("%", "")
                val currentMaxValue = if (currentMaxText.isEmpty()) 0 else currentMaxText.toInt()
                animateTextValue(binding.tvMax, currentMaxValue, newMaxScore, "%")
                maxScore = newMaxScore
                Log.d("HomeFragment", "Updated maxScore display: $maxScore")
                // 앱 사용 데이터 로드
        loadAppUsageData()
    } catch (e: Exception) {
                binding.tvMax.text = "$newMaxScore%"
                // 앱 사용 데이터 로드
        loadAppUsageData()
    }
            // 앱 사용 데이터 로드
        loadAppUsageData()
    }
        // 앱 사용 데이터 로드
        loadAppUsageData()
    }
    
    /**
     * 새 스코어 값으로 UI를 업데이트합니다.
     */
    private fun updateUIWithNewScore(oldValue: Int, newValue: Int) {
        if (_binding == null) return  // binding이 null이면 실행하지 않음
        
        // 상태 텍스트 및 색상 결정 (현재 Overrun Score 기준)
        val (statusText, colorHex) = when {
            newValue <= 33 -> "Cruising Mode" to "#76F376" // 초록
            newValue <= 66 -> "Warming Up" to "#FFDE58"    // 노랑
            else -> "Overrun Point" to "#C42727"           // 빨강
            // 앱 사용 데이터 로드
        loadAppUsageData()
    }

        // 현재 Overrun Score 텍스트 업데이트 (애니메이션)
        animateTextValue(binding.tvPercentage, oldValue, newValue, "%")
        binding.tvStatus.text = statusText

        // 상태 텍스트 배경 둥글게 설정
        val statusBg = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = 100f
            setColor(Color.parseColor(colorHex))
            // 앱 사용 데이터 로드
        loadAppUsageData()
    }
        binding.tvStatus.background = statusBg

        binding.yellowBackground.setBackgroundColor(Color.parseColor(colorHex))

        // 텍스트 색상 설정
        val isOverrun = newValue >= 67
        val textColorHex = if (isOverrun) "#FFFFFF" else "#5E4510"
        val subtitleColorHex = if (isOverrun) "#F8F8F8" else "#9D8A70" 
        val percentageColorHex = if (isOverrun) "#C42727" else "#000000"

        binding.tvDate.setTextColor(textColorHex.toColorInt())
        binding.tvOverrunTitle.setTextColor(textColorHex.toColorInt())
        binding.divider.setBackgroundColor(textColorHex.toColorInt())
        binding.tvOverrunSubtitle.setTextColor(subtitleColorHex.toColorInt())
        binding.tvPercentage.setTextColor(percentageColorHex.toColorInt())
        binding.tvMax.setTextColor(colorHex.toColorInt())

        // 최소값과 최대값 업데이트
        updateMinMaxValues(newValue)

        // ArcProgressView 애니메이션 업데이트
        arcProgressView?.let { arcView ->
            // 부드럽게 차오르는 애니메이션
            ValueAnimator.ofFloat(oldValue.toFloat(), newValue.toFloat()).apply {
                duration = 1000L
                interpolator = DecelerateInterpolator()
                addUpdateListener {
                    if (_binding == null) {
                        // 애니메이션 도중에 binding이 null이 되었으면 애니메이션 취소
                        cancel()
                        return@addUpdateListener
                        // 앱 사용 데이터 로드
        loadAppUsageData()
    }
                    arcView.percentage = (it.animatedValue as Float)
                    // 앱 사용 데이터 로드
        loadAppUsageData()
    }
                start()
                // 앱 사용 데이터 로드
        loadAppUsageData()
    }
            
            // 색상 업데이트
            arcView.setFixedColor(colorHex)
            // 앱 사용 데이터 로드
        loadAppUsageData()
    }
        // 앱 사용 데이터 로드
        loadAppUsageData()
    }
    
    /**
     * 텍스트 값을 애니메이션으로 변경합니다.
     * binding이 null이 아닌지 확인하여 안전하게 처리합니다.
     */
    private fun animateTextValue(textView: View, startValue: Int, endValue: Int, suffix: String = "") {
        if (_binding == null) return  // binding이 null이면 실행하지 않음
        
        ValueAnimator.ofInt(startValue, endValue).apply {
            duration = 1000
            interpolator = DecelerateInterpolator()
            addUpdateListener { animation ->
                if (_binding == null) {
                    // 애니메이션 도중에 binding이 null이 되었으면 애니메이션 취소
                    cancel()
                    return@addUpdateListener
                    // 앱 사용 데이터 로드
        loadAppUsageData()
    }
                
                val value = animation.animatedValue as Int
                when (textView) {
                    binding.tvPercentage -> binding.tvPercentage.text = "$value$suffix"
                    binding.tvMin -> binding.tvMin.text = "$value$suffix"
                    binding.tvMax -> binding.tvMax.text = "$value$suffix"
                    // 앱 사용 데이터 로드
        loadAppUsageData()
    }
                // 앱 사용 데이터 로드
        loadAppUsageData()
    }
            start()
            // 앱 사용 데이터 로드
        loadAppUsageData()
    }
        // 앱 사용 데이터 로드
        loadAppUsageData()
    }
    
    /**
     * 최소값과 최대값을 조정하고 표시합니다.
     * 24시간 내의 최소/최대값을 유지합니다.
     */
    private fun updateMinMaxValues(currentValue: Int) {
        if (_binding == null) return  // binding이 null이면 실행하지 않음
        
        Log.d("HomeFragment", "updateMinMaxValues - Current: $currentValue, Min: $minScore, Max: $maxScore")
        
        // 24시간 동안의 최소/최대 값 업데이트 (SharedPreferences에 저장)
        // 이미 loadAddictionProbability에서 수행했으므로 여기서는 결과만 확인
        val minChanged = prefManager.updateMinScore24h(currentValue)
        val maxChanged = prefManager.updateMaxScore24h(currentValue)
        
        // 저장된 최소/최대 값 가져오기
        val newMinScore = prefManager.getMinScore24h(currentValue) 
        val newMaxScore = prefManager.getMaxScore24h(currentValue)
        
        // UI 업데이트 (값이 변경되었거나 표시에 차이가 있는 경우만)
        if (minChanged || newMinScore != minScore) {
            try {
                val currentMinText = binding.tvMin.text.toString().replace("%", "")
                val currentMinValue = if (currentMinText.isEmpty()) 0 else currentMinText.toInt()
                
                if (currentMinValue != newMinScore) {
                    Log.d("HomeFragment", "Animating minScore from $currentMinValue to $newMinScore")
                    animateTextValue(binding.tvMin, currentMinValue, newMinScore, "%")
                    // 앱 사용 데이터 로드
        loadAppUsageData()
    }
                
                minScore = newMinScore
                // 앱 사용 데이터 로드
        loadAppUsageData()
    } catch (e: Exception) {
                Log.e("HomeFragment", "Error updating min score: ${e.message}")
                binding.tvMin.text = "$newMinScore%"
                // 앱 사용 데이터 로드
        loadAppUsageData()
    }
            // 앱 사용 데이터 로드
        loadAppUsageData()
    }
        
        if (maxChanged || newMaxScore != maxScore) {
            try {
                val currentMaxText = binding.tvMax.text.toString().replace("%", "")
                val currentMaxValue = if (currentMaxText.isEmpty()) 0 else currentMaxText.toInt()
                
                if (currentMaxValue != newMaxScore) {
                    Log.d("HomeFragment", "Animating maxScore from $currentMaxValue to $newMaxScore")
                    animateTextValue(binding.tvMax, currentMaxValue, newMaxScore, "%")
                    // 앱 사용 데이터 로드
        loadAppUsageData()
    }
                
                maxScore = newMaxScore
                // 앱 사용 데이터 로드
        loadAppUsageData()
    } catch (e: Exception) {
                Log.e("HomeFragment", "Error updating max score: ${e.message}")
                binding.tvMax.text = "$newMaxScore%"
                // 앱 사용 데이터 로드
        loadAppUsageData()
    }
            // 앱 사용 데이터 로드
        loadAppUsageData()
    }
        // 앱 사용 데이터 로드
        loadAppUsageData()
    }
    
    /**
     * 5초마다 데이터를 갱신하는 기능을 시작합니다.
     */
    private fun startDataUpdates() {
        // 이전 실행을 먼저 취소
        stopDataUpdates()
        
        updateRunnable = Runnable {
            // 화면이 표시되고 있는 상태에서만 갱신 실행
            if (view != null && isAdded && _binding != null) {
                try {
                    // 중독 확률 데이터 갱신
                    loadAddictionProbability()
                    
                    // 다음 업데이트 예약 (5초 후)
                    if (isAdded && view != null && _binding != null) { // 추가 안전장치
                        handler.postDelayed(updateRunnable!!, 5000)
                        // 앱 사용 데이터 로드
        loadAppUsageData()
    } else {
                        stopDataUpdates()
                        // 앱 사용 데이터 로드
        loadAppUsageData()
    }
                    // 앱 사용 데이터 로드
        loadAppUsageData()
    } catch (e: Exception) {
                    Log.e("HomeFragment", "Error during update: ${e.message}")
                    stopDataUpdates() // 오류 발생 시 업데이트 중지
                    // 앱 사용 데이터 로드
        loadAppUsageData()
    }
                // 앱 사용 데이터 로드
        loadAppUsageData()
    } else {
                // Fragment가 화면에서 사라졌을 경우 갱신 중지
                Log.d("HomeFragment", "Fragment no longer visible, stopping updates")
                stopDataUpdates()
                // 앱 사용 데이터 로드
        loadAppUsageData()
    }
            // 앱 사용 데이터 로드
        loadAppUsageData()
    }
        
        // 첫 번째 업데이트 예약
        handler.postDelayed(updateRunnable!!, 5000)
        // 앱 사용 데이터 로드
        loadAppUsageData()
    }
    
    /**
     * 데이터 갱신을 중지합니다.
     */
    private fun stopDataUpdates() {
        updateRunnable?.let {
            handler.removeCallbacks(it)
            updateRunnable = null
            // 앱 사용 데이터 로드
        loadAppUsageData()
    }
        // 앱 사용 데이터 로드
        loadAppUsageData()
    }

    private fun setupUI() {
        val dateFormat = SimpleDateFormat("EEEE, MMMM d, yyyy", Locale.US)
        binding.tvDate.text = dateFormat.format(Date())

        // 상태 텍스트 및 색상 결정 (현재 Overrun Score 기준)
        val (statusText, colorHex) = when {
            currentScoreValue <= 33 -> "Cruising Mode" to "#76F376" // 초록
            currentScoreValue <= 66 -> "Warming Up" to "#FFDE58"    // 노랑
            else -> "Overrun Point" to "#C42727"                   // 빨강
            // 앱 사용 데이터 로드
        loadAppUsageData()
    }

        // 현재 Overrun Score 표시
        binding.tvPercentage.text = "$currentScoreValue%"
        binding.tvStatus.text = statusText

        // Alert Level 표시
        binding.tvAlertLevel.text = "$alertThresholdValue%"

        // 현재 값으로 최소/최대값 초기 업데이트 (SharedPreferences에 기록)
        prefManager.updateMinScore24h(currentScoreValue)
        prefManager.updateMaxScore24h(currentScoreValue)
        
        // 24시간 동안의 최소/최대 값 가져오기
        minScore = prefManager.getMinScore24h(currentScoreValue)
        maxScore = prefManager.getMaxScore24h(currentScoreValue)
        
        Log.d("HomeFragment", "setupUI - Current: $currentScoreValue, Min: $minScore, Max: $maxScore")
        
        // MIN/MAX 값 점진적으로 표시 (애니메이션)
        binding.tvMin.text = "0%"  // 시작값은 0%로 설정
        binding.tvMax.text = "0%"
        animateTextValue(binding.tvMin, 0, minScore, "%")
        animateTextValue(binding.tvMax, 0, maxScore, "%")

        // 상태 텍스트 배경 둥글게 설정
        val statusBg = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = 100f
            setColor(Color.parseColor(colorHex))
            // 앱 사용 데이터 로드
        loadAppUsageData()
    }
        binding.tvStatus.background = statusBg

        binding.yellowBackground.setBackgroundColor(Color.parseColor(colorHex))

        // 텍스트 색상 설정
        val isOverrun = currentScoreValue >= 67
        val textColorHex = if (isOverrun) "#FFFFFF" else "#5E4510"
        val subtitleColorHex = if (isOverrun) "#F8F8F8" else "#9D8A70"
        val percentageColorHex = if (isOverrun) "#C42727" else "#000000"

        binding.tvDate.setTextColor(textColorHex.toColorInt())
        binding.tvOverrunTitle.setTextColor(textColorHex.toColorInt())
        binding.divider.setBackgroundColor(textColorHex.toColorInt())
        binding.tvOverrunSubtitle.setTextColor(subtitleColorHex.toColorInt())
        binding.tvPercentage.setTextColor(percentageColorHex.toColorInt())
        binding.tvMax.setTextColor(colorHex.toColorInt())

        // ArcProgressView 생성 및 부드러운 애니메이션
        binding.unionContainer.post {
            val unionContainer = binding.unionContainer
            val overlayContainer = binding.arcOverlayContainer

            val unionLocation = IntArray(2)
            val overlayLocation = IntArray(2)
            unionContainer.getLocationOnScreen(unionLocation)
            overlayContainer.getLocationOnScreen(overlayLocation)

            val offsetX = (unionLocation[0] - overlayLocation[0]).toFloat()
            val offsetY = (unionLocation[1] - overlayLocation[1]).toFloat()

            val unionWidth = unionContainer.width.toFloat()
            val unionHeight = unionContainer.height.toFloat()
            val radius = unionWidth * 0.34f
            val diameter = (radius * 2).toInt()

            val centerX = offsetX + unionWidth / 2f
            val centerY = offsetY + unionHeight / 2f

            // 기존 ArcProgressView가 있다면 제거
            if (arcProgressView != null) {
                overlayContainer.removeView(arcProgressView)
                // 앱 사용 데이터 로드
        loadAppUsageData()
    }

            // 새 ArcProgressView 생성
            arcProgressView = ArcProgressView(requireContext()).apply {
                layoutParams = FrameLayout.LayoutParams(diameter, diameter)
                x = centerX - radius
                y = centerY - radius
                z = 99f

                // 색상 설정
                setFixedColor(colorHex)
                // 앱 사용 데이터 로드
        loadAppUsageData()
    }

            overlayContainer.addView(arcProgressView)

            // 부드럽게 차오르는 애니메이션
            ValueAnimator.ofFloat(0f, currentScoreValue.toFloat()).apply {
                duration = 1200L
                interpolator = DecelerateInterpolator()
                addUpdateListener {
                    arcProgressView?.percentage = (it.animatedValue as Float)
                    // 앱 사용 데이터 로드
        loadAppUsageData()
    }
                start()
                // 앱 사용 데이터 로드
        loadAppUsageData()
    }

            Log.d("Debug", "Arc center = ($centerX, $centerY), radius = $radius")
            // 앱 사용 데이터 로드
        loadAppUsageData()
    }
        // 앱 사용 데이터 로드
        loadAppUsageData()
    }

    override fun onResume() {
        super.onResume()
        // 화면이 다시 보일 때마다 임계값과 중독 확률 다시 로드
        loadAlertThreshold()
        loadAddictionProbability()
        
        // 데이터 갱신 다시 시작
        startDataUpdates()
        // 앱 사용 데이터 로드
        loadAppUsageData()
    }

    override fun onDestroyView() {
        // 데이터 갱신 중지 - View 소멸 전에 반드시 먼저 호출
        stopDataUpdates()
        
        // ArcProgressView 참조 해제
        arcProgressView = null
        
        // View 바인딩 해제
        _binding = null
        
        super.onDestroyView()
        // 앱 사용 데이터 로드
        loadAppUsageData()
    }
    
    override fun onPause() {
        super.onPause()
        // 화면이 보이지 않을 때 데이터 갱신 중지
        stopDataUpdates()
        // 앱 사용 데이터 로드
        loadAppUsageData()
    }
}
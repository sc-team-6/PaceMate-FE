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
import android.os.Handler
import android.os.Looper
import android.view.animation.DecelerateInterpolator
import androidx.lifecycle.lifecycleScope
import com.gdg.scrollmanager.utils.DataStoreUtils
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class HomeFragment : Fragment() {
    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!

    // 설문으로부터 계산된 임계값(Alert Level)
    private var alertThresholdValue: Int = 80
    
    // 현재 Overrun Score (중독 확률)
    private var currentScoreValue: Int = 0
    
    // 게이지의 최소값과 최대값
    private var minScore: Int = 0
    private var maxScore: Int = 0
    
    // 5초마다 데이터를 갱신하기 위한 핸들러
    private val handler = Handler(Looper.getMainLooper())
    private var updateRunnable: Runnable? = null
    
    // ArcProgressView 참조 저장
    private var arcProgressView: ArcProgressView? = null
    
    // 영속성 저장을 위한 컴파닌 클래스
    companion object {
        private const val PREF_MAX_SCORE = "max_score"
        private const val PREF_MIN_SCORE = "min_score"
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentHomeBinding.inflate(inflater, container, false)
        return binding.root as ConstraintLayout
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
    }
    
    /**
     * PreferenceManager에서 임계값을 가져와 사용합니다.
     */
    private fun loadAlertThreshold() {
        // PreferenceManager 인스턴스 생성
        val prefManager = PreferenceManager(requireContext())
        
        // 저장된 알림 임계값을 가져옴
        alertThresholdValue = prefManager.getAlertThreshold()
        
        Log.d("HomeFragment", "Loaded alert threshold: $alertThresholdValue%")
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
                
                // 현재 스코어 값 업데이트
                if (probability != currentScoreValue) {
                    val oldValue = currentScoreValue
                    currentScoreValue = probability
                    
                    // UI가 이미 초기화되었다면 업데이트
                    if (_binding != null && arcProgressView != null) {
                        updateUIWithNewScore(oldValue, probability)
                    }
                }
            } catch (e: Exception) {
                Log.e("HomeFragment", "Error loading addiction probability: ${e.message}")
                
                // 문제가 있을 경우 0으로 설정
                currentScoreValue = 0
            }
        }
    }
    
    /**
     * 새 스코어 값으로 UI를 업데이트합니다.
     */
    private fun updateUIWithNewScore(oldValue: Int, newValue: Int) {
        // 상태 텍스트 및 색상 결정 (현재 Overrun Score 기준)
        val (statusText, colorHex) = when {
            newValue <= 33 -> "Cruising Mode" to "#76F376" // 초록
            newValue <= 66 -> "Warming Up" to "#FFDE58"    // 노랑
            else -> "Overrun Point" to "#C42727"           // 빨강
        }

        // 현재 Overrun Score 텍스트 업데이트
        binding.tvPercentage.text = "$newValue%"
        binding.tvStatus.text = statusText

        // 상태 텍스트 배경 둥글게 설정
        val statusBg = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = 100f
            setColor(Color.parseColor(colorHex))
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
                    arcView.percentage = (it.animatedValue as Float)
                }
                start()
            }
            
            // 색상 업데이트
            arcView.setFixedColor(colorHex)
        }
    }
    
    /**
     * 최소값과 최대값을 조정하고 표시합니다.
     */
    private fun updateMinMaxValues(currentValue: Int) {
        // 앱이 재시작되었을 때마다 최소/최대값 및 최근 값이 초기화됨
        // 현재 값을 최소값과 최대값으로 사용
        if (minScore == 0 || currentValue < minScore) {
            minScore = currentValue
            binding.tvMin.text = "$minScore%"
        }
        
        if (currentValue > maxScore) {
            maxScore = currentValue
            binding.tvMax.text = "$maxScore%"
        }
    }
    
    /**
     * 5초마다 데이터를 갱신하는 기능을 시작합니다.
     */
    private fun startDataUpdates() {
        // 이전 실행을 먼저 취소
        stopDataUpdates()
        
        updateRunnable = Runnable {
            // 화면이 표시되고 있는 상태에서만 갱신 실행
            if (view != null && isAdded) {
                try {
                    // 중독 확률 데이터 갱신
                    loadAddictionProbability()
                    
                    // 다음 업데이트 예약 (5초 후)
                    if (isAdded && view != null) { // 추가 안전장치
                        handler.postDelayed(updateRunnable!!, 5000)
                    } else {
                        stopDataUpdates()
                    }
                } catch (e: Exception) {
                    Log.e("HomeFragment", "Error during update: ${e.message}")
                    stopDataUpdates() // 오류 발생 시 업데이트 중지
                }
            } else {
                // Fragment가 화면에서 사라졌을 경우 갱신 중지
                Log.d("HomeFragment", "Fragment no longer visible, stopping updates")
                stopDataUpdates()
            }
        }
        
        // 첫 번째 업데이트 예약
        handler.postDelayed(updateRunnable!!, 5000)
    }
    
    /**
     * 데이터 갱신을 중지합니다.
     */
    private fun stopDataUpdates() {
        updateRunnable?.let {
            handler.removeCallbacks(it)
            updateRunnable = null
        }
    }

    private fun setupUI() {
        val dateFormat = SimpleDateFormat("EEEE, MMMM d, yyyy", Locale.US)
        binding.tvDate.text = dateFormat.format(Date())

        // 상태 텍스트 및 색상 결정 (현재 Overrun Score 기준)
        val (statusText, colorHex) = when {
            currentScoreValue <= 33 -> "Cruising Mode" to "#76F376" // 초록
            currentScoreValue <= 66 -> "Warming Up" to "#FFDE58"    // 노랑
            else -> "Overrun Point" to "#C42727"                   // 빨강
        }

        // 현재 Overrun Score 표시
        binding.tvPercentage.text = "$currentScoreValue%"
        binding.tvStatus.text = statusText

        // Alert Level 표시
        binding.tvAlertLevel.text = "$alertThresholdValue%"

        // MIN 값 설정 (앱이 재시작되면 초기화)
        minScore = currentScoreValue
        binding.tvMin.text = "$minScore%"

        // MAX 값 설정 (최초에는 현재 값으로 초기화)
        maxScore = currentScoreValue
        binding.tvMax.text = "$maxScore%"

        // 상태 텍스트 배경 둥글게 설정
        val statusBg = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = 100f
            setColor(Color.parseColor(colorHex))
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
            }

            // 새 ArcProgressView 생성
            arcProgressView = ArcProgressView(requireContext()).apply {
                layoutParams = FrameLayout.LayoutParams(diameter, diameter)
                x = centerX - radius
                y = centerY - radius
                z = 99f

                // 색상 설정
                setFixedColor(colorHex)
            }

            overlayContainer.addView(arcProgressView)

            // 부드럽게 차오르는 애니메이션
            ValueAnimator.ofFloat(0f, currentScoreValue.toFloat()).apply {
                duration = 1200L
                interpolator = DecelerateInterpolator()
                addUpdateListener {
                    arcProgressView?.percentage = (it.animatedValue as Float)
                }
                start()
            }

            Log.d("Debug", "Arc center = ($centerX, $centerY), radius = $radius")
        }
    }

    override fun onResume() {
        super.onResume()
        // 화면이 다시 보일 때마다 임계값과 중독 확률 다시 로드
        loadAlertThreshold()
        loadAddictionProbability()
        
        // 데이터 갱신 다시 시작
        startDataUpdates()
    }

    override fun onDestroyView() {
        // 데이터 갱신 중지 - View 소멸 전에 반드시 먼저 호출
        stopDataUpdates()
        
        // ArcProgressView 참조 해제
        arcProgressView = null
        
        // View 바인딩 해제
        _binding = null
        
        super.onDestroyView()
    }
    
    override fun onPause() {
        super.onPause()
        // 화면이 보이지 않을 때 데이터 갱신 중지
        stopDataUpdates()
    }
}
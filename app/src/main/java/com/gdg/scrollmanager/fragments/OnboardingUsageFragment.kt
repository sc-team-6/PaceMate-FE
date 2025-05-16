package com.gdg.scrollmanager.fragments

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.fragment.app.Fragment
import com.gdg.scrollmanager.OnboardingActivity
import com.gdg.scrollmanager.R
import com.gdg.scrollmanager.utils.PreferenceManager
import com.gdg.scrollmanager.views.ArcProgressView

class OnboardingUsageFragment : Fragment() {
    private lateinit var btnStartManaging: Button
    private lateinit var tvPercentage: TextView
    private lateinit var progressArc: ArcProgressView
    
    // 기본값 설정
    private var alertThresholdPercentage: Int = 80

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_onboarding_usage, container, false)
        
        btnStartManaging = view.findViewById(R.id.btnStartManaging)
        tvPercentage = view.findViewById(R.id.tvPercentage)
        progressArc = view.findViewById(R.id.progressArc)
        
        return view
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        // Personalize 화면 스킵에 따른 기본값 설정
        setDefaultPersonalizeValues()
        
        // 사용자 응답을 기반으로 threshold 계산
        calculateAlertThreshold()
        
        // 화면에 퍼센트 표시 업데이트
        updatePercentageDisplay()
        
        btnStartManaging.setOnClickListener {
            // 설정된 임계값 저장
            saveAlertThreshold()
            (activity as? OnboardingActivity)?.finishOnboarding()
        }
    }
    
    /**
     * Personalize 화면에서 설정했어야 하는 기본값을 설정하는 함수
     */
    private fun setDefaultPersonalizeValues() {
        val sharedPref = requireActivity().getSharedPreferences("MyAppPrefs", 0)
        
        // 수면 시간 설정이 있는지 확인
        val hasSleepSettings = sharedPref.contains("sleep_time") && sharedPref.contains("wake_time")
        
        // 값이 설정되어 있지 않다면 기본값 설정
        if (!hasSleepSettings) {
            with(sharedPref.edit()) {
                // 기본 수면 시간: 오후 11시 ~ 오전 7시
                putString("sleep_time", "23:00")
                putString("wake_time", "07:00")
                // 기본적으로 캘린더 접근 허용하지 않음
                putBoolean("calendar_access", false)
                apply()
            }
        }
    }
    
    /**
     * 사용자 응답을 기반으로 알림 임계값을 계산
     * 이 함수는 이전 설문의 응답을 분석하여 50%-75% 범위 내에서 적절한 값을 계산합니다.
     */
    private fun calculateAlertThreshold() {
        val sharedPref = requireActivity().getSharedPreferences("MyAppPrefs", 0)
        
        // 1. 일일 화면 시간
        val screenTime = sharedPref.getString("screen_time", "3_to_4") ?: "3_to_4"
        // 2. 줄이고 싶은 사용량 유형
        val reduceUsage = sharedPref.getString("reduce_usage", "social_media") ?: "social_media"
        // 3. 줄이고 싶은 정도 
        val reduceAmount = sharedPref.getString("reduce_amount", "a_little") ?: "a_little"
        // 4. 수면 시간 (있다면)
        val sleepStart = sharedPref.getString("sleep_time", "23:00") ?: "23:00"
        val sleepEnd = sharedPref.getString("wake_time", "07:00") ?: "07:00"
        
        // 기본 임계값: 60%
        var baseThreshold = 60
        
        // 1. 화면 시간에 따른 조정
        when (screenTime) {
            "less_than_2" -> baseThreshold += 10 // 적은 사용량 -> 높은 임계값 (더 엄격하게)
            "3_to_4" -> baseThreshold += 5  // 적당한 사용량 -> 약간 높은 임계값
            "5_to_7" -> baseThreshold -= 5  // 많은 사용량 -> 약간 낮은 임계값 (여유롭게)
            "more_than_8" -> baseThreshold -= 10 // 매우 많은 사용량 -> 낮은 임계값 (더 많은 여유)
        }
        
        // 2. 줄이고 싶은 사용량 유형에 따른 조정
        when (reduceUsage) {
            "social_media" -> baseThreshold += 2  // 소셜 미디어는 빈번한 알림 필요
            "media_content" -> baseThreshold += 0 // 중립적
            "games" -> baseThreshold -= 2         // 게임은 몰입도가 높으므로 약간 낮게
        }
        
        // 3. 줄이고 싶은 정도에 따른 조정
        when (reduceAmount) {
            "a_lot" -> baseThreshold -= 8       // 많이 줄이고 싶음 -> 더 많은 알림 (낮은 임계값)
            "a_little" -> baseThreshold -= 3    // 조금 줄이고 싶음 -> 약간 낮은 임계값
            "keep_current" -> baseThreshold += 5 // 현재 수준 유지 -> 적은 알림 (높은 임계값)
        }
        
        // 최종 임계값을 50%-75% 범위 내로 제한
        alertThresholdPercentage = baseThreshold.coerceIn(50, 75)
    }
    
    private fun saveAlertThreshold() {
        // PreferenceManager에 계산된 임계값 저장
        val prefManager = PreferenceManager(requireContext())
        prefManager.setAlertThreshold(alertThresholdPercentage)
    }
    
    private fun updatePercentageDisplay() {
        // TextView에 퍼센트 표시 업데이트
        tvPercentage.text = "$alertThresholdPercentage%"
        
        // 호 프로그레스 뷰 업데이트
        progressArc.percentage = alertThresholdPercentage.toFloat()
    }
}
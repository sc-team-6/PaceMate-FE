package com.gdg.scrollmanager.fragments

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import com.gdg.scrollmanager.utils.ToastUtils
import androidx.fragment.app.Fragment
import com.gdg.scrollmanager.OnboardingActivity
import com.gdg.scrollmanager.R

class OnboardingPersonalizeFragment : Fragment() {
    private lateinit var btnNext: Button
    private lateinit var btnLater: Button
    // TEMPORARILY COMMENTED OUT - Calendar access button
    // private lateinit var btnSetAccess: Button
    private lateinit var tvStartTime: TextView
    private lateinit var tvEndTime: TextView

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_onboarding_personalize, container, false)
        
        btnNext = view.findViewById(R.id.btnNext)
        btnLater = view.findViewById(R.id.btnLater)
        // TEMPORARILY COMMENTED OUT - Calendar access button
        // btnSetAccess = view.findViewById(R.id.btnSetAccess)
        tvStartTime = view.findViewById(R.id.tvStartTime)
        tvEndTime = view.findViewById(R.id.tvEndTime)
        
        return view
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        // Screen Time 화면이 스킵되었을 때 기본값 설정
        setDefaultScreenTimeValues()
        
        // TEMPORARILY COMMENTED OUT - Calendar access button click handler
        // Calendar permissions request functionality removed temporarily
        /* 
        btnSetAccess.setOnClickListener {
            // 여기서 캘린더 접근 권한 요청 로직 구현
            saveCalendarAccess(true)
        }
        */
        
        // 버튼 항상 활성화 유지
        btnNext.isEnabled = true
        
        tvStartTime.setOnClickListener {
            // 시간 선택 다이얼로그 구현 (간소화를 위해 생략)
        }
        
        tvEndTime.setOnClickListener {
            // 시간 선택 다이얼로그 구현 (간소화를 위해 생략)
        }
        
        btnNext.setOnClickListener {
            // 수면 시간이 선택되었는지 확인
            if (isTimeSelectionValid()) {
                saveSleepSchedule(tvStartTime.text.toString(), tvEndTime.text.toString())
                (activity as? OnboardingActivity)?.goToNextPage()
            } else {
                showSelectionRequiredMessage()
            }
        }
        
        btnLater.setOnClickListener {
            // "Prev" 버튼은 이전 화면으로 돌아가기
            (activity as? OnboardingActivity)?.goToPreviousPage()
        }
    }
    
    // Screen Time 화면에서 설정했어야 하는 기본값을 설정하는 함수
    private fun setDefaultScreenTimeValues() {
        val sharedPref = requireActivity().getSharedPreferences("MyAppPrefs", 0)
        
        // 이미 값이 설정되어 있는지 확인
        val hasScreenTimeSettings = sharedPref.contains("screen_time")
        
        // 값이 설정되어 있지 않다면 기본값 설정
        if (!hasScreenTimeSettings) {
            with(sharedPref.edit()) {
                // 기본값: "5-7 hours"
                putString("screen_time", "5_to_7")
                // 기본값: "Social Media"
                putString("reduce_usage", "social_media")
                // 기본값: "A little"
                putString("reduce_amount", "a_little")
                apply()
            }
        }
    }
    
    private fun isTimeSelectionValid(): Boolean {
        // 여기서는 시간 형식 검증 등의 로직 추가 가능
        // 현재는 기본값이 이미 설정되어 있으므로 항상 true 반환
        val startTime = tvStartTime.text.toString()
        val endTime = tvEndTime.text.toString()
        
        return startTime.isNotEmpty() && endTime.isNotEmpty()
    }
    
    private fun showSelectionRequiredMessage() {
        val message = "Please select both sleep and wake times"
        activity?.let {
            ToastUtils.show(it, message)
        }
    }

    private fun saveCalendarAccess(allowed: Boolean) {
        val sharedPref = requireActivity().getSharedPreferences("MyAppPrefs", 0)
        with(sharedPref.edit()) {
            putBoolean("calendar_access", allowed)
            apply()
        }
    }

    private fun saveSleepSchedule(sleepTime: String, wakeTime: String) {
        val sharedPref = requireActivity().getSharedPreferences("MyAppPrefs", 0)
        with(sharedPref.edit()) {
            putString("sleep_time", sleepTime)
            putString("wake_time", wakeTime)
            apply()
        }
    }
}
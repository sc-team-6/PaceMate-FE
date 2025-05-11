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

class OnboardingPersonalizeFragment : Fragment() {
    private lateinit var btnNext: Button
    private lateinit var btnLater: Button
    private lateinit var btnSetAccess: Button
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
        btnSetAccess = view.findViewById(R.id.btnSetAccess)
        tvStartTime = view.findViewById(R.id.tvStartTime)
        tvEndTime = view.findViewById(R.id.tvEndTime)
        
        return view
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        btnSetAccess.setOnClickListener {
            // 여기서 캘린더 접근 권한 요청 로직 구현
            saveCalendarAccess(true)
        }
        
        tvStartTime.setOnClickListener {
            // 시간 선택 다이얼로그 구현 (간소화를 위해 생략)
        }
        
        tvEndTime.setOnClickListener {
            // 시간 선택 다이얼로그 구현 (간소화를 위해 생략)
        }
        
        btnNext.setOnClickListener {
            saveSleepSchedule(tvStartTime.text.toString(), tvEndTime.text.toString())
            (activity as? OnboardingActivity)?.goToNextPage()
        }
        
        btnLater.setOnClickListener {
            (activity as? OnboardingActivity)?.goToNextPage()
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

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
        
        // 저장된 퍼센트 값을 가져오기
        loadAlertThreshold()
        
        // 화면에 퍼센트 표시 업데이트
        updatePercentageDisplay()
        
        btnStartManaging.setOnClickListener {
            (activity as? OnboardingActivity)?.finishOnboarding()
        }
    }
    
    private fun loadAlertThreshold() {
        // PreferenceManager에서 저장된 값 가져오기
        val prefManager = PreferenceManager(requireContext())
        alertThresholdPercentage = prefManager.getAlertThreshold()
    }
    
    private fun updatePercentageDisplay() {
        // TextView에 퍼센트 표시 업데이트
        tvPercentage.text = "$alertThresholdPercentage%"
        
        // 호 프로그레스 뷰 업데이트
        // 0%일 때 비어있고 100%일 때 꽉 차도록 설정
        progressArc.percentage = alertThresholdPercentage.toFloat()
    }
}
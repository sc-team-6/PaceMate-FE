package com.gdg.scrollmanager

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import androidx.fragment.app.Fragment
import com.gdg.scrollmanager.R
import com.gdg.scrollmanager.fragments.GemmaTestFragment

class SettingsFragment : Fragment() {

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val root = inflater.inflate(R.layout.fragment_settings, container, false)
        
        val onboardingButton = root.findViewById<Button>(R.id.btn_view_onboarding)
        onboardingButton.setOnClickListener {
            // 온보딩 페이지로 이동
            val intent = Intent(activity, OnboardingActivity::class.java)
            intent.putExtra("FROM_SETTINGS", true)  // 설정에서 접근했음을 표시
            startActivity(intent)
        }
        
        val gemmaTestButton = root.findViewById<Button>(R.id.btn_gemma_test)
        gemmaTestButton.setOnClickListener {
            // Gemma 테스트 프래그먼트로 이동
            val fragmentManager = parentFragmentManager
            val transaction = fragmentManager.beginTransaction()
            transaction.replace(R.id.fragment_container, GemmaTestFragment())
            transaction.addToBackStack(null)  // 백 스택에 추가하여 뒤로가기 가능하게 함
            transaction.commit()
        }
        
        return root
    }
}
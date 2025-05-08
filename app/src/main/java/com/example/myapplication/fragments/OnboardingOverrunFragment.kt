package com.example.myapplication.fragments

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import androidx.fragment.app.Fragment
import com.example.myapplication.OnboardingActivity
import com.example.myapplication.R

class OnboardingOverrunFragment : Fragment() {
    private lateinit var btnSetItUp: Button

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_onboarding_overrun, container, false)
        btnSetItUp = view.findViewById(R.id.btnSetItUp)
        return view
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        btnSetItUp.setOnClickListener {
            // finishOnboarding() 대신 goToNextPage() 호출
            (activity as? OnboardingActivity)?.goToNextPage()
        }
    }
}

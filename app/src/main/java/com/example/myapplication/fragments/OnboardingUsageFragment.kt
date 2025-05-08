package com.example.myapplication.fragments

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import androidx.fragment.app.Fragment
import com.example.myapplication.OnboardingActivity
import com.example.myapplication.R

class OnboardingUsageFragment : Fragment() {
    private lateinit var btnStartManaging: Button

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_onboarding_usage, container, false)
        
        btnStartManaging = view.findViewById(R.id.btnStartManaging)
        
        return view
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        btnStartManaging.setOnClickListener {
            (activity as? OnboardingActivity)?.finishOnboarding()
        }
    }
}

package com.example.myapplication.fragments

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import androidx.fragment.app.Fragment
import com.example.myapplication.OnboardingActivity
import com.example.myapplication.R

class OnboardingWelcomeFragment : Fragment() {
    private lateinit var btnNext: Button

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_onboarding_welcome, container, false)
        btnNext = view.findViewById(R.id.btnNext)
        return view
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        btnNext.setOnClickListener {
            (activity as? OnboardingActivity)?.goToNextPage()
        }
    }
}

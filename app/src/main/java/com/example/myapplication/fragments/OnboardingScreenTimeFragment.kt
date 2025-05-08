package com.example.myapplication.fragments

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.RadioGroup
import androidx.fragment.app.Fragment
import com.example.myapplication.OnboardingActivity
import com.example.myapplication.R

class OnboardingScreenTimeFragment : Fragment() {
    private lateinit var btnNext: Button
    private lateinit var btnLater: Button
    private lateinit var radioGroup: RadioGroup
    private lateinit var radioGroup2: RadioGroup
    private var selectedScreenTime: String? = null
    private var selectedReduceUsage: String? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_onboarding_screen_time, container, false)
        btnNext = view.findViewById(R.id.btnNext)
        btnLater = view.findViewById(R.id.btnLater)
        radioGroup = view.findViewById(R.id.radioGroup)
        radioGroup2 = view.findViewById(R.id.radioGroup2)
        return view
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        setupOptionListeners()
        
        btnNext.setOnClickListener {
            saveSelections()
            (activity as? OnboardingActivity)?.goToNextPage()
        }
        
        btnLater.setOnClickListener {
            (activity as? OnboardingActivity)?.goToNextPage()
        }
    }

    private fun setupOptionListeners() {
        radioGroup.setOnCheckedChangeListener { _, checkedId ->
            when (checkedId) {
                R.id.radioOption1 -> selectedScreenTime = "less_than_2"
                R.id.radioOption2 -> selectedScreenTime = "3_to_4"
                R.id.radioOption3 -> selectedScreenTime = "5_to_7"
                R.id.radioOption4 -> selectedScreenTime = "more_than_8"
            }
            updateNextButton()
        }
        
        radioGroup2.setOnCheckedChangeListener { _, checkedId ->
            when (checkedId) {
                R.id.radioOption5 -> selectedReduceUsage = "social_media"
                R.id.radioOption6 -> selectedReduceUsage = "media_content"
                R.id.radioOption7 -> selectedReduceUsage = "games"
            }
            updateNextButton()
        }
    }
    
    private fun updateNextButton() {
        btnNext.isEnabled = selectedScreenTime != null || selectedReduceUsage != null
    }

    private fun saveSelections() {
        val sharedPref = requireActivity().getSharedPreferences("MyAppPrefs", 0)
        with(sharedPref.edit()) {
            selectedScreenTime?.let { putString("screen_time", it) }
            selectedReduceUsage?.let { putString("reduce_usage", it) }
            apply()
        }
    }
}

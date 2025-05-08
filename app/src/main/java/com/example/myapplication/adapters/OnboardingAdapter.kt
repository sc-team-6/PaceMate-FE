package com.example.myapplication.adapters

import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.viewpager2.adapter.FragmentStateAdapter
import com.example.myapplication.fragments.*

class OnboardingAdapter(activity: FragmentActivity) : FragmentStateAdapter(activity) {
    override fun getItemCount(): Int = 5

    override fun createFragment(position: Int): Fragment {
        return when (position) {
            0 -> OnboardingWelcomeFragment()    // Welcome Aboard!
            1 -> OnboardingOverrunFragment()    // What is Overrun?
            2 -> OnboardingScreenTimeFragment() // 일일 화면 시간
            3 -> OnboardingPersonalizeFragment() // 일정 고려
            4 -> OnboardingUsageFragment()      // Overrun Threshold
            else -> OnboardingWelcomeFragment()
        }
    }
}

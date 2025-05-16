package com.gdg.scrollmanager.adapters

import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.viewpager2.adapter.FragmentStateAdapter
import com.gdg.scrollmanager.fragments.*

class OnboardingAdapter(activity: FragmentActivity) : FragmentStateAdapter(activity) {
    // 포지션 2 (PersonalizeFragment)를 제거하여 총 4페이지로 변경
    override fun getItemCount(): Int = 4

    override fun createFragment(position: Int): Fragment {
        return when (position) {
            0 -> OnboardingWelcomeFragment()    // Welcome Aboard!
            1 -> OnboardingOverrunFragment()    // What is Overrun?
            2 -> OnboardingScreenTimeFragment() // 일일 화면 시간
            // PersonalizeFragment(포지션 3)를 제거하고 다음 화면으로 바로 이동
            3 -> OnboardingUsageFragment()      // Overrun Threshold
            else -> OnboardingWelcomeFragment()
        }
    }
}

package com.example.myapplication

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.viewpager2.widget.ViewPager2
import com.example.myapplication.adapters.OnboardingAdapter

class OnboardingActivity : AppCompatActivity() {
    private lateinit var viewPager: ViewPager2
    private lateinit var onboardingAdapter: OnboardingAdapter
    private var isFromSettings = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_onboarding)
        
        // 설정 화면에서 진입했는지 확인
        isFromSettings = intent.getBooleanExtra("FROM_SETTINGS", false)
        
        viewPager = findViewById(R.id.viewPager)
        setupViewPager()
    }

    private fun setupViewPager() {
        onboardingAdapter = OnboardingAdapter(this)
        viewPager.adapter = onboardingAdapter
        
        // 스와이프로 페이지 변경 비활성화 (버튼으로만 이동)
        viewPager.isUserInputEnabled = false
    }

    fun goToNextPage() {
        val currentItem = viewPager.currentItem
        if (currentItem < onboardingAdapter.itemCount - 1) {
            viewPager.currentItem = currentItem + 1
        }
    }

    fun goToPreviousPage() {
        val currentItem = viewPager.currentItem
        if (currentItem > 0) {
            viewPager.currentItem = currentItem - 1
        }
    }

    fun finishOnboarding() {
        // 설정에서 접근한 경우에는 온보딩 완료로 표시하지 않음
        if (!isFromSettings) {
            // 온보딩 완료 설정 저장
            val sharedPref = getSharedPreferences("MyAppPrefs", MODE_PRIVATE)
            with(sharedPref.edit()) {
                putBoolean("isOnboardingCompleted", true)
                apply()
            }
        }

        // 메인 화면으로 이동
        startActivity(Intent(this, MainActivity::class.java))
        finish()
    }
}
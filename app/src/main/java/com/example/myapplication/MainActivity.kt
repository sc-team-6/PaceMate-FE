package com.example.myapplication

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.View
import android.view.WindowManager
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.example.myapplication.databinding.ActivityMainBinding
import com.example.myapplication.fragments.*

class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // 온보딩 완료 여부 체크
        if (!isOnboardingCompleted()) {
            startActivity(Intent(this, OnboardingActivity::class.java))
            finish()
            return
        }
        
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // 상태바 색상 설정
        window.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS)
        window.statusBarColor = ContextCompat.getColor(this, R.color.yellow_background)
        
        // 상태바 아이콘 색상을 어둡게 설정 (검은색 아이콘)
        window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR

        // 네비게이션 바 설정
        binding.bottomNavigation.setBackgroundColor(Color.WHITE)
        binding.bottomNavigation.itemIconTintList = ContextCompat.getColorStateList(this, R.color.bottom_nav_item_color)
        binding.bottomNavigation.itemTextColor = ContextCompat.getColorStateList(this, R.color.bottom_nav_item_color)

        setupBottomNavigation()
        
        // 초기 프래그먼트 설정
        if (savedInstanceState == null) {
            replaceFragment(HomeFragment())
        }
    }

    private fun isOnboardingCompleted(): Boolean {
        val sharedPref = getSharedPreferences("MyAppPrefs", MODE_PRIVATE)
        return sharedPref.getBoolean("isOnboardingCompleted", false)
    }

    private fun setupBottomNavigation() {
        binding.bottomNavigation.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.navigation_home -> {
                    replaceFragment(HomeFragment())
                    true
                }
                R.id.navigation_alert -> {
                    // Alert 프래그먼트 추가 필요
                    true
                }
                R.id.navigation_report -> {
                    replaceFragment(UsageReportFragment())
                    true
                }
                R.id.navigation_settings -> {
                    replaceFragment(SettingsFragment())
                    true
                }
                else -> false
            }
        }
    }

    private fun replaceFragment(fragment: Fragment) {
        supportFragmentManager.beginTransaction()
            .replace(R.id.fragment_container, fragment)
            .commit()
    }
}
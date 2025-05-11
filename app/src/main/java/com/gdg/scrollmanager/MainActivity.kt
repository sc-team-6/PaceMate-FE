package com.gdg.scrollmanager

import android.app.AppOpsManager
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.os.Process
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import com.gdg.scrollmanager.databinding.ActivityMainBinding
import com.gdg.scrollmanager.R
import com.gdg.scrollmanager.fragments.*

class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding
    
    // Nav items
    private lateinit var navHome: LinearLayout
    private lateinit var navAlert: LinearLayout
    private lateinit var navReport: LinearLayout
    private lateinit var navSettings: LinearLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // 온보딩 완료 여부 체크
        if (!isOnboardingCompleted()) {
            startActivity(Intent(this, OnboardingActivity::class.java))
            finish()
            return
        }
        
        // 권한 체크
        if (!arePermissionsGranted()) {
            startActivity(Intent(this, PermissionsActivity::class.java))
            finish()
            return
        }
        
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // 상태바 완전 투명 설정 (진짜 투명)
        window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_LAYOUT_STABLE or 
                                              View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or
                                              View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR
        window.statusBarColor = Color.TRANSPARENT

        // 커스텀 네비게이션 바 설정
        setupCustomNavigation()
        
        // 초기 프래그먼트 설정
        if (savedInstanceState == null) {
            replaceFragment(HomeFragment())
        }
    }

    private fun isOnboardingCompleted(): Boolean {
        val sharedPref = getSharedPreferences("MyAppPrefs", MODE_PRIVATE)
        return sharedPref.getBoolean("isOnboardingCompleted", false)
    }
    
    private fun arePermissionsGranted(): Boolean {
        // 권한 설정 완료 여부 확인 (사용자가 완전히 건너뛰기를 선택한 경우에도 true)
        val sharedPref = getSharedPreferences("MyAppPrefs", MODE_PRIVATE)
        if (sharedPref.getBoolean("permissionsGranted", false)) {
            return true
        }
        
        // 실제 권한 상태 확인
        val hasUsageStats = hasUsageStatsPermission()
        
        // 접근성 서비스는 없어도 앱을 사용할 수 있도록 함
        return hasUsageStats
    }
    
    private fun hasUsageStatsPermission(): Boolean {
        val appOps = getSystemService(Context.APP_OPS_SERVICE) as android.app.AppOpsManager
        val mode = appOps.checkOpNoThrow(
            android.app.AppOpsManager.OPSTR_GET_USAGE_STATS,
            android.os.Process.myUid(),
            packageName
        )
        return mode == android.app.AppOpsManager.MODE_ALLOWED
    }

    private fun setupCustomNavigation() {
        // 네비게이션 아이템 초기화
        navHome = findViewById(R.id.nav_home)
        navAlert = findViewById(R.id.nav_alert)
        navReport = findViewById(R.id.nav_report)
        navSettings = findViewById(R.id.nav_settings)
        
        // 클릭 리스너 설정
        navHome.setOnClickListener {
            updateNavSelection(0)
            replaceFragment(HomeFragment())
        }
        
        navAlert.setOnClickListener {
            updateNavSelection(1)
            // Alert 프래그먼트 추가 필요
        }
        
        navReport.setOnClickListener {
            updateNavSelection(2)
            replaceFragment(UsageReportFragment())
        }
        
        navSettings.setOnClickListener {
            updateNavSelection(3)
            replaceFragment(SettingsFragment())
        }
        
        // 초기 선택 상태 설정
        updateNavSelection(0)
    }
    
    private fun updateNavSelection(selectedIndex: Int) {
        // 모든 아이템 초기화
        setNavItemInactive(navHome)
        setNavItemInactive(navAlert)
        setNavItemInactive(navReport)
        setNavItemInactive(navSettings)
        
        // 선택된 아이템 활성화
        when (selectedIndex) {
            0 -> setNavItemActive(navHome)
            1 -> setNavItemActive(navAlert)
            2 -> setNavItemActive(navReport)
            3 -> setNavItemActive(navSettings)
        }
    }
    
    private fun setNavItemActive(item: LinearLayout) {
        // 아이콘과 텍스트를 민트색으로 변경
        val icon = item.findViewById<ImageView>(getIconIdForItem(item))
        val text = item.findViewById<TextView>(getTextIdForItem(item))
        
        icon.setColorFilter(Color.parseColor("#70BCA4"))
        text.setTextColor(Color.parseColor("#70BCA4"))
    }
    
    private fun setNavItemInactive(item: LinearLayout) {
        // 아이콘과 텍스트를 회색으로 변경
        val icon = item.findViewById<ImageView>(getIconIdForItem(item))
        val text = item.findViewById<TextView>(getTextIdForItem(item))
        
        icon.setColorFilter(Color.parseColor("#CCCCCC"))
        text.setTextColor(Color.parseColor("#CCCCCC"))
    }
    
    private fun getIconIdForItem(item: LinearLayout): Int {
        return when (item.id) {
            R.id.nav_home -> R.id.icon_home
            R.id.nav_alert -> R.id.icon_alert
            R.id.nav_report -> R.id.icon_report
            R.id.nav_settings -> R.id.icon_settings
            else -> -1
        }
    }
    
    private fun getTextIdForItem(item: LinearLayout): Int {
        return when (item.id) {
            R.id.nav_home -> R.id.text_home
            R.id.nav_alert -> R.id.text_alert
            R.id.nav_report -> R.id.text_report
            R.id.nav_settings -> R.id.text_settings
            else -> -1
        }
    }

    private fun replaceFragment(fragment: Fragment) {
        supportFragmentManager.beginTransaction()
            .replace(R.id.fragment_container, fragment)
            .commit()
    }
}
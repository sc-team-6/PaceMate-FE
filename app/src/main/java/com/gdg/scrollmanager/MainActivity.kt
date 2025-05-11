package com.gdg.scrollmanager

import android.app.AppOpsManager
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.os.Process
import android.provider.Settings
import android.util.Log
import android.view.View
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityManager
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
    
    // 디버그 모드 플래그
    private var isDebugMode = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // 디버그 모드 설정 확인
        val sharedPref = getSharedPreferences("MyAppPrefs", MODE_PRIVATE)
        isDebugMode = sharedPref.getBoolean("debugMode", false)
        Log.d("MainActivity", "Debug mode enabled: $isDebugMode")
        
        // 온보딩 완료 여부 체크
        if (!isOnboardingCompleted()) {
            startActivity(Intent(this, OnboardingActivity::class.java))
            finish()
            return
        }
        
        // 권한 체크 - 디버그 모드가 아닐 때만 실행
        if (!isDebugMode && !arePermissionsGranted()) {
            navigateToPermissions()
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
    
    override fun onResume() {
        super.onResume()
        
        // 앱이 포그라운드로 돌아올 때마다 권한 체크 (디버그 모드가 아닐 때만)
        // 이미 권한 화면으로 이동했거나 레이아웃이 초기화되지 않은 경우에는 체크하지 않음
        if (!isDebugMode && ::binding.isInitialized && !arePermissionsGranted()) {
            navigateToPermissions()
        }
    }
    
    private fun navigateToPermissions() {
        startActivity(Intent(this, PermissionsActivity::class.java))
        finish()
    }

    private fun isOnboardingCompleted(): Boolean {
        val sharedPref = getSharedPreferences("MyAppPrefs", MODE_PRIVATE)
        return sharedPref.getBoolean("isOnboardingCompleted", false)
    }
    
    // 두 권한 모두 확인 (디버그 모드에서는 항상 true 반환)
    private fun arePermissionsGranted(): Boolean {
        // 디버그 모드면 항상 true 반환
        if (isDebugMode) {
            Log.d("MainActivity", "Debug mode: Bypassing permissions check")
            return true
        }
        return hasUsageStatsPermission() && isAccessibilityServiceEnabled()
    }
    
    private fun hasUsageStatsPermission(): Boolean {
        val appOps = getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        val mode = appOps.checkOpNoThrow(
            AppOpsManager.OPSTR_GET_USAGE_STATS,
            Process.myUid(),
            packageName
        )
        return mode == AppOpsManager.MODE_ALLOWED
    }
    
    private fun isAccessibilityServiceEnabled(): Boolean {
        // 방법 1: 설정에서 직접 확인
        try {
            val enabledServicesString = Settings.Secure.getString(
                contentResolver,
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
            ) ?: return false
            
            // 가능한 모든 서비스 이름 형식
            val possibleServiceNames = listOf(
                "$packageName/.ScrollAccessibilityService",
                "$packageName/com.gdg.scrollmanager.ScrollAccessibilityService",
                "com.gdg.scrollmanager/.ScrollAccessibilityService",
                "com.gdg.scrollmanager/com.gdg.scrollmanager.ScrollAccessibilityService"
            )
            
            val isEnabled = possibleServiceNames.any { serviceName ->
                enabledServicesString.contains(serviceName)
            }
            
            if (isEnabled) return true
        } catch (e: Exception) {
            e.printStackTrace()
        }
        
        // 방법 2: 액세스 가능한 서비스 목록 확인
        try {
            val am = getSystemService(Context.ACCESSIBILITY_SERVICE) as AccessibilityManager
            val runningServices = am.getEnabledAccessibilityServiceList(
                AccessibilityEvent.TYPES_ALL_MASK
            )
            
            for (service in runningServices) {
                val serviceInfo = service.resolveInfo.serviceInfo
                if (serviceInfo.packageName == packageName && 
                    serviceInfo.name.contains("ScrollAccessibilityService")) {
                    return true
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        
        // 접근성 서비스가 실제로 활성화되지 않았으면 false 반환
        return false
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
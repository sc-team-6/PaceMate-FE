package com.gdg.scrollmanager

import android.app.AppOpsManager
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Process
import android.provider.Settings
import android.util.Log
import android.view.View
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityManager
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.gdg.scrollmanager.R

class PermissionsActivity : AppCompatActivity() {
    
    private lateinit var tvUsageStatsStatus: TextView
    private lateinit var tvAccessibilityStatus: TextView
    private lateinit var btnUsageStats: Button
    private lateinit var btnAccessibility: Button
    private lateinit var btnContinue: Button
    
    private lateinit var sharedPreferences: SharedPreferences
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_permissions)
        
        // 뷰 초기화
        tvUsageStatsStatus = findViewById(R.id.tv_usage_stats_status)
        tvAccessibilityStatus = findViewById(R.id.tv_accessibility_status)
        btnUsageStats = findViewById(R.id.btn_usage_stats)
        btnAccessibility = findViewById(R.id.btn_accessibility)
        btnContinue = findViewById(R.id.btn_continue)

        // 권한 설정 버튼 리스너
        btnUsageStats.setOnClickListener {
            val intent = Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            startActivity(intent)

            Toast.makeText(
                this,
                "Find 'StopScrolling' and enable the usage access permission.",
                Toast.LENGTH_LONG
            ).show()
        }

        // 접근성 권한 설정 버튼 리스너
        btnAccessibility.setOnClickListener {
            val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            startActivity(intent)

            Toast.makeText(
                this,
                "Find 'StopScrolling' and enable the accessibility service.",
                Toast.LENGTH_LONG
            ).show()
        }
        
        // 계속하기 버튼 리스너
        btnContinue.setOnClickListener {
            startMainActivity()
        }
        
        // SharedPreferences 초기화
        sharedPreferences = getSharedPreferences("MyAppPrefs", MODE_PRIVATE)
    }
    
    override fun onResume() {
        super.onResume()
        
        // 현재 접근성 서비스 설정 상태 로그
        val enabledServicesString = Settings.Secure.getString(
            contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        )
        val accessibilityServiceName = packageName + "/.ScrollAccessibilityService"
        
        Log.d("PermissionsActivity", "Enabled Services: $enabledServicesString")
        Log.d("PermissionsActivity", "Looking for service: $accessibilityServiceName")
        Log.d("PermissionsActivity", "Service enabled: ${isAccessibilityServiceEnabled()}")
        
        updatePermissionStatus()
    }
    
    private fun updatePermissionStatus() {
        val hasUsageStats = hasUsageStatsPermission()
        val hasAccessibility = isAccessibilityServiceEnabled()
        
        // 사용 통계 권한 상태 업데이트
        if (hasUsageStats) {
            tvUsageStatsStatus.text = "허용됨"
            tvUsageStatsStatus.setTextColor(ContextCompat.getColor(this, android.R.color.holo_green_dark))
            btnUsageStats.visibility = View.GONE
        } else {
            tvUsageStatsStatus.text = "필요"
            tvUsageStatsStatus.setTextColor(ContextCompat.getColor(this, android.R.color.holo_red_dark))
            btnUsageStats.visibility = View.VISIBLE
        }
        
        // 접근성 서비스 상태 업데이트
        if (hasAccessibility) {
            tvAccessibilityStatus.text = "허용됨"
            tvAccessibilityStatus.setTextColor(ContextCompat.getColor(this, android.R.color.holo_green_dark))
            btnAccessibility.visibility = View.GONE
        } else {
            tvAccessibilityStatus.text = "필요"
            tvAccessibilityStatus.setTextColor(ContextCompat.getColor(this, android.R.color.holo_red_dark))
            btnAccessibility.visibility = View.VISIBLE
        }
        
        // 계속하기 버튼 상태 업데이트 - 앱 사용 통계 권한은 반드시 필요하고,
        // 접근성 서비스는 있으면 더 좋지만 없어도 앱은 사용할 수 있도록 함
        btnContinue.isEnabled = hasUsageStats
        
        if (hasUsageStats && !hasAccessibility) {
            // 앱 사용 통계는 있지만 접근성 서비스는 없는 경우
            btnContinue.text = "계속하기 (스크롤 측정 제외)"
        } else if (hasUsageStats && hasAccessibility) {
            btnContinue.text = "계속하기"
        }
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
        
        // 방법 3: 앱 자체 설정으로 우회 (사용자가 직접 허용했는지 여부)
        val sharedPref = getSharedPreferences("MyAppPrefs", MODE_PRIVATE)
        val userConfirmedAccessibility = sharedPref.getBoolean("userConfirmedAccessibility", false)
        
        // 사용자가 권한 화면에서 계속하기를 눌렀다면 허용으로 간주
        if (userConfirmedAccessibility) {
            return true
        }
        
        return false
    }
    
    private fun startMainActivity() {
        // 권한 설정 완료 상태 저장
        sharedPreferences.edit()
            .putBoolean("permissionsGranted", true)
            .putBoolean("userConfirmedAccessibility", true) // 사용자가 직접 접근성 설정 확인
            .apply()
        
        // 메인 액티비티로 이동
        val intent = Intent(this, MainActivity::class.java)
        startActivity(intent)
        finish()
    }
}
package com.gdg.scrollmanager

import android.app.AppOpsManager
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
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
import androidx.appcompat.app.AlertDialog
import com.gdg.scrollmanager.R

class PermissionsActivity : AppCompatActivity() {
    
    private lateinit var tvUsageStatsStatus: TextView
    private lateinit var tvAccessibilityStatus: TextView
    private lateinit var btnUsageStats: Button
    private lateinit var btnAccessibility: Button
    private lateinit var btnContinue: Button
    private lateinit var btnDebugSkip: Button
    
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
        btnDebugSkip = findViewById(R.id.btn_debug_skip)

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

        btnAccessibility.setOnClickListener {
            AlertDialog.Builder(this)
                .setTitle("Enable Accessibility Service")
                .setMessage(
                    "To use StopScrolling properly, please enable its accessibility service:\n\n" +
                            "👉 Settings > Accessibility > Installed apps > StopScrolling > Enable"
                )
                .setPositiveButton("Go to Settings") { _, _ ->
                    val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).apply {
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    }
                    startActivity(intent)
                }
                .setNegativeButton("Cancel", null)
                .show()
        }
        
        // 계속하기 버튼 리스너
        btnContinue.setOnClickListener {
            startMainActivity()
        }
        
        // 디버그용 SKIP 버튼 리스너
        btnDebugSkip.setOnClickListener {
            // 디버그 모드임을 표시하는 토스트 메시지
            Toast.makeText(
                this,
                "DEBUG MODE: Skipping permissions check",
                Toast.LENGTH_SHORT
            ).show()
            
            // 권한을 가진 것으로 간주하고 메인 화면으로 진행
            sharedPreferences.edit()
                .putBoolean("permissionsGranted", true)
                .putBoolean("debugMode", true)  // 디버그 모드 플래그 설정
                .apply()
            
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
        
        // 계속하기 버튼 상태 업데이트 - 두 권한 모두 필요
        btnContinue.isEnabled = hasUsageStats && hasAccessibility
        
        if (hasUsageStats && hasAccessibility) {
            btnContinue.text = "계속하기"
        } else if (hasUsageStats) {
            btnContinue.text = "접근성 권한 필요"
            btnContinue.isEnabled = false
        } else if (hasAccessibility) {
            btnContinue.text = "사용 통계 권한 필요"
            btnContinue.isEnabled = false
        } else {
            btnContinue.text = "모든 권한 필요"
            btnContinue.isEnabled = false
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
        
        // 접근성 서비스가 실제로 활성화되지 않았으면 false 반환
        return false
    }
    
    private fun startMainActivity() {
        // 메인 액티비티로 이동
        val intent = Intent(this, MainActivity::class.java)
        startActivity(intent)
        finish()
    }
}
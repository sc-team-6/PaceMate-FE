package com.example.myapplication.utils

import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Color
import android.util.Log
import com.example.myapplication.models.AppUsageInfo
import java.text.SimpleDateFormat
import java.util.*
import kotlin.collections.ArrayList
import kotlin.collections.HashMap

object UsageStatsUtils {
    
    // 기존 앱 사용 통계 가져오기 함수
    fun getAppUsageStats(context: Context): List<AppUsageInfo> {
        val usageStatsManager = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        val packageManager = context.packageManager

        val calendar = Calendar.getInstance()
        calendar.add(Calendar.DAY_OF_YEAR, -7)

        val startTime = calendar.timeInMillis
        val endTime = System.currentTimeMillis()

        val events = usageStatsManager.queryEvents(startTime, endTime)
        val event = UsageEvents.Event()
        val appUsageMap = HashMap<String, Long>()

        var currentApp = ""
        var currentStartTime: Long = 0

        while (events.hasNextEvent()) {
            events.getNextEvent(event)

            if (event.eventType == UsageEvents.Event.ACTIVITY_RESUMED) {
                currentApp = event.packageName
                currentStartTime = event.timeStamp
            } else if (event.eventType == UsageEvents.Event.ACTIVITY_PAUSED && currentApp == event.packageName) {
                val currentTime = appUsageMap[currentApp] ?: 0L
                appUsageMap[currentApp] = currentTime + (event.timeStamp - currentStartTime)
                currentApp = ""
            }
        }

        val appInfoList = ArrayList<AppUsageInfo>()
        val colorList = listOf(
            Color.parseColor("#F44336"),
            Color.parseColor("#2196F3"),
            Color.parseColor("#4CAF50"),
            Color.parseColor("#FFC107"),
            Color.parseColor("#9C27B0"),
            Color.parseColor("#FF5722"),
            Color.parseColor("#607D8B"),
            Color.parseColor("#009688"),
            Color.parseColor("#673AB7"),
            Color.parseColor("#795548")
        )

        var colorIndex = 0

        for ((packageName, timeInForeground) in appUsageMap) {
            if (timeInForeground > 0) {
                try {
                    val appInfo = packageManager.getApplicationInfo(packageName, 0)
                    val appName = packageManager.getApplicationLabel(appInfo).toString()

                    appInfoList.add(
                        AppUsageInfo(
                            packageName = packageName,
                            appName = appName,
                            timeInForeground = timeInForeground,
                            color = colorList[colorIndex % colorList.size]
                        )
                    )
                    colorIndex++
                } catch (e: PackageManager.NameNotFoundException) {
                    continue
                }
            }
        }

        return appInfoList
    }
    
    // 특정 시간대의 총 사용 시간 (분 단위)
    fun getTotalUsageTimeInMinutes(context: Context, minutes: Int): Int {
        val usageStatsManager = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        
        val endTime = System.currentTimeMillis()
        val startTime = endTime - (minutes * 60 * 1000)
        
        val events = usageStatsManager.queryEvents(startTime, endTime)
        val event = UsageEvents.Event()
        
        var totalUsageTime = 0L
        var currentApp = ""
        var currentStartTime: Long = 0
        
        while (events.hasNextEvent()) {
            events.getNextEvent(event)
            
            if (event.eventType == UsageEvents.Event.ACTIVITY_RESUMED) {
                currentApp = event.packageName
                currentStartTime = event.timeStamp
            } else if (event.eventType == UsageEvents.Event.ACTIVITY_PAUSED && currentApp == event.packageName) {
                totalUsageTime += (event.timeStamp - currentStartTime)
                currentApp = ""
            }
        }
        
        // 현재 사용 중인 앱이 있으면 현재 시간까지 계산
        if (currentApp.isNotEmpty() && currentStartTime > 0) {
            totalUsageTime += (System.currentTimeMillis() - currentStartTime)
        }
        
        // 밀리초를 분으로 변환
        return (totalUsageTime / (1000 * 60)).toInt()
    }
    
    // 특정 시간대의 폰 잠금 해제 횟수
    fun getUnlockCount(context: Context, minutes: Int): Int {
        val usageStatsManager = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        
        val endTime = System.currentTimeMillis()
        val startTime = endTime - (minutes * 60 * 1000)
        
        val events = usageStatsManager.queryEvents(startTime, endTime)
        val event = UsageEvents.Event()
        
        var unlockCount = 0
        
        while (events.hasNextEvent()) {
            events.getNextEvent(event)
            
            if (event.eventType == UsageEvents.Event.KEYGUARD_HIDDEN) {
                unlockCount++
            }
        }
        
        return unlockCount
    }
    
    // 특정 시간대의 앱 전환 횟수
    fun getAppSwitchCount(context: Context, minutes: Int): Int {
        val usageStatsManager = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        
        val endTime = System.currentTimeMillis()
        val startTime = endTime - (minutes * 60 * 1000)
        
        val events = usageStatsManager.queryEvents(startTime, endTime)
        val event = UsageEvents.Event()
        
        var switchCount = 0
        var lastApp = ""
        
        while (events.hasNextEvent()) {
            events.getNextEvent(event)
            
            if (event.eventType == UsageEvents.Event.ACTIVITY_RESUMED) {
                val currentApp = event.packageName
                if (lastApp.isNotEmpty() && lastApp != currentApp) {
                    switchCount++
                }
                lastApp = currentApp
            }
        }
        
        return switchCount
    }
    
    // 주 사용 앱 카테고리 가져오기
    fun getMainAppCategory(context: Context): String {
        val usageStats = getAppUsageStats(context)
        
        if (usageStats.isEmpty()) {
            return "Unknown"
        }
        
        // 사용 시간이 가장 긴 앱 찾기
        val mostUsedApp = usageStats.maxByOrNull { it.timeInForeground }
        if (mostUsedApp == null) {
            return "Unknown"
        }
        
        try {
            val packageManager = context.packageManager
            val info = packageManager.getApplicationInfo(mostUsedApp.packageName, PackageManager.GET_META_DATA)
            
            // 카테고리를 직접 처리하는 대신 패키지 이름으로 추측
            return when {
                mostUsedApp.packageName.contains("facebook") || 
                mostUsedApp.packageName.contains("instagram") || 
                mostUsedApp.packageName.contains("twitter") || 
                mostUsedApp.packageName.contains("snapchat") || 
                mostUsedApp.packageName.contains("kakao") -> "Social"
                
                mostUsedApp.packageName.contains("game") || 
                mostUsedApp.packageName.contains("play") -> "Game"
                
                mostUsedApp.packageName.contains("chrome") || 
                mostUsedApp.packageName.contains("browser") || 
                mostUsedApp.packageName.contains("internet") -> "Browser"
                
                mostUsedApp.packageName.contains("music") || 
                mostUsedApp.packageName.contains("spotify") || 
                mostUsedApp.packageName.contains("youtube") -> "Entertainment"
                
                mostUsedApp.packageName.contains("mail") || 
                mostUsedApp.packageName.contains("gmail") -> "Communication"
                
                mostUsedApp.packageName.contains("map") || 
                mostUsedApp.packageName.contains("naver") || 
                mostUsedApp.packageName.contains("gps") -> "Navigation"
                
                else -> "App"
            }
        } catch (e: Exception) {
            Log.e("UsageStatsUtils", "Error getting app category: ${e.message}")
            return "App"
        }
    }
    
    // SNS 앱 사용 횟수
    fun getSocialMediaAppUsageCount(context: Context, minutes: Int): Int {
        val usageStatsManager = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        
        val endTime = System.currentTimeMillis()
        val startTime = endTime - (minutes * 60 * 1000)
        
        val events = usageStatsManager.queryEvents(startTime, endTime)
        val event = UsageEvents.Event()
        
        val socialMediaApps = listOf(
            "com.facebook.katana",          // Facebook
            "com.instagram.android",        // Instagram
            "com.twitter.android",          // Twitter
            "com.zhiliaoapp.musically",     // TikTok
            "com.snapchat.android",         // Snapchat
            "com.linkedin.android",         // LinkedIn
            "com.pinterest",                // Pinterest
            "com.kakao.talk",               // KakaoTalk
            "jp.naver.line.android",        // Line
            "com.whatsapp",                 // WhatsApp
            "org.telegram.messenger"        // Telegram
        )
        
        var usageCount = 0
        var lastOpenedSocialApp = ""
        
        while (events.hasNextEvent()) {
            events.getNextEvent(event)
            
            if (event.eventType == UsageEvents.Event.ACTIVITY_RESUMED && 
                socialMediaApps.contains(event.packageName)) {
                // 같은 앱이 아닌 경우에만 카운트 (다른 SNS 앱으로 전환한 경우)
                if (lastOpenedSocialApp != event.packageName) {
                    usageCount++
                    lastOpenedSocialApp = event.packageName
                }
            }
        }
        
        return usageCount
    }
    
    // 평균 세션 길이 (초 단위)
    fun getAverageSessionDuration(context: Context): Float {
        val usageStatsManager = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        
        val calendar = Calendar.getInstance()
        calendar.add(Calendar.DAY_OF_YEAR, -1) // 지난 24시간
        
        val startTime = calendar.timeInMillis
        val endTime = System.currentTimeMillis()
        
        val events = usageStatsManager.queryEvents(startTime, endTime)
        val event = UsageEvents.Event()
        
        var sessionStart: Long = 0
        var totalSessionDuration: Long = 0
        var sessionCount = 0
        var isSessionActive = false
        
        while (events.hasNextEvent()) {
            events.getNextEvent(event)
            
            if (event.eventType == UsageEvents.Event.KEYGUARD_HIDDEN) {
                // 화면 잠금 해제 = 세션 시작
                sessionStart = event.timeStamp
                isSessionActive = true
            } else if (event.eventType == UsageEvents.Event.KEYGUARD_SHOWN && isSessionActive) {
                // 화면 잠금 = 세션 종료
                val sessionDuration = event.timeStamp - sessionStart
                totalSessionDuration += sessionDuration
                sessionCount++
                isSessionActive = false
            }
        }
        
        // 열린 세션이 있다면 현재 시간까지 계산
        if (isSessionActive && sessionStart > 0) {
            val sessionDuration = System.currentTimeMillis() - sessionStart
            totalSessionDuration += sessionDuration
            sessionCount++
        }
        
        return if (sessionCount > 0) {
            // 평균 세션 길이 (초 단위)
            (totalSessionDuration / sessionCount / 1000).toFloat()
        } else {
            0f
        }
    }
    
    // 현재 날짜/시간 포맷
    fun getCurrentDateTime(): String {
        val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
        return sdf.format(Date())
    }
    
    // 스크롤 길이 가져오기 (DataStore에서 직접 가져와야 함)
    // ScrollAccessibilityService에서 기록한 데이터를 가져오는 것은 별도 함수에서 처리
}
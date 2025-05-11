package com.gdg.scrollmanager.utils

import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Color
import android.util.Log
import com.gdg.scrollmanager.models.AppUsageInfo
import java.text.SimpleDateFormat
import java.util.*

object UsageStatsUtils {
    
    // 홈 화면 런처 패키지 목록
    private val launcherPackages = listOf(
        "com.sec.android.app.launcher",       // Samsung
        "com.google.android.apps.nexuslauncher", // Google Pixel
        "com.miui.home",                      // Xiaomi
        "com.android.launcher",               // AOSP
        "com.android.launcher2",              // AOSP
        "com.android.launcher3",              // AOSP
        "com.oneplus.launcher",               // OnePlus
        "com.huawei.android.launcher",        // Huawei
        "com.lge.launcher2",                  // LG
        "com.microsoft.launcher",             // Microsoft Launcher
        "com.oppo.launcher",                  // Oppo
        "com.htc.launcher",                   // HTC
        "com.asus.launcher",                  // Asus
        "com.vivo.launcher",                  // Vivo
        "is.launcher",                        // Action Launcher
        "com.anddoes.launcher",               // Apex Launcher
        "com.teslacoilsw.launcher",           // Nova Launcher
        "com.actionlauncher.playstore",       // Action Launcher
        "com.sonymobile.home",                // Sony
        "com.blackberry.launcher"             // BlackBerry
    )
    
    // 패키지가 홈 런처인지 확인
    private fun isLauncherPackage(packageName: String): Boolean {
        return launcherPackages.any { packageName.contains(it) || packageName == it }
    }
    
    // 기존 앱 사용 통계 가져오기 함수
    fun getAppUsageStats(context: Context): List<AppUsageInfo> {
        val usageStatsManager = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        val packageManager = context.packageManager

        val calendar = Calendar.getInstance()
        calendar.add(Calendar.DAY_OF_YEAR, -1) // 1일 데이터로 변경

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
    
    // 특정 시간대의 화면 켜짐(잠금 해제) 횟수
    fun getUnlockCount(context: Context, minutes: Int): Int {
        val usageStatsManager = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        
        val endTime = System.currentTimeMillis()
        val startTime = endTime - (minutes * 60 * 1000)
        
        val events = usageStatsManager.queryEvents(startTime, endTime)
        val event = UsageEvents.Event()
        
        var screenOnCount = 0
        
        while (events.hasNextEvent()) {
            events.getNextEvent(event)
            
            // 화면이 켜진 경우만 카운트 (잠금 해제 이벤트)
            if (event.eventType == UsageEvents.Event.KEYGUARD_HIDDEN) {
                screenOnCount++
            }
        }
        
        return screenOnCount
    }
    
    // 특정 시간대의 앱 전환 횟수 (홈 화면 제외)
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
                
                // 홈 화면(런처)은 건너뛰기
                val isCurrentAppLauncher = isLauncherPackage(currentApp)
                val isLastAppLauncher = lastApp.isNotEmpty() && isLauncherPackage(lastApp)
                
                if (lastApp.isNotEmpty() && lastApp != currentApp && !isCurrentAppLauncher && !isLastAppLauncher) {
                    switchCount++
                }
                
                if (!isCurrentAppLauncher) {
                    lastApp = currentApp
                }
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
        
        // 사용 시간 기준으로 상위 20개 앱 추출 (홈 런처 제외)
        val top20Apps = usageStats
            .filter { !isLauncherPackage(it.packageName) }
            .sortedByDescending { it.timeInForeground }
            .take(20)
        
        // 카테고리별 사용 시간 합산
        val categorizedUsage = mutableMapOf<String, Long>()
        
        for (app in top20Apps) {
            val category = getCategoryForApp(app.packageName)
            val currentTime = categorizedUsage[category] ?: 0L
            categorizedUsage[category] = currentTime + app.timeInForeground
        }
        
        // 가장 많이 사용한 카테고리 찾기
        val mainCategory = categorizedUsage.maxByOrNull { it.value }?.key ?: "Unknown"
        return mainCategory
    }
    
    // 앱 패키지 이름으로 카테고리 추측
    private fun getCategoryForApp(packageName: String): String {
        return when {
            packageName.contains("facebook") || 
            packageName.contains("instagram") || 
            packageName.contains("twitter") || 
            packageName.contains("snapchat") ||
            packageName.contains("tiktok") ||
            packageName.contains("musically") ||
            packageName.contains("kakao") || 
            packageName.contains("pinterest") ||
            packageName.contains("linkedin") ||
            packageName.contains("reddit") ||
            packageName.contains("discord") ||
            packageName.contains("telegram") ||
            packageName.contains("whatsapp") ||
            packageName.contains("line.android") ||
            packageName.contains("youtube") -> "Social"  // 유튜브를 소셜로 포함
            
            packageName.contains("game") || 
            packageName.contains("games") ||
            packageName.contains("play.games") -> "Game"
            
            packageName.contains("chrome") || 
            packageName.contains("browser") || 
            packageName.contains("firefox") ||
            packageName.contains("internet") -> "Browser"
            
            packageName.contains("music") || 
            packageName.contains("spotify") ||
            packageName.contains("player") ||
            packageName.contains("audio") ||
            packageName.contains("netflix") ||
            packageName.contains("hulu") ||
            packageName.contains("disney") ||
            packageName.contains("video") -> "Entertainment"
            
            packageName.contains("mail") || 
            packageName.contains("gmail") ||
            packageName.contains("outlook") ||
            packageName.contains("message") -> "Communication"
            
            packageName.contains("map") || 
            packageName.contains("naver") || 
            packageName.contains("gps") ||
            packageName.contains("navigation") -> "Navigation"
            
            packageName.contains("office") ||
            packageName.contains("docs") ||
            packageName.contains("sheets") ||
            packageName.contains("slides") ||
            packageName.contains("word") ||
            packageName.contains("excel") ||
            packageName.contains("powerpoint") -> "Productivity"
            
            packageName.contains("camera") ||
            packageName.contains("photo") ||
            packageName.contains("gallery") -> "Photography"
            
            packageName.contains("news") ||
            packageName.contains("magazine") -> "News"
            
            packageName.contains("shop") ||
            packageName.contains("store") ||
            packageName.contains("amazon") ||
            packageName.contains("ebay") -> "Shopping"
            
            else -> "App"
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
            "org.telegram.messenger",       // Telegram
            "com.google.android.youtube"    // YouTube (추가됨)
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
    
    // 15분 내 앱 전환 디버그 로그 (홈 화면 제외)
    fun debugAppSwitchesIn15Min(context: Context): List<AppSwitchEvent> {
        val usageStatsManager = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        val packageManager = context.packageManager
        
        val endTime = System.currentTimeMillis()
        val startTime = endTime - (15 * 60 * 1000) // 15분
        
        val events = usageStatsManager.queryEvents(startTime, endTime)
        val event = UsageEvents.Event()
        
        val appSwitches = mutableListOf<AppSwitchEvent>()
        var lastApp = ""
        var lastTimestamp = 0L
        
        while (events.hasNextEvent()) {
            events.getNextEvent(event)
            
            if (event.eventType == UsageEvents.Event.ACTIVITY_RESUMED) {
                val currentApp = event.packageName
                val currentTimestamp = event.timeStamp
                
                // 홈 화면(런처)은 건너뛰기
                if (isLauncherPackage(currentApp)) {
                    continue
                }
                
                // 앱 이름 가져오기
                val appName = try {
                    val appInfo = packageManager.getApplicationInfo(currentApp, 0)
                    packageManager.getApplicationLabel(appInfo).toString()
                } catch (e: PackageManager.NameNotFoundException) {
                    currentApp // 앱 이름을 찾을 수 없으면 패키지 이름 사용
                }
                
                // 앱 전환 시간 포맷
                val dateFormat = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
                val dateTime = dateFormat.format(Date(currentTimestamp))
                
                // 앱 전환 기록 (홈 화면에서 전환된 경우는 무시)
                if (lastApp.isNotEmpty() && lastApp != currentApp && !isLauncherPackage(lastApp)) {
                    val duration = if (lastTimestamp > 0) (currentTimestamp - lastTimestamp) / 1000 else 0
                    
                    appSwitches.add(
                        AppSwitchEvent(
                            fromApp = lastApp,
                            toApp = appName,
                            timestamp = dateTime,
                            durationSec = duration
                        )
                    )
                    
                    // 로그 출력
                    Log.d("AppSwitch", "[$dateTime] From: $lastApp, To: $appName, Duration: ${duration}s")
                }
                
                lastApp = currentApp
                lastTimestamp = currentTimestamp
            }
        }
        
        // 최종 앱 사용 시간 계산 및 로그 (홈 화면이 아닌 경우만)
        if (lastApp.isNotEmpty() && lastTimestamp > 0 && !isLauncherPackage(lastApp)) {
            val currentDuration = (System.currentTimeMillis() - lastTimestamp) / 1000
            Log.d("AppSwitch", "Current app: $lastApp, Using for: ${currentDuration}s")
        }
        
        return appSwitches
    }
    
    // 앱 전환 이벤트 데이터 클래스
    data class AppSwitchEvent(
        val fromApp: String,
        val toApp: String,
        val timestamp: String,
        val durationSec: Long
    )
    
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
}
package com.gdg.scrollmanager.utils

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import java.util.*

/**
 * SharedPreferences를 사용하여 앱 설정 및 데이터를 관리하는 클래스
 */
class PreferenceManager(context: Context) {
    
    companion object {
        private const val PREF_NAME = "app_usage_stats_prefs"
        private const val KEY_ALERT_THRESHOLD = "alert_threshold"
        private const val DEFAULT_ALERT_THRESHOLD = 60
        
        // 24시간 기록 관련 키
        private const val KEY_MIN_SCORE = "min_score_24h"
        private const val KEY_MAX_SCORE = "max_score_24h"
        private const val KEY_LAST_RESET_TIME = "last_reset_time"
        
        // 24시간 (밀리초)
        private const val RESET_INTERVAL_MS = 24 * 60 * 60 * 1000L
    }
    
    private val sharedPreferences: SharedPreferences = context.getSharedPreferences(
        PREF_NAME, Context.MODE_PRIVATE
    )
    
    /**
     * 알림 임계값 퍼센트를 가져옵니다.
     * 저장된 값이 없으면 기본값(80%)을 반환합니다.
     */
    fun getAlertThreshold(): Int {
        return sharedPreferences.getInt(KEY_ALERT_THRESHOLD, DEFAULT_ALERT_THRESHOLD)
    }
    
    /**
     * 알림 임계값 퍼센트를 저장합니다.
     */
    fun setAlertThreshold(percentage: Int) {
        sharedPreferences.edit().putInt(KEY_ALERT_THRESHOLD, percentage).apply()
    }
    
    /**
     * 24시간 동안의 최소 점수를 가져옵니다.
     * 필요한 경우 시간 경과 체크 및 초기화를 수행합니다.
     */
    fun getMinScore24h(currentScore: Int): Int {
        checkAndResetIfNeeded()
        val minScore = sharedPreferences.getInt(KEY_MIN_SCORE, currentScore)
        Log.d("PreferenceManager", "getMinScore24h: $minScore (current: $currentScore)")
        return minScore
    }
    
    /**
     * 24시간 동안의 최대 점수를 가져옵니다.
     * 필요한 경우 시간 경과 체크 및 초기화를 수행합니다.
     */
    fun getMaxScore24h(currentScore: Int): Int {
        checkAndResetIfNeeded()
        val maxScore = sharedPreferences.getInt(KEY_MAX_SCORE, currentScore)
        Log.d("PreferenceManager", "getMaxScore24h: $maxScore (current: $currentScore)")
        return maxScore
    }
    
    /**
     * 현재 점수와 비교하여 필요한 경우 최소 점수를 업데이트합니다.
     * 반환값: 값이 변경되었는지 여부
     */
    fun updateMinScore24h(currentScore: Int): Boolean {
        checkAndResetIfNeeded()
        
        val savedMin = sharedPreferences.getInt(KEY_MIN_SCORE, Integer.MAX_VALUE)
        if (currentScore < savedMin) {
            Log.d("PreferenceManager", "updateMinScore24h: Updating min from $savedMin to $currentScore")
            sharedPreferences.edit().putInt(KEY_MIN_SCORE, currentScore).apply()
            return true
        }
        return false
    }
    
    /**
     * 현재 점수와 비교하여 필요한 경우 최대 점수를 업데이트합니다.
     * 반환값: 값이 변경되었는지 여부
     */
    fun updateMaxScore24h(currentScore: Int): Boolean {
        checkAndResetIfNeeded()
        
        val savedMax = sharedPreferences.getInt(KEY_MAX_SCORE, Integer.MIN_VALUE)
        if (currentScore > savedMax) {
            Log.d("PreferenceManager", "updateMaxScore24h: Updating max from $savedMax to $currentScore")
            sharedPreferences.edit().putInt(KEY_MAX_SCORE, currentScore).apply()
            return true
        }
        return false
    }
    
    /**
     * 마지막 리셋 시간으로부터 24시간이 지났는지 확인하고,
     * 지났다면 최소/최대 값을 초기화합니다.
     */
    private fun checkAndResetIfNeeded() {
        val currentTime = System.currentTimeMillis()
        val lastResetTime = sharedPreferences.getLong(KEY_LAST_RESET_TIME, 0)
        
        // 첫 실행이거나 24시간이 지났다면 초기화
        if (lastResetTime == 0L || (currentTime - lastResetTime) >= RESET_INTERVAL_MS) {
            Log.d("PreferenceManager", "Resetting min/max values (lastReset: ${formatTime(lastResetTime)}, current: ${formatTime(currentTime)})")
            
            // 모든 값을 초기화하고 타임스탬프 업데이트
            sharedPreferences.edit()
                .remove(KEY_MIN_SCORE)
                .remove(KEY_MAX_SCORE)
                .putLong(KEY_LAST_RESET_TIME, currentTime)
                .apply()
        }
    }
    
    /**
     * 디버깅을 위한 시간 포맷 함수
     */
    private fun formatTime(timeMillis: Long): String {
        if (timeMillis == 0L) return "never"
        val date = java.util.Date(timeMillis)
        val format = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault())
        return format.format(date)
    }
}
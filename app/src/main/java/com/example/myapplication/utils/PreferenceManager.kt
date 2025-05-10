package com.example.myapplication.utils

import android.content.Context
import android.content.SharedPreferences

/**
 * SharedPreferences를 사용하여 앱 설정 및 데이터를 관리하는 클래스
 */
class PreferenceManager(context: Context) {
    
    companion object {
        private const val PREF_NAME = "app_usage_stats_prefs"
        private const val KEY_ALERT_THRESHOLD = "alert_threshold"
        private const val DEFAULT_ALERT_THRESHOLD = 80
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
}
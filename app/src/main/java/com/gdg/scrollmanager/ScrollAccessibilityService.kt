package com.gdg.scrollmanager

import android.accessibilityservice.AccessibilityService
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import androidx.datastore.preferences.core.edit
import com.gdg.scrollmanager.utils.DataStoreUtils
import com.gdg.scrollmanager.utils.scrollDataStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class ScrollAccessibilityService : AccessibilityService() {

    override fun onCreate() {
        super.onCreate()
        // 알림 제거 - AccessibilityService는 별도의 알림 없이도 실행 가능
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event?.eventType == AccessibilityEvent.TYPE_VIEW_SCROLLED) {
            Log.d("ScrollService", "외부 스크롤 감지됨 - ${event.packageName}")
            addScrollDistanceToDataStore(100f)
        }
    }

    private fun addScrollDistanceToDataStore(amount: Float) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                // 1. 총 스크롤 거리 업데이트
                applicationContext.scrollDataStore.edit { preferences ->
                    val current = preferences[DataStoreUtils.EXTERNAL_SCROLL_KEY] ?: 0f
                    preferences[DataStoreUtils.EXTERNAL_SCROLL_KEY] = current + amount
                }
                
                // 2. 최근 스크롤 픽셀 값 업데이트
                val currentPixels = DataStoreUtils.getRecentScrollPixels(applicationContext)
                val newPixels = currentPixels + (amount * 100).toInt()
                DataStoreUtils.saveRecentScrollPixels(applicationContext, newPixels)
                Log.d("ScrollService", "스크롤 픽셀 업데이트됨: $currentPixels → $newPixels")
            } catch (e: Exception) {
                Log.e("ScrollService", "스크롤 데이터 저장 오류: ${e.message}")
            }
        }
    }

    override fun onInterrupt() {}
}
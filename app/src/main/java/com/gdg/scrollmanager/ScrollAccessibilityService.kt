package com.gdg.scrollmanager

import android.accessibilityservice.AccessibilityService
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import com.gdg.scrollmanager.utils.scrollDataStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class ScrollAccessibilityService : AccessibilityService() {

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event?.eventType == AccessibilityEvent.TYPE_VIEW_SCROLLED) {
            Log.d("ScrollService", "외부 스크롤 감지됨 - ${event.packageName}")
            addScrollDistanceToDataStore(100f)
        }
    }

    private fun addScrollDistanceToDataStore(amount: Float) {
        CoroutineScope(Dispatchers.IO).launch {
            val key = floatPreferencesKey("external_scroll_distance")
            applicationContext.scrollDataStore.edit { preferences ->
                val current = preferences[key] ?: 0f
                preferences[key] = current + amount
            }
        }
    }

    override fun onInterrupt() {}
}
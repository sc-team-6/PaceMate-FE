package com.example.myapplication.utils

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

// DataStore 인스턴스
val Context.userDataStore by preferencesDataStore(name = "user_settings")
val Context.scrollDataStore by preferencesDataStore(name = "scroll_data")

object DataStoreUtils {
    // 스크롤 거리 관련 키
    val EXTERNAL_SCROLL_KEY = floatPreferencesKey("external_scroll_distance")
    
    // 스크롤 총 거리 가져오기
    suspend fun getTotalScrollDistance(context: Context): Float {
        return context.scrollDataStore.data.map { preferences ->
            preferences[EXTERNAL_SCROLL_KEY] ?: 0f
        }.first()
    }
    
    // 스크롤 거리 포맷팅
    fun formatScrollDistance(distance: Float): String {
        return when {
            distance >= 100000 -> "${String.format("%.1f", distance / 100000)}km"
            distance >= 100 -> "${String.format("%.1f", distance / 100)}m"
            else -> "${distance.toInt()}cm"
        }
    }
}
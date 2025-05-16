package com.gdg.scrollmanager.utils

import android.content.Context
import android.util.Log
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.gdg.scrollmanager.models.UsageReport
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

// DataStore 인스턴스
val Context.userDataStore by preferencesDataStore(name = "user_settings")
val Context.scrollDataStore by preferencesDataStore(name = "scroll_data")
val Context.usageDataStore by preferencesDataStore(name = "usage_data")

object DataStoreUtils {
    private val gson = Gson()
    
    // 스크롤 거리 관련 키
    val EXTERNAL_SCROLL_KEY = floatPreferencesKey("external_scroll_distance")
    val LAST_SCROLL_DISTANCE_KEY = floatPreferencesKey("last_scroll_distance")
    val RECENT_SCROLL_PIXELS_KEY = intPreferencesKey("recent_scroll_pixels")
    
    // 실시간 사용 데이터 관련 키
    val LATEST_USAGE_REPORT_KEY = stringPreferencesKey("latest_usage_report")
    val PREDICTION_RESULT_KEY = intPreferencesKey("prediction_result")
    val PREDICTION_PROBABILITY_KEY = floatPreferencesKey("prediction_probability")
    val MODEL_INPUT_KEY = stringPreferencesKey("model_input")
    val DATA_COLLECTION_PROGRESS_KEY = intPreferencesKey("data_collection_progress")
    val LAST_UPDATE_TIMESTAMP_KEY = longPreferencesKey("last_update_timestamp")
    
    // 스크롤 총 거리 가져오기
    suspend fun getTotalScrollDistance(context: Context): Float {
        return context.scrollDataStore.data.map { preferences ->
            preferences[EXTERNAL_SCROLL_KEY] ?: 0f
        }.first()
    }
    
    // 마지막 스크롤 거리 가져오기
    suspend fun getLastScrollDistance(context: Context): Float {
        return context.scrollDataStore.data.map { preferences ->
            preferences[LAST_SCROLL_DISTANCE_KEY] ?: 0f
        }.first()
    }
    
    // 마지막 스크롤 거리 저장하기
    suspend fun saveLastScrollDistance(context: Context, distance: Float) {
        context.scrollDataStore.edit { preferences ->
            preferences[LAST_SCROLL_DISTANCE_KEY] = distance
        }
    }
    
    // 스크롤 거리 포맷팅
    fun formatScrollDistance(distance: Float): String {
        return when {
            distance >= 100000 -> "${String.format("%.1f", distance / 100000)}km"
            distance >= 100 -> "${String.format("%.1f", distance / 100)}m"
            else -> "${distance.toInt()}cm"
        }
    }
    
    // 최신 사용 리포트 Flow 가져오기
    fun getLatestUsageReportFlow(context: Context): Flow<UsageReport?> {
        return context.usageDataStore.data.map { preferences ->
            val reportJson = preferences[LATEST_USAGE_REPORT_KEY]
            if (reportJson != null) {
                try {
                    gson.fromJson(reportJson, UsageReport::class.java)
                } catch (e: Exception) {
                    null
                }
            } else {
                null
            }
        }
    }
    
    // 최신 예측 결과 Flow 가져오기
    fun getPredictionResultFlow(context: Context): Flow<Pair<Int, Float>> {
        return context.usageDataStore.data.map { preferences ->
            val result = preferences[PREDICTION_RESULT_KEY] ?: 0
            val probability = preferences[PREDICTION_PROBABILITY_KEY] ?: 0f
            Pair(result, probability)
        }
    }
    
    // 모델 입력 Flow 가져오기
    fun getModelInputFlow(context: Context): Flow<String?> {
        return context.usageDataStore.data.map { preferences ->
            preferences[MODEL_INPUT_KEY]
        }
    }
    
    // 예측 결과 저장하기
    suspend fun savePredictionResult(context: Context, result: Int, probability: Float) {
        context.usageDataStore.edit { preferences ->
            preferences[PREDICTION_RESULT_KEY] = result
            preferences[PREDICTION_PROBABILITY_KEY] = probability
            preferences[LAST_UPDATE_TIMESTAMP_KEY] = System.currentTimeMillis()
        }
    }
    
    // 데이터 수집 진행상황 가져오기 (Flow)
    fun getDataCollectionProgressFlow(context: Context): Flow<Int> {
        return context.usageDataStore.data.map { preferences ->
            preferences[DATA_COLLECTION_PROGRESS_KEY] ?: 0
        }
    }
    
    // 데이터 수집 진행상황 가져오기 (동기)
    suspend fun getDataCollectionProgress(context: Context): Int {
        return context.usageDataStore.data.map { preferences ->
            preferences[DATA_COLLECTION_PROGRESS_KEY] ?: 0
        }.first()
    }
    
    // 마지막 업데이트 시간 Flow 가져오기
    fun getLastUpdateTimestampFlow(context: Context): Flow<Long> {
        return context.usageDataStore.data.map { preferences ->
            preferences[LAST_UPDATE_TIMESTAMP_KEY] ?: 0L
        }
    }
    
    // 최근 사용한 앱 패키지 목록 키
    val RECENT_APPS_KEY = stringPreferencesKey("recent_apps_packages")
    
    // 최근 사용한 앱 패키지 목록 저장
    suspend fun saveRecentApps(context: Context, packages: List<String>) {
        try {
            // 패키지 목록을 JSON 문자열로 변환
            val packagesJson = gson.toJson(packages)
            Log.d("DataStoreUtils", "최근 앱 패키지 저장: ${packages.joinToString()}")
            
            context.usageDataStore.edit { preferences ->
                preferences[RECENT_APPS_KEY] = packagesJson
                preferences[LAST_UPDATE_TIMESTAMP_KEY] = System.currentTimeMillis()
            }
        } catch (e: Exception) {
            Log.e("DataStoreUtils", "최근 앱 저장 오류: ${e.message}")
        }
    }
    
    // 최근 사용한 앱 패키지 목록 가져오기
    fun getRecentAppsFlow(context: Context): Flow<List<String>> {
        return context.usageDataStore.data.map { preferences ->
            val packagesJson = preferences[RECENT_APPS_KEY]
            if (packagesJson != null) {
                try {
                    val type = object : TypeToken<List<String>>() {}.type
                    val packages = gson.fromJson<List<String>>(packagesJson, type)
                    Log.d("DataStoreUtils", "최근 앱 패키지 로드됨: ${packages.joinToString()}")
                    packages
                } catch (e: Exception) {
                    Log.e("DataStoreUtils", "최근 앱 패키지 파싱 오류: ${e.message}")
                    emptyList()
                }
            } else {
                Log.d("DataStoreUtils", "저장된 최근 앱 패키지 없음")
                emptyList()
            }
        }
    }
    
    // 최근 사용한 앱 패키지 목록 가져오기 (동기 버전)
    suspend fun getRecentApps(context: Context): List<String> {
        return context.usageDataStore.data.map { preferences ->
            val packagesJson = preferences[RECENT_APPS_KEY]
            if (packagesJson != null) {
                try {
                    val type = object : TypeToken<List<String>>() {}.type
                    val packages = gson.fromJson<List<String>>(packagesJson, type)
                    Log.d("DataStoreUtils", "최근 앱 패키지 로드됨 (동기): ${packages.joinToString()}")
                    packages
                } catch (e: Exception) {
                    Log.e("DataStoreUtils", "최근 앱 패키지 파싱 오류 (동기): ${e.message}")
                    emptyList()
                }
            } else {
                Log.d("DataStoreUtils", "저장된 최근 앱 패키지 없음 (동기)")
                emptyList()
            }
        }.first()
    }
    
    // 최근 스크롤 픽셀 값 가져오기
    suspend fun getRecentScrollPixels(context: Context): Int {
        return context.scrollDataStore.data.map { preferences ->
            preferences[RECENT_SCROLL_PIXELS_KEY] ?: 0
        }.first()
    }
    
    // 최근 스크롤 픽셀 값 저장하기
    suspend fun saveRecentScrollPixels(context: Context, pixels: Int) {
        context.scrollDataStore.edit { preferences ->
            preferences[RECENT_SCROLL_PIXELS_KEY] = pixels
        }
    }
}
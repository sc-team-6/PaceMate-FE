package com.gdg.scrollmanager.utils

import android.content.Context
import android.util.Log
import com.gdg.scrollmanager.models.ModelInput
import com.gdg.scrollmanager.models.UsageDataPoint
import java.util.*
import kotlin.math.min

/**
 * 5초 간격으로 수집된 데이터를 5분 단위로 집계하는 클래스
 */
object UsageDataAggregator {
    private const val TAG = "UsageDataAggregator"
    
    // 5분을 5초 단위로 나눈 개수 (60초 * 5분 / 5초)
    private const val MAX_DATA_POINTS = 60
    
    // 저장된 데이터 포인트 (최대 MAX_DATA_POINTS개)
    private val dataPoints = LinkedList<UsageDataPoint>()
    
    /**
     * 새로운 데이터 포인트를 추가합니다.
     * 최대 5분(300초) 분량의 데이터만 유지합니다.
     */
    fun addDataPoint(dataPoint: UsageDataPoint) {
        synchronized(dataPoints) {
            dataPoints.add(dataPoint)
            
            // 최대 60개 데이터 포인트만 유지 (5초 * 60 = 5분)
            while (dataPoints.size > MAX_DATA_POINTS) {
                dataPoints.removeFirst()
            }
            
            Log.d(TAG, "데이터 포인트 추가됨. 현재 개수: ${dataPoints.size}/${MAX_DATA_POINTS}")
        }
    }
    
    /**
     * 데이터를 5분 단위로 집계합니다.
     * 만약 5분보다 적은 데이터가 있다면, 비례하여 확장합니다.
     * 최소 6개(30초) 이상의 데이터가 있을 때부터 유효한 결과를 제공합니다.
     */
    fun aggregateData(): ModelInput {
        synchronized(dataPoints) {
            // 데이터가 없는 경우 기본값 반환
            if (dataPoints.isEmpty()) {
                Log.d(TAG, "집계할 데이터가 없습니다.")
                return createEmptyModelInput()
            }
            
            // 최소 6개(30초) 이상의 데이터가 필요
            val minDataPoints = 6
            if (dataPoints.size < minDataPoints) {
                Log.d(TAG, "데이터가 부족합니다. 현재: ${dataPoints.size}, 필요: $minDataPoints")
                // 데이터가 부족해도 현재까지 수집된 데이터로 집계
            }
            
            val scaleFactor = MAX_DATA_POINTS.toFloat() / dataPoints.size.toFloat()
            Log.d(TAG, "데이터 확장 비율: $scaleFactor (${dataPoints.size}/${MAX_DATA_POINTS})")
            
            // 현재 5분(또는 그보다 적은 시간) 동안의 집계
            var currentScreenSeconds = 0
            var currentScrollPx = 0
            var currentUnlocks = 0
            var currentAppsUsed = 0
            val currentPackages = mutableSetOf<String>()
            
            // 데이터 포인트 집계
            for (point in dataPoints) {
                currentScreenSeconds += point.screenTimeSeconds
                currentScrollPx += point.scrollPixels
                currentUnlocks += point.unlockCount
                currentPackages.addAll(point.appPackages)
            }
            
            currentAppsUsed = currentPackages.size
            
            // 5분 기준으로 확장
            val scaledScreenSeconds = (currentScreenSeconds * scaleFactor).toInt()
            val scaledScrollPx = (currentScrollPx * scaleFactor).toInt()
            val scaledUnlocks = (currentUnlocks * scaleFactor).toInt()
            
            // 최근 15분/30분/1시간 데이터는 현재 데이터로부터 추정
            // (실제로는 이전 데이터도 저장하고 있어야 더 정확함)
            val screenLast15m = min(scaledScreenSeconds * 3, 15 * 60) // 최대 15분(900초)
            val screenLast30m = min(scaledScreenSeconds * 6, 30 * 60) // 최대 30분(1800초)
            val screenLast1h = min(scaledScreenSeconds * 12, 60 * 60) // 최대 1시간(3600초)
            
            val unlocksLast15m = min(scaledUnlocks * 3, 30) // 임의 최대값 설정
            
            // 분당 잠금 해제 횟수
            val unlocksPerMin = if (scaledUnlocks > 0) scaledUnlocks / 5.0f else 0f
            
            // 초당 스크롤 픽셀 수
            val scrollRate = if (scaledScreenSeconds > 0) scaledScrollPx / scaledScreenSeconds.toFloat() else 0f
            
            // 현재 시간
            val calendar = Calendar.getInstance()
            val hour = calendar.get(Calendar.HOUR_OF_DAY)
            val minute = calendar.get(Calendar.MINUTE)
            
            // 시간 정보를 사인/코사인으로 인코딩
            val sinHour = kotlin.math.sin(2 * kotlin.math.PI * hour / 24).toFloat()
            val cosHour = kotlin.math.cos(2 * kotlin.math.PI * hour / 24).toFloat()
            val sinMinute = kotlin.math.sin(2 * kotlin.math.PI * minute / 60).toFloat()
            val cosMinute = kotlin.math.cos(2 * kotlin.math.PI * minute / 60).toFloat()
            
            // 앱 임베딩 (임시로 0 값 사용)
            val appEmbedding = List(32) { 0f }
            
            return ModelInput(
                screenSeconds = scaledScreenSeconds,
                scrollPx = scaledScrollPx,
                unlocks = scaledUnlocks,
                appsUsed = currentAppsUsed,
                screenLast15m = screenLast15m,
                screenLast30m = screenLast30m,
                screenLast1h = screenLast1h,
                unlocksPerMin = unlocksPerMin,
                unlocksLast15m = unlocksLast15m,
                scrollRate = scrollRate,
                sinHour = sinHour,
                cosHour = cosHour,
                sinMinute = sinMinute,
                cosMinute = cosMinute,
                appEmbedding = appEmbedding
            )
        }
    }
    
    /**
     * 빈 모델 입력을 생성합니다.
     */
    private fun createEmptyModelInput(): ModelInput {
        val calendar = Calendar.getInstance()
        val hour = calendar.get(Calendar.HOUR_OF_DAY)
        val minute = calendar.get(Calendar.MINUTE)
        
        val sinHour = kotlin.math.sin(2 * kotlin.math.PI * hour / 24).toFloat()
        val cosHour = kotlin.math.cos(2 * kotlin.math.PI * hour / 24).toFloat()
        val sinMinute = kotlin.math.sin(2 * kotlin.math.PI * minute / 60).toFloat()
        val cosMinute = kotlin.math.cos(2 * kotlin.math.PI * minute / 60).toFloat()
        
        return ModelInput(
            screenSeconds = 0,
            scrollPx = 0,
            unlocks = 0,
            appsUsed = 0,
            screenLast15m = 0,
            screenLast30m = 0,
            screenLast1h = 0,
            unlocksPerMin = 0f,
            unlocksLast15m = 0,
            scrollRate = 0f,
            sinHour = sinHour,
            cosHour = cosHour,
            sinMinute = sinMinute,
            cosMinute = cosMinute,
            appEmbedding = List(32) { 0f }
        )
    }
    
    /**
     * 현재 저장된 데이터 포인트의 개수를 반환합니다.
     */
    fun getDataPointCount(): Int {
        synchronized(dataPoints) {
            return dataPoints.size
        }
    }
    
    /**
     * 데이터 포인트 수집의 진행 상황을 퍼센트로 반환합니다.
     */
    fun getDataCollectionProgress(): Int {
        synchronized(dataPoints) {
            return (dataPoints.size * 100 / MAX_DATA_POINTS)
        }
    }
    
    /**
     * 모든 데이터 포인트를 지웁니다.
     */
    fun clearData() {
        synchronized(dataPoints) {
            dataPoints.clear()
            Log.d(TAG, "모든 데이터 포인트 삭제됨")
        }
    }
}
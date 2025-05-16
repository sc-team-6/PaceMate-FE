package com.gdg.scrollmanager.utils

import android.content.Context
import android.util.Log
import com.gdg.scrollmanager.models.ModelInput
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.io.IOException
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * ONNX 모델 입력 데이터 생성을 위한 유틸리티 클래스
 */
object ModelInputUtils {
    private const val TAG = "ModelInputUtils"
    private const val APP_EMBEDDING_DIM = 32
    
    /**
     * 모델 입력을 위한 데이터를 생성합니다.
     */
    fun createModelInput(
        context: Context,
        screenSeconds: Int,               // 현재 5분 간격 화면 켜짐 시간 (초)
        scrollPx: Int,                    // 현재 5분 간격 스크롤 양 (픽셀)
        unlocks: Int,                     // 현재 5분 간격 잠금 해제 횟수
        appsUsed: Int,                    // 현재 5분 간격 사용한 앱 개수
        screenLast15m: Int,               // 최근 15분간 화면 켜짐 시간 (초)
        screenLast30m: Int,               // 최근 30분간 화면 켜짐 시간 (초)
        screenLast1h: Int,                // 최근 1시간 화면 켜짐 시간 (초)
        unlocksLast15m: Int,              // 최근 15분간 잠금 해제 횟수
        packageNames: List<String> = listOf() // 사용한 앱 패키지 목록
    ): ModelInput {
        // 현재 시간 정보 가져오기
        val calendar = java.util.Calendar.getInstance()
        val hour = calendar.get(java.util.Calendar.HOUR_OF_DAY)
        val minute = calendar.get(java.util.Calendar.MINUTE)
        
        // 시간 정보를 사인/코사인으로 인코딩 
        val sinHour = sin(2 * PI * hour / 24)
        val cosHour = cos(2 * PI * hour / 24)
        val sinMinute = sin(2 * PI * minute / 60)
        val cosMinute = cos(2 * PI * minute / 60)
        
        // 분당 잠금 해제 횟수 (5분 기준)
        val unlocksPerMin = if (unlocks > 0) unlocks / 5.0f else 0f
        
        // 스크롤 속도 (초당 픽셀)
        val scrollRate = if (screenSeconds > 0) scrollPx / screenSeconds.toFloat() else 0f
        
        // 앱 임베딩 가져오기 
        val appEmbedding = getAppEmbedding(context, packageNames)
        
        return ModelInput(
            screenSeconds = screenSeconds,
            scrollPx = scrollPx,
            unlocks = unlocks,
            appsUsed = appsUsed,
            screenLast15m = screenLast15m,
            screenLast30m = screenLast30m,
            screenLast1h = screenLast1h,
            unlocksPerMin = unlocksPerMin,
            unlocksLast15m = unlocksLast15m,
            scrollRate = scrollRate,
            sinHour = sinHour.toFloat(),
            cosHour = cosHour.toFloat(),
            sinMinute = sinMinute.toFloat(),
            cosMinute = cosMinute.toFloat(),
            appEmbedding = appEmbedding
        )
    }
    
    /**
     * 앱 임베딩을 가져옵니다. 
     * 현재는 단순히 0으로 채워진 32차원 벡터를 반환합니다.
     * 추후 앱 임베딩 JSON이 있으면 그것을 사용하도록 업데이트 필요
     */
    private fun getAppEmbedding(context: Context, packageNames: List<String>): List<Float> {
        // 임시로 0으로 채워진 32차원 벡터 반환
        return List(APP_EMBEDDING_DIM) { 0f }
        
        // TODO: 실제 앱 임베딩 JSON이 있을 경우 아래 코드 활성화
        /*
        try {
            val gson = Gson()
            val json = context.assets.open("app_emb.json").bufferedReader().use { it.readText() }
            val type = object : TypeToken<Map<String, List<Float>>>() {}.type
            val appEmbMap: Map<String, List<Float>> = gson.fromJson(json, type)
            
            return averageEmbedding(packageNames, appEmbMap)
        } catch (e: IOException) {
            Log.e(TAG, "앱 임베딩 로드 실패: ${e.message}")
            return List(APP_EMBEDDING_DIM) { 0f }
        }
        */
    }
    
    /**
     * 앱 패키지에 해당하는 임베딩을 가져옵니다.
     */
    private fun getEmbedding(pkg: String, appEmb: Map<String, List<Float>>, dim: Int = APP_EMBEDDING_DIM): List<Float> {
        return appEmb[pkg] ?: List(dim) { 0f }
    }
    
    /**
     * 여러 앱 패키지의 임베딩 평균을 계산합니다.
     */
    private fun averageEmbedding(packages: List<String>, appEmb: Map<String, List<Float>>, dim: Int = APP_EMBEDDING_DIM): List<Float> {
        val vectors = packages.mapNotNull { appEmb[it] }
        if (vectors.isEmpty()) return List(dim) { 0f }
        
        return List(dim) { i ->
            vectors.map { it[i] }.average().toFloat()
        }
    }
}
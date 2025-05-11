package com.gdg.scrollmanager.ml

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import android.content.Context
import android.util.Log
import java.nio.FloatBuffer
import java.util.*

/**
 * 휴대폰 사용 패턴 분석 및 중독 예측을 위한 ONNX Runtime 유틸리티 클래스
 */
class PhoneUsagePredictor(private val context: Context) {
    
    private var ortEnvironment: OrtEnvironment? = null
    private var session: OrtSession? = null
    private val TAG = "PhoneUsagePredictor"

    // 카테고리형 특성과 숫자 인코딩을 위한 매핑
    private val appCategoryMapping = mapOf(
        "Social" to 0,
        "Games" to 1,
        "Entertainment" to 2,
        "Utility" to 3,
        "Productivity" to 4,
        "Communication" to 5,
        "Education" to 6,
        "Unknown" to 3  // 알 수 없는 카테고리는 Utility로 처리
    )

    /**
     * ONNX 모델 초기화
     */
    fun initialize() {
        try {
            ortEnvironment = OrtEnvironment.getEnvironment()
            try {
                val modelBytes = context.assets.open("phone_usage_model.onnx").readBytes()
                session = ortEnvironment?.createSession(modelBytes)
                Log.d(TAG, "모델 로드 성공")
            } catch (e: Exception) {
                Log.e(TAG, "모델 로드 실패: ${e.message}")
                e.printStackTrace()
                // 모델 파일이 없을 경우 여기서 예외 발생 - 계속 진행
            }
        } catch (e: Exception) {
            Log.e(TAG, "ONNX Runtime 초기화 실패: ${e.message}")
            e.printStackTrace()
        }
    }

    /**
     * 휴대폰 사용 데이터에 대한 추론 실행
     * @return Pair<FloatArray, Int> - 첫 번째 항목은 확률, 두 번째는 분류 결과 (0: 정상, 1: 중독)
     */
    fun predict(
        recent15minUsage: Int,
        recent30minUsage: Int,
        recent60minUsage: Int,
        unlocks15min: Int,
        appSwitches15min: Int,
        snsAppUsage: Int,
        avgSessionLength: Float,
        hour: Int,
        dayOfWeek: Int,
        scrollLength: Int,
        unlockRate: Float,
        switchRate: Float,
        scrollRate: Float,
        topAppCategory: String
    ): Pair<FloatArray, Int> {
        
        // ONNX 모델이 로드되지 않은 경우 휴리스틱 모델 사용
        if (session == null) {
            Log.d(TAG, "ONNX 모델 없음, 휴리스틱 모델 사용")
            return predictWithHeuristic(
                recent15minUsage, recent30minUsage, recent60minUsage, 
                unlocks15min, appSwitches15min, snsAppUsage, 
                avgSessionLength, hour, dayOfWeek, scrollLength, 
                unlockRate, switchRate, scrollRate, topAppCategory
            )
        }
        
        // 입력 맵 생성
        val inputMap = HashMap<String, OnnxTensor>()
        
        try {
            // ONNX 모델이 기대하는 입력 이름으로 변경
            inputMap["recent_15min_usage"] = OnnxTensor.createTensor(ortEnvironment, FloatBuffer.wrap(floatArrayOf(recent15minUsage.toFloat())), longArrayOf(1))
            inputMap["recent_30min_usage"] = OnnxTensor.createTensor(ortEnvironment, FloatBuffer.wrap(floatArrayOf(recent30minUsage.toFloat())), longArrayOf(1))
            inputMap["recent_60min_usage"] = OnnxTensor.createTensor(ortEnvironment, FloatBuffer.wrap(floatArrayOf(recent60minUsage.toFloat())), longArrayOf(1))
            inputMap["unlocks_15min"] = OnnxTensor.createTensor(ortEnvironment, FloatBuffer.wrap(floatArrayOf(unlocks15min.toFloat())), longArrayOf(1))
            inputMap["app_switches_15min"] = OnnxTensor.createTensor(ortEnvironment, FloatBuffer.wrap(floatArrayOf(appSwitches15min.toFloat())), longArrayOf(1))
            inputMap["sns_app_usage"] = OnnxTensor.createTensor(ortEnvironment, FloatBuffer.wrap(floatArrayOf(snsAppUsage.toFloat())), longArrayOf(1))
            inputMap["avg_session_length"] = OnnxTensor.createTensor(ortEnvironment, FloatBuffer.wrap(floatArrayOf(avgSessionLength)), longArrayOf(1))
            inputMap["hour"] = OnnxTensor.createTensor(ortEnvironment, FloatBuffer.wrap(floatArrayOf(hour.toFloat())), longArrayOf(1))
            inputMap["dayofweek"] = OnnxTensor.createTensor(ortEnvironment, FloatBuffer.wrap(floatArrayOf(dayOfWeek.toFloat())), longArrayOf(1))
            inputMap["scroll_length"] = OnnxTensor.createTensor(ortEnvironment, FloatBuffer.wrap(floatArrayOf(scrollLength.toFloat())), longArrayOf(1))
            inputMap["unlock_rate"] = OnnxTensor.createTensor(ortEnvironment, FloatBuffer.wrap(floatArrayOf(unlockRate)), longArrayOf(1))
            inputMap["switch_rate"] = OnnxTensor.createTensor(ortEnvironment, FloatBuffer.wrap(floatArrayOf(switchRate)), longArrayOf(1))
            inputMap["scroll_rate"] = OnnxTensor.createTensor(ortEnvironment, FloatBuffer.wrap(floatArrayOf(scrollRate)), longArrayOf(1))
            
            // 앱 카테고리는 숫자형 인코딩 사용
            val categoryValue = appCategoryMapping[topAppCategory] ?: 3 // 기본값은 Utility
            inputMap["top_app_category"] = OnnxTensor.createTensor(ortEnvironment, FloatBuffer.wrap(floatArrayOf(categoryValue.toFloat())), longArrayOf(1))
            
            // 추론 실행
            val results = session?.run(inputMap)
            
            // 모델이 로드되지 않았거나 추론에 실패한 경우 기본값 반환
            if (results == null) {
                Log.e(TAG, "추론 결과가 null, 휴리스틱 모델 사용")
                return predictWithHeuristic(
                    recent15minUsage, recent30minUsage, recent60minUsage, 
                    unlocks15min, appSwitches15min, snsAppUsage, 
                    avgSessionLength, hour, dayOfWeek, scrollLength, 
                    unlockRate, switchRate, scrollRate, topAppCategory
                )
            }
            
            try {
                // 출력 처리
                val probabilitiesOutput = results.get(0)?.value as? Array<*>
                val predictionOutput = results.get(1)?.value as? Array<*>
                
                // null 체크 후 안전하게 처리
                if (probabilitiesOutput != null && predictionOutput != null) {
                    val probabilities = (probabilitiesOutput[0] as? FloatArray) ?: floatArrayOf(0.5f, 0.5f)
                    val prediction = ((predictionOutput[0] as? LongArray)?.get(0) ?: 0L).toInt()
                    
                    Log.d(TAG, "ONNX 모델 예측 성공: $prediction, 확률: ${probabilities[0]} ${probabilities[1]}")
                    return Pair(probabilities, prediction)
                } else {
                    Log.e(TAG, "출력 결과가 null, 휴리스틱 모델 사용")
                    return predictWithHeuristic(
                        recent15minUsage, recent30minUsage, recent60minUsage, 
                        unlocks15min, appSwitches15min, snsAppUsage, 
                        avgSessionLength, hour, dayOfWeek, scrollLength, 
                        unlockRate, switchRate, scrollRate, topAppCategory
                    )
                }
            } catch (e: Exception) {
                Log.e(TAG, "출력 처리 예외 발생: ${e.message}")
                e.printStackTrace()
                return predictWithHeuristic(
                    recent15minUsage, recent30minUsage, recent60minUsage, 
                    unlocks15min, appSwitches15min, snsAppUsage, 
                    avgSessionLength, hour, dayOfWeek, scrollLength, 
                    unlockRate, switchRate, scrollRate, topAppCategory
                )
            } finally {
                // 리소스 정리 - 모든 텐서 닫기
                for (tensor in inputMap.values) {
                    try {
                        tensor.close()
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "입력 생성 예외 발생: ${e.message}")
            e.printStackTrace()
            return predictWithHeuristic(
                recent15minUsage, recent30minUsage, recent60minUsage, 
                unlocks15min, appSwitches15min, snsAppUsage, 
                avgSessionLength, hour, dayOfWeek, scrollLength, 
                unlockRate, switchRate, scrollRate, topAppCategory
            )
        }
    }
    
    /**
     * ONNX 모델 사용 실패 시 대체 예측 방법
     */
    private fun predictWithHeuristic(
        recent15minUsage: Int,
        recent30minUsage: Int,
        recent60minUsage: Int,
        unlocks15min: Int,
        appSwitches15min: Int,
        snsAppUsage: Int,
        avgSessionLength: Float,
        hour: Int,
        dayOfWeek: Int,
        scrollLength: Int,
        unlockRate: Float,
        switchRate: Float,
        scrollRate: Float,
        topAppCategory: String
    ): Pair<FloatArray, Int> {
        // 간단한 휴리스틱 모델 구현
        var score = 0.0f
        
        // 사용 시간 요소
        score += recent15minUsage * 0.1f
        score += recent30minUsage * 0.05f
        score += recent60minUsage * 0.025f
        
        // 휴대폰 상호작용 요소
        score += unlocks15min * 0.2f
        score += appSwitches15min * 0.1f
        score += snsAppUsage * 0.3f
        
        // 시간 요소
        score += avgSessionLength * 0.01f // 세션 길이는 초 단위이므로 낮은 가중치 부여
        
        // 시간대를 고려 (야간 사용을 더 높게 가중치)
        if (hour >= 22 || hour <= 5) {
            score *= 1.5f
        }
        
        // 스크롤 및 잠금 해제 요소
        score += scrollLength / 1000f // 스크롤 길이는 큰 값이므로 낮은 가중치
        score += unlockRate * 2f
        score += switchRate * 1.5f
        score += scrollRate * 10f
        
        // 앱 카테고리 요소
        when (topAppCategory) {
            "Social" -> score *= 1.5f
            "Game", "Games" -> score *= 1.4f
            "Entertainment" -> score *= 1.3f
            else -> score *= 0.9f
        }
        
        // 점수를 확률로 정규화
        val normalizedScore = minOf(1.0f, maxOf(0.0f, score / 15f))
        
        // 확률 배열 생성
        val probabilities = floatArrayOf(1.0f - normalizedScore, normalizedScore)
        
        // 예측 결과 결정
        val prediction = if (normalizedScore > 0.5f) 1 else 0
        
        Log.d(TAG, "휴리스틱 예측 결과: $prediction, 확률: ${probabilities[0]} ${probabilities[1]}")
        
        return Pair(probabilities, prediction)
    }
    
    /**
     * ONNX 세션 및 환경 종료
     */
    fun close() {
        try {
            session?.close()
            ortEnvironment?.close()
        } catch (e: Exception) {
            Log.e(TAG, "리소스 해제 중 오류: ${e.message}")
            e.printStackTrace()
        }
    }
}
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
                
                try {
                    Log.d(TAG, "모델 입력 개수: ${session?.inputInfo?.size ?: 0}")
                    session?.inputInfo?.forEach { (name, info) ->
                        Log.d(TAG, "모델 입력 이름: $name")
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "모델 입력 정보 가져오기 실패: ${e.message}")
                }
                
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
            // 카테고리 값 인코딩 - 정수로 변환
            val categoryValue = appCategoryMapping[topAppCategory] ?: 3 // 기본값은 Utility
            
            // 입력 이름과 데이터 확인을 위한 로깅
            Log.d(TAG, "모델 입력 데이터:")
            Log.d(TAG, "recent_15min_usage: $recent15minUsage")
            Log.d(TAG, "recent_30min_usage: $recent30minUsage")
            Log.d(TAG, "recent_60min_usage: $recent60minUsage")
            Log.d(TAG, "unlocks_15min: $unlocks15min")
            Log.d(TAG, "app_switches_15min: $appSwitches15min")
            Log.d(TAG, "sns_app_usage: $snsAppUsage")
            Log.d(TAG, "avg_session_length: $avgSessionLength")
            Log.d(TAG, "hour: $hour")
            Log.d(TAG, "dayofweek: $dayOfWeek")
            Log.d(TAG, "scroll_length: $scrollLength")
            Log.d(TAG, "unlock_rate: $unlockRate")
            Log.d(TAG, "switch_rate: $switchRate")
            Log.d(TAG, "scroll_rate: $scrollRate")
            Log.d(TAG, "top_app_category: $topAppCategory -> $categoryValue")
            
            // 모델 입력 정보 확인
            val inputInfo = session?.inputInfo
            if (inputInfo == null || inputInfo.isEmpty()) {
                Log.e(TAG, "모델 입력 정보가 없음")
                return predictWithHeuristic(
                    recent15minUsage, recent30minUsage, recent60minUsage, 
                    unlocks15min, appSwitches15min, snsAppUsage, 
                    avgSessionLength, hour, dayOfWeek, scrollLength, 
                    unlockRate, switchRate, scrollRate, topAppCategory
                )
            }
            
            // 입력 이름 매핑 (모델에 따라 조정 필요)
            val numericInputs = mapOf(
                "recent_15min_usage" to recent15minUsage.toFloat(),
                "recent_30min_usage" to recent30minUsage.toFloat(),
                "recent_60min_usage" to recent60minUsage.toFloat(),
                "unlocks_15min" to unlocks15min.toFloat(),
                "app_switches_15min" to appSwitches15min.toFloat(),
                "sns_app_usage" to snsAppUsage.toFloat(),
                "avg_session_length" to avgSessionLength,
                "hour" to hour.toFloat(),
                "day_of_week" to dayOfWeek.toFloat(),
                "scroll_length" to scrollLength.toFloat(),
                "unlock_rate" to unlockRate,
                "switch_rate" to switchRate,
                "scroll_rate" to scrollRate
            )
            
            val stringInputs = mapOf(
                "top_app_category" to topAppCategory
            )
            
            // 모델의 입력에 맞게 텐서 생성
            for ((name, info) in inputInfo) {
                // 입력 이름이 숫자 매핑에 있는지 확인
                val numValue = numericInputs[name]
                if (numValue != null) {
                    // 숫자 텐서 생성
                    val tensor = OnnxTensor.createTensor(
                        ortEnvironment,
                        FloatBuffer.wrap(floatArrayOf(numValue)),
                        longArrayOf(1, 1) // 차원을 [1, 1]로 설정 (2차원 텐서)
                    )
                    inputMap[name] = tensor
                    Log.d(TAG, "숫자 입력 텐서 생성: $name = $numValue")
                    continue
                }
                
                // 입력 이름이 문자열 매핑에 있는지 확인
                val strValue = stringInputs[name]
                if (strValue != null) {
                    // 문자열 텐서 생성
                    val strArray = Array(1) { Array(1) { strValue } } // 2차원 문자열 배열 [1, 1]
                    val tensor = OnnxTensor.createTensor(ortEnvironment, strArray)
                    inputMap[name] = tensor
                    Log.d(TAG, "문자열 입력 텐서 생성: $name = $strValue")
                    continue
                }
                
                Log.w(TAG, "모델이 요구하는 입력 이름이 매핑에 없음: $name")
            }
            
            // 추론 실행
            val results = session?.run(inputMap)
            Log.d(TAG, "추론 완료, 결과: ${results?.size() ?: 0}개")
            
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
                // 결과 확인을 위한 로깅
                for (i in 0 until (results.size() ?: 0)) {
                    // 출력 이름 대신 인덱스 사용
                    val outputIndex = i
                    Log.d(TAG, "결과[$i] 인덱스: $outputIndex")
                    Log.d(TAG, "결과[$i] 값 타입: ${results.get(i)?.value?.javaClass}")
                }
                
                // 출력 처리 - 출력 형식에 맞게 수정
                // 로그를 통해 확인한 결과: 
                // [0] - Long 배열(prediction)
                // [1] - 2차원 Float 배열(probabilities)
                
                val predictionOutput = results.get(0)?.value as? LongArray
                val probabilitiesOutput = results.get(1)?.value as? Array<FloatArray>
                
                // null 체크 후 안전하게 처리
                if (predictionOutput != null && probabilitiesOutput != null && probabilitiesOutput.isNotEmpty()) {
                    val prediction = predictionOutput[0].toInt()
                    val probabilities = probabilitiesOutput[0]
                    
                    Log.d(TAG, "ONNX 모델 예측 성공: $prediction, 확률: ${probabilities.joinToString()}")
                    return Pair(probabilities, prediction)
                } else {
                    Log.e(TAG, "출력 결과 타입 변환 실패, 휴리스틱 모델 사용")
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
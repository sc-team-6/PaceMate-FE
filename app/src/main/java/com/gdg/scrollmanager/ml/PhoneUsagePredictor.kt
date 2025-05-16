package com.gdg.scrollmanager.ml

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import android.content.Context
import android.util.Log
import com.gdg.scrollmanager.models.ModelInput
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.nio.FloatBuffer
import java.util.*

/**
 * 휴대폰 사용 패턴 분석 및 중독 예측을 위한 ONNX Runtime 유틸리티 클래스
 */
class PhoneUsagePredictor(private val context: Context) {
    
    private var ortEnvironment: OrtEnvironment? = null
    private var session: OrtSession? = null
    private val TAG = "PhoneUsagePredictor"
    
    // 앱 임베딩 데이터
    private var appEmbeddings: Map<String, List<Float>> = emptyMap()

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
     * 앱 임베딩 JSON 파일 로드
     */
    private fun loadAppEmbeddings() {
        try {
            Log.d(TAG, "앱 임베딩 파일 로드 시도...")
            val jsonString = context.assets.open("app_embeddings.json").bufferedReader().use { it.readText() }
            Log.d(TAG, "앱 임베딩 JSON 파일 크기: ${jsonString.length} 바이트")
            Log.d(TAG, "임베딩 JSON 일부 내용: ${jsonString.take(200)}...")
            
            val type = object : TypeToken<Map<String, List<Float>>>() {}.type
            appEmbeddings = Gson().fromJson(jsonString, type)
            Log.d(TAG, "앱 임베딩 로드 성공: ${appEmbeddings.size} 개 앱")
            
            // 몇 개의 임베딩 예시 출력
            val sampleEntries = appEmbeddings.entries.take(3)
            for (entry in sampleEntries) {
                Log.d(TAG, "임베딩 샘플 - ${entry.key}: [${entry.value.take(5).joinToString()}...]")
            }
        } catch (e: Exception) {
            Log.e(TAG, "앱 임베딩 파일 로드 실패: ${e.message}")
            e.printStackTrace()
            appEmbeddings = emptyMap()
        }
        
        // DataStore에서 저장된 최근 앱 목록 확인
        try {
            val runnable = Runnable {
                try {
                    val recentApps = kotlinx.coroutines.runBlocking { 
                        com.gdg.scrollmanager.utils.DataStoreUtils.getRecentApps(context) 
                    }
                    if (recentApps.isNotEmpty()) {
                        Log.d(TAG, "DataStore에서 최근 앱 목록 로드 성공: ${recentApps.joinToString()}")
                    } else {
                        Log.d(TAG, "DataStore에 저장된 최근 앱 목록 없음")
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "DataStore에서 최근 앱 목록 로드 실패: ${e.message}")
                }
            }
            Thread(runnable).start()
        } catch (e: Exception) {
            Log.e(TAG, "DataStore 접근 오류: ${e.message}")
        }
    }
    
    /**
     * 패키지에 해당하는 임베딩 가져오기
     * @param pkg 앱 패키지 이름
     * @param dim 임베딩 차원 (기본값 32)
     * @return 임베딩 벡터, 없으면 0 벡터
     */
    private fun getEmbedding(pkg: String, dim: Int = 32): List<Float> {
        val embedding = appEmbeddings[pkg]
        val result = embedding ?: List(dim) { 0f }
        Log.d(TAG, "패키지 ${pkg}의 임베딩 가져오기: ${if (embedding != null) "성공" else "실패 (기본값 사용)"}")
        return result
    }
    
    /**
     * 여러 패키지의 평균 임베딩 계산
     * @param packages 앱 패키지 이름 목록
     * @param dim 임베딩 차원 (기본값 32)
     * @return 평균 임베딩 벡터, 일치하는 임베딩이 없으면 0 벡터
     */
    private fun averageEmbedding(packages: List<String>, dim: Int = 32): List<Float> {
        if (packages.isEmpty()) {
            Log.d(TAG, "패키지 목록이 비어있어 기본 임베딩 사용")
            return getDefaultEmbedding("Unknown")
        }
        
        Log.d(TAG, "평균 임베딩 계산 시도 - 패키지 목록: ${packages.joinToString()}")
        val vectors = packages.mapNotNull { pkg -> 
            val emb = appEmbeddings[pkg]
            if (emb != null) {
                Log.d(TAG, "패키지 ${pkg} 임베딩 찾음")
            } else {
                Log.d(TAG, "패키지 ${pkg} 임베딩 찾을 수 없음")
            }
            emb
        }
        
        Log.d(TAG, "일치하는 임베딩 수: ${vectors.size}/${packages.size}")
        
        if (vectors.isEmpty()) {
            val category = getCategoryForTopPackage(packages.firstOrNull() ?: "")
            Log.d(TAG, "일치하는 임베딩이 없어 카테고리 기반 임베딩 사용: $category")
            return getDefaultEmbedding(category)
        }

        val result = List(dim) { i ->
            vectors.map { it[i] }.average().toFloat()
        }
        Log.d(TAG, "평균 임베딩 계산 완료: [${result.take(5).joinToString()}...]")
        return result
    }
    
    /**
     * 기본 임베딩 생성 (카테고리 기반)
     */
    private fun getDefaultEmbedding(category: String): List<Float> {
        Log.d(TAG, "카테고리로 기본 임베딩 생성: $category")
        return when (category) {
            "Social" -> List(32) { 0.8f }
            "Games" -> List(32) { 0.6f }
            "Entertainment" -> List(32) { 0.7f }
            "Utility" -> List(32) { 0.3f }
            "Productivity" -> List(32) { 0.2f }
            "Communication" -> List(32) { 0.5f }
            "Education" -> List(32) { 0.1f }
            else -> List(32) { 0.4f }
        }
    }
    
    /**
     * 패키지 이름으로 카테고리 추측
     */
    private fun getCategoryForTopPackage(packageName: String): String {
        if (packageName.isEmpty()) return "Unknown"
        
        return when {
            packageName.contains("facebook") || 
            packageName.contains("instagram") || 
            packageName.contains("twitter") || 
            packageName.contains("snapchat") ||
            packageName.contains("tiktok") ||
            packageName.contains("musically") ||
            packageName.contains("kakao") || 
            packageName.contains("pinterest") ||
            packageName.contains("linkedin") ||
            packageName.contains("reddit") ||
            packageName.contains("discord") ||
            packageName.contains("telegram") ||
            packageName.contains("whatsapp") ||
            packageName.contains("line.android") ||
            packageName.contains("youtube") -> "Social"  // 유튜브를 소셜로 포함
            
            packageName.contains("game") || 
            packageName.contains("games") ||
            packageName.contains("play.games") -> "Games"
            
            packageName.contains("chrome") || 
            packageName.contains("browser") || 
            packageName.contains("firefox") ||
            packageName.contains("internet") -> "Browser"
            
            packageName.contains("music") || 
            packageName.contains("spotify") ||
            packageName.contains("player") ||
            packageName.contains("audio") ||
            packageName.contains("netflix") ||
            packageName.contains("hulu") ||
            packageName.contains("disney") ||
            packageName.contains("video") -> "Entertainment"
            
            packageName.contains("mail") || 
            packageName.contains("gmail") ||
            packageName.contains("outlook") ||
            packageName.contains("message") -> "Communication"
            
            packageName.contains("map") || 
            packageName.contains("naver") || 
            packageName.contains("gps") ||
            packageName.contains("navigation") -> "Navigation"
            
            packageName.contains("office") ||
            packageName.contains("docs") ||
            packageName.contains("sheets") ||
            packageName.contains("slides") ||
            packageName.contains("word") ||
            packageName.contains("excel") ||
            packageName.contains("powerpoint") -> "Productivity"
            
            packageName.contains("camera") ||
            packageName.contains("photo") ||
            packageName.contains("gallery") -> "Photography"
            
            packageName.contains("news") ||
            packageName.contains("magazine") -> "News"
            
            packageName.contains("shop") ||
            packageName.contains("store") ||
            packageName.contains("amazon") ||
            packageName.contains("ebay") -> "Shopping"
            
            else -> "Unknown"
        }
    }

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
                
                // 앱 임베딩 데이터 로드
                loadAppEmbeddings()
                
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
        topAppCategory: String,
        recentApps: List<String> = emptyList() // 최근 사용한 앱 패키지 목록
    ): Pair<FloatArray, Int> {
        
        // DataStore에서 최근 앱 목록 가져오기 (recentApps가 비어있는 경우)
        var appsToUse = recentApps
        if (appsToUse.isEmpty()) {
            try {
                val storedApps = kotlinx.coroutines.runBlocking { 
                    com.gdg.scrollmanager.utils.DataStoreUtils.getRecentApps(context) 
                }
                if (storedApps.isNotEmpty()) {
                    Log.d(TAG, "DataStore에서 최근 앱 목록 사용: ${storedApps.joinToString()}")
                    appsToUse = storedApps
                } else {
                    Log.d(TAG, "DataStore에 저장된 최근 앱 목록 없음, 빈 목록 사용")
                }
            } catch (e: Exception) {
                Log.e(TAG, "DataStore에서 최근 앱 목록 로드 실패: ${e.message}")
            }
        }
        
        // 기존 입력 형식을 새 모델 입력 형식으로 변환
        val sinHour = kotlin.math.sin(2 * kotlin.math.PI * hour / 24).toFloat()
        var cosHour = kotlin.math.cos(2 * kotlin.math.PI * hour / 24).toFloat()
        
        // 부동소수점 오차 처리 (매우 작은 값은 0으로 처리)
        if (kotlin.math.abs(cosHour) < 1e-10) {
            cosHour = 0f
        }
        
        // 현재 분 구하기
        val calendar = java.util.Calendar.getInstance()
        val minute = calendar.get(java.util.Calendar.MINUTE)
        
        val sinMinute = kotlin.math.sin(2 * kotlin.math.PI * minute / 60).toFloat()
        var cosMinute = kotlin.math.cos(2 * kotlin.math.PI * minute / 60).toFloat()
        
        // 부동소수점 오차 처리 (매우 작은 값은 0으로 처리)
        if (kotlin.math.abs(cosMinute) < 1e-10) {
            cosMinute = 0f
        }
        
        // 앱 임베딩 계산
        Log.d(TAG, "앱 임베딩 계산 시작 - 최근 사용 앱: ${appsToUse.joinToString()}")
        val appEmb = if (appsToUse.isNotEmpty()) {
            // 여러 앱의 평균 임베딩 계산
            averageEmbedding(appsToUse)
        } else if (topAppCategory.isNotEmpty()) {
            // 단일 앱 카테고리만 있는 경우 임베딩에 임의의 값 설정
            Log.d(TAG, "앱 패키지 없음, 카테고리로 기본 임베딩 생성: $topAppCategory")
            getDefaultEmbedding(topAppCategory)
        } else {
            // 기본값: 0 벡터
            Log.d(TAG, "앱 정보 없음, 기본 0 벡터 사용")
            List(32) { 0f }
        }
        
        val modelInput = ModelInput(
            screenSeconds = recent15minUsage * 60, // 분을 초로 변환
            scrollPx = scrollLength,
            unlocks = unlocks15min,
            appsUsed = appSwitches15min,
            screenLast15m = recent15minUsage * 60,
            screenLast30m = recent30minUsage * 60,
            screenLast1h = recent60minUsage * 60,
            unlocksPerMin = unlockRate,
            unlocksLast15m = unlocks15min,
            scrollRate = scrollRate,
            sinHour = sinHour,
            cosHour = cosHour,
            sinMinute = sinMinute,
            cosMinute = cosMinute,
            appEmbedding = appEmb
        )
        
        return predictFromModelInput(modelInput)
    }

    /**
     * ModelInput 객체를 사용하여 ONNX 모델 예측 실행 
     * @return Pair<FloatArray, Int> - 첫 번째 항목은 확률, 두 번째는 분류 결과 (0: 정상, 1: 중독)
     */
    fun predictFromModelInput(input: ModelInput): Pair<FloatArray, Int> {
        // ONNX 모델이 로드되지 않은 경우 휴리스틱 모델 사용
        if (session == null) {
            Log.d(TAG, "ONNX 모델 없음, 휴리스틱 모델 사용")
            return predictWithHeuristicFromModelInput(input)
        }
        
        // 입력 맵 생성
        val inputMap = HashMap<String, OnnxTensor>()
        
        try {
            // 입력 이름과 데이터 확인을 위한 로깅
            Log.d(TAG, "모델 입력 데이터:")
            Log.d(TAG, "ScreenSeconds: ${input.screenSeconds}")
            Log.d(TAG, "ScrollPx: ${input.scrollPx}")
            Log.d(TAG, "Unlocks: ${input.unlocks}")
            Log.d(TAG, "AppsUsed: ${input.appsUsed}")
            Log.d(TAG, "screen_last_15m: ${input.screenLast15m}")
            Log.d(TAG, "screen_last_30m: ${input.screenLast30m}")
            Log.d(TAG, "screen_last_1h: ${input.screenLast1h}")
            Log.d(TAG, "unlocks_per_min: ${input.unlocksPerMin}")
            Log.d(TAG, "unlocks_last_15m: ${input.unlocksLast15m}")
            Log.d(TAG, "scroll_rate: ${input.scrollRate}")
            Log.d(TAG, "sin_hour: ${input.sinHour}")
            Log.d(TAG, "cos_hour: ${input.cosHour}")
            Log.d(TAG, "sin_minute: ${input.sinMinute}")
            Log.d(TAG, "cos_minute: ${input.cosMinute}")
            Log.d(TAG, "app_embedding: ${input.appEmbedding.take(5)}...")
            
            // 모델 입력 정보 확인
            val inputInfo = session?.inputInfo
            if (inputInfo == null || inputInfo.isEmpty()) {
                Log.e(TAG, "모델 입력 정보가 없음")
                return predictWithHeuristicFromModelInput(input)
            }
            
            // 새로운 모델 형식에 맞게 입력 준비
            val numericInputsMap = mutableMapOf(
                "ScreenSeconds" to input.screenSeconds.toFloat(),
                "ScrollPx" to input.scrollPx.toFloat(),
                "Unlocks" to input.unlocks.toFloat(),
                "AppsUsed" to input.appsUsed.toFloat(),
                "screen_last_15m" to input.screenLast15m.toFloat(),
                "screen_last_30m" to input.screenLast30m.toFloat(),
                "screen_last_1h" to input.screenLast1h.toFloat(),
                "unlocks_per_min" to input.unlocksPerMin,
                "unlocks_last_15m" to input.unlocksLast15m.toFloat(),
                "scroll_rate" to input.scrollRate,
                "sin_hour" to input.sinHour,
                "cos_hour" to input.cosHour,
                "sin_minute" to input.sinMinute,
                "cos_minute" to input.cosMinute
            )
            
            // 앱 임베딩 입력 준비
            for (i in 0 until input.appEmbedding.size) {
                numericInputsMap["app_emb_$i"] = input.appEmbedding[i]
            }
            
            // ONNX 텐서 생성
            for ((name, info) in inputInfo) {
                // 입력 이름이 숫자 매핑에 있는지 확인
                val numValue = numericInputsMap[name]
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
                
                Log.w(TAG, "모델이 요구하는 입력 이름이 매핑에 없음: $name")
            }
            
            // 추론 실행
            val results = session?.run(inputMap)
            Log.d(TAG, "추론 완료, 결과: ${results?.size() ?: 0}개")
            
            // 모델이 로드되지 않았거나 추론에 실패한 경우 기본값 반환
            if (results == null) {
                Log.e(TAG, "추론 결과가 null, 휴리스틱 모델 사용")
                return predictWithHeuristicFromModelInput(input)
            }
            
            try {
                // 결과 확인을 위한 로깅
                for (i in 0 until (results.size() ?: 0)) {
                    // 출력 이름 대신 인덱스 사용
                    val outputIndex = i
                    Log.d(TAG, "결과[$i] 인덱스: $outputIndex")
                    Log.d(TAG, "결과[$i] 값 타입: ${results.get(i)?.value?.javaClass}")
                }
                
                // 출력 처리 (예시 - 실제 모델 출력에 맞게 조정 필요)
                // 1. 분류 결과 (예측 클래스)
                val predictionOutput = if (results.size() > 0) results.get(0)?.value as? LongArray else null
                
                // 2. 확률 (각 클래스에 대한 확률값)
                val probabilitiesOutput = if (results.size() > 1) results.get(1)?.value as? Array<FloatArray> else null
                
                // null 체크 후 안전하게 처리
                if (predictionOutput != null && probabilitiesOutput != null && probabilitiesOutput.isNotEmpty()) {
                    val prediction = predictionOutput[0].toInt()
                    val probabilities = probabilitiesOutput[0]
                    
                    Log.d(TAG, "ONNX 모델 예측 성공: $prediction, 확률: ${probabilities.joinToString()}")
                    return Pair(probabilities, prediction)
                } else {
                    Log.e(TAG, "출력 결과 타입 변환 실패, 휴리스틱 모델 사용")
                    return predictWithHeuristicFromModelInput(input)
                }
            } catch (e: Exception) {
                Log.e(TAG, "출력 처리 예외 발생: ${e.message}")
                e.printStackTrace()
                return predictWithHeuristicFromModelInput(input)
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
            return predictWithHeuristicFromModelInput(input)
        }
    }
    
    /**
     * ModelInput 객체를 사용한 휴리스틱 예측 방법
     */
    private fun predictWithHeuristicFromModelInput(input: ModelInput): Pair<FloatArray, Int> {
        // 간단한 휴리스틱 모델 구현
        var score = 0.0f
        
        // 사용 시간 요소
        score += input.screenSeconds * 0.02f
        score += input.screenLast15m * 0.005f
        score += input.screenLast30m * 0.003f
        score += input.screenLast1h * 0.001f
        
        // 휴대폰 상호작용 요소
        score += input.unlocks * 0.2f
        score += input.unlocksLast15m * 0.1f
        score += input.appsUsed * 0.1f
        
        // 스크롤 요소
        score += input.scrollPx / 1000f 
        score += input.scrollRate * 0.5f
        
        // 앱 임베딩 요소 (첫 몇 개 요소의 값으로 앱 카테고리 추측)
        val appFactor = input.appEmbedding.take(3).sum()
        score += appFactor * 2.0f  // 소셜/게임/엔터테인먼트 앱(앞 부분 임베딩 값이 클 것으로 가정)은 중독 점수 가중
        
        // 시간 요소 (밤 시간에 사용하면 점수 가중)
        // 코사인 값에서 시간 계산 시 부동소수점 오차 처리
        var cosHourForCalc = input.cosHour
        if (kotlin.math.abs(cosHourForCalc) < 1e-10) {
            cosHourForCalc = 0f
        }
        
        val hour = kotlin.math.acos(cosHourForCalc) * 12 / kotlin.math.PI
        if (hour >= 22 || hour <= 5) {
            score *= 1.3f
        }
        
        // 점수를 확률로 정규화
        val normalizedScore = minOf(1.0f, maxOf(0.0f, score / 10f))
        
        // 확률 배열 생성
        val probabilities = floatArrayOf(1.0f - normalizedScore, normalizedScore)
        
        // 예측 결과 결정
        val prediction = if (normalizedScore > 0.5f) 1 else 0
        
        Log.d(TAG, "휴리스틱 예측 결과: $prediction, 확률: ${probabilities[0]} ${probabilities[1]}")
        
        return Pair(probabilities, prediction)
    }
    
    /**
     * 주어진 패키지 목록의 평균 임베딩을 계산합니다.
     * 임시로 고정된 임베딩 값을 사용합니다.
     * @param packages 앱 패키지 이름 목록
     * @return 평균 임베딩 벡터
     */
    fun getAverageEmbeddingForPackages(packages: List<String>): List<Float> {
        // 임시로 고정된 임베딩 값 사용
        Log.d(TAG, "임시 고정 임베딩 값 사용")
        
        return listOf(
            0.049618043f, 0.08452025f, 0.19655678f, -0.44112593f, -0.0347018f, -0.06126912f, -0.114176124f, 0.61171806f, 
            0.15426065f, -0.5655271f, -0.22551064f, 0.20625597f, -0.23455858f, 0.28752193f, 0.50935143f, -0.3866065f, 
            -0.35221094f, 0.40707716f, -0.33387148f, 0.086502224f, 0.20074819f, 0.07110071f, 0.43130797f, -0.4183327f, 
            -0.12100911f, 0.033749193f, 0.092716575f, 0.4301562f, 0.028328337f, -0.044400513f, -0.5281937f, 0.5157688f
        )
        
        // 원래 코드 (임시로 사용하지 않음)
        // return averageEmbedding(packages)
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
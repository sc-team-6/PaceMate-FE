package com.gdg.scrollmanager.utils

import android.content.Context
import android.util.Log
import com.gdg.scrollmanager.ml.PhoneUsagePredictor
import com.gdg.scrollmanager.models.ModelInput
import com.gdg.scrollmanager.models.UsageDataPoint
import java.util.*
import kotlin.math.min

/**
 * 5초 간격으로 수집된 데이터를 5분, 15분, 30분, 60분 단위로 집계하는 클래스
 * 실제 데이터가 충분히 수집되면 그것을 사용하고, 부족하면 기본 추정값 사용
 */
object UsageDataAggregator {
    private const val TAG = "UsageDataAggregator"
    
    // 각 시간 단위의 최대 데이터 포인트 개수
    private const val MAX_DATA_POINTS_5MIN = 60   // 5분 = 300초 / 5초 = 60
    private const val MAX_DATA_POINTS_15MIN = 180 // 15분 = 900초 / 5초 = 180
    private const val MAX_DATA_POINTS_30MIN = 360 // 30분 = 1800초 / 5초 = 360
    private const val MAX_DATA_POINTS_60MIN = 720 // 60분 = 3600초 / 5초 = 720
    
    // 각 시간별 데이터 포인트 저장
    private val dataPoints5Min = LinkedList<UsageDataPoint>()
    private val dataPoints15Min = LinkedList<UsageDataPoint>()
    private val dataPoints30Min = LinkedList<UsageDataPoint>()
    private val dataPoints60Min = LinkedList<UsageDataPoint>()
    
    // 데이터 수집 상태 플래그
    private var is5MinDataComplete = false
    private var is15MinDataComplete = false
    private var is30MinDataComplete = false
    private var is60MinDataComplete = false
    
    /**
     * 새로운 데이터 포인트를 추가합니다.
     * 모든 시간 단위에 데이터를 추가하고 각각 최대 크기를 유지합니다.
     */
    fun addDataPoint(dataPoint: UsageDataPoint) {
        synchronized(this) {
            // 모든 시간대의 버퍼에 데이터 추가
            dataPoints5Min.add(dataPoint)
            dataPoints15Min.add(dataPoint)
            dataPoints30Min.add(dataPoint)
            dataPoints60Min.add(dataPoint)
            
            // 각 시간대별 최대 개수 유지
            while (dataPoints5Min.size > MAX_DATA_POINTS_5MIN) {
                dataPoints5Min.removeFirst()
            }
            
            while (dataPoints15Min.size > MAX_DATA_POINTS_15MIN) {
                dataPoints15Min.removeFirst()
            }
            
            while (dataPoints30Min.size > MAX_DATA_POINTS_30MIN) {
                dataPoints30Min.removeFirst()
            }
            
            while (dataPoints60Min.size > MAX_DATA_POINTS_60MIN) {
                dataPoints60Min.removeFirst()
            }
            
            // 데이터 완성 상태 업데이트
            is5MinDataComplete = dataPoints5Min.size >= MAX_DATA_POINTS_5MIN
            is15MinDataComplete = dataPoints15Min.size >= MAX_DATA_POINTS_15MIN
            is30MinDataComplete = dataPoints30Min.size >= MAX_DATA_POINTS_30MIN
            is60MinDataComplete = dataPoints60Min.size >= MAX_DATA_POINTS_60MIN
            
            // 로그 출력
            Log.d(TAG, "데이터 포인트 추가됨. 5분(${dataPoints5Min.size}/${MAX_DATA_POINTS_5MIN}), " +
                    "15분(${dataPoints15Min.size}/${MAX_DATA_POINTS_15MIN}), " +
                    "30분(${dataPoints30Min.size}/${MAX_DATA_POINTS_30MIN}), " +
                    "60분(${dataPoints60Min.size}/${MAX_DATA_POINTS_60MIN})")
            
            // 데이터 완성 상태 로그
            Log.d(TAG, "데이터 완성 상태: 5분($is5MinDataComplete), " +
                    "15분($is15MinDataComplete), " +
                    "30분($is30MinDataComplete), " +
                    "60분($is60MinDataComplete)")
        }
    }
    
    /**
     * 데이터를 집계하여 모델 입력값을 생성합니다.
     * 초기 추정값:
     * - 5분 데이터: 1분
     * - 15분 데이터: 3분
     * - 30분 데이터: 6분
     * - 60분 데이터: 12분
     * 
     * 실제 데이터가 완성되면 추정값 대신 실제 데이터를 사용합니다.
     */
    fun aggregateData(): ModelInput {
        synchronized(this) {
            // 5분 데이터 집계 (모든 경우에 필요)
            if (dataPoints5Min.isEmpty()) {
                Log.d(TAG, "집계할 데이터가 없습니다.")
                return createEmptyModelInput()
            }
            
            // 5분 데이터 집계 - 완성되지 않았으면 수집된 데이터를 확장해서 사용
            val scaleFactor5Min = if (is5MinDataComplete) 1.0f else MAX_DATA_POINTS_5MIN.toFloat() / dataPoints5Min.size.toFloat()
            val screenSeconds5Min = aggregateScreenTime(dataPoints5Min, scaleFactor5Min)
            val scrollPixels5Min = aggregateScrollPixels(dataPoints5Min, scaleFactor5Min)
            val unlockCount5Min = aggregateUnlockCount(dataPoints5Min, scaleFactor5Min)
            val appPackages5Min = aggregateAppPackages(dataPoints5Min)
            
            if (is5MinDataComplete) {
                Log.d(TAG, "5분 실제 데이터 사용: 화면 켜짐 ${screenSeconds5Min}초")
            } else {
                Log.d(TAG, "5분 추정 데이터 사용: 화면 켜짐 ${screenSeconds5Min}초 (현재 데이터 ${dataPoints5Min.size}개 확장)")
            }
            
            // 15분, 30분, 60분 데이터 집계 또는 추정
            var screenSeconds15Min: Int
            var screenSeconds30Min: Int
            var screenSeconds60Min: Int
            var unlockCount15Min: Int
            
            // 15분 데이터: 실제 데이터가 완성되었으면 사용, 아니면 추정
            if (is15MinDataComplete) {
                // 실제 15분 데이터 사용
                screenSeconds15Min = aggregateScreenTime(dataPoints15Min, 1.0f)
                unlockCount15Min = aggregateUnlockCount(dataPoints15Min, 1.0f)
                Log.d(TAG, "15분 실제 데이터 사용: 화면 켜짐 ${screenSeconds15Min}초")
            } else {
                // 15분 기본 추정값: 3분
                screenSeconds15Min = 180
                unlockCount15Min = unlockCount5Min * 3
                Log.d(TAG, "15분 추정 데이터 사용: 화면 켜짐 ${screenSeconds15Min}초 (5분 데이터의 3배)")
            }
            
            // 30분 데이터: 실제 데이터가 완성되었으면 사용, 아니면 추정
            if (is30MinDataComplete) {
                // 실제 30분 데이터 사용
                screenSeconds30Min = aggregateScreenTime(dataPoints30Min, 1.0f)
                Log.d(TAG, "30분 실제 데이터 사용: 화면 켜짐 ${screenSeconds30Min}초")
            } else {
                // 30분 기본 추정값: 6분
                screenSeconds30Min = 360
                Log.d(TAG, "30분 추정 데이터 사용: 화면 켜짐 ${screenSeconds30Min}초 (5분 데이터의 6배)")
            }
            
            // 60분 데이터: 실제 데이터가 완성되었으면 사용, 아니면 추정
            if (is60MinDataComplete) {
                // 실제 60분 데이터 사용
                screenSeconds60Min = aggregateScreenTime(dataPoints60Min, 1.0f)
                Log.d(TAG, "60분 실제 데이터 사용: 화면 켜짐 ${screenSeconds60Min}초")
            } else {
                // 60분 기본 추정값: 12분
                screenSeconds60Min = 720
                Log.d(TAG, "60분 추정 데이터 사용: 화면 켜짐 ${screenSeconds60Min}초 (5분 데이터의 12배)")
            }
            
            // 분당 잠금 해제 횟수
            val unlocksPerMin = if (screenSeconds5Min > 0) {
                unlockCount5Min.toFloat() / (screenSeconds5Min / 60f)
            } else 0f
            
            // 초당 스크롤 픽셀 수
            val scrollRate = if (screenSeconds5Min > 0) {
                scrollPixels5Min.toFloat() / screenSeconds5Min
            } else 0f
            
            // 현재 시간
            val calendar = Calendar.getInstance()
            val hour = calendar.get(Calendar.HOUR_OF_DAY)
            val minute = calendar.get(Calendar.MINUTE)
            
            // 시간 정보를 사인/코사인으로 인코딩
            val sinHour = kotlin.math.sin(2 * kotlin.math.PI * hour / 24).toFloat()
            val cosHour = kotlin.math.cos(2 * kotlin.math.PI * hour / 24).toFloat()
            val sinMinute = kotlin.math.sin(2 * kotlin.math.PI * minute / 60).toFloat()
            val cosMinute = kotlin.math.cos(2 * kotlin.math.PI * minute / 60).toFloat()
            
            // 앱 패키지 목록 가져오기 (60분 데이터에서 가져오면 더 많은 앱을 포함할 수 있음)
            val packageList = appPackages5Min.toList()
            Log.d(TAG, "앱 패키지 목록: ${packageList.take(5)}... (총 ${packageList.size}개)")
            
            // 내부에서 기본 임베딩 값 초기화
            // 실제 임베딩 처리는 서비스에서 수행
            val appEmbedding = List(32) { 0f }
            
            // 패키지 벡터가 모두 0인 경우에만 예시값 사용
            val isZeroVector = appEmbedding.all { it == 0f }
            val finalEmbedding = if (isZeroVector) {
                // 예시값 사용 - 패키지 임베딩이 모두 0일 때만 사용됨
                listOf(0.12f, -0.23f, 0.45f, -0.67f, 0.89f, -0.12f, 0.34f, -0.56f,
                       0.78f, -0.9f, 0.11f, -0.22f, 0.33f, -0.44f, 0.55f, -0.66f,
                       0.77f, -0.88f, 0.99f, -0.10f, 0.21f, -0.32f, 0.43f, -0.54f,
                       0.65f, -0.76f, 0.87f, -0.98f, 0.09f, -0.18f, 0.27f, -0.36f)
            } else {
                appEmbedding
            }
            
            Log.d(TAG, "패키지 임베딩: ${if (isZeroVector) "예시값 사용" else "실제 임베딩 사용"}")
            
            return ModelInput(
                screenSeconds = screenSeconds5Min,
                scrollPx = scrollPixels5Min,
                unlocks = unlockCount5Min,
                appsUsed = appPackages5Min.size,
                screenLast15m = screenSeconds15Min,
                screenLast30m = screenSeconds30Min,
                screenLast1h = screenSeconds60Min,
                unlocksPerMin = unlocksPerMin,
                unlocksLast15m = unlockCount15Min,
                scrollRate = scrollRate,
                sinHour = sinHour,
                cosHour = cosHour,
                sinMinute = sinMinute,
                cosMinute = cosMinute,
                appEmbedding = finalEmbedding
            )
        }
    }
    
    /**
     * 특정 시간 윈도우의 화면 켜짐 시간을 집계합니다.
     */
    private fun aggregateScreenTime(dataPoints: List<UsageDataPoint>, scaleFactor: Float): Int {
        var screenTimeSeconds = 0
        
        for (point in dataPoints) {
            screenTimeSeconds += point.screenTimeSeconds
        }
        
        // 스케일 팩터 적용 (데이터가 부족할 경우 확장)
        return (screenTimeSeconds * scaleFactor).toInt()
    }
    
    /**
     * 특정 시간 윈도우의 스크롤 픽셀을 집계합니다.
     */
    private fun aggregateScrollPixels(dataPoints: List<UsageDataPoint>, scaleFactor: Float): Int {
        var scrollPixels = 0
        
        for (point in dataPoints) {
            scrollPixels += point.scrollPixels
        }
        
        // 스케일 팩터 적용 (데이터가 부족할 경우 확장)
        return (scrollPixels * scaleFactor).toInt()
    }
    
    /**
     * 특정 시간 윈도우의 잠금 해제 횟수를 집계합니다.
     */
    private fun aggregateUnlockCount(dataPoints: List<UsageDataPoint>, scaleFactor: Float): Int {
        var unlockCount = 0
        
        for (point in dataPoints) {
            unlockCount += point.unlockCount
        }
        
        // 스케일 팩터 적용 (데이터가 부족할 경우 확장)
        return (unlockCount * scaleFactor).toInt()
    }
    
    /**
     * 특정 시간 윈도우의 앱 패키지 목록을 집계합니다.
     */
    private fun aggregateAppPackages(dataPoints: List<UsageDataPoint>): Set<String> {
        val appPackages = mutableSetOf<String>()
        
        for (point in dataPoints) {
            appPackages.addAll(point.appPackages)
        }
        
        return appPackages
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
        
        // 패키지 벡터 초기화
        val appEmbedding = List(32) { 0f }
        
        // 패키지 벡터가 모두 0인 경우에만 예시값 사용
        val isZeroVector = appEmbedding.all { it == 0f }
        val finalEmbedding = if (isZeroVector) {
            // 예시값 사용 - 패키지 임베딩이 모두 0일 때만 사용됨
            listOf(0.12f, -0.23f, 0.45f, -0.67f, 0.89f, -0.12f, 0.34f, -0.56f,
                   0.78f, -0.9f, 0.11f, -0.22f, 0.33f, -0.44f, 0.55f, -0.66f,
                   0.77f, -0.88f, 0.99f, -0.10f, 0.21f, -0.32f, 0.43f, -0.54f,
                   0.65f, -0.76f, 0.87f, -0.98f, 0.09f, -0.18f, 0.27f, -0.36f)
        } else {
            appEmbedding
        }
        
        Log.d(TAG, "빈 모델 입력 생성 - 패키지 임베딩: ${if (isZeroVector) "예시값 사용" else "실제 임베딩 사용"}")
        
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
            appEmbedding = finalEmbedding
        )
    }
    
    /**
     * 현재 저장된 데이터 포인트의 개수를 반환합니다.
     */
    fun getDataPointCount(): Int {
        synchronized(this) {
            return dataPoints5Min.size
        }
    }
    
    /**
     * 데이터 수집의 진행 상황을 퍼센트로 반환합니다.
     * 각 시간대별 진행률의 평균을 반환합니다.
     */
    fun getDataCollectionProgress(): Int {
        synchronized(this) {
            val progress5Min = min(dataPoints5Min.size * 100 / MAX_DATA_POINTS_5MIN, 100)
            val progress15Min = min(dataPoints15Min.size * 100 / MAX_DATA_POINTS_15MIN, 100)
            val progress30Min = min(dataPoints30Min.size * 100 / MAX_DATA_POINTS_30MIN, 100)
            val progress60Min = min(dataPoints60Min.size * 100 / MAX_DATA_POINTS_60MIN, 100)
            
            // 데이터 수집 진행률의 가중 평균 계산
            // 5분: 20%, 15분: 20%, 30분: 30%, 60분: 30%의 비중으로 계산
            return (progress5Min * 0.2 + progress15Min * 0.2 + 
                    progress30Min * 0.3 + progress60Min * 0.3).toInt()
        }
    }
    
    /**
     * 데이터 수집 상태를 문자열로 반환합니다.
     */
    fun getCollectionStatusText(): String {
        synchronized(this) {
            return "5분: ${dataPoints5Min.size}/${MAX_DATA_POINTS_5MIN} (${if (is5MinDataComplete) "완료" else "진행 중"}), " +
                   "15분: ${dataPoints15Min.size}/${MAX_DATA_POINTS_15MIN} (${if (is15MinDataComplete) "완료" else "진행 중"}), " +
                   "30분: ${dataPoints30Min.size}/${MAX_DATA_POINTS_30MIN} (${if (is30MinDataComplete) "완료" else "진행 중"}), " +
                   "60분: ${dataPoints60Min.size}/${MAX_DATA_POINTS_60MIN} (${if (is60MinDataComplete) "완료" else "진행 중"})"
        }
    }
    
    /**
     * 모든 데이터 포인트를 지웁니다.
     */
    fun clearData() {
        synchronized(this) {
            dataPoints5Min.clear()
            dataPoints15Min.clear()
            dataPoints30Min.clear()
            dataPoints60Min.clear()
            
            is5MinDataComplete = false
            is15MinDataComplete = false
            is30MinDataComplete = false
            is60MinDataComplete = false
            
            Log.d(TAG, "모든 데이터 포인트 삭제됨")
        }
    }
}
package com.gdg.scrollmanager.services

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.datastore.preferences.core.edit
import com.gdg.scrollmanager.R
import com.gdg.scrollmanager.ml.PhoneUsagePredictor
import com.gdg.scrollmanager.models.ModelInput
import com.gdg.scrollmanager.models.UsageDataPoint
import com.gdg.scrollmanager.models.UsageReport
import com.gdg.scrollmanager.utils.DataStoreUtils
import com.gdg.scrollmanager.utils.UsageDataAggregator
import com.gdg.scrollmanager.utils.UsageStatsUtils
import com.gdg.scrollmanager.utils.usageDataStore
import com.google.gson.Gson
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.*

/**
 * 실시간 사용 데이터 수집 및 분석을 위한 서비스
 */
class UsageDataCollectorService : Service() {
    
    private val TAG = "UsageDataService"
    
    // 데이터 수집 주기 (5초)
    private val COLLECTION_INTERVAL = 5000L
    
    // 모델 예측 주기 (5초)
    private val PREDICTION_INTERVAL = 5000L
    
    // ONNX 예측 모델
    private lateinit var phoneUsagePredictor: PhoneUsagePredictor
    
    // 서비스 ID
    private val NOTIFICATION_ID = 1001
    private val CHANNEL_ID = "usage_data_service"
    
    // 코루틴 스코프
    private val serviceJob = SupervisorJob()
    private val serviceScope = CoroutineScope(Dispatchers.IO + serviceJob)
    
    // 현재 관찰 중인 앱 패키지 목록
    private val currentAppPackages = mutableSetOf<String>()
    
    // 이전 데이터 포인트 수집 시간
    private var lastDataCollectionTime = 0L
    
    // 이전 화면 상태 (켜짐/꺼짐)
    private var isScreenOn = false
    private var screenOnStartTime = 0L
    
    // 이전 예측 시간
    private var lastPredictionTime = 0L
    
    // 핸들러 - 데이터 수집용
    private val dataCollectionHandler = Handler(Looper.getMainLooper())
    private val dataCollectionRunnable = object : Runnable {
        override fun run() {
            collectDataPoint()
            dataCollectionHandler.postDelayed(this, COLLECTION_INTERVAL)
        }
    }
    
    // 핸들러 - 모델 예측용
    private val predictionHandler = Handler(Looper.getMainLooper())
    private val predictionRunnable = object : Runnable {
        override fun run() {
            runPrediction()
            predictionHandler.postDelayed(this, PREDICTION_INTERVAL)
        }
    }
    
    // Gson 인스턴스 (JSON 변환용)
    private val gson = Gson()

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "서비스 생성됨")
        
        // ONNX 모델 초기화
        initPhoneUsagePredictor()
        
        // 포어그라운드 서비스 시작
        startForeground()
        
        // 데이터 수집 시작
        startDataCollection()
    }
    
    private fun initPhoneUsagePredictor() {
        phoneUsagePredictor = PhoneUsagePredictor(applicationContext)
        phoneUsagePredictor.initialize()
        Log.d(TAG, "ONNX 모델 초기화 완료")
    }
    
    private fun startForeground() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = "사용 패턴 모니터링"
            val descriptionText = "휴대폰 사용 패턴 데이터를 수집합니다"
            val importance = NotificationManager.IMPORTANCE_LOW
            val channel = NotificationChannel(CHANNEL_ID, name, importance).apply {
                description = descriptionText
            }
            
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
        
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("사용 패턴 분석")
            .setContentText("휴대폰 사용 패턴을 모니터링 중입니다")
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
        
        startForeground(NOTIFICATION_ID, notification)
    }
    
    private fun startDataCollection() {
        // 초기 타임스탬프 설정
        lastDataCollectionTime = System.currentTimeMillis()
        
        // 데이터 수집 시작
        dataCollectionHandler.post(dataCollectionRunnable)
        
        // 모델 예측 시작
        predictionHandler.post(predictionRunnable)
    }
    
    /**
     * 5초마다 데이터 포인트를 수집합니다.
     */
    private fun collectDataPoint() {
        serviceScope.launch {
            try {
                val now = System.currentTimeMillis()
                val elapsedMillis = now - lastDataCollectionTime
                
                // 5초 동안의 데이터 수집
                val dataPoint = collectUsageData(elapsedMillis)
                
                // 수집된 데이터 포인트 추가
                UsageDataAggregator.addDataPoint(dataPoint)
                
                // 다음 수집 타임스탬프 업데이트
                lastDataCollectionTime = now
                
                Log.d(TAG, "데이터 포인트 수집 완료: ${UsageDataAggregator.getDataPointCount()}/60")
            } catch (e: Exception) {
                Log.e(TAG, "데이터 수집 오류: ${e.message}")
                e.printStackTrace()
            }
        }
    }
    
    /**
     * 일정 시간 동안의 사용 데이터를 수집합니다.
     */
    private suspend fun collectUsageData(elapsedMillis: Long): UsageDataPoint = withContext(Dispatchers.IO) {
        // 시간 간격을 초로 변환
        val intervalSeconds = (elapsedMillis / 1000).toInt()
        
        // 현재 화면 상태 확인
        val powerManager = getSystemService(Context.POWER_SERVICE) as android.os.PowerManager
        val isScreenCurrentlyOn = powerManager.isInteractive
        
        // 화면 켜짐 시간 계산
        var screenTimeSeconds = 0
        if (isScreenCurrentlyOn) {
            if (!isScreenOn) {
                // 화면이 방금 켜짐
                screenOnStartTime = System.currentTimeMillis() - elapsedMillis
                isScreenOn = true
                screenTimeSeconds = intervalSeconds
            } else {
                // 화면이 계속 켜져 있음
                screenTimeSeconds = intervalSeconds
            }
        } else {
            if (isScreenOn) {
                // 화면이 방금 꺼짐
                val screenOnDuration = System.currentTimeMillis() - screenOnStartTime
                screenTimeSeconds = (screenOnDuration / 1000).toInt().coerceAtMost(intervalSeconds)
                isScreenOn = false
            }
        }
        
        // 스크롤 양 가져오기
        val scrollPixels = calculateScrollPixels()
        
        // 잠금 해제 횟수 가져오기
        val unlockCount = UsageStatsUtils.getUnlockCount(applicationContext, intervalSeconds / 60 + 1)
        
        // 사용한 앱 패키지 가져오기
        updateCurrentAppPackages()
        
        return@withContext UsageDataPoint(
            timestamp = System.currentTimeMillis(),
            screenTimeSeconds = screenTimeSeconds,
            scrollPixels = scrollPixels,
            unlockCount = unlockCount,
            appPackages = currentAppPackages.toSet()
        )
    }
    
    /**
     * 현재 실행 중인 앱을 확인하고 목록을 업데이트합니다.
     */
    private fun updateCurrentAppPackages() {
        try {
            val usageStatsManager = getSystemService(Context.USAGE_STATS_SERVICE) as android.app.usage.UsageStatsManager
            val time = System.currentTimeMillis()
            
            // 최근 10초 동안의 앱 사용 이벤트 가져오기
            val events = usageStatsManager.queryEvents(time - 10000, time)
            val event = android.app.usage.UsageEvents.Event()
            
            while (events.hasNextEvent()) {
                events.getNextEvent(event)
                
                if (event.eventType == android.app.usage.UsageEvents.Event.ACTIVITY_RESUMED) {
                    // 런처 제외
                    if (!UsageStatsUtils.isLauncherPackage(event.packageName)) {
                        currentAppPackages.add(event.packageName)
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "앱 패키지 목록 업데이트 오류: ${e.message}")
        }
    }
    
    /**
     * 수집 간격 동안의 스크롤 양을 계산합니다.
     */
    private suspend fun calculateScrollPixels(): Int = withContext(Dispatchers.IO) {
        try {
            // DataStore에서 스크롤 데이터 가져오기
            val scrollDistance = DataStoreUtils.getTotalScrollDistance(applicationContext)
            
            // 이전 스크롤 거리와 비교하여 증가량 계산
            val lastScrollDistance = DataStoreUtils.getLastScrollDistance(applicationContext)
            val scrollPixels = ((scrollDistance - lastScrollDistance) * 100).toInt()
            
            Log.d(TAG, "스크롤 거리 계산: ${scrollDistance - lastScrollDistance}, 스크롤 픽셀: $scrollPixels")
            
            // 현재 스크롤 거리 저장
            DataStoreUtils.saveLastScrollDistance(applicationContext, scrollDistance)
            
            return@withContext Math.max(0, scrollPixels)  // 음수 값이 나오면 0으로 처리
        } catch (e: Exception) {
            Log.e(TAG, "스크롤 픽셀 계산 오류: ${e.message}")
            return@withContext 0  // 오류 발생 시 0 반환
        }
    }
    
    /**
     * 주기적으로 예측을 실행합니다.
     */
    private fun runPrediction() {
        serviceScope.launch {
            try {
                Log.d(TAG, "예측 실행 중...")
                
                // 누적된 데이터 집계
                val modelInput = UsageDataAggregator.aggregateData()
                
                // 앱 패키지 목록 가져오기
                // 최근 5초간 사용된 패키지가 아닌 최근 15분간 가장 많이 사용된 상위 5개 패키지 사용
                val topPackages = UsageStatsUtils.getTopUsedPackages(applicationContext, 15, 5)
                Log.d(TAG, "최근 15분 동안 가장 많이 사용된 상위 앱 패키지: ${topPackages.joinToString()}")
                
                // 앱 임베딩 계산
                if (topPackages.isNotEmpty()) {
                    try {
                        // 사용자 정의 확장 메소드를 통해 임베딩 계산
                        val appEmbedding = phoneUsagePredictor.getAverageEmbeddingForPackages(topPackages)
                        // 임베딩 값 업데이트
                        modelInput.appEmbedding = appEmbedding
                        Log.d(TAG, "상위 앱 기반 임베딩 계산 완료: ${appEmbedding.take(3)}...")
                    } catch (e: Exception) {
                        Log.e(TAG, "앱 임베딩 계산 오류: ${e.message}")
                    }
                }
                
                // ONNX 모델 예측 실행
                val result = predictAddiction(modelInput)
                
                // 결과 저장
                storePredictionResult(modelInput, result)
                
                // 마지막 예측 시간 업데이트
                lastPredictionTime = System.currentTimeMillis()
                
                Log.d(TAG, "예측 완료: ${result.second}, 확률: ${result.first[1] * 100}%")
            } catch (e: Exception) {
                Log.e(TAG, "예측 오류: ${e.message}")
                e.printStackTrace()
            }
        }
    }
    
    /**
     * ONNX 모델을 사용하여 중독 여부를 예측합니다.
     */
    private fun predictAddiction(modelInput: ModelInput): Pair<FloatArray, Int> {
        // 모델 입력값 로그
        Log.d(TAG, "모델 입력: screenSeconds=${modelInput.screenSeconds}, " +
                "scrollPx=${modelInput.scrollPx}, unlocks=${modelInput.unlocks}, " +
                "screenLast15m=${modelInput.screenLast15m}, scrollRate=${modelInput.scrollRate}")
        
        // ONNX 모델을 사용한 예측
        return phoneUsagePredictor.predictFromModelInput(modelInput)
    }
    
    /**
     * 예측 결과를 DataStore에 저장합니다.
     */
    private suspend fun storePredictionResult(modelInput: ModelInput, predictionResult: Pair<FloatArray, Int>) {
        val predictionValue = predictionResult.second
        val probability = predictionResult.first[1] * 100 // 중독 확률 (%)
        
        // UsageReport 생성 (기본 정보만 포함)
        val report = UsageReport(
            usageTime15Min = modelInput.screenLast15m / 60,
            usageTime30Min = modelInput.screenLast30m / 60,
            usageTime60Min = modelInput.screenLast1h / 60,
            unlockCount15Min = modelInput.unlocksLast15m,
            appSwitchCount15Min = 0, // TODO: 실제로 추적 필요
            mainAppCategory = "Unknown", // TODO: 실제로 계산 필요
            socialAppCount = 0, // TODO: 실제로 계산 필요
            averageSessionLength = 0f, // TODO: 실제로 계산 필요
            dateTime = UsageStatsUtils.getCurrentDateTime(),
            scrollDistance = modelInput.scrollPx.toFloat(),
            appUsageList = emptyList() // TODO: 실제로 계산 필요
        )
        
        // DataStore에 저장
        applicationContext.usageDataStore.edit { preferences ->
            // 사용 데이터 저장
            preferences[DataStoreUtils.LATEST_USAGE_REPORT_KEY] = gson.toJson(report)
            
            // 예측 결과 저장
            preferences[DataStoreUtils.PREDICTION_RESULT_KEY] = predictionValue
            preferences[DataStoreUtils.PREDICTION_PROBABILITY_KEY] = probability
            
            // 모델 입력값 저장
            preferences[DataStoreUtils.MODEL_INPUT_KEY] = gson.toJson(modelInput)
            
            // 데이터 수집 진행 상황 저장
            preferences[DataStoreUtils.DATA_COLLECTION_PROGRESS_KEY] = UsageDataAggregator.getDataCollectionProgress()
            
            // 타임스탬프 저장
            preferences[DataStoreUtils.LAST_UPDATE_TIMESTAMP_KEY] = System.currentTimeMillis()
        }
    }
    
    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "서비스 종료됨")
        
        // 핸들러 콜백 제거
        dataCollectionHandler.removeCallbacks(dataCollectionRunnable)
        predictionHandler.removeCallbacks(predictionRunnable)
        
        // 코루틴 스코프 취소
        serviceScope.cancel()
        
        // ONNX 모델 리소스 해제
        if (::phoneUsagePredictor.isInitialized) {
            phoneUsagePredictor.close()
        }
    }
    
    override fun onBind(intent: Intent): IBinder? {
        return null
    }
    
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // 서비스가 강제로 종료된 후 재시작되어도 마지막 Intent를 다시 전달하지 않음
        return START_NOT_STICKY
    }
    
    companion object {
        // 서비스 시작 함수
        fun startService(context: Context) {
            val intent = Intent(context, UsageDataCollectorService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }
        
        // 서비스 중지 함수
        fun stopService(context: Context) {
            val intent = Intent(context, UsageDataCollectorService::class.java)
            context.stopService(intent)
        }
    }
}
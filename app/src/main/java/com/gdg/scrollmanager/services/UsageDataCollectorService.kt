package com.gdg.scrollmanager.services

import android.app.Notification
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
import com.gdg.scrollmanager.models.UsageReport
import com.gdg.scrollmanager.utils.DataStoreUtils
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
    
    // ONNX 예측 모델
    private lateinit var phoneUsagePredictor: PhoneUsagePredictor
    
    // 서비스 ID
    private val NOTIFICATION_ID = 1001
    private val CHANNEL_ID = "usage_data_service"
    
    // 코루틴 스코프
    private val serviceJob = SupervisorJob()
    private val serviceScope = CoroutineScope(Dispatchers.IO + serviceJob)
    
    // 핸들러 - 주기적인 작업 실행용
    private val handler = Handler(Looper.getMainLooper())
    private val dataCollectionRunnable = object : Runnable {
        override fun run() {
            collectDataPeriodically()
            handler.postDelayed(this, COLLECTION_INTERVAL)
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
        
        // 주기적 데이터 수집 시작
        startPeriodicDataCollection()
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
    
    private fun startPeriodicDataCollection() {
        handler.post(dataCollectionRunnable)
    }
    
    private fun collectDataPeriodically() {
        serviceScope.launch {
            try {
                collectAndStoreUsageData()
            } catch (e: Exception) {
                Log.e(TAG, "데이터 수집 중 오류 발생: ${e.message}")
                e.printStackTrace()
            }
        }
    }
    
    private suspend fun collectAndStoreUsageData() = withContext(Dispatchers.IO) {
        Log.d(TAG, "데이터 수집 중...")
        
        try {
            // 사용 통계 데이터 수집
            val report = generateUsageReport()
            
            // 예측 실행
            val predictionResult = runPrediction(report)
            
            // DataStore에 저장
            storeUsageDataToDataStore(report, predictionResult)
            
            Log.d(TAG, "데이터 수집 완료: 중독 예측 결과=${predictionResult.second}")
        } catch (e: Exception) {
            Log.e(TAG, "데이터 수집 오류: ${e.message}")
            e.printStackTrace()
        }
    }
    
    private suspend fun generateUsageReport(): UsageReport {
        val context = applicationContext
        val appUsageList = UsageStatsUtils.getAppUsageStats(context)
        
        val usageTime15Min = UsageStatsUtils.getTotalUsageTimeInMinutes(context, 15)
        val usageTime30Min = UsageStatsUtils.getTotalUsageTimeInMinutes(context, 30)
        val usageTime60Min = UsageStatsUtils.getTotalUsageTimeInMinutes(context, 60)
        
        val unlockCount = UsageStatsUtils.getUnlockCount(context, 15)
        val appSwitchCount = UsageStatsUtils.getAppSwitchCount(context, 15)
        
        val mainAppCategory = UsageStatsUtils.getMainAppCategory(context)
        val socialAppCount = UsageStatsUtils.getSocialMediaAppUsageCount(context, 60)
        
        val averageSessionLength = UsageStatsUtils.getAverageSessionDuration(context)
        val dateTime = UsageStatsUtils.getCurrentDateTime()
        
        // 스크롤 거리 가져오기
        val scrollDistance = DataStoreUtils.getTotalScrollDistance(context)
        
        return UsageReport(
            usageTime15Min = usageTime15Min,
            usageTime30Min = usageTime30Min,
            usageTime60Min = usageTime60Min,
            unlockCount15Min = unlockCount,
            appSwitchCount15Min = appSwitchCount,
            mainAppCategory = mainAppCategory,
            socialAppCount = socialAppCount,
            averageSessionLength = averageSessionLength,
            dateTime = dateTime,
            scrollDistance = scrollDistance,
            appUsageList = appUsageList
        )
    }
    
    private fun runPrediction(report: UsageReport): Pair<FloatArray, Int> {
        // 현재 시간 관련 데이터 가져오기
        val calendar = Calendar.getInstance()
        val hour = calendar.get(Calendar.HOUR_OF_DAY)
        val dayOfWeek = calendar.get(Calendar.DAY_OF_WEEK) - 1 
        
        // 스크롤 관련 데이터 계산
        val scrollLength = report.scrollDistance.toInt() 
        
        // 비율 계산 (변화율)
        val unlockRate: Float = if (report.usageTime15Min > 0) {
            report.unlockCount15Min.toFloat() / report.usageTime15Min.toFloat()
        } else 0f
        
        val switchRate: Float = if (report.usageTime15Min > 0) {
            report.appSwitchCount15Min.toFloat() / report.usageTime15Min.toFloat()
        } else 0f
        
        val scrollRate: Float = if (report.usageTime60Min > 0 && scrollLength > 0) {
            scrollLength.toFloat() / (report.usageTime60Min.toFloat() * 60f) 
        } else 0f
        
        // ONNX 모델 예측 실행
        return phoneUsagePredictor.predict(
            recent15minUsage = report.usageTime15Min,
            recent30minUsage = report.usageTime30Min,
            recent60minUsage = report.usageTime60Min,
            unlocks15min = report.unlockCount15Min,
            appSwitches15min = report.appSwitchCount15Min,
            snsAppUsage = report.socialAppCount,
            avgSessionLength = report.averageSessionLength,
            hour = hour,
            dayOfWeek = dayOfWeek,
            scrollLength = scrollLength,
            unlockRate = unlockRate,
            switchRate = switchRate,
            scrollRate = scrollRate,
            topAppCategory = report.mainAppCategory
        )
    }
    
    private suspend fun storeUsageDataToDataStore(report: UsageReport, predictionResult: Pair<FloatArray, Int>) {
        val reportJson = gson.toJson(report)
        val predictionValue = predictionResult.second
        val probability = predictionResult.first[1] * 100 // 중독 확률 (%)
        
        applicationContext.usageDataStore.edit { preferences ->
            // 사용 데이터 저장
            preferences[DataStoreUtils.LATEST_USAGE_REPORT_KEY] = reportJson
            
            // 예측 결과 저장
            preferences[DataStoreUtils.PREDICTION_RESULT_KEY] = predictionValue
            preferences[DataStoreUtils.PREDICTION_PROBABILITY_KEY] = probability
            
            // 타임스탬프 저장
            preferences[DataStoreUtils.LAST_UPDATE_TIMESTAMP_KEY] = System.currentTimeMillis()
        }
    }
    
    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "서비스 종료됨")
        
        // 핸들러 콜백 제거
        handler.removeCallbacks(dataCollectionRunnable)
        
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
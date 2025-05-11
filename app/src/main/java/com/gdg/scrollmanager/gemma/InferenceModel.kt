package com.gdg.scrollmanager.gemma

import android.content.Context
import android.util.Log
import com.google.common.util.concurrent.ListenableFuture
import com.google.mediapipe.tasks.genai.llminference.LlmInference
import com.google.mediapipe.tasks.genai.llminference.LlmInferenceSession
import com.google.mediapipe.tasks.genai.llminference.ProgressListener
import java.io.File
import java.io.FileNotFoundException
import java.io.FileOutputStream
import kotlin.math.max
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** The maximum number of tokens the model can process. */
const val MAX_TOKENS = 1024

/**
 * An offset in tokens that we use to ensure that the model always has the ability to respond when
 * we compute the remaining context length.
 */
const val DECODE_TOKEN_OFFSET = 256

class ModelLoadFailException(message: String) :
    Exception(message)

class ModelSessionCreateFailException :
    Exception("Failed to create model session, please try again")

// 진행률 업데이트를 위한 함수형 인터페이스
typealias ProgressCallback = (Float) -> Unit

class InferenceModel private constructor(
    context: Context,
    private val progressCallback: ProgressCallback
) {
    private lateinit var llmInference: LlmInference
    private lateinit var llmInferenceSession: LlmInferenceSession
    private val TAG = "InferenceModel"

    val uiState = UiState(model.thinking)
    var isInitialized = false
        private set // 외부에서 읽기는 가능하지만 쓰기는 불가능
    
    // 로컬 진행률 상태
    private val _loadingProgress = MutableStateFlow(0f)
    val loadingProgress: StateFlow<Float> = _loadingProgress.asStateFlow()
    
    // 진행률 업데이트 함수 - 둘 다 업데이트
    private fun updateProgress(progress: Float) {
        val currentProgress = _loadingProgress.value
        // 이전 진행률과 새 진행률 모두 로깅
        Log.d(TAG, "Progress update: $currentProgress -> $progress (delta: ${progress - currentProgress})")
        
        // 로컬 상태 업데이트
        _loadingProgress.value = progress
        
        // 글로벌 콜백을 통한 업데이트
        progressCallback(progress)
    }

    init {
        initializeModel(context)
    }
    
    private fun initializeModel(context: Context) {
        try {
            // 초기화 시작 시간 기록
            val startTime = System.currentTimeMillis()
            Log.d(TAG, "=== MODEL INITIALIZATION STARTED at ${java.util.Date(startTime)} ===")
            
            // 진행률 0% 시작
            updateProgress(0f)
            Log.d(TAG, "Model initialization phase 1/3: Copying model file if needed")
            
            // 단계 1: 모델 파일 복사 (0% → 50%)
            val copyStartTime = System.currentTimeMillis()
            copyModelFromAssetsIfNeeded(context)
            val copyEndTime = System.currentTimeMillis()
            val copyDurationSeconds = (copyEndTime - copyStartTime) / 1000
            Log.d(TAG, "Model file copy completed in $copyDurationSeconds seconds")
            
            // 시간 기반 점진적 진행률 업데이트를 위한 타이머 스레드 시작 (50% -> 100%)
            // 평균적인 소요 시간을 기반으로 예상 시간 설정
            val progressTimer = Thread {
                try {
                    // 엔진 생성과 세션 생성의 총 예상 시간 (초)
                    val expectedEngineDuration = 12 // 초
                    val totalUpdateCount = expectedEngineDuration * 2 // 0.5초마다 업데이트
                    
                    // 초기 진행률 (50%)과 최종 진행률 (100%) 사이의 증분
                    val progressIncrement = 0.5f / totalUpdateCount
                    var currentProgress = 0.5f
                    
                    // 0.5초마다 진행률 업데이트
                    for (i in 0 until totalUpdateCount) {
                        currentProgress += progressIncrement
                        if (currentProgress < 0.99f) { // 99%까지만 업데이트 (최종 100%는 완료 시 설정)
                            updateProgress(currentProgress)
                            Log.d(TAG, "Time-based progress update: ${(currentProgress * 100).toInt()}%")
                        }
                        Thread.sleep(500)
                    }
                } catch (e: InterruptedException) {
                    // 타이머 중단됨 (정상적인 케이스)
                    Log.d(TAG, "Progress timer interrupted, model initialization completed")
                }
            }
            
            // 타이머 시작
            progressTimer.isDaemon = true
            progressTimer.start()
            
            // try 블록 외부에서 접근할 변수들을 미리 선언
            var engineDurationSeconds: Long = 0
            var sessionDurationSeconds: Long = 0
            
            try {
                // 단계 2: 모델 엔진 생성 (시간 기반 진행률 업데이트는 타이머가 담당)
                Log.d(TAG, "Model initialization phase 2/3: Creating inference engine")
                val engineStartTime = System.currentTimeMillis()
                createEngine(context)
                val engineEndTime = System.currentTimeMillis()
                engineDurationSeconds = (engineEndTime - engineStartTime) / 1000
                Log.d(TAG, "Engine creation completed in $engineDurationSeconds seconds")
                
                // 단계 3: 세션 생성 (시간 기반 진행률 업데이트는 타이머가 담당)
                Log.d(TAG, "Model initialization phase 3/3: Creating inference session")
                val sessionStartTime = System.currentTimeMillis()
                createSession()
                val sessionEndTime = System.currentTimeMillis()
                sessionDurationSeconds = (sessionEndTime - sessionStartTime) / 1000
                Log.d(TAG, "Session creation completed in $sessionDurationSeconds seconds")
            } finally {
                // 타이머 중단
                progressTimer.interrupt()
            }
            
            // 완료 시 진행률 설정
            updateProgress(1.0f)
            
            // 전체 초기화 완료 시간 및 로그
            val endTime = System.currentTimeMillis()
            val totalDurationSeconds = (endTime - startTime) / 1000
            Log.d(TAG, "=== MODEL INITIALIZATION COMPLETED at ${java.util.Date(endTime)} ===")
            Log.d(TAG, "Total initialization time: $totalDurationSeconds seconds")
            Log.d(TAG, "Breakdown: Copy=$copyDurationSeconds s, Engine=${engineDurationSeconds}s, Session=${sessionDurationSeconds}s")
            
            // 초기화가 완전히 성공했음을 나타내는 플래그 설정
            try {
                context.getSharedPreferences("app_state", Context.MODE_PRIVATE)
                    .edit()
                    .putBoolean("init_complete", true)
                    .apply()
                Log.d(TAG, "Initialization complete flag set successfully")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to set initialization complete flag: ${e.message}")
            }
            
            isInitialized = true
        } catch (e: Exception) {
            // 로그에 오류 기록
            Log.e(TAG, "Model initialization failed: ${e.message}")
            Log.e(TAG, "Stack trace: ", e)
            
            // 초기화 실패 시 사용자에게 메시지 표시
            val errorMsg = when (e) {
                is FileNotFoundException -> "모델 파일을 찾을 수 없습니다. 앱의 assets 폴더에 gemma3-1b-it-int4.task 파일이 있는지 확인해주세요."
                is ModelLoadFailException -> e.message ?: "모델 로드 실패"
                is ModelSessionCreateFailException -> e.message ?: "모델 세션 생성 실패"
                else -> "모델 초기화 중 오류 발생: ${e.message}"
            }
            
            uiState.addMessage(errorMsg, MODEL_PREFIX)
            
            // 예외를 다시 던져서 호출자에게 알림
            if (e is ModelLoadFailException || e is ModelSessionCreateFailException) {
                throw e
            } else {
                throw ModelLoadFailException("모델 초기화 실패: ${e.message}")
            }
        }
    }

    fun close() {
        if (::llmInferenceSession.isInitialized) {
            llmInferenceSession.close()
        }
        if (::llmInference.isInitialized) {
            llmInference.close()
        }
    }

    fun resetSession() {
        if (::llmInferenceSession.isInitialized) {
            llmInferenceSession.close()
            createSession()
        } else {
            throw ModelSessionCreateFailException()
        }
    }
}
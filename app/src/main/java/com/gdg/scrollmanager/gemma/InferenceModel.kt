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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch

/** The maximum number of tokens the model can process. */
const val MAX_TOKENS = 2048

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

    val uiState = UiState(supportsThinking = model.thinking)
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
    
    // 에셋에서 모델 파일을 내부 저장소로 복사
    private fun copyModelFromAssetsIfNeeded(context: Context) {
        val modelFile = File(context.filesDir, Model.MODEL_FILENAME)
        
        // 디버그 정보: 내부 저장소 경로 출력
        Log.d(TAG, "Internal storage path: ${context.filesDir.absolutePath}")
        
        // 파일이 존재하는지 확인하고, 크기가 올바른지 검증
        var needCopy = true
        var assetFileSize: Long = 0  // 상위 범위에서 변수 선언
        var internalFileSize: Long = 0
        var fileExistsFlag = false
        
        // 먼저 파일이 실제로 존재하는지 더블 체크
        fileExistsFlag = modelFile.exists() && modelFile.isFile
        Log.d(TAG, "File exists check: $fileExistsFlag (path: ${modelFile.absolutePath})")
        
        if (fileExistsFlag) {
            // 실제 파일 정보 로깅
            Log.d(TAG, "File last modified: ${java.util.Date(modelFile.lastModified())}")
            internalFileSize = modelFile.length()
            Log.d(TAG, "File size according to File.length(): $internalFileSize bytes")

            try {
                // Assets에 있는 원본 파일의 크기 확인 - open + available 방식으로 일괄 변경
                var assetSize = 0L
                // 항상 open() + available() 사용
                context.assets.open(Model.MODEL_FILENAME).use { stream ->
                    assetSize = stream.available().toLong()
                    Log.d(TAG, "Asset file size using open() + available(): $assetSize bytes (${assetSize / (1024 * 1024)} MB)")
                }
                assetFileSize = assetSize

                // 내부 저장소에 있는 파일의 크기
                internalFileSize = modelFile.length()

                Log.d(TAG, "Asset file size: $assetFileSize bytes (${assetFileSize / (1024 * 1024)} MB)")
                Log.d(TAG, "Internal file size: $internalFileSize bytes (${internalFileSize / (1024 * 1024)} MB)")

                // 두 파일의 크기가 같은지 확인
                if (assetFileSize == internalFileSize && internalFileSize > 0) {
                    Log.d(TAG, "Model file already exists with correct size - no need to copy")
                    val fileSizeBytes = modelFile.length()
                    val fileSizeMB = fileSizeBytes / (1024 * 1024)
                    val fileSizeGB = fileSizeBytes / (1024 * 1024 * 1024)
                    Log.d(TAG, "Existing model file size: $fileSizeBytes bytes ($fileSizeMB MB, $fileSizeGB GB)")

                    // 파일 크기가 일치하면 복사 불필요
                    needCopy = false
                    Log.d(TAG, "needCopy set to false - skipping copy process")
                     // 이미 파일이 있고 크기가 일치하면 즉시 진행률 증가
                    updateProgress(0.5f)
                    return
                } else {
                    // 크기가 다르거나 0이면 파일 삭제 후 다시 복사
                    if (internalFileSize == 0L) {
                        Log.w(TAG, "Model file exists but has zero size. Deleting and re-copying.")
                    } else {
                        Log.w(TAG, "Model file exists but has incorrect size. Deleting and re-copying.")
                        Log.w(TAG, "Expected size: $assetFileSize bytes, Actual size: $internalFileSize bytes")
                        Log.w(TAG, "Size difference: ${Math.abs(assetFileSize - internalFileSize)} bytes (${Math.abs(assetFileSize - internalFileSize) / (1024 * 1024)} MB)")
                    }

                    val deleted = modelFile.delete()
                    Log.d(TAG, "Deleted incorrect size model file: $deleted")
                    Log.d(TAG, "needCopy remains true - will copy file from assets")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error checking file sizes: ${e.message}", e)
                // 오류 발생 시 파일 삭제 후 다시 복사
                val deleted = modelFile.delete()
                Log.d(TAG, "Exception occurred during size check. Deleted file: $deleted")
                Log.d(TAG, "needCopy remains true due to exception - will copy file from assets")
            }
        } else {
            Log.d(TAG, "Model file does not exist at ${modelFile.absolutePath}")
            Log.d(TAG, "needCopy is true - will copy file from assets")
            try {
                // 파일이 없는 경우에도 asset 파일 크기 확인 - open + available 방식 일괄 적용
                var assetSize = 0L
                context.assets.open(Model.MODEL_FILENAME).use { stream ->
                    assetSize = stream.available().toLong()
                    Log.d(TAG, "Asset file size using open() + available(): $assetSize bytes (${assetSize / (1024 * 1024)} MB)")
                }
                assetFileSize = assetSize
                Log.d(TAG, "Asset file size estimate: $assetFileSize bytes (${assetFileSize / (1024 * 1024)} MB)")
            } catch (e: Exception) {
                Log.e(TAG, "Error getting asset file size: ${e.message}", e)
            }
        }
        
        // 파일이 없거나 크기가 다른 경우 복사 진행
        if (needCopy) {
            try {
                // assets 폴더에서 모델 파일 열기 전 디버깅 로그
                Log.d(TAG, "Attempting to open model file from assets: ${Model.MODEL_FILENAME}")
                
                // assets에 있는 모든 파일 리스트 출력 (디버깅용)
                val assetFiles = context.assets.list("")
                Log.d(TAG, "Available files in assets: ${assetFiles?.joinToString(", ") ?: "none"}")
                
                // assets 폴더에서 모델 파일 열기 시도
                context.assets.open(Model.MODEL_FILENAME).use { inputStream ->
                    // 스트림에서 사용 가능한 바이트 수 확인 (open + available 방식)
                    val availableEstimate = inputStream.available()
                    Log.d(TAG, "Model file size using open() + available(): $availableEstimate bytes (${availableEstimate/(1024*1024)} MB)")
                    
                    // 대용량 파일 복사를 위한 설정
                    val buffer = ByteArray(8 * 1024 * 1024) // 8MB 버퍼
                    var bytesRead: Int
                    var totalBytesRead = 0L
                    val startTime = System.currentTimeMillis()
                    var lastLogTime = startTime
                    
                    // 파일 복사 시작 알림
                    Log.d(TAG, "Starting model file copy at ${java.util.Date(startTime)}")
                    updateProgress(0.01f)
                    
                    FileOutputStream(modelFile).use { outputStream ->
                        // 버퍼 단위로 복사하면서 진행률 업데이트
                        while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                            outputStream.write(buffer, 0, bytesRead)
                            
                            // 복사된 데이터 양 추적
                            totalBytesRead += bytesRead
                            
                            // 현재 시간
                            val currentTime = System.currentTimeMillis()
                            
                            // 1초마다 로그 출력 (과도한 로그 방지)
                            if (currentTime - lastLogTime >= 1000) {
                                // 복사 속도 계산
                                val elapsedSeconds = (currentTime - startTime) / 1000f
                                val copySpeedMBps = if (elapsedSeconds > 0) 
                                    (totalBytesRead / (1024f * 1024f)) / elapsedSeconds 
                                else 0f
                                
                                // 진행률 추정 (최대 0.5 = 50%)
                                // 실제 크기를 모르므로 available() 값을 임시로 활용하되 최소 기준값 설정
                                val totalSizeEstimate = maxOf(availableEstimate.toLong(), totalBytesRead)
                                val progress = (totalBytesRead.toFloat() / totalSizeEstimate).coerceAtMost(1f) * 0.5f
                                
                                // 진행률 및 복사 상태 로그
                                val totalMB = totalBytesRead / (1024 * 1024)
                                Log.d(TAG, "Copy progress: $totalBytesRead bytes ($totalMB MB) copied, " +
                                      "Speed: ${copySpeedMBps.toInt()} MB/s, " +
                                      "Elapsed: ${elapsedSeconds.toInt()}s, " +
                                      "Estimated progress: ${(progress * 100).toInt()}%")
                                
                                // UI 진행률 업데이트
                                updateProgress(progress)
                                
                                lastLogTime = currentTime
                            }
                        }
                    }
                    
                    // 복사 완료 후 최종 진행률 설정
                    updateProgress(0.5f)
                    
                    // 복사 완료 상태 및 시간 측정
                    val endTime = System.currentTimeMillis()
                    val totalTimeSeconds = (endTime - startTime) / 1000
                    val finalFileSizeBytes = modelFile.length()
                    val finalFileSizeMB = finalFileSizeBytes / (1024 * 1024)
                    val finalFileSizeGB = finalFileSizeBytes / (1024 * 1024 * 1024)
                    val avgSpeedMBps = if (totalTimeSeconds > 0) 
                        (finalFileSizeMB / totalTimeSeconds) 
                    else 0
                    
                    // 최종 복사 결과 종합 로그
                    Log.d(TAG, "Model file copy COMPLETED:")
                    Log.d(TAG, "- Total size: $finalFileSizeBytes bytes ($finalFileSizeMB MB, $finalFileSizeGB GB)")
                    Log.d(TAG, "- Copy time: $totalTimeSeconds seconds")
                    Log.d(TAG, "- Avg speed: $avgSpeedMBps MB/s")
                    Log.d(TAG, "- Destination: ${modelFile.absolutePath}")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to copy model from assets: ${e.message}", e)
                throw ModelLoadFailException("모델 파일 복사 중 오류 발생: ${e.message}")
            }
        }
    }

    private fun createEngine(context: Context) {
        val modelFile = File(context.filesDir, Model.MODEL_FILENAME)
        
        // 모델 파일 정보 확인 및 디버깅
        if (!modelFile.exists()) {
            Log.e(TAG, "Model file does not exist at expected path: ${modelFile.absolutePath}")
            throw ModelLoadFailException("모델 파일이 존재하지 않습니다: ${modelFile.absolutePath}")
        }
        
        // 모델 파일 크기 및 정보 로깅
        val fileSizeBytes = modelFile.length()
        val fileSizeMB = fileSizeBytes / (1024 * 1024)
        Log.d(TAG, "Loading model file: ${modelFile.absolutePath}")
        Log.d(TAG, "Model file size: $fileSizeBytes bytes ($fileSizeMB MB)")
        
        val modelPath = modelFile.absolutePath
        
        // 50%에서 시작하여 점진적으로 증가하도록 타이머 시작
        // 이미 50%는 파일 복사에서 설정됨
        // 별도의 업데이트 없음
                
        val inferenceOptions = LlmInference.LlmInferenceOptions.builder()
            .setModelPath(modelPath)
            .setMaxTokens(MAX_TOKENS)
            .apply { model.preferredBackend.let { setPreferredBackend(it) } }
            .build()

        try {
            // 엔진 생성 시작 시간 기록
            val startTime = System.currentTimeMillis()
            Log.d(TAG, "LlmInference.createFromOptions() started at ${java.util.Date(startTime)}")
            
            // 모델 엔진 생성 - 이 단계가 오래 걸릴 수 있음
            Log.d(TAG, "Creating model engine, this may take some time...")
            llmInference = LlmInference.createFromOptions(context, inferenceOptions)
            
            // 엔진 생성 완료 시간 및 소요 시간 계산
            val endTime = System.currentTimeMillis()
            val durationSeconds = (endTime - startTime) / 1000
            Log.d(TAG, "LlmInference.createFromOptions() completed at ${java.util.Date(endTime)}")
            Log.d(TAG, "Engine creation took $durationSeconds seconds")
            
            // 엔진 생성 후에도 진행률 업데이트는 하지 않음
            // 타이머가 계속 진행되도록 함
        } catch (e: Exception) {
            Log.e(TAG, "Load model error: ${e.message}", e)
            Log.e(TAG, "Stack trace: ", e)
            throw ModelLoadFailException("모델 로드 실패: ${e.message}")
        }
    }

    private fun createSession() {
        // 세션 생성 단계에서도 추가 진행률 업데이트 없음
        // 타이머에 의한 자연스러운 증가만 유지
        
        // 세션 옵션 설정 및 로깅
        Log.d(TAG, "Creating session with options:")
        Log.d(TAG, "- Temperature: ${model.temperature}")
        Log.d(TAG, "- TopK: ${model.topK}")
        Log.d(TAG, "- TopP: ${model.topP}")
        
        val sessionOptions = LlmInferenceSession.LlmInferenceSessionOptions.builder()
            .setTemperature(model.temperature)
            .setTopK(model.topK)
            .setTopP(model.topP)
            .build()

        try {
            // 세션 생성 시작 시간 기록
            val startTime = System.currentTimeMillis()
            Log.d(TAG, "Session creation started at ${java.util.Date(startTime)}")
            
            // 세션 생성
            llmInferenceSession = LlmInferenceSession.createFromOptions(llmInference, sessionOptions)
            
            // 세션 생성 완료 시간 및 소요 시간 계산
            val endTime = System.currentTimeMillis()
            val durationMs = endTime - startTime
            Log.d(TAG, "Session creation completed at ${java.util.Date(endTime)}")
            Log.d(TAG, "Session creation took $durationMs ms")
            
            // 세션 정보 확인
            try {
                val contextSize = llmInferenceSession.sizeInTokens("test message")
                Log.d(TAG, "Test context size: $contextSize tokens")
            } catch (e: Exception) {
                Log.w(TAG, "Unable to test token counting: ${e.message}")
            }
        } catch (e: Exception) {
            Log.e(TAG, "LlmInferenceSession create error: ${e.message}", e)
            Log.e(TAG, "Stack trace: ", e)
            throw ModelSessionCreateFailException()
        }
    }

    fun generateResponseAsync(prompt: String, progressListener: ProgressListener<String>) : ListenableFuture<String> {
        if (!isInitialized || !::llmInferenceSession.isInitialized) {
            throw ModelLoadFailException("모델 세션이 초기화되지 않았습니다. 앱을 다시 시작해주세요.")
        }
        
        llmInferenceSession.addQueryChunk(prompt)
        return llmInferenceSession.generateResponseAsync(progressListener)
    }
    
    // LLM이 추론을 수행할 준비가 되었는지 확인하는 함수
    fun isLlmReady(): Boolean {
        return isInitialized && ::llmInferenceSession.isInitialized
    }
    
    // 세션이 이미 사용 중인지 확인하는 함수
    fun isSessionBusy(): Boolean {
        // 세션이 초기화되지 않았으면 사용 중이 아님
        if (!isInitialized || !::llmInferenceSession.isInitialized) {
            return false
        }
        
        // 세션이 사용 중인지 확인하는 방법이 직접적으로 제공되지 않으므로
        // 간접적으로 확인 (isBusy 필드가 있다면 사용 가능)
        try {
            // 세션에 대한 짧은 테스트 질의 시도
            val testPrompt = " "  // 공백 한 칸
            llmInferenceSession.sizeInTokens(testPrompt)
            return false  // 성공하면 사용 중이 아님
        } catch (e: Exception) {
            // 오류 발생 시 "Previous invocation still processing" 문자열 확인
            val errorMessage = e.message ?: ""
            return errorMessage.contains("Previous invocation still processing") ||
                   errorMessage.contains("Wait for done=true")
        }
    }

    fun estimateTokensRemaining(prompt: String): Int {
        if (!isInitialized || !::llmInferenceSession.isInitialized) {
            return -1
        }
        
        val context = uiState.messages.joinToString { it.rawMessage } + prompt
        if (context.isEmpty()) return -1 // Special marker if no content has been added

        val sizeOfAllMessages = llmInferenceSession.sizeInTokens(context)
        val approximateControlTokens = uiState.messages.size * 3
        val remainingTokens = MAX_TOKENS - sizeOfAllMessages - approximateControlTokens - DECODE_TOKEN_OFFSET
        // Token size is approximate so, let's not return anything below 0
        return max(0, remainingTokens)
    }

    companion object {
        // 모델 인스턴스 - 원래 GEMMA_3_1B_IT_INT4 설정으로 고정
        val model: Model = Model.DEFAULT
        private var instance: InferenceModel? = null
        
        // 모델 진행률을 위한 별도의 MutableStateFlow
        private val _initializationProgress = MutableStateFlow(0f)
        val initializationProgress: StateFlow<Float> = _initializationProgress.asStateFlow()
        
        // 인스턴스가 이미 초기화되어 있는지 확인하는 함수
        fun isInstanceInitialized(): Boolean {
            val currentInstance = instance
            // 인스턴스가 존재하고 초기화가 완료되었는지 확인
            return currentInstance != null && currentInstance.isInitialized
        }
        
        // 진행률 업데이트 함수 - 싱글톤 인스턴스에 종속되지 않음
        fun updateInitProgress(progress: Float) {
            Log.d("InferenceModel", "Global progress update: ${_initializationProgress.value} -> $progress")
            _initializationProgress.value = progress
        }

        fun getInstance(context: Context): InferenceModel {
            if (instance == null) {
                // 인스턴스 없을 때 진행률 초기화
                _initializationProgress.value = 0f
                
                // 실제 인스턴스 생성 (동기화)
                synchronized(this) {
                    // 이중 체크
                    if (instance == null) {
                        try {
                            // 진행률 업데이트 함수를 람다로 전달
                            instance = InferenceModel(context) { progress ->
                                updateInitProgress(progress)
                            }
                        } catch (e: Exception) {
                            // 오류 발생 시 진행률 리셋
                            _initializationProgress.value = 0f
                            throw e
                        }
                    }
                }
            }
            return instance!!
        }

        fun resetInstance(context: Context): InferenceModel {
            synchronized(this) {
                _initializationProgress.value = 0f
                instance?.close()
                instance = null
                
                // 초기화 완료 플래그 제거
                try {
                    context.getSharedPreferences("app_state", Context.MODE_PRIVATE)
                        .edit()
                        .putBoolean("init_complete", false)
                        .apply()
                    Log.d("InferenceModel", "Reset initialization complete flag")
                } catch (e: Exception) {
                    Log.e("InferenceModel", "Failed to reset initialization flag: ${e.message}")
                }
                
                return getInstance(context)
            }
        }
    }
}
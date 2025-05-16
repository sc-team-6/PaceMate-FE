package com.gdg.scrollmanager.fragments

import android.content.Context
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.fragment.app.Fragment
import com.gdg.scrollmanager.gemma.InferenceModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.gdg.scrollmanager.utils.AddictionProbabilityManager
import com.gdg.scrollmanager.utils.DataStoreUtils
import com.gdg.scrollmanager.utils.UsageStatsUtils
import java.io.File
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive

class AlertFragment : Fragment() {
    companion object {
        const val TAG = "AlertFragment"
    }

    // 코루틴 작업을 관리하기 위한 Job 객체
    private var messageGenerationJob: kotlinx.coroutines.Job? = null

    override fun onDestroyView() {
        super.onDestroyView()
        // Fragment가 파괴될 때 실행 중인 코루틴 작업 취소
        messageGenerationJob?.cancel()
        messageGenerationJob = null
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return ComposeView(requireContext()).apply {
            setContent {
                MaterialTheme {
                    Surface(
                        modifier = Modifier.fillMaxSize(),
                        color = MaterialTheme.colorScheme.background
                    ) {
                        MainScreen()
                    }
                }
                }
            }
        }


    @Composable
    fun MainScreen() {
        var isLoading by remember { mutableStateOf(true) }
        var isModelInitialized by remember { mutableStateOf(false) }
        var errorMessage by remember { mutableStateOf<String?>(null) }
        var loadingProgress by remember { mutableStateOf(0f) }
        var isModelFileCopyNeeded by remember { mutableStateOf(true) }
        var isGeneratingMessage by remember { mutableStateOf(false) } // 메시지 생성 중인지 상태 추가
        var showInferenceDialog by remember { mutableStateOf(false) } // 추론 중 모달 다이얼로그 표시 여부

        val coroutineScope = rememberCoroutineScope()
        val context = LocalContext.current

        // 중독 확률 관리
        var addictionProbability by remember { mutableStateOf(getAddictionProbability(context)) }
        var encouragementMessage by remember { mutableStateOf("") }

        if (showInferenceDialog) {
            AlertDialog(
                onDismissRequest = { /* 닫히지 않도록 빈 함수로 설정 */ },
                title = { Text("Analyzing Usage Pattern") },
                text = {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.fillMaxWidth().padding(16.dp)
                    ) {
                        CircularProgressIndicator(modifier = Modifier.padding(bottom = 16.dp))
                        Text(
                            "AI is analyzing your smartphone usage pattern.",
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            "Please wait. Do not close this window. It may takes 1-2 minutes.",
                            fontSize = 14.sp,
                            color = Color.Gray,
                            textAlign = TextAlign.Center
                        )
                    }
                },
                confirmButton = { /* 뺄 */ },
                dismissButton = { /* 뺄 */ }
            )
        }
        // 모델 파일이 완전하게 복사되었는지 확인하는 함수
        fun isModelFileCompletelyExists(context: Context): Boolean {
            val modelFile = File(context.filesDir, com.gdg.scrollmanager.gemma.Model.MODEL_FILENAME)
            if (!modelFile.exists()) {
                Log.d("AlertScreen", "Model file doesn't exist in internal storage")
                return false
            }

            try {
                // Assets에 있는 원본 파일의 크기 확인 - open + available 방식 적용
                var assetFileSize = 0L
                context.assets.open(com.gdg.scrollmanager.gemma.Model.MODEL_FILENAME).use { stream ->
                    assetFileSize = stream.available().toLong()
                }
                // 내부 저장소에 있는 파일의 크기
                val internalFileSize = modelFile.length()

                Log.d("AlertScreen", "Asset file size (using open+available): $assetFileSize bytes (${assetFileSize / (1024 * 1024)} MB)")
                Log.d("AlertScreen", "Internal file size: $internalFileSize bytes (${internalFileSize / (1024 * 1024)} MB)")

                // 두 파일의 크기가 같은지 확인
                if (assetFileSize != internalFileSize) {
                    Log.w("AlertScreen", "Model file exists but has different size: $internalFileSize bytes (expected: $assetFileSize bytes)")
                    Log.w("AlertScreen", "Size difference: ${Math.abs(assetFileSize - internalFileSize)} bytes")
                    // 크기가 다르면 불완전한 파일로 간주하고 삭제
                    val deleted = modelFile.delete()
                    Log.d("AlertScreen", "Deleted incorrect size model file: $deleted")
                    return false
                }

                Log.d("AlertScreen", "Model file exists and has correct size: $internalFileSize bytes - validation PASSED ✓")
                return true
            } catch (e: Exception) {
                Log.e("AlertScreen", "Error checking model file sizes: ${e.message}", e)
                // 안전을 위해 파일 삭제 시도
                try {
                    val deleted = modelFile.delete()
                    Log.d("AlertScreen", "Attempted to delete file after error: $deleted")
                } catch (deleteException: Exception) {
                    Log.e("AlertScreen", "Failed to delete file after error: ${deleteException.message}")
                }
                // 파일 크기 확인 중 오류 발생 시 안전하게 복사 필요로 판단
                return false
            }
        }

        // 모델 초기화가 필요할 경우 초기화 프로세스 시작하는 함수
        val startModelInitialization = {
            coroutineScope.launch(Dispatchers.IO) {
                try {
                    Log.d("AlertScreen", "Starting model initialization in background")
                    // 모델 파일 존재 여부 확인 및 UI 업데이트
                    val modelExists = isModelFileCompletelyExists(context)
                    withContext(Dispatchers.Main) {
                        isModelFileCopyNeeded = !modelExists
                        if (modelExists) {
                            Log.d("AlertScreen", "Model file already exists and is complete, showing simple loader")
                        } else {
                            Log.d("AlertScreen", "Model file doesn't exist or is incomplete, showing detailed progress")
                        }
                    }

                    // 모델 초기화 - 여기서 진행률이 업데이트됨
                    InferenceModel.getInstance(context)
                } catch (e: Exception) {
                    withContext(Dispatchers.Main) {
                        Log.e("AlertScreen", "Model initialization failed: $e")
                        errorMessage = e.message ?: "An unknown error occurred."
                        isLoading = false
                    }
                }
            }
        }

        // 컴포저블 초기화 시 이미 모델이 로드되어 있는지 확인
        LaunchedEffect(Unit) {
            try {
                // 모델 인스턴스가 이미 존재하는지 확인
                if (InferenceModel.isInstanceInitialized()) {
                    Log.d("AlertScreen", "Model already initialized, skipping loading screen")
                    isModelInitialized = true
                    isLoading = false

                    // 모델 초기화되어 있지만 추론은 자동으로 시작하지 않음
                    // 사용자가 추론하기 버튼을 누르면 추론 시작
                } else {
                    // 모델 파일 존재 여부 미리 확인
                    isModelFileCopyNeeded = !isModelFileCompletelyExists(context)

                    // 없다면 진행률 Flow 구독 시작
                    // 글로벌 진행률 Flow 구독
                    Log.d("AlertScreen", "Model needs initialization, starting progress monitoring")
                    Log.d("AlertScreen", "Model file copy needed: $isModelFileCopyNeeded")
                    InferenceModel.initializationProgress.collect { progress: Float ->
                        withContext(Dispatchers.Main) {
                            Log.d("AlertScreen", "Received global progress update: $progress")
                            loadingProgress = progress

                            // 초기화 완료 확인
                            if (progress >= 1.0f) {
                                try {
                                    // 초기화 완료 시 인스턴스 확인
                                    val model = InferenceModel.getInstance(context)
                                    isModelInitialized = true
                                    isLoading = false
                                    Log.d("AlertScreen", "Model initialization completed")

                                    // 모델 초기화가 완료되면 자동 추론 시작하지 않음
                                    // 추론하기 버튼을 누르면 추론 시작
                                } catch (e: Exception) {
                                    errorMessage = e.message
                                    isLoading = false
                                    Log.e("AlertScreen", "Error accessing model: $e")
                                }
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e("AlertScreen", "Error checking model initialization status: $e")
                // 에러 발생 시 기존 로직으로 대체
                startModelInitialization()
            }
        }

        // 모델 초기화가 필요한 경우만 초기화 실행
        LaunchedEffect(Unit) {
            if (isLoading && !isModelInitialized && errorMessage == null) {
                startModelInitialization()
            }
        }

        // 디버그 로그를 위한 효과
        LaunchedEffect(loadingProgress) {
            Log.d("AlertScreen", "Progress updated in UI: $loadingProgress")
        }

        // 상태에 따라 적절한 화면 표시
        when {
            isLoading -> {
                if (isModelFileCopyNeeded) {
                    // 모델 파일 복사가 필요한 경우 기존의 상세 진행률 표시
                    RealTimeLoadingScreen(progress = loadingProgress)
                } else {
                    // 파일이 이미 있는 경우 단순 로딩 화면 표시
                    SimpleLoadingScreen()
                }
            }
            errorMessage != null -> {
                ErrorScreen(
                    message = errorMessage!!,
                    onRetry = {
                        isLoading = true
                        errorMessage = null
                        loadingProgress = 0f

                        coroutineScope.launch {
                            try {
                                // 앱 캐시 정리
                                withContext(Dispatchers.IO) {
                                    // 초기화 완료 플래그 제거
                                    context.getSharedPreferences("app_state", Context.MODE_PRIVATE)
                                        .edit()
                                        .putBoolean("init_complete", false)
                                        .apply()

                                    // 캐시 정리
                                    context.cacheDir.listFiles()?.forEach { it.delete() }

                                    // TFLite 관련 캐시 정리
                                    val searchDirs = listOf(context.cacheDir, context.filesDir)
                                    searchDirs.forEach { dir ->
                                        dir.listFiles { file ->
                                            file.name.contains(".xnnpack_cache") ||
                                            file.name.contains(".tflite") ||
                                            file.name.endsWith(".cache")
                                        }?.forEach { file ->
                                            file.delete()
                                        }
                                    }

                                    Log.d("AlertScreen", "Cleared all cache before retry")

                                    // 모델 재설정
                                    InferenceModel.resetInstance(context)
                                }
                            } catch (e: Exception) {
                                withContext(Dispatchers.Main) {
                                    errorMessage = e.message ?: "An unknown error occurred."
                                    isLoading = false
                                }
                            }
                        }
                    }
                )
            }
            isModelInitialized -> {
                // 모델 초기화가 완료되면 Alert 화면 표시
                AddictionProbabilityScreen(
                    addictionProbability = addictionProbability,
                    encouragementMessage = encouragementMessage,
                    isGeneratingMessage = isGeneratingMessage,
                    onGenerateMessage = {
                        if (!isGeneratingMessage) {
                            isGeneratingMessage = true
                            messageGenerationJob = coroutineScope.launch {
                                try {
                                    // UI 상태 업데이트 (로딩 시작)
                                    withContext(Dispatchers.Main) {
                                        showInferenceDialog = true
                                        isLoading = true
                                    }
                                    
                                    // 백그라운드에서 메시지 생성
                                    val result = withContext(Dispatchers.IO) {
                                        // 메시지 생성 (중독 확률 전달)
                                        generateMessage(context, addictionProbability)
                                    }
                                    
                                    // UI 업데이트 (완료)
                                    withContext(Dispatchers.Main) {
                                        encouragementMessage = result
                                        isLoading = false
                                        isGeneratingMessage = false
                                        showInferenceDialog = false
                                    }
                                } catch (e: Exception) {
                                    Log.e(TAG, "Message generation error: ${e.message}", e)
                                    withContext(Dispatchers.Main) {
                                        errorMessage = e.message ?: "An unknown error occurred."
                                        isLoading = false
                                        isGeneratingMessage = false
                                        showInferenceDialog = false
                                    }
                                }
                            }
                        }
                    }
                )
            }
        }
    }

    @Composable
    fun RealTimeLoadingScreen(progress: Float) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.fillMaxSize()
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(32.dp)
            ) {
                // 진행률 텍스트
                Text(
                    text = "${(progress * 100).toInt()}%",
                    style = MaterialTheme.typography.headlineLarge,
                    color = MaterialTheme.colorScheme.primary
                )

                // 프로그레스 바
                LinearProgressIndicator(
                    progress = { progress.coerceIn(0f, 1f) },
                    modifier = Modifier.padding(vertical = 16.dp)
                )

                // 진행 단계
                val stageText = when {
                    progress < 0.5f -> "Copying model file..."
                    else -> "Initializing model engine..."
                }

                Text(
                    text = stageText,
                    style = MaterialTheme.typography.bodyLarge
                )
            }
        }
    }

    @Composable
    fun SimpleLoadingScreen() {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.fillMaxSize()
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(32.dp)
            ) {
                // 원형 프로그레스 인디케이터
                CircularProgressIndicator(
                    modifier = Modifier.padding(16.dp),
                    color = MaterialTheme.colorScheme.primary
                )

                // 상태 메시지
                Text(
                    text = "Initializing the model...",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onBackground,
                    textAlign = TextAlign.Center
                )

                Text(
                    text = "Please wait a moment",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }
        }
    }

    @Composable
    fun ErrorScreen(message: String, onRetry: () -> Unit) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "Loading Failed",
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.error
                )

                Text(
                    text = message,
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(vertical = 16.dp)
                )

                Button(
                    onClick = onRetry,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp),
                    colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF6AB9A3) // mint_primary 색상
                    ),
                    shape = RoundedCornerShape(4.dp)
                ) {
                    Text(
                        text = "Retry",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Medium,
                        color = Color.White
                    )
                }
            }
        }
    }

    @Composable
    fun AddictionProbabilityScreen(
        addictionProbability: Int,
        encouragementMessage: String,
        isGeneratingMessage: Boolean,
        onGenerateMessage: () -> Unit
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Top
        ) {
            Spacer(modifier = Modifier.height(50.dp))

            Spacer(modifier = Modifier.height(32.dp))

            // 제목
            Text(
                text = "Digital Addiction Analysis Result",
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(32.dp))

            // 중독 확률 표시
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color(0xFFE6F7F0))
                    .padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "Your Digital Addiction Probability",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Medium
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    Text(
                        text = "$addictionProbability%",
                        fontSize = 48.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF70BCA4)
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    val riskLevel = when {
                        addictionProbability >= 80 -> "Very High"
                        addictionProbability >= 60 -> "High"
                        addictionProbability >= 40 -> "Medium"
                        addictionProbability >= 20 -> "Low"
                        else -> "Very Low"
                    }

                    Text(
                        text = "Risk Level: $riskLevel",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }

            Spacer(modifier = Modifier.height(32.dp))

            // Gemma 응원 메시지 표시 영역
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color(0xFFF5F5F5))
                    .padding(24.dp)
                    .height(250.dp) // 고정 높이로 설정
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "AI Usage Pattern Analysis",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Medium,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    if (encouragementMessage.isEmpty()) {
                        if (isGeneratingMessage) {
                            // 생성 중인 경우 로딩 표시
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                CircularProgressIndicator(
                                    modifier = Modifier.padding(16.dp),
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Text(
                                    text = "Generating usage pattern analysis...",
                                    fontSize = 14.sp,
                                    textAlign = TextAlign.Center
                                )
                            }
                        } else {
                            // 생성 중이 아닌 경우 버튼 표시
                            Box(
                                modifier = Modifier.fillMaxSize(),
                                contentAlignment = Alignment.Center
                            ) {
                                Button(
                                    onClick = onGenerateMessage,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(56.dp),
                                    colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                                        containerColor = Color(0xFF6AB9A3) // mint_primary 색상
                                    ),
                                    shape = RoundedCornerShape(4.dp)
                                ) {
                                    Text(
                                        text = "Analyze Usage Pattern",
                                        fontSize = 16.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = Color.White
                                    )
                                }
                            }
                        }
                    } else {
                        // 스크롤 가능한 텍스트 영역 (남은 공간 모두 채우기)
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .verticalScroll(rememberScrollState())
                                .padding(vertical = 8.dp)
                        ) {
                            Text(
                                text = encouragementMessage,
                                fontSize = 16.sp,
                                lineHeight = 24.sp,
                                textAlign = TextAlign.Center,
                                // 에러 메시지인 경우 빨간색으로 표시
                                color = if (encouragementMessage.startsWith("ERROR:")) Color.Red else Color.Black,
                                fontWeight = if (encouragementMessage.startsWith("ERROR:")) FontWeight.Bold else FontWeight.Normal,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // 도움말 텍스트
            Text(
                text = "We analyzed your digital usage patterns to calculate addiction probability. Get personalized advice for healthy digital life.",
                fontSize = 14.sp,
                color = Color.Gray,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 16.dp)
            )

            Spacer(modifier = Modifier.height(32.dp))
        }
    }

    // 중독 확률을 가져오는 함수
    private fun getAddictionProbability(context: Context): Int {
        return AddictionProbabilityManager.getAddictionProbability(context)
    }

    // 응원 메시지 생성 함수 - 세션 상태 확인 후 모델 호출 (실제 사용자 데이터 사용)
    private suspend fun generateMessage(context: Context, probability: Int): String {
        // 현재 코루틴이 취소되었는지 확인
        kotlinx.coroutines.currentCoroutineContext().ensureActive()

        // 모델 인스턴스 가져오기
        val model = InferenceModel.getInstance(context)
        
        // 이전 세션의 상태를 확인 (이미 처리 중인지)
        if (model.isSessionBusy()) {
            Log.w(TAG, "Previous session is still processing")
            return "ERROR: Previous analysis session is still active. Please try again after a few minutes."
        }
        
        // LLM 인스턴스가 준비되었는지 확인
        if (!model.isLlmReady()) {
            Log.e(TAG, "LLM is not ready for inference")
            return "The analysis engine is not ready yet. Please try again in a moment."
        }

        // 실제 사용자 데이터 수집
        val usageStatsUtils = UsageStatsUtils
        val dataStoreUtils = DataStoreUtils
        
        // 현재 날짜
        val currentDateTime = usageStatsUtils.getCurrentDateTime()
        
        // 앱 사용 시간 통계
        val appUsageStats = usageStatsUtils.getAppUsageStats(context)
        val topApps = appUsageStats.sortedByDescending { it.timeInForeground }.take(5)
        
        // 앱 사용 정보 및 기타 통계 수집
        val screenTime5Min = usageStatsUtils.getTotalUsageTimeInMinutes(context, 5) * 60
        val screenTime15Min = usageStatsUtils.getTotalUsageTimeInMinutes(context, 15) * 60
        val screenTime30Min = usageStatsUtils.getTotalUsageTimeInMinutes(context, 30) * 60
        val screenTime60Min = usageStatsUtils.getTotalUsageTimeInMinutes(context, 60) * 60
        
        val unlocks5Min = usageStatsUtils.getUnlockCount(context, 5)
        val unlocks15Min = usageStatsUtils.getUnlockCount(context, 15)
        val unlocks30Min = usageStatsUtils.getUnlockCount(context, 30)
        val unlocks60Min = usageStatsUtils.getUnlockCount(context, 60)
        
        val recentScrollPixels = dataStoreUtils.getRecentScrollPixels(context)
        val socialMediaCount15Min = usageStatsUtils.getSocialMediaAppUsageCount(context, 15)
        val socialMediaCount60Min = usageStatsUtils.getSocialMediaAppUsageCount(context, 60)
        val mainCategory = usageStatsUtils.getMainAppCategory(context)
        
        // 최근 예측 결과 (있는 경우)
        val predictionResult = try {
            dataStoreUtils.getPredictionResultFlow(context).first()
        } catch (e: Exception) {
            Pair(0, 0f)
        }
        
        // Data 포맷팅 - 원본 프롬프트 요구사항에 맞게 구성
        val rawData = StringBuilder()
        rawData.appendLine("Data collected at: $currentDateTime")
        rawData.appendLine()
        
        // 테이블 포맷으로 데이터 표시
        rawData.appendLine("| ScreenSeconds | ScrollPx | Unlocks | Timestamp             | AppsUsed | PackagesOpened                               |")
        rawData.appendLine("|---------------|----------|---------|----------------------|----------|---------------------------------------------|")
        
        // 5분 데이터
        val topApps5Min = usageStatsUtils.getTopUsedPackages(context, 5).joinToString(", ")
        rawData.appendLine("| $screenTime5Min | $recentScrollPixels | $unlocks5Min | $currentDateTime | ${topApps5Min.count { it == ',' } + 1} | [${topApps5Min}] |")
        
        // 15분 데이터
        val topApps15Min = usageStatsUtils.getTopUsedPackages(context, 15).joinToString(", ")
        rawData.appendLine("| $screenTime15Min | ${recentScrollPixels * 2} | $unlocks15Min | $currentDateTime | ${topApps15Min.count { it == ',' } + 1} | [${topApps15Min}] |")
        
        // 30분 데이터
        val topApps30Min = usageStatsUtils.getTopUsedPackages(context, 30).joinToString(", ")
        rawData.appendLine("| $screenTime30Min | ${recentScrollPixels * 3} | $unlocks30Min | $currentDateTime | ${topApps30Min.count { it == ',' } + 1} | [${topApps30Min}] |")
        
        // 60분 데이터
        val topApps60Min = usageStatsUtils.getTopUsedPackages(context, 60).joinToString(", ")
        rawData.appendLine("| $screenTime60Min | ${recentScrollPixels * 4} | $unlocks60Min | $currentDateTime | ${topApps60Min.count { it == ',' } + 1} | [${topApps60Min}] |")
        
        // 추가 데이터
        rawData.appendLine()
        rawData.appendLine("Additional data:")
        rawData.appendLine("- Prediction Result: ${predictionResult.first}")
        rawData.appendLine("- Prediction Probability: ${predictionResult.second * 100}%")
        rawData.appendLine("- Data Collection Progress: ${dataStoreUtils.getDataCollectionProgress(context)}%")
        rawData.appendLine("- Social Media App Usage Count (15min): $socialMediaCount15Min")
        rawData.appendLine("- Social Media App Usage Count (60min): $socialMediaCount60Min")
        rawData.appendLine("- Main App Category: $mainCategory")
        
        // 상위 앱 사용 통계
        rawData.appendLine()
        rawData.appendLine("Top 5 Apps by Usage:")
        topApps.forEach { app ->
            val hours = app.timeInForeground / (1000 * 60 * 60)
            val minutes = (app.timeInForeground / (1000 * 60)) % 60
            rawData.appendLine("- ${app.appName}: ${hours}h ${minutes}m")
        }
        
        // 원본 프롬프트 사용 (요청된 대로)
        val prompt = """
            Generate a structured medical report summary based on the following raw smartphone usage data, intended for a medical professional to review in the context of potential smartphone addiction assessment.
            The raw data consists of records with the following fields: ScreenSeconds (screen time in seconds), ScrollPx (scroll amount in pixels), Unlocks (number of unlocks), Timestamp (YYYY-MM-DD HH:MM:SS), AppsUsed (number of unique apps used in the record's interval), PackagesOpened (list of opened app package names in the record's interval).
            The report should be factual, objective, and based SOLELY on the provided raw data. Do NOT make a diagnosis or suggest the user has an addiction. Your task is to analyze and summarize the behavioral patterns observed in the data that are relevant to common criteria used in assessing potential smartphone addiction (Preoccupation, Tolerance, Craving/Withdrawal [behavioral indicators inferred from data], Loss of Control [behavioral indicators inferred from data], Neglect of other areas [behavioral indicators inferred from data]).
            Analyze the raw data to identify patterns related to:
            *   Total usage time and average daily/weekly duration (derived from ScreenSeconds and Timestamp).
            *   Frequency of interaction and checking behavior (derived from Unlocks and Timestamp).
            *   Intensity of engagement, particularly in scrolling activities (derived from ScrollPx, ScreenSeconds, and PackagesOpened).
            *   Timing of usage throughout the day and week (derived from Timestamp).
            *   Variability and trends in usage patterns over the data period (derived from analysis of all fields across the Timestamp range).
            *   Concentration of usage in specific apps or types of apps (derived from PackagesOpened and AppsUsed in relation to ScreenSeconds and ScrollPx).
            Structure the report as follows:
            **Smartphone Usage Behavior Summary**
            **User Identifier:** [Use placeholder if data is anonymous, or insert placeholder for ID]
            **Data Period:** [State the exact date range the provided data covers]
            **Summary of Usage Patterns Derived from Data:**
            Present the key findings and observed patterns, based on the analysis of the raw data points provided. Describe the *behaviors* the data reveals, linking them back to the analyzed data points (ScreenSeconds, Unlocks, ScrollPx, Timestamp patterns, etc.).
            *   **Overall Engagement and Volume:** Report aggregated ScreenSeconds data (e.g., total for period, average per day/week), noting overall usage duration.
            *   **Frequency of Interaction:** Report aggregated Unlocks data (e.g., total for period, average per day), noting how often the device is accessed.
            *   **Engagement Characteristics:** Describe patterns observed from ScreenSeconds, ScrollPx, and PackagesOpened analysis. Note significant scroll activity, common apps consuming time/scrolls, and patterns that might suggest rapid, brief interactions or prolonged immersion.
            *   **Timing and Consistency of Use:** Describe patterns from Timestamp analysis. Report peak usage times, prevalence of late-night or early-morning use, usage during typical work/study hours, and day-to-day variability in usage volume and timing.
            *   **Trends Over Period:** Describe any notable increases, decreases, or shifts in ScreenSeconds, Unlocks, ScrollPx, or usage timing throughout the specified Data Period.
            *   **App Focus:** Summarize which apps or app categories contribute most significantly to ScreenSeconds and ScrollPx, and briefly mention the variety indicated by AppsUsed.
            **Note on Data Source:** This report is based solely on the provided raw smartphone usage data records and does not include subjective self-report or clinical interview information.
            **Disclaimer:** This summary is provided for informational purposes only to present observed behavioral data. Diagnosis and clinical assessment are the sole responsibility of a qualified medical professional.
            
            Raw Usage Data:
            ${rawData.toString()}
            
            Addiction Probability: $probability%
        """.trimIndent()
        
        // 프롬프트가 너무 길면 간략화된 버전 사용 (이전 동일하게)
        val shortPrompt = if (prompt.length > 3000) {
            """
            Generate a brief medical report on smartphone usage patterns for addiction assessment.
            Analyze only the provided data, without making diagnoses.
            
            Key areas to focus on:
            1. Overall screen time (${screenTime60Min / 60} min in last hour)
            2. Unlock frequency ($unlocks60Min times in last hour)
            3. Scroll intensity ($recentScrollPixels pixels)
            4. App usage patterns (Main category: $mainCategory)
            5. Social media usage ($socialMediaCount60Min in last hour)
            
            Top apps: ${topApps.take(3).joinToString(", ") { it.appName }}
            
            Create a concise 2-paragraph report highlighting behavioral patterns.
            Current addiction probability assessment: $probability%
            """.trimIndent()
        } else {
            prompt
        }

        Log.d(TAG, "Using data-based prompt: ${shortPrompt.take(200)}...")

        // 응답 생성
        var message = ""
        try {
            // 응답 생성 시도
            val asyncFuture = model.generateResponseAsync(shortPrompt) { partialResult, done ->
                if (partialResult.isNotEmpty()) {
                    message += partialResult
                }
            }

            // 타임아웃을 설정하여 무한 대기 방지
            try {
                asyncFuture.get(30, java.util.concurrent.TimeUnit.SECONDS)
            } catch (e: java.util.concurrent.TimeoutException) {
                Log.w(TAG, "Response generation timed out")
                if (message.isBlank()) {
                    return "Analysis is taking longer than expected. Please try again later."
                }
            }
            
            Log.d(TAG, "Generated message: $message")
            
            if (message.isBlank()) {
                return getFallbackResponse(probability)
            }
            
            return message.trim()
            
        } catch (e: Exception) {
            Log.e(TAG, "Error generating message: ${e.message}", e)
            
            // 특정 오류 메시지에 따라 사용자에게 다른 안내 제공
            if (e.message?.contains("Previous invocation still processing") == true) {
                return "ERROR: Previous analysis session is still active. Please try again after a few minutes."
            }
            
            return "Unable to complete analysis at this time. Please try again later."
        }
    }
    
    // 대체 응답 생성 (모델 호출 실패 시 사용)
    private fun getFallbackResponse(probability: Int): String {
        return when {
            probability >= 80 -> "Your usage patterns show intensive engagement with social and video apps, with significant daily screen time. Consider setting specific usage limitations and scheduled breaks to build healthier digital habits."
            probability >= 60 -> "Your smartphone usage shows frequent engagement with browser and social applications, including significant time on entertainment platforms. Your unlock patterns suggest regular checking behavior throughout the day."
            probability >= 40 -> "Your usage is distributed across browsing, social, and utility apps. While entertainment apps consume significant time, your usage appears varied across different categories. Setting specific goals could help maintain digital balance."
            else -> "Your smartphone usage primarily focuses on communication and utility applications, with moderate entertainment use. Your current digital habits demonstrate a relatively balanced approach to technology."
        }
    }
    
    // 사용자의 실제 사용 데이터 수집 함수는 더 이상 필요하지 않으므로 제거
}
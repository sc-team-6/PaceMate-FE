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
import java.io.File
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
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
                onDismissRequest = { /* 백버튼으로 닫힐 수 없게 설정 */ },
                title = { Text("메시지 생성 중") },
                text = {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.fillMaxWidth().padding(16.dp)
                    ) {
                        CircularProgressIndicator(modifier = Modifier.padding(bottom = 16.dp))
                        Text(
                            "인공지능이 메시지를 생성하고 있습니다.",
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            "잠시만 기다려주세요. 이 창을 닫지 마세요.",
                            fontSize = 14.sp,
                            color = Color.Gray,
                            textAlign = TextAlign.Center
                        )
                    }
                },
                confirmButton = { /* 확인 버튼 없음 */ },
                dismissButton = { /* 취소 버튼 없음 */ }
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
                        errorMessage = e.message ?: "알 수 없는 오류가 발생했습니다."
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

                    // 모델이 초기화되어 있고 중독 확률이 있으면 자동으로 메시지 생성
                    if (addictionProbability > 0 && !isGeneratingMessage) {
                        isGeneratingMessage = true
                        messageGenerationJob = coroutineScope.launch {
                        showInferenceDialog = true // 추론 시작 전 모달 표시
                        generateEncouragementMessage(
                        context,
                        addictionProbability,
                        onStart = { /* 이미 로딩 화면은 처리됨 */ },
                        onComplete = { result ->
                        encouragementMessage = result
                            isGeneratingMessage = false
                            showInferenceDialog = false // 추론 완료 시 모달 다이얼로그 닫기
                        },
                        onError = { errorMsg ->
                            errorMessage = errorMsg
                                isGeneratingMessage = false
                                                        showInferenceDialog = false // 오류 발생 시에도 모달 다이얼로그 닫기
                                                    }
                                                )
                        }
                    }
                } else {
                    // 모델 파일 존재 여부 미리 확인
                    isModelFileCopyNeeded = !isModelFileCompletelyExists(context)

                    // 없다면 진행률 Flow 구독 시작
                    // 글로벌 진행률 Flow 구독
                    Log.d("AlertScreen", "Model needs initialization, starting progress monitoring")
                    Log.d("AlertScreen", "Model file copy needed: $isModelFileCopyNeeded")
                    InferenceModel.initializationProgress.collect { progress ->
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

                                    // 모델 초기화가 완료되면 응원 메시지 생성
                                    if (addictionProbability > 0 && !isGeneratingMessage) {
                                        isGeneratingMessage = true
                                        messageGenerationJob = coroutineScope.launch {
                                            generateEncouragementMessage(
                                                context,
                                                addictionProbability,
                                                onStart = { /* 이미 로딩 화면은 처리됨 */ },
                                                onComplete = { result ->
                                                    encouragementMessage = result
                                                    isGeneratingMessage = false
                                                },
                                                onError = { errorMsg ->
                                                    errorMessage = errorMsg
                                                    isGeneratingMessage = false
                                                }
                                            )
                                        }
                                    }
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
                                    errorMessage = e.message ?: "알 수 없는 오류가 발생했습니다."
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
                            showInferenceDialog = true // 추론 시작 전 모달 표시
                            generateEncouragementMessage(
                            context,
                            addictionProbability,
                            onStart = { isLoading = true },
                            onComplete = { result ->
                            encouragementMessage = result
                            isLoading = false
                                isGeneratingMessage = false
                                showInferenceDialog = false // 추론 완료 시 모달 다이얼로그 닫기
                            },
                            onError = { errorMsg ->
                            errorMessage = errorMsg
                                isLoading = false
                                    isGeneratingMessage = false
                                                showInferenceDialog = false // 오류 발생 시에도 모달 다이얼로그 닫기
                                            }
                                        )
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
                    progress < 0.5f -> "모델 파일 복사 중..."
                    else -> "모델 엔진 초기화 중..."
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
                    text = "모델을 초기화 중입니다...",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onBackground,
                    textAlign = TextAlign.Center
                )

                Text(
                    text = "잠시만 기다려주세요",
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
                    text = "로딩 실패",
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.error
                )

                Text(
                    text = message,
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(vertical = 16.dp)
                )

                Button(onClick = onRetry) {
                    Text(text = "다시 시도")
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
            Spacer(modifier = Modifier.height(32.dp))

            // 제목
            Text(
                text = "디지털 중독 분석 결과",
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
                        text = "당신의 디지털 중독 확률",
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
                        addictionProbability >= 80 -> "매우 높음"
                        addictionProbability >= 60 -> "높음"
                        addictionProbability >= 40 -> "중간"
                        addictionProbability >= 20 -> "낮음"
                        else -> "매우 낮음"
                    }

                    Text(
                        text = "위험도: $riskLevel",
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
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "AI 응원 메시지",
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
                                    text = "응원 메시지를 생성하고 있습니다...",
                                    fontSize = 14.sp,
                                    textAlign = TextAlign.Center
                                )
                            }
                        } else {
                            // 생성 중이 아닌 경우 버튼 표시
                            Button(onClick = onGenerateMessage) {
                                Text("응원 메시지 받기")
                            }
                        }
                    } else {
                        Text(
                            text = encouragementMessage,
                            fontSize = 16.sp,
                            lineHeight = 24.sp,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // 도움말 텍스트
            Text(
                text = "디지털 사용 패턴을 분석하여 중독 확률을 계산했습니다. 건강한 디지털 생활을 위한 맞춤형 조언을 받아보세요.",
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

    // 응원 메시지 생성 함수
    private suspend fun generateEncouragementMessage(
        context: Context,
        probability: Int,
        onStart: () -> Unit,
        onComplete: (String) -> Unit,
        onError: (String) -> Unit
    ) {
        withContext(Dispatchers.IO) {
            onStart()

            try {
                // 현재 코루틴이 취소되었는지 확인
                ensureActive()

                // Gemma 모델 인스턴스 가져오기
                val model = InferenceModel.getInstance(context)

                // 확률에 따른 프롬프트 생성
                val prompt = when {
                    probability >= 80 -> "사용자의 디지털 중독 확률이 ${probability}%로 매우 높습니다. 걱정하지 마세요. 중독을 극복할 수 있는 방법과 함께 따뜻한 응원 메시지를 100자 이내로 작성해주세요."
                    probability >= 60 -> "사용자의 디지털 중독 확률이 ${probability}%로 높은 편입니다. 개선할 수 있는 작은 습관과 함께 응원 메시지를 100자 이내로 작성해주세요."
                    probability >= 40 -> "사용자의 디지털 중독 확률이 ${probability}%로 중간 정도입니다. 현재 상태를 유지하며 더 건강한 디지털 사용을 위한 팁과 함께 응원 메시지를 100자 이내로 작성해주세요."
                    probability >= 20 -> "사용자의 디지털 중독 확률이 ${probability}%로 낮은 편입니다. 건강한 디지털 사용 습관을 칭찬하고 이를 유지할 수 있는 팁과 함께 응원 메시지를 100자 이내로 작성해주세요."
                    else -> "사용자의 디지털 중독 확률이 ${probability}%로 매우 낮습니다. 아주 건강한 디지털 사용 습관을 가지고 있습니다. 이런 좋은 습관을 계속 유지할 수 있도록 격려하는 메시지를 100자 이내로 작성해주세요."
                }

                Log.d(AlertFragment.TAG, "Generating message with prompt: $prompt")

                // 응답 생성
                var message = ""
                val asyncFuture = model.generateResponseAsync(prompt) { partialResult, done ->
                    if (partialResult.isNotEmpty()) {
                        message += partialResult
                    }
                }

                // 취소 감지 설정
                try {
                    // 현재 코루틴이 취소되면 빠져나감
                    while (isActive) {
                        if (asyncFuture.isDone) {
                            break
                        }
                        delay(100) // 100ms마다 확인
                    }

                    // 코루틴이 취소되었으면 작업도 취소
                    if (!isActive) {
                        Log.d(AlertFragment.TAG, "Coroutine cancelled, cancelling message generation")
                        // asyncFuture는 직접 취소 메서드가 없을 수 있음 - 생략
                        throw CancellationException("Message generation cancelled")
                    }

                    // 정상적으로 완료된 경우
                    asyncFuture.get()

                    withContext(Dispatchers.Main) {
                        if (isActive) { // 여전히 활성 상태인 경우에만
                            Log.d(AlertFragment.TAG, "Generated message: $message")
                            onComplete(message.trim())
                        }
                    }
                } catch (e: CancellationException) {
                    Log.d(AlertFragment.TAG, "Message generation was cancelled")
                    throw e  // 코루틴 취소 처리를 위해 다시 throw
                }

            } catch (e: CancellationException) {
                Log.d(AlertFragment.TAG, "Message generation cancelled: ${e.message}")
                throw e  // 코루틴 취소 처리를 위해 다시 throw
            } catch (e: Exception) {
                Log.e(AlertFragment.TAG, "Error generating message: ${e.message}", e)
                if (isActive) { // 여전히 활성 상태인 경우에만
                    withContext(Dispatchers.Main) {
                        onError("메시지 생성 중 오류가 발생했습니다: ${e.message}")
                    }
                }
            }
        }
    }
}
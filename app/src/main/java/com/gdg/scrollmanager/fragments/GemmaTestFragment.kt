package com.gdg.scrollmanager.fragments

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import com.gdg.scrollmanager.R
import com.gdg.scrollmanager.gemma.ChatViewModel
import com.gdg.scrollmanager.gemma.InferenceModel
import android.content.Context
import android.util.Log
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class GemmaTestFragment : Fragment() {

    private val TAG = "GemmaTestFragment"

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
        
        val coroutineScope = rememberCoroutineScope()
        val context = LocalContext.current
        
        // 모델 파일이 완전하게 복사되었는지 확인하는 함수
        fun isModelFileCompletelyExists(context: Context): Boolean {
            val modelFile = File(context.filesDir, com.gdg.scrollmanager.gemma.Model.MODEL_FILENAME)
            if (!modelFile.exists()) {
                Log.d("MainScreen", "Model file doesn't exist in internal storage")
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
                
                Log.d("MainScreen", "Asset file size (using open+available): $assetFileSize bytes (${assetFileSize / (1024 * 1024)} MB)")
                Log.d("MainScreen", "Internal file size: $internalFileSize bytes (${internalFileSize / (1024 * 1024)} MB)")
                
                // 두 파일의 크기가 같은지 확인
                if (assetFileSize != internalFileSize) {
                    Log.w("MainScreen", "Model file exists but has different size: $internalFileSize bytes (expected: $assetFileSize bytes)")
                    Log.w("MainScreen", "Size difference: ${Math.abs(assetFileSize - internalFileSize)} bytes")
                    // 크기가 다르면 불완전한 파일로 간주하고 삭제
                    val deleted = modelFile.delete()
                    Log.d("MainScreen", "Deleted incorrect size model file: $deleted")
                    return false
                }
                
                Log.d("MainScreen", "Model file exists and has correct size: $internalFileSize bytes - validation PASSED ✓")
                return true
            } catch (e: Exception) {
                Log.e("MainScreen", "Error checking model file sizes: ${e.message}", e)
                // 안전을 위해 파일 삭제 시도
                try {
                    val deleted = modelFile.delete()
                    Log.d("MainScreen", "Attempted to delete file after error: $deleted")
                } catch (deleteException: Exception) {
                    Log.e("MainScreen", "Failed to delete file after error: ${deleteException.message}")
                }
                // 파일 크기 확인 중 오류 발생 시 안전하게 복사 필요로 판단
                return false
            }
        }
        
        // 모델 초기화가 필요할 경우 초기화 프로세스 시작하는 함수
        val startModelInitialization = {
            coroutineScope.launch(Dispatchers.IO) {
                try {
                    Log.d("MainScreen", "Starting model initialization in background")
                    // 모델 파일 존재 여부 확인 및 UI 업데이트
                    val modelExists = isModelFileCompletelyExists(context)
                    withContext(Dispatchers.Main) {
                        isModelFileCopyNeeded = !modelExists
                        if (modelExists) {
                            Log.d("MainScreen", "Model file already exists and is complete, showing simple loader")
                        } else {
                            Log.d("MainScreen", "Model file doesn't exist or is incomplete, showing detailed progress")
                        }
                    }
                    
                    // 모델 초기화 - 여기서 진행률이 업데이트됨
                    InferenceModel.getInstance(context)
                } catch (e: Exception) {
                    withContext(Dispatchers.Main) {
                        Log.e("MainScreen", "Model initialization failed: $e")
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
                    Log.d("MainScreen", "Model already initialized, skipping loading screen")
                    isModelInitialized = true
                    isLoading = false
                } else {
                    // 모델 파일 존재 여부 미리 확인
                    isModelFileCopyNeeded = !isModelFileCompletelyExists(context)
                    
                    // 없다면 진행률 Flow 구독 시작
                    // 글로벌 진행률 Flow 구독
                    Log.d("MainScreen", "Model needs initialization, starting progress monitoring")
                    Log.d("MainScreen", "Model file copy needed: $isModelFileCopyNeeded")
                    InferenceModel.initializationProgress.collect { progress ->
                        withContext(Dispatchers.Main) {
                            Log.d("MainScreen", "Received global progress update: $progress")
                            loadingProgress = progress
                            
                            // 초기화 완료 확인
                            if (progress >= 1.0f) {
                                try {
                                    // 초기화 완료 시 인스턴스 확인
                                    val model = InferenceModel.getInstance(context)
                                    isModelInitialized = true
                                    isLoading = false
                                    Log.d("MainScreen", "Model initialization completed")
                                } catch (e: Exception) {
                                    errorMessage = e.message
                                    isLoading = false
                                    Log.e("MainScreen", "Error accessing model: $e")
                                }
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e("MainScreen", "Error checking model initialization status: $e")
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
            Log.d("MainScreen", "Progress updated in UI: $loadingProgress")
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
                                    
                                    Log.d("MainScreen", "Cleared all cache before retry")
                                    
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
                SafeChatRoute()
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
                androidx.compose.material3.LinearProgressIndicator(
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
                
                androidx.compose.material3.Button(onClick = onRetry) {
                    Text(text = "다시 시도")
                }
            }
        }
    }

    @Composable
    fun SafeChatRoute() {
        val context = LocalContext.current
        val factory = ChatViewModel.getFactory(context)
        
        // ViewModel 생성
        val chatViewModel = viewModel<ChatViewModel>(factory = factory)
        
        com.gdg.scrollmanager.gemma.ChatScreen(
            uiState = chatViewModel.uiState.collectAsStateWithLifecycle().value,
            tokensRemaining = chatViewModel.tokensRemaining.collectAsStateWithLifecycle().value,
            isTextInputEnabled = chatViewModel.isTextInputEnabled.collectAsStateWithLifecycle().value,
            onSendMessage = { chatViewModel.sendMessage(it) },
            onUpdateRemainingTokens = { chatViewModel.recomputeSizeInTokens(it) }
        )
    }
}
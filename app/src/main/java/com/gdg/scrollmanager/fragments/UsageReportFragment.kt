package com.gdg.scrollmanager.fragments

import android.app.AppOpsManager
import android.content.Context
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
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
import com.gdg.scrollmanager.utils.DataStoreUtils
import com.gdg.scrollmanager.utils.UsageDataAggregator
import com.gdg.scrollmanager.utils.UsageStatsUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class UsageReportFragment : Fragment() {
    
    companion object {
        const val TAG = "UsageReportFragment"
        private const val REFRESH_INTERVAL_MS = 5000L // 5초마다 새로고침
    }
    
    // 핸들러와 러너블 정의
    private val handler = Handler(Looper.getMainLooper())
    private var refreshRunnable: Runnable? = null
    
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
                        ReportScreen()
                    }
                }
            }
        }
    }
    
    override fun onPause() {
        super.onPause()
        stopAutoRefresh()
    }
    
    override fun onResume() {
        super.onResume()
        refreshData() // 화면 다시 열릴 때 데이터 갱신
        startAutoRefresh() // 자동 새로고침 시작
    }
    
    override fun onDestroyView() {
        super.onDestroyView()
        stopAutoRefresh()
    }
    
    // 자동 새로고침 시작
    private fun startAutoRefresh() {
        stopAutoRefresh() // 기존 핸들러 중지
        
        refreshRunnable = object : Runnable {
            override fun run() {
                refreshData()
                handler.postDelayed(this, REFRESH_INTERVAL_MS)
            }
        }
        
        handler.post(refreshRunnable!!)
    }
    
    // 자동 새로고침 중지
    private fun stopAutoRefresh() {
        refreshRunnable?.let {
            handler.removeCallbacks(it)
            refreshRunnable = null
        }
    }
    
    // 데이터 새로고침
    private fun refreshData() {
        view?.let { view ->
            val composeView = view as? ComposeView
            composeView?.setContent {
                MaterialTheme {
                    Surface(
                        modifier = Modifier.fillMaxSize(),
                        color = MaterialTheme.colorScheme.background
                    ) {
                        ReportScreen()
                    }
                }
            }
        }
    }
    
    /**
     * 사용 리포트 데이터 클래스
     */
    data class UsageReportData(
        val screenTimeMinutes: Int = 0,
        val scrollPixels: Int = 0,
        val scrollRate: Float = 0f,
        val unlockCount: Int = 0,
        val appSwitchCount: Int = 0
    )
    
    @Composable
    fun ReportScreen() {
        val context = LocalContext.current
        val coroutineScope = rememberCoroutineScope()
        
        // 유저 데이터 상태
        var reportData by remember { mutableStateOf(UsageReportData()) }
        
        // 데이터 로드
        LaunchedEffect(Unit) {
            coroutineScope.launch {
                val data = loadUsageData(context)
                withContext(Dispatchers.Main) {
                    reportData = data
                }
            }
        }
        
        // 주기적인 데이터 새로고침
        DisposableEffect(Unit) {
            val refreshRunnable = object : Runnable {
                override fun run() {
                    coroutineScope.launch {
                        val data = loadUsageData(context)
                        reportData = data
                    }
                    handler.postDelayed(this, REFRESH_INTERVAL_MS)
                }
            }
            
            handler.post(refreshRunnable)
            
            onDispose {
                handler.removeCallbacks(refreshRunnable)
            }
        }
        
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Top
        ) {
            Spacer(modifier = Modifier.height(40.dp))
            
            // 제목
            Text(
                text = "Usage Alert",
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                color = Color(0xFF6AB9A3), // mint_text 색상
                modifier = Modifier.fillMaxWidth()
            )
            
            Spacer(modifier = Modifier.height(32.dp))
            
            // 첫 번째 행 (2개의 카드)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // 왼쪽 카드 (화면 사용 시간)
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(16.dp))
                        .background(Color(0xFFE6F7F0))
                        .padding(16.dp)
                ) {
                    Column {
                        Text(
                            text = "Screen Time",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Medium
                        )
                        
                        Spacer(modifier = Modifier.height(8.dp))
                        
                        Text(
                            text = "${reportData.screenTimeMinutes}",
                            fontSize = 32.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF6AB9A3)
                        )
                        
                        Spacer(modifier = Modifier.height(4.dp))
                        
                        Text(
                            text = "minutes",
                            fontSize = 14.sp,
                            color = Color.Gray
                        )
                    }
                }
                
                // 오른쪽 카드 (스크롤 레이트)
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(16.dp))
                        .background(Color(0xFFE6F0F7))
                        .padding(16.dp)
                ) {
                    Column {
                        Text(
                            text = "Scroll Rate",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Medium
                        )
                        
                        Spacer(modifier = Modifier.height(8.dp))
                        
                        Text(
                            text = "${reportData.scrollRate.toInt()}",
                            fontSize = 32.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF6AB9A3)
                        )
                        
                        Spacer(modifier = Modifier.height(4.dp))
                        
                        Text(
                            text = "px/sec",
                            fontSize = 14.sp,
                            color = Color.Gray
                        )
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(8.dp))
            
            // 두 번째 행 (2개의 카드)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // 왼쪽 카드 (화면 잠금해제 횟수)
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(16.dp))
                        .background(Color(0xFFF7E6F0))
                        .padding(16.dp)
                ) {
                    Column {
                        Text(
                            text = "Screen Unlocks",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Medium
                        )
                        
                        Spacer(modifier = Modifier.height(8.dp))
                        
                        Text(
                            text = "${reportData.unlockCount}",
                            fontSize = 32.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF6AB9A3)
                        )
                        
                        Spacer(modifier = Modifier.height(4.dp))
                        
                        Text(
                            text = "times",
                            fontSize = 14.sp,
                            color = Color.Gray
                        )
                    }
                }
                
                // 오른쪽 카드 (앱 전환 횟수)
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(16.dp))
                        .background(Color(0xFFF7F0E6))
                        .padding(16.dp)
                ) {
                    Column {
                        Text(
                            text = "App Switches",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Medium
                        )
                        
                        Spacer(modifier = Modifier.height(8.dp))
                        
                        Text(
                            text = "${reportData.appSwitchCount}",
                            fontSize = 32.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF6AB9A3)
                        )
                        
                        Spacer(modifier = Modifier.height(4.dp))
                        
                        Text(
                            text = "times",
                            fontSize = 14.sp,
                            color = Color.Gray
                        )
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(32.dp))
            
            // 정보 텍스트
            Text(
                text = "This data is collected over the last hour and is updated every 5 seconds.",
                fontSize = 14.sp,
                color = Color.Gray,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 16.dp)
            )
            
            Spacer(modifier = Modifier.height(32.dp))
        }
    }
    
    /**
     * 사용 데이터 로드 함수
     */
    private suspend fun loadUsageData(context: Context): UsageReportData {
        return withContext(Dispatchers.IO) {
            try {
                // 공통으로 60분(1시간) 데이터 사용
                val timeFrame = 60
                
                // 1. 총 화면 사용 시간 (분)
                val screenTimeMinutes = UsageStatsUtils.getTotalUsageTimeInMinutes(context, timeFrame)
                
                // 2. 스크롤 픽셀
                val scrollPixels = DataStoreUtils.getRecentScrollPixels(context)
                
                // 3. 스크롤 레이트 - UsageDataAggregator에서 모델 입력 가져오는 방법으로 변경
                // 이 방식은 PhoneUsagePredictor에서 사용하는 것과 동일합니다
                val modelInput = UsageDataAggregator.aggregateData()
                val scrollRate = modelInput.scrollRate
                
                // 스크롤 레이트 로그 출력 (디버깅용)
                Log.d(TAG, "ModelInput으로부터 스크롤 레이트: $scrollRate (PhoneUsagePredictor와 동일한 값)")
                
                // 4. 화면 잠금해제 횟수
                val unlockCount = UsageStatsUtils.getUnlockCount(context, timeFrame)
                
                // 5. 앱 전환 횟수
                val appSwitchCount = UsageStatsUtils.getAppSwitchCount(context, timeFrame)
                
                UsageReportData(
                    screenTimeMinutes = screenTimeMinutes,
                    scrollPixels = scrollPixels,
                    scrollRate = scrollRate,
                    unlockCount = unlockCount,
                    appSwitchCount = appSwitchCount
                )
            } catch (e: Exception) {
                Log.e(TAG, "Error loading usage data: ${e.message}")
                UsageReportData() // 오류 발생 시 기본값 반환
            }
        }
    }
}
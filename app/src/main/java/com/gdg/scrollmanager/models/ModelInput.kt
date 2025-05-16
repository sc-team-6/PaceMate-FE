package com.gdg.scrollmanager.models

/**
 * ONNX 모델 입력을 위한 데이터 클래스
 */
data class ModelInput(
    val screenSeconds: Int,           // 현재 5분 간격 화면 켜짐 시간 (초)
    val scrollPx: Int,                // 현재 5분 간격 스크롤 양 (픽셀)
    val unlocks: Int,                 // 현재 5분 간격 잠금 해제 횟수
    val appsUsed: Int,                // 현재 5분 간격 사용한 앱 개수
    val screenLast15m: Int,           // 최근 15분간 화면 켜짐 시간 (초)
    val screenLast30m: Int,           // 최근 30분간 화면 켜짐 시간 (초)
    val screenLast1h: Int,            // 최근 1시간 화면 켜짐 시간 (초)
    val unlocksPerMin: Float,         // 분당 잠금 해제 횟수
    val unlocksLast15m: Int,          // 최근 15분간 잠금 해제 횟수
    val scrollRate: Float,            // 초당 스크롤 픽셀 수
    val sinHour: Float,               // 시간의 사인값
    val cosHour: Float,               // 시간의 코사인값
    val sinMinute: Float,             // 분의 사인값 
    val cosMinute: Float,             // 분의 코사인값
    val appEmbedding: List<Float>     // 32차원 앱 임베딩
)
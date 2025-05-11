package com.gdg.scrollmanager.models

data class UsageReport(
    val usageTime15Min: Int,         // 최근 15분 총 사용 시간 (분)
    val usageTime30Min: Int,         // 최근 30분 총 사용 시간 (분)
    val usageTime60Min: Int,         // 최근 60분 총 사용 시간 (분)
    val unlockCount15Min: Int,       // 최근 15분 폰 잠금 해제 횟수
    val appSwitchCount15Min: Int,    // 최근 15분 앱 전환 횟수
    val mainAppCategory: String,     // 주 사용 앱 카테고리
    val socialAppCount: Int,         // SNS 앱 사용 횟수
    val averageSessionLength: Float, // 평균 세션 길이 (초)
    val dateTime: String,            // 날짜/시간
    val scrollDistance: Float,       // 스크롤 길이 (단위는 표시에 따라 변환)
    val appUsageList: List<AppUsageInfo> // 앱별 사용 정보
)
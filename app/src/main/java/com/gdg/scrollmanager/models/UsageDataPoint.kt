package com.gdg.scrollmanager.models

/**
 * 5초 간격으로 수집되는 사용 데이터 포인트
 */
data class UsageDataPoint(
    val timestamp: Long,               // 수집 시간 (밀리초)
    val screenTimeSeconds: Int,        // 화면 켜짐 시간 (5초 중에서)
    val scrollPixels: Int,             // 스크롤 양 (픽셀)
    val unlockCount: Int,              // 잠금 해제 횟수
    val appPackages: Set<String>       // 사용한 앱 패키지명
) {
    companion object {
        /**
         * 빈 데이터 포인트를 생성합니다.
         */
        fun empty(): UsageDataPoint {
            return UsageDataPoint(
                timestamp = System.currentTimeMillis(),
                screenTimeSeconds = 0,
                scrollPixels = 0,
                unlockCount = 0,
                appPackages = emptySet()
            )
        }
    }
}
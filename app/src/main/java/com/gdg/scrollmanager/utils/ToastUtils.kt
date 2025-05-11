package com.gdg.scrollmanager.utils

import android.content.Context
import android.widget.Toast

/**
 * Toast 메시지를 관리하는 유틸리티 클래스
 */
object ToastUtils {
    private var toast: Toast? = null

    /**
     * 이전에 표시된 토스트를 취소하고 새로운 토스트 메시지를 표시합니다.
     * 
     * @param context 컨텍스트
     * @param message 표시할 메시지
     * @param duration 표시 지속 시간 (Toast.LENGTH_SHORT 또는 Toast.LENGTH_LONG)
     */
    fun show(context: Context, message: String, duration: Int = Toast.LENGTH_SHORT) {
        // 이전 토스트가 있으면 취소
        toast?.cancel()
        
        // 새로운 토스트 생성 및 표시
        toast = Toast.makeText(context, message, duration)
        toast?.show()
    }
}

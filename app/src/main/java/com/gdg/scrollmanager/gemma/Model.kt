package com.gdg.scrollmanager.gemma

import com.google.mediapipe.tasks.genai.llminference.LlmInference.Backend

// 단일 모델만 사용 - GEMMA_3_1B_IT_INT4
data class Model(
    val path: String,
    val preferredBackend: Backend,
    val thinking: Boolean,
    val temperature: Float,
    val topK: Int,
    val topP: Float,
) {
    companion object {
        // 에셋에서 가져올 모델의 이름
        const val MODEL_FILENAME = "gemma3-1b-it-int4.task"
        
        // 미리 정의된 모델 인스턴스 (원래 GEMMA_3_1B_IT_INT4 설정과 동일)
        val DEFAULT = Model(
            path = MODEL_FILENAME, // 에셋 폴더에서 로드할 것이므로 파일명만 필요
            preferredBackend = Backend.CPU,
            thinking = false,
            temperature = 0.8f,
            topK = 64,
            topP = 0.95f
        )
    }
}
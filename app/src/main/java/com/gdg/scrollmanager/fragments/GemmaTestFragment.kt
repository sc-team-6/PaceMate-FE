package com.gdg.scrollmanager.fragments

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.fragment.app.Fragment
import com.gdg.scrollmanager.R

class GemmaTestFragment : Fragment() {

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        // 레이아웃 인플레이트
        return inflater.inflate(R.layout.fragment_gemma_test, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // UI 요소 초기화
        val testButton = view.findViewById<Button>(R.id.btn_run_gemma_test)
        val resultTextView = view.findViewById<TextView>(R.id.text_gemma_result)

        // 테스트 버튼 클릭 리스너 설정
        testButton.setOnClickListener {
            // Gemma 테스트 실행 (여기에서는 간단히 텍스트 변경만 수행)
            resultTextView.text = "Gemma 테스트가 실행되었습니다!\n이곳에 실제 Gemma AI 응답이 표시됩니다."
            
            // 실제 구현에서는 이곳에 Gemma API 호출 등의 테스트 로직을 추가할 수 있습니다.
        }
    }
}
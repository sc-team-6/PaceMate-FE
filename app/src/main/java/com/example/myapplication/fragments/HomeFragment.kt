package com.example.myapplication.fragments

import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.fragment.app.Fragment
import com.example.myapplication.databinding.FragmentHomeBinding
import com.example.myapplication.views.ArcProgressView
import java.text.SimpleDateFormat
import java.util.*

class HomeFragment : Fragment() {
    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!

    // ✅ 전역 percentage 변수
    private var percentageValue: Int = 80

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentHomeBinding.inflate(inflater, container, false)
        return binding.root as ConstraintLayout
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupUI()
    }

    private fun setupUI() {
        // 날짜 표시
        val dateFormat = SimpleDateFormat("EEEE, MMMM d, yyyy", Locale.US)
        binding.tvDate.text = dateFormat.format(Date())

        // 🔥 상태 텍스트 및 색상 결정
        val (statusText, colorHex) = when {
            percentageValue <= 33 -> "Cruising Mode" to "#76F376" // 초록
            percentageValue <= 66 -> "Warming Up" to "#FFDE58"     // 노랑
            else -> "Overrun Point" to "#C42727"                   // 빨강
        }

        // 텍스트 및 상태 색상 UI 반영
        binding.tvPercentage.text = "$percentageValue%"
        binding.tvStatus.text = statusText

        // ✅ 둥근 배경 생성 후 상태에 맞게 적용
        val statusBg = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = 100f
            setColor(Color.parseColor(colorHex))
        }
        binding.tvStatus.background = statusBg

        // 상단 배경 색 변경
        binding.yellowBackground.setBackgroundColor(Color.parseColor(colorHex))

        // ✅ ArcProgressView를 unionContainer 중심에 배치
        binding.unionContainer.post {
            val unionContainer = binding.unionContainer
            val overlayContainer = binding.arcOverlayContainer

            // unionContainer와 overlayContainer 위치 계산
            val unionLocation = IntArray(2)
            val overlayLocation = IntArray(2)
            unionContainer.getLocationOnScreen(unionLocation)
            overlayContainer.getLocationOnScreen(overlayLocation)

            val offsetX = (unionLocation[0] - overlayLocation[0]).toFloat()
            val offsetY = (unionLocation[1] - overlayLocation[1]).toFloat()

            val unionWidth = unionContainer.width.toFloat()
            val unionHeight = unionContainer.height.toFloat()
            val radius = unionWidth * 0.34f
            val diameter = (radius * 2).toInt()

            val centerX = offsetX + unionWidth / 2f
            val centerY = offsetY + unionHeight / 2f

            val arcView = ArcProgressView(requireContext()).apply {
                layoutParams = FrameLayout.LayoutParams(diameter, diameter)
                x = centerX - radius
                y = centerY - radius
                percentage = percentageValue
                z = 99f
            }

            overlayContainer.addView(arcView)

            Log.d("Debug", "Arc center = ($centerX, $centerY), radius = $radius")
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
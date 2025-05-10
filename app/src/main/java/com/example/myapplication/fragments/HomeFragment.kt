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
import androidx.core.graphics.toColorInt
import android.animation.ValueAnimator
import android.view.animation.DecelerateInterpolator

class HomeFragment : Fragment() {
    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!

    // ✅ 최종 퍼센트 값
    private var percentageValue: Int = 20

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
        val dateFormat = SimpleDateFormat("EEEE, MMMM d, yyyy", Locale.US)
        binding.tvDate.text = dateFormat.format(Date())

        // 🔥 상태 텍스트 및 색상 결정
        val (statusText, colorHex) = when {
            percentageValue <= 33 -> "Cruising Mode" to "#76F376" // 초록
            percentageValue <= 66 -> "Warming Up" to "#FFDE58"     // 노랑
            else -> "Overrun Point" to "#C42727"                   // 빨강
        }

        binding.tvPercentage.text = "$percentageValue%"
        binding.tvStatus.text = statusText

        // ✅ 상태 텍스트 배경 둥글게 설정
        val statusBg = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = 100f
            setColor(Color.parseColor(colorHex))
        }
        binding.tvStatus.background = statusBg

        binding.yellowBackground.setBackgroundColor(Color.parseColor(colorHex))

        // 텍스트 색상 설정
        val isOverrun = percentageValue >= 67
        val textColorHex = if (isOverrun) "#FFFFFF" else "#5E4510"
        val subtitleColorHex = if (isOverrun) "#F8F8F8" else "#9D8A70"
        val percentageColorHex = if (isOverrun) "#C42727" else "#000000"

        binding.tvDate.setTextColor(textColorHex.toColorInt())
        binding.tvOverrunTitle.setTextColor(textColorHex.toColorInt())
        binding.divider.setBackgroundColor(textColorHex.toColorInt())
        binding.tvOverrunSubtitle.setTextColor(subtitleColorHex.toColorInt())
        binding.tvPercentage.setTextColor(percentageColorHex.toColorInt())
        binding.tvMax.setTextColor(colorHex.toColorInt())

        // ✅ ArcProgressView 생성 및 부드러운 애니메이션
        binding.unionContainer.post {
            val unionContainer = binding.unionContainer
            val overlayContainer = binding.arcOverlayContainer

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
                z = 99f

                // ✅ 최종 색상 고정
                setFixedColor(colorHex)
            }

            overlayContainer.addView(arcView)

            // ✅ 부드럽게 차오르는 애니메이션
            ValueAnimator.ofFloat(0f, percentageValue.toFloat()).apply {
                duration = 1200L
                interpolator = DecelerateInterpolator()
                addUpdateListener {
                    arcView.percentage = (it.animatedValue as Float)
                }
                start()
            }

            Log.d("Debug", "Arc center = ($centerX, $centerY), radius = $radius")
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
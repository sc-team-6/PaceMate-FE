package com.example.myapplication.fragments

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.fragment.app.Fragment
import com.example.myapplication.R
import com.example.myapplication.databinding.FragmentHomeBinding
import com.example.myapplication.views.ArcProgressView
import java.text.SimpleDateFormat
import java.util.*

class HomeFragment : Fragment() {
    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentHomeBinding.inflate(inflater, container, false)
        // ConstraintLayout을 반환하도록 변경 (새로운 루트 요소 타입)
        val rootView = binding.root as ConstraintLayout
        return rootView
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupUI()
    }

    private fun setupUI() {
        // Set current date
        val dateFormat = SimpleDateFormat("EEEE, MMMM d, yyyy", Locale.US)
        val currentDate = dateFormat.format(Date())
        binding.tvDate.text = currentDate

        // unionContainer와 overlayContainer 모두 준비된 후 실행
        binding.unionContainer.post {
            val unionContainer = binding.unionContainer
            val overlayContainer = binding.arcOverlayContainer

            // unionContainer의 화면상 위치 계산
            val unionLocation = IntArray(2)
            val overlayLocation = IntArray(2)
            unionContainer.getLocationOnScreen(unionLocation)
            overlayContainer.getLocationOnScreen(overlayLocation)

            val offsetX = (unionLocation[0] - overlayLocation[0]).toFloat()
            val offsetY = (unionLocation[1] - overlayLocation[1]).toFloat()

            // unionContainer 기준 중심 좌표 및 크기 계산
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
                percentage = 60
                z = 99f
            }

            overlayContainer.addView(arcView)

            Log.d("Debug", "union center = ($centerX, $centerY), radius = $radius")
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
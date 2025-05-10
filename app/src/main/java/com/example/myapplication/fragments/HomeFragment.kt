package com.example.myapplication.fragments

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.fragment.app.Fragment
import com.example.myapplication.R
import com.example.myapplication.databinding.FragmentHomeBinding
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

        // 중심 원 추가
        binding.unionContainer.post {
            val unionContainer = binding.unionContainer

            val unionWidth = unionContainer.width.toFloat()
            val unionHeight = unionContainer.height.toFloat()
            val radius = unionWidth * 0.28f
            val diameter = (radius * 2).toInt()

            val centerX = unionWidth / 2f
            val centerY = unionHeight / 2f

            val overlay = View(requireContext()).apply {
                layoutParams = ViewGroup.LayoutParams(diameter, diameter)
                x = centerX - radius
                y = centerY - radius
                background = requireContext().getDrawable(R.drawable.black_stroke_circle)
                z = 99f
            }

            unionContainer.addView(overlay)

            Log.d("Debug", "unionWidth = $unionWidth, unionHeight = $unionHeight")
            Log.d("Debug", "centerX = $centerX, centerY = $centerY")
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
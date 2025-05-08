package com.example.myapplication.fragments

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.fragment.app.Fragment
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

        // 다른 UI 설정은 XML에서 이미 설정되어 있으므로 필요한 경우에만 코드로 업데이트
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
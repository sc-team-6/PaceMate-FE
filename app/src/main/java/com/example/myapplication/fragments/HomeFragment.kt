package com.example.myapplication.fragments

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
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
        return binding.root
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

        // Set circular progress
        binding.circularProgress.setProgress(55)
        
        // Set status text based on progress
        updateStatusText(55)
        
        // Set min, max, and alert levels
        binding.tvMin.text = "30%"
        binding.tvAlertLevel.text = "80%"
        binding.tvMax.text = "55%"
        
        // Set advice text
        binding.tvAdvice.text = "Things are speeding up - slow down and take a breath."
        
        // Set highest value
        binding.tvHighestValue.text = "55%"
        
        // Set usage stats
        binding.tvTotalTime.text = "00h 52min"
        binding.tvLowTime.text = "00h 52min"
        binding.tvScroll.text = "1m"
        
        // Set drop-down click listener
        binding.ivDropdown.setOnClickListener {
            toggleAlertSection()
        }
    }
    
    private fun updateStatusText(progress: Int) {
        val statusText = when {
            progress < 30 -> "Calm"
            progress < 60 -> "Warming Up"
            progress < 80 -> "Getting Hot"
            else -> "Overheated"
        }
        binding.tvStatus.text = statusText
    }
    
    private fun toggleAlertSection() {
        val isVisible = binding.cardYoutube.visibility == View.VISIBLE
        val newVisibility = if (isVisible) View.GONE else View.VISIBLE
        
        binding.cardYoutube.visibility = newVisibility
        binding.cardUnhealthy.visibility = newVisibility
        
        // Rotate dropdown icon
        binding.ivDropdown.rotation = if (isVisible) 0f else 180f
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
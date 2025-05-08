package com.example.myapplication.fragments

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.example.myapplication.R
import com.example.myapplication.databinding.FragmentScreenStatusBinding

class ScreenStatusFragment : Fragment() {
    private var _binding: FragmentScreenStatusBinding? = null
    private val binding get() = _binding!!
    
    private var screenOnCount = 0
    private var screenOffCount = 0
    private var isScreenOn = true
    
    private val screenReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                Intent.ACTION_SCREEN_ON -> {
                    screenOnCount++
                    isScreenOn = true
                    updateUI()
                }
                Intent.ACTION_SCREEN_OFF -> {
                    screenOffCount++
                    isScreenOn = false
                    updateUI()
                }
            }
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentScreenStatusBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        updateUI()
    }

    override fun onResume() {
        super.onResume()
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_ON)
            addAction(Intent.ACTION_SCREEN_OFF)
        }
        requireContext().registerReceiver(screenReceiver, filter)
    }

    override fun onPause() {
        super.onPause()
        requireContext().unregisterReceiver(screenReceiver)
    }

    private fun updateUI() {
        binding.currentStatusText.text = if (isScreenOn) "화면 켜짐" else "화면 꺼짐"
        binding.currentStatusText.setTextColor(
            if (isScreenOn) {
                requireContext().getColor(R.color.colorPrimary)
            } else {
                requireContext().getColor(R.color.colorError)
            }
        )
        binding.screenOnCountText.text = screenOnCount.toString()
        binding.screenOffCountText.text = screenOffCount.toString()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
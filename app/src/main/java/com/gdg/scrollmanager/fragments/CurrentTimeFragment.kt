package com.gdg.scrollmanager.fragments

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.gdg.scrollmanager.databinding.FragmentCurrentTimeBinding
import java.text.SimpleDateFormat
import java.util.*

class CurrentTimeFragment : Fragment() {
    private var _binding: FragmentCurrentTimeBinding? = null
    private val binding get() = _binding!!
    
    private val handler = Handler(Looper.getMainLooper())
    private val timeUpdateRunnable = object : Runnable {
        override fun run() {
            updateCurrentTime()
            handler.postDelayed(this, 1000)
        }
    }
    
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentCurrentTimeBinding.inflate(inflater, container, false)
        return binding.root
    }
    
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        updateCurrentTime()
    }
    
    override fun onResume() {
        super.onResume()
        handler.post(timeUpdateRunnable)
    }
    
    override fun onPause() {
        super.onPause()
        handler.removeCallbacks(timeUpdateRunnable)
    }
    
    private fun updateCurrentTime() {
        val calendar = Calendar.getInstance()
        val timeFormat = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
        val dateFormat = SimpleDateFormat("yyyy년 MM월 dd일 (E)", Locale.getDefault())
        
        binding.timeText.text = timeFormat.format(calendar.time)
        binding.dateText.text = dateFormat.format(calendar.time)
        
        // 시간대 정보
        val timeZone = TimeZone.getDefault()
        binding.timezoneText.text = timeZone.displayName
        
        // UTC 오프셋
        val offset = timeZone.rawOffset / (1000 * 60 * 60)
        binding.utcOffsetText.text = "UTC${if(offset >= 0) "+" else ""}$offset"
    }
    
    override fun onDestroyView() {
        super.onDestroyView()
        handler.removeCallbacks(timeUpdateRunnable)
        _binding = null
    }
}
package com.gdg.scrollmanager.fragments

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.RadioGroup
import com.gdg.scrollmanager.utils.ToastUtils
import androidx.fragment.app.Fragment
import com.gdg.scrollmanager.OnboardingActivity
import com.gdg.scrollmanager.R

class OnboardingScreenTimeFragment : Fragment() {
    private lateinit var btnNext: Button
    private lateinit var btnLater: Button
    private lateinit var radioGroup: RadioGroup
    private lateinit var radioGroup2: RadioGroup
    private lateinit var radioGroup3: RadioGroup
    private lateinit var customTimeLayout: LinearLayout
    private lateinit var editCustomHours: EditText
    
    private var selectedScreenTime: String? = null
    private var selectedReduceUsage: String? = null
    private var selectedReduceAmount: String? = null
    private var customScreenTimeHours: Float? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_onboarding_screen_time, container, false)
        btnNext = view.findViewById(R.id.btnNext)
        btnLater = view.findViewById(R.id.btnLater)
        radioGroup = view.findViewById(R.id.radioGroup)
        radioGroup2 = view.findViewById(R.id.radioGroup2)
        radioGroup3 = view.findViewById(R.id.radioGroup3)
        customTimeLayout = view.findViewById(R.id.customTimeLayout)
        editCustomHours = view.findViewById(R.id.editCustomHours)
        return view
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        setupOptionListeners()
        
        // 초기 상태에서 Next 버튼 활성화 (토스트 메시지가 보이도록)
        btnNext.isEnabled = true
        
        btnNext.setOnClickListener {
            // 모든 항목이 선택되었을 때만 다음으로 진행
            if (allOptionsSelected()) {
                saveSelections()
                (activity as? OnboardingActivity)?.goToNextPage()
            } else {
                showSelectionRequiredMessage()
            }
        }
        
        btnLater.setOnClickListener {
            // "Prev" 버튼은 이전 화면으로 돌아가기
            (activity as? OnboardingActivity)?.goToPreviousPage()
        }
        
        // 이전에 설정한 값들이 있으면 로드
        loadSavedSelections()
    }

    private fun setupOptionListeners() {
        radioGroup.setOnCheckedChangeListener { _, checkedId ->
            when (checkedId) {
                R.id.radioOption1 -> {
                    selectedScreenTime = "less_than_2"
                    customTimeLayout.visibility = View.GONE
                    customScreenTimeHours = null
                }
                R.id.radioOption2 -> {
                    selectedScreenTime = "3_to_4"
                    customTimeLayout.visibility = View.GONE
                    customScreenTimeHours = null
                }
                R.id.radioOption3 -> {
                    selectedScreenTime = "5_to_7"
                    customTimeLayout.visibility = View.GONE
                    customScreenTimeHours = null
                }
                R.id.radioOption4 -> {
                    selectedScreenTime = "more_than_8"
                    customTimeLayout.visibility = View.GONE
                    customScreenTimeHours = null
                }
                R.id.radioOptionCustom -> {
                    selectedScreenTime = "custom"
                    customTimeLayout.visibility = View.VISIBLE
                    // 현재 입력된 값이 있으면 저장
                    updateCustomTimeValue()
                }
            }
            checkAllOptionsSelected()
        }
        
        // 커스텀 시간 입력 변경 감지
        editCustomHours.setOnFocusChangeListener { _, hasFocus ->
            if (!hasFocus) {
                updateCustomTimeValue()
            }
        }
        
        radioGroup2.setOnCheckedChangeListener { _, checkedId ->
            when (checkedId) {
                R.id.radioOption5 -> selectedReduceUsage = "social_media"
                R.id.radioOption6 -> selectedReduceUsage = "media_content"
                R.id.radioOption7 -> selectedReduceUsage = "games"
            }
            checkAllOptionsSelected()
        }
        
        radioGroup3.setOnCheckedChangeListener { _, checkedId ->
            when (checkedId) {
                R.id.radioOption8 -> selectedReduceAmount = "a_lot"
                R.id.radioOption9 -> selectedReduceAmount = "a_little"
                R.id.radioOption10 -> selectedReduceAmount = "keep_current"
            }
            checkAllOptionsSelected()
        }
    }
    
    private fun updateCustomTimeValue() {
        val timeText = editCustomHours.text.toString()
        if (timeText.isNotEmpty()) {
            try {
                customScreenTimeHours = timeText.toFloat()
            } catch (e: NumberFormatException) {
                customScreenTimeHours = null
            }
        } else {
            customScreenTimeHours = null
        }
    }
    
    // 모든 항목이 선택되었는지 확인하고 Next 버튼 상태 업데이트
    private fun checkAllOptionsSelected() {
        // 버튼 색상은 항상 활성 상태로 유지하되 내부적으로 진행 가능 여부 판단
        btnNext.isEnabled = true
    }
    
    private fun allOptionsSelected(): Boolean {
        // 커스텀 시간 선택 시 입력값이 있어야 함
        val screenTimeValid = if (selectedScreenTime == "custom") {
            customScreenTimeHours != null && customScreenTimeHours!! > 0
        } else {
            selectedScreenTime != null
        }
        
        return screenTimeValid && 
               selectedReduceUsage != null && 
               selectedReduceAmount != null
    }
    
    private fun showSelectionRequiredMessage() {
        val message = if (selectedScreenTime == "custom" && (customScreenTimeHours == null || customScreenTimeHours!! <= 0)) {
            "Please enter a valid value for custom screen time"
        } else {
            "Please select an option for all three questions to continue"
        }
        
        activity?.let {
            ToastUtils.show(it, message)
        }
    }

    private fun saveSelections() {
        val sharedPref = requireActivity().getSharedPreferences("MyAppPrefs", 0)
        with(sharedPref.edit()) {
            selectedScreenTime?.let { putString("screen_time", it) }
            selectedReduceUsage?.let { putString("reduce_usage", it) }
            selectedReduceAmount?.let { putString("reduce_amount", it) }
            
            // 커스텀 시간 저장
            if (selectedScreenTime == "custom" && customScreenTimeHours != null) {
                putFloat("custom_screen_time_hours", customScreenTimeHours!!)
            } else {
                remove("custom_screen_time_hours")
            }
            
            apply()
        }
    }
    
    private fun loadSavedSelections() {
        val sharedPref = requireActivity().getSharedPreferences("MyAppPrefs", 0)
        
        // 저장된 값 불러오기
        val savedScreenTime = sharedPref.getString("screen_time", null)
        val savedReduceUsage = sharedPref.getString("reduce_usage", null)
        val savedReduceAmount = sharedPref.getString("reduce_amount", null)
        val savedCustomHours = if (sharedPref.contains("custom_screen_time_hours")) {
            sharedPref.getFloat("custom_screen_time_hours", 0f)
        } else null
        
        // 스크린 타임 설정
        if (savedScreenTime != null) {
            selectedScreenTime = savedScreenTime
            when (savedScreenTime) {
                "less_than_2" -> radioGroup.check(R.id.radioOption1)
                "3_to_4" -> radioGroup.check(R.id.radioOption2)
                "5_to_7" -> radioGroup.check(R.id.radioOption3)
                "more_than_8" -> radioGroup.check(R.id.radioOption4)
                "custom" -> {
                    radioGroup.check(R.id.radioOptionCustom)
                    customTimeLayout.visibility = View.VISIBLE
                    savedCustomHours?.let {
                        editCustomHours.setText(it.toString())
                        customScreenTimeHours = it
                    }
                }
            }
        }
        
        // 감소하고 싶은 사용 유형 설정
        if (savedReduceUsage != null) {
            selectedReduceUsage = savedReduceUsage
            when (savedReduceUsage) {
                "social_media" -> radioGroup2.check(R.id.radioOption5)
                "media_content" -> radioGroup2.check(R.id.radioOption6)
                "games" -> radioGroup2.check(R.id.radioOption7)
            }
        }
        
        // 감소량 설정
        if (savedReduceAmount != null) {
            selectedReduceAmount = savedReduceAmount
            when (savedReduceAmount) {
                "a_lot" -> radioGroup3.check(R.id.radioOption8)
                "a_little" -> radioGroup3.check(R.id.radioOption9)
                "keep_current" -> radioGroup3.check(R.id.radioOption10)
            }
        }
    }
}
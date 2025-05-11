package com.gdg.scrollmanager.fragments

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
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
    private var selectedScreenTime: String? = null
    private var selectedReduceUsage: String? = null
    private var selectedReduceAmount: String? = null

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
    }

    private fun setupOptionListeners() {
        radioGroup.setOnCheckedChangeListener { _, checkedId ->
            when (checkedId) {
                R.id.radioOption1 -> selectedScreenTime = "less_than_2"
                R.id.radioOption2 -> selectedScreenTime = "3_to_4"
                R.id.radioOption3 -> selectedScreenTime = "5_to_7"
                R.id.radioOption4 -> selectedScreenTime = "more_than_8"
            }
            checkAllOptionsSelected()
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
    
    // 모든 항목이 선택되었는지 확인하고 Next 버튼 상태 업데이트
    private fun checkAllOptionsSelected() {
        // 버튼 색상은 항상 활성 상태로 유지하되 내부적으로 진행 가능 여부 판단
        btnNext.isEnabled = true
    }
    
    private fun allOptionsSelected(): Boolean {
        return selectedScreenTime != null && 
               selectedReduceUsage != null && 
               selectedReduceAmount != null
    }
    
    private fun showSelectionRequiredMessage() {
        val message = "Please select an option for all three questions to continue"
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
            apply()
        }
    }
}
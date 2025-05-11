package com.gdg.scrollmanager

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import com.gdg.scrollmanager.R
import com.gdg.scrollmanager.fragments.GemmaTestFragment
import com.gdg.scrollmanager.utils.ToastUtils

class SettingsFragment : Fragment() {

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val root = inflater.inflate(R.layout.fragment_settings, container, false)
        
        val onboardingButton = root.findViewById<Button>(R.id.btn_view_onboarding)
        onboardingButton.setOnClickListener {
            // 온보딩 페이지로 이동
            val intent = Intent(activity, OnboardingActivity::class.java)
            intent.putExtra("FROM_SETTINGS", true)  // 설정에서 접근했음을 표시
            startActivity(intent)
        }
        
        // 권한 설정 페이지 버튼
        val permissionsButton = root.findViewById<Button>(R.id.btn_permissions_settings)
        permissionsButton.setOnClickListener {
            showPermissionsResetDialog()
        }
        
        val gemmaTestButton = root.findViewById<Button>(R.id.btn_gemma_test)
        gemmaTestButton.setOnClickListener {
            // Gemma 테스트 프래그먼트로 이동
            val fragmentManager = parentFragmentManager
            val transaction = fragmentManager.beginTransaction()
            transaction.replace(R.id.fragment_container, GemmaTestFragment())
            transaction.addToBackStack(null)  // 백 스택에 추가하여 뒤로가기 가능하게 함
            transaction.commit()
        }
        
        return root
    }
    
    /**
     * 권한 설정 리셋 다이얼로그 표시
     */
    private fun showPermissionsResetDialog() {
        context?.let { ctx ->
            AlertDialog.Builder(ctx)
                .setTitle("Reset Permissions Settings")
                .setMessage("This will reset your permissions settings and disable debug mode. The app will restart to apply these changes.")
                .setPositiveButton("Reset") { _, _ ->
                    // 디버그 모드 해제 및 권한 상태 리셋
                    resetPermissionsSettings()
                    
                    // 권한 설정 페이지로 이동
                    val intent = Intent(activity, PermissionsActivity::class.java)
                    intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK)
                    startActivity(intent)
                    
                    // 현재 액티비티 종료
                    activity?.finish()
                }
                .setNegativeButton("Cancel", null)
                .show()
        }
    }
    
    /**
     * 권한 설정 및 디버그 모드 리셋
     */
    private fun resetPermissionsSettings() {
        context?.let { ctx ->
            // SharedPreferences에서 관련 설정 제거
            val sharedPref = ctx.getSharedPreferences("MyAppPrefs", 0)
            with(sharedPref.edit()) {
                remove("permissionsGranted")
                remove("debugMode")
                apply()
            }
            
            ToastUtils.show(ctx, "Permissions settings reset")
        }
    }
}
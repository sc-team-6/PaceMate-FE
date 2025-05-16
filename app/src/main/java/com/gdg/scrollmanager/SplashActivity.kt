package com.gdg.scrollmanager

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.animation.AlphaAnimation
import android.view.animation.Animation
import android.view.animation.AnimationSet
import android.view.animation.ScaleAnimation
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

class SplashActivity : AppCompatActivity() {
    
    private val SPLASH_DELAY = 2000L // 2초 동안 스플래시 화면 표시
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // 액션바 숨기기
        supportActionBar?.hide()
        
        // 시스템 UI(상태바, 네비게이션 바) 숨기기
        hideSystemUI()
        
        setContentView(R.layout.activity_splash)
        
        // 로고와 앱 이름 애니메이션 적용
        animateSplashElements()
        
        // 일정 시간 후 메인 액티비티로 전환
        Handler(Looper.getMainLooper()).postDelayed({
            // 메인 액티비티로 이동
            val intent = Intent(this, MainActivity::class.java)
            startActivity(intent)
            
            // 화면 전환 애니메이션 적용
            overridePendingTransition(R.anim.fade_in, R.anim.fade_out)
            
            // 스플래시 액티비티 종료
            finish()
        }, SPLASH_DELAY)
    }
    
    private fun animateSplashElements() {
        val logoImage = findViewById<ImageView>(R.id.splash_logo)
        
        // 로고 애니메이션 설정 (페이드인 + 살짝 확대)
        val logoAnimSet = AnimationSet(true).apply {
            // 페이드인 애니메이션
            val fadeIn = AlphaAnimation(0f, 1f).apply {
                duration = 800
                fillAfter = true
            }
            
            // 확대 애니메이션 (0.8 -> 1.0)
            val scale = ScaleAnimation(
                0.8f, 1.0f, 0.8f, 1.0f,
                Animation.RELATIVE_TO_SELF, 0.5f,
                Animation.RELATIVE_TO_SELF, 0.5f
            ).apply {
                duration = 800
                fillAfter = true
            }
            
            addAnimation(fadeIn)
            addAnimation(scale)
        }
        
        // 앱 이름 애니메이션 설정 (페이드인, 딜레이 있음)
        val nameAnim = AlphaAnimation(0f, 1f).apply {
            duration = 800
            startOffset = 300 // 로고 애니메이션 후 조금 딜레이
            fillAfter = true
        }
        
        // 애니메이션 시작
        logoImage.startAnimation(logoAnimSet)
    }
    
    private fun hideSystemUI() {
        // 안드로이드 버전에 따라 다른 방식으로 시스템 UI 숨기기
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            // Android 11 이상
            window.setDecorFitsSystemWindows(false)
            window.decorView.windowInsetsController?.let { controller ->
                // 상태바와 네비게이션 바 숨기기
                controller.hide(android.view.WindowInsets.Type.systemBars())
                // 사용자 상호작용 시 시스템 바가 다시 나타나지 않도록 설정
                controller.systemBarsBehavior = android.view.WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            }
        } else {
            // Android 10 이하
            @Suppress("DEPRECATION")
            window.decorView.systemUiVisibility = (
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                or View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                or View.SYSTEM_UI_FLAG_FULLSCREEN
            )
        }
    }
    
    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) {
            hideSystemUI()
        }
    }
}

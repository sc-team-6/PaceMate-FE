package com.gdg.scrollmanager

import android.content.Intent
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.view.WindowManager
import android.view.animation.AlphaAnimation
import android.view.animation.Animation
import android.view.animation.AnimationSet
import android.view.animation.ScaleAnimation
import android.widget.ImageView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat

class SplashActivity : AppCompatActivity() {
    
    private val SPLASH_DELAY = 2000L // 2초 동안 스플래시 화면 표시
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // 이 부분을 추가하여 액션바와 시스템 바를 숨기기 위한 플래그 설정
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            window.attributes.layoutInDisplayCutoutMode = 
                WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
        }
        
        // 액션바 숨기기
        supportActionBar?.hide()
        
        // 상태바, 네비게이션 바 투명하게 설정 및 전체 화면 모드
        makeFullScreen()
        
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
    
    private fun makeFullScreen() {
        // 상태바, 네비게이션 바 색상 투명하게 설정
        window.statusBarColor = Color.TRANSPARENT
        window.navigationBarColor = Color.TRANSPARENT
        
        // 시스템 장식(데코) 레이아웃이 콘텐츠 영역을 고려하지 않도록 설정
        WindowCompat.setDecorFitsSystemWindows(window, false)
        
        // 시스템 바를 숨기는 최신 방식
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            // Android 11+ 방식
            window.insetsController?.let { controller ->
                controller.hide(WindowInsets.Type.statusBars() or WindowInsets.Type.navigationBars())
                controller.systemBarsBehavior = WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            }
        } else {
            // Android 10 이하 방식 (레거시)
            @Suppress("DEPRECATION")
            window.setFlags(
                WindowManager.LayoutParams.FLAG_FULLSCREEN,
                WindowManager.LayoutParams.FLAG_FULLSCREEN
            )
            
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
        
        // 애니메이션 시작
        logoImage.startAnimation(logoAnimSet)
    }
    
    // 앱이 포커스를 얻을 때마다 전체 화면 상태 유지
    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) {
            makeFullScreen()
        }
    }
}

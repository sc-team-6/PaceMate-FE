package com.example.myapplication.views

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View
import kotlin.math.min

/**
 * 원호 형태의 프로그레스를 표시하는 커스텀 뷰
 * 퍼센트 값(0-100)에 따라 호의 범위가 변경됩니다.
 * 두 개의 호를 그리며, 두 호는 정확히 같은 위치에 겹쳐서 표시됩니다:
 * 1. 배경 호(연한 회색) - 항상 전체 범위로 표시됨
 * 2. 프로그레스 호(빨간색) - 퍼센트에 따라 채워짐
 */
class ArcProgressView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    // 배경 호를 그리기 위한 Paint 객체
    private val backgroundArcPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 14f  // 호의 두께
        color = Color.parseColor("#F2F2F2")  // 매우 연한 회색
        strokeCap = Paint.Cap.ROUND  // 호의 끝을 둥글게
    }
    
    // 진행 호를 그리기 위한 Paint 객체
    private val progressArcPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 14f  // 호의 두께 (배경 호와 동일하게)
        color = Color.parseColor("#D9433E")  // 빨간색 계열의 호
        strokeCap = Paint.Cap.ROUND  // 호의 끝을 둥글게
    }

    // 호가 그려질 영역 (두 호 모두 같은 영역에 그려짐)
    private val arcRectF = RectF()
    
    // 현재 퍼센트 값 (0-100)
    var percentage: Int = 0
        set(value) {
            field = value.coerceIn(0, 100)  // 0에서 100 사이로 제한
            invalidate()  // 뷰 다시 그리기
        }
    
    // 호의 각도 관련 설정
    private val startAngle = -225f  // 오른쪽 상단에서 시작
    private val sweepAngle = 270f  // 원의 3/4 (270도)

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val width = MeasureSpec.getSize(widthMeasureSpec)
        val height = MeasureSpec.getSize(heightMeasureSpec)
        
        // 전체 높이에 맞게 설정 (너비는 측정된 값 그대로 사용)
        setMeasuredDimension(width, height)
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        
        // 패딩과 스트로크 너비를 고려하여 호의 영역 계산
        val strokeWidth = progressArcPaint.strokeWidth
        val padding = strokeWidth / 2
        
        // 호의 크기 (너비의 90%로 설정)
        val arcSize = min(width, height) * 0.8f
        
        // 호의 영역 계산 (뷰 중앙에 위치)
        arcRectF.set(
            (width - arcSize) / 2f,
            (height - arcSize) / 2f,
            (width + arcSize) / 2f,
            (height + arcSize) / 2f
        )
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        
        // 배경 호: 항상 전체가 그려지는 연한 회색 호
        canvas.drawArc(arcRectF, startAngle, sweepAngle, false, backgroundArcPaint)
        
        // 퍼센트에 따른 호의 스윕 각도 계산
        val progressSweepAngle = sweepAngle * (percentage / 100f)
        
        // 프로그레스 호: 진행 상태를 나타내는 빨간색 호 (퍼센트에 따라 채워짐)
        canvas.drawArc(arcRectF, startAngle, progressSweepAngle, false, progressArcPaint)
    }
}
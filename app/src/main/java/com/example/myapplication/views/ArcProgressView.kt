package com.example.myapplication.views

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View
import kotlin.math.min

class ArcProgressView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    // 배경 호를 그리기 위한 Paint 객체
    private val backgroundArcPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 20f
        color = Color.parseColor("#F2F2F2") // 연한 회색
        strokeCap = Paint.Cap.ROUND
    }

    // 진행 호를 그리기 위한 Paint 객체
    private val progressArcPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 20f
        color = Color.parseColor("#76F376") // 초기 초록
        strokeCap = Paint.Cap.ROUND
    }

    private val arcRectF = RectF()

    // 현재 퍼센트 값 (0 ~ 100)
    var percentage: Int = 0
        set(value) {
            field = value.coerceIn(0, 100)
            updateProgressColor(field)
            invalidate()
        }

    // 진행률에 따른 색상 자동 설정
    private fun updateProgressColor(value: Int) {
        progressArcPaint.color = when {
            value <= 33 -> Color.parseColor("#76F376") // 초록
            value <= 66 -> Color.parseColor("#FFDE58") // 노랑
            else -> Color.parseColor("#C42727")        // 빨강
        }
    }

    private val startAngle = -225f
    private val sweepAngle = 270f

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val width = MeasureSpec.getSize(widthMeasureSpec)
        val height = MeasureSpec.getSize(heightMeasureSpec)
        setMeasuredDimension(width, height)
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)

        val strokeWidth = progressArcPaint.strokeWidth
        val padding = strokeWidth / 2f
        val arcSize = min(width, height) * 0.8f

        arcRectF.set(
            (width - arcSize) / 2f,
            (height - arcSize) / 2f,
            (width + arcSize) / 2f,
            (height + arcSize) / 2f
        )
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        // 배경 호
        canvas.drawArc(arcRectF, startAngle, sweepAngle, false, backgroundArcPaint)

        // 진행 호
        val progressSweepAngle = sweepAngle * (percentage / 100f)
        canvas.drawArc(arcRectF, startAngle, progressSweepAngle, false, progressArcPaint)
    }
}
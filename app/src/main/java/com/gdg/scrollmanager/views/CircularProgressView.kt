package com.gdg.scrollmanager.views

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View

class CircularProgressView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val progressPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 40f
        strokeCap = Paint.Cap.ROUND
        color = Color.parseColor("#FFCC00")
    }

    private val backgroundPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 40f
        color = Color.parseColor("#EEEEEE")
    }

    private val progressRect = RectF()
    
    private var progress = 55
    private val startAngle = 135f
    private val sweepAngle = 270f

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        val padding = progressPaint.strokeWidth / 2
        progressRect.set(
            padding,
            padding,
            width - padding,
            height - padding
        )
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        // Draw background arc
        canvas.drawArc(
            progressRect,
            startAngle,
            sweepAngle,
            false,
            backgroundPaint
        )

        // Draw progress arc
        val progressSweepAngle = sweepAngle * progress / 100
        canvas.drawArc(
            progressRect,
            startAngle,
            progressSweepAngle,
            false,
            progressPaint
        )
    }

    fun setProgress(progress: Int) {
        this.progress = progress
        invalidate()
    }

    fun getProgress(): Int {
        return progress
    }
}
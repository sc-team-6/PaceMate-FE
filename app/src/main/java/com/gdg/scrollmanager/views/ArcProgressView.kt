package com.gdg.scrollmanager.views

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

    private val backgroundArcPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 20f
        color = Color.parseColor("#F2F2F2")
        strokeCap = Paint.Cap.ROUND
    }

    private val progressArcPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 20f
        color = Color.parseColor("#76F376") // default green
        strokeCap = Paint.Cap.ROUND
    }

    private val arcRectF = RectF()

    var percentage: Float = 0f
        set(value) {
            field = value.coerceIn(0f, 100f)
            invalidate()
        }

    private val startAngle = -225f
    private val sweepAngle = 270f

    private var fixedColor: Int? = null

    fun setFixedColor(hex: String) {
        fixedColor = Color.parseColor(hex)
        progressArcPaint.color = fixedColor!!
        invalidate()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val width = MeasureSpec.getSize(widthMeasureSpec)
        val height = MeasureSpec.getSize(heightMeasureSpec)
        setMeasuredDimension(width, height)
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)

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
        canvas.drawArc(arcRectF, startAngle, sweepAngle, false, backgroundArcPaint)

        val progressSweepAngle = sweepAngle * (percentage / 100f)
        canvas.drawArc(arcRectF, startAngle, progressSweepAngle, false, progressArcPaint)
    }
}
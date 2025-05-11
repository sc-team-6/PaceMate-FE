package com.gdg.scrollmanager.views

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View
import com.gdg.scrollmanager.models.AppUsageInfo
import java.util.concurrent.TimeUnit

class PieChartView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 2f
        color = 0xFFFFFFFF.toInt()
    }
    
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = 14f * resources.displayMetrics.density
        color = 0xFF000000.toInt()
    }
    
    private var appData: List<AppUsageInfo> = emptyList()
    private val rectF = RectF()
    private val padding = 32f * resources.displayMetrics.density

    fun setData(data: List<AppUsageInfo>) {
        appData = data
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        
        if (appData.isEmpty()) return
        
        val width = width.toFloat()
        val height = height.toFloat()
        val radius = minOf(width, height) / 2.5f
        val centerX = width / 2
        val centerY = height / 2
        
        rectF.set(
            centerX - radius,
            centerY - radius,
            centerX + radius,
            centerY + radius
        )
        
        val totalTime = appData.sumOf { it.timeInForeground }
        var startAngle = 0f
        
        // 파이 차트 그리기
        appData.forEach { appInfo ->
            val sweepAngle = 360f * (appInfo.timeInForeground.toFloat() / totalTime.toFloat())
            
            // 섹션 채우기
            paint.color = appInfo.color
            canvas.drawArc(rectF, startAngle, sweepAngle, true, paint)
            
            // 섹션 테두리
            canvas.drawArc(rectF, startAngle, sweepAngle, true, strokePaint)
            
            startAngle += sweepAngle
        }
        
        // 범례 그리기
        var legendY = height - padding
        appData.forEachIndexed { index, appInfo ->
            val legendX = padding
            
            // 색상 표시
            paint.color = appInfo.color
            canvas.drawRect(
                legendX,
                legendY - 10f * resources.displayMetrics.density,
                legendX + 20f * resources.displayMetrics.density,
                legendY + 10f * resources.displayMetrics.density,
                paint
            )
            
            // 앱 이름과 사용 시간
            val text = "${appInfo.appName}: ${formatTime(appInfo.timeInForeground)}"
            canvas.drawText(
                text,
                legendX + 30f * resources.displayMetrics.density,
                legendY + 5f * resources.displayMetrics.density,
                textPaint
            )
            
            legendY += 25f * resources.displayMetrics.density
        }
    }
    
    private fun formatTime(timeInMillis: Long): String {
        val hours = TimeUnit.MILLISECONDS.toHours(timeInMillis)
        val minutes = TimeUnit.MILLISECONDS.toMinutes(timeInMillis) % 60

        return when {
            hours > 0 -> String.format("%d시간 %d분", hours, minutes)
            minutes > 0 -> String.format("%d분", minutes)
            else -> "1분 미만"
        }
    }
}
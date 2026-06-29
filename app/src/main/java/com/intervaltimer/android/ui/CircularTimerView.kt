package com.intervaltimer.android.ui

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.View
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

class CircularTimerView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : View(context, attrs) {

    var timeText: String = "0:00"
        set(v) { field = v; invalidate() }

    // progress 0..1 — сколько времени прошло (для стрелки)
    var progress: Float = 0f
        set(v) { field = v; invalidate() }

    private val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#E6141820")
        style = Paint.Style.FILL
    }

    private val trackPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#33FFFFFF")
        style = Paint.Style.STROKE
        strokeWidth = 10f
    }

    private val arcPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#FF4CAF82")
        style = Paint.Style.STROKE
        strokeWidth = 10f
        strokeCap = Paint.Cap.ROUND
    }

    private val tickPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#44FFFFFF")
        style = Paint.Style.STROKE
        strokeWidth = 2f
    }

    private val handPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#FF4CAF82")
        style = Paint.Style.STROKE
        strokeWidth = 4f
        strokeCap = Paint.Cap.ROUND
    }

    private val centerDotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#FF4CAF82")
        style = Paint.Style.FILL
    }

    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textAlign = Paint.Align.CENTER
        textSize = 42f
        typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
    }

    private val arcRect = RectF()

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val size = resolveSize(300, widthMeasureSpec)
        setMeasuredDimension(size, size)
    }

    override fun onDraw(canvas: Canvas) {
        val cx = width / 2f
        val cy = height / 2f
        val radius = min(cx, cy) - 16f
        val arcRadius = radius - 5f

        // Фон — круг
        canvas.drawCircle(cx, cy, radius, bgPaint)

        // Трек (полный круг серый)
        arcRect.set(cx - arcRadius, cy - arcRadius, cx + arcRadius, cy + arcRadius)
        canvas.drawArc(arcRect, -90f, 360f, false, trackPaint)

        // Засечки-минуты (12 штук)
        for (i in 0 until 12) {
            val angle = Math.toRadians((i * 30 - 90).toDouble())
            val innerR = radius - 22f
            val outerR = radius - 10f
            canvas.drawLine(
                cx + (innerR * cos(angle)).toFloat(), cy + (innerR * sin(angle)).toFloat(),
                cx + (outerR * cos(angle)).toFloat(), cy + (outerR * sin(angle)).toFloat(),
                tickPaint
            )
        }

        // Дуга прогресса
        if (progress > 0f) {
            canvas.drawArc(arcRect, -90f, progress * 360f, false, arcPaint)
        }

        // Стрелка-часовая
        val handLength = radius - 26f
        val handAngle = Math.toRadians((progress * 360f - 90f).toDouble())
        val hx = cx + (handLength * cos(handAngle)).toFloat()
        val hy = cy + (handLength * sin(handAngle)).toFloat()
        canvas.drawLine(cx, cy, hx, hy, handPaint)

        // Центральная точка
        canvas.drawCircle(cx, cy, 7f, centerDotPaint)

        // Время в центре
        textPaint.textSize = radius * 0.38f
        val textY = cy - (textPaint.descent() + textPaint.ascent()) / 2f
        canvas.drawText(timeText, cx, textY, textPaint)
    }
}

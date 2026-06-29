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

    // Жёлтый акцент
    private val accentColor = Color.parseColor("#FFFFCC00")
    private val accentDim   = Color.parseColor("#66FFCC00")

    var timeText: String = "0:00"
        set(v) { field = v; invalidate() }
    var progress: Float = 0f           // 0..1 прогресс текущего интервала
        set(v) { field = v; invalidate() }
    var labelTop: String = ""          // "3 / 5"
        set(v) { field = v; invalidate() }
    var labelBottom: String = ""       // "ещё 12 мин"
        set(v) { field = v; invalidate() }

    private val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#EE141820")
        style = Paint.Style.FILL
    }
    private val trackPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#33FFFFFF")
        style = Paint.Style.STROKE
        strokeWidth = 10f
    }
    private val arcPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = accentColor
        style = Paint.Style.STROKE
        strokeWidth = 10f
        strokeCap = Paint.Cap.ROUND
    }
    private val tickPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#33FFFFFF")
        style = Paint.Style.STROKE
        strokeWidth = 1.5f
    }
    private val handPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = accentColor
        style = Paint.Style.STROKE
        strokeWidth = 3.5f
        strokeCap = Paint.Cap.ROUND
    }
    private val dotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = accentColor
        style = Paint.Style.FILL
    }
    private val timePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textAlign = Paint.Align.CENTER
        typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
    }
    private val topPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = accentColor
        textAlign = Paint.Align.CENTER
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
    }
    private val bottomPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#CCFFFFFF")
        textAlign = Paint.Align.CENTER
        typeface = Typeface.DEFAULT
    }

    private val arcRect = RectF()

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val size = resolveSize(300, widthMeasureSpec)
        setMeasuredDimension(size, size)
    }

    override fun onDraw(canvas: Canvas) {
        val cx = width / 2f
        val cy = height / 2f
        val radius = min(cx, cy) - 14f
        val arcR = radius - 5f

        // Фон-круг
        canvas.drawCircle(cx, cy, radius, bgPaint)

        // Трек
        arcRect.set(cx - arcR, cy - arcR, cx + arcR, cy + arcR)
        canvas.drawArc(arcRect, -90f, 360f, false, trackPaint)

        // 12 засечек
        for (i in 0 until 12) {
            val angle = Math.toRadians((i * 30 - 90).toDouble())
            val len = if (i % 3 == 0) 14f else 8f
            val innerR = radius - len - 5f
            val outerR = radius - 5f
            canvas.drawLine(
                cx + (innerR * cos(angle)).toFloat(), cy + (innerR * sin(angle)).toFloat(),
                cx + (outerR * cos(angle)).toFloat(), cy + (outerR * sin(angle)).toFloat(),
                tickPaint
            )
        }

        // Дуга прогресса
        if (progress > 0.001f) {
            canvas.drawArc(arcRect, -90f, progress * 360f, false, arcPaint)
        }

        // Стрелка
        val handLen = radius - 22f
        val handAngle = Math.toRadians((progress * 360f - 90f).toDouble())
        val hx = cx + (handLen * cos(handAngle)).toFloat()
        val hy = cy + (handLen * sin(handAngle)).toFloat()
        canvas.drawLine(cx, cy, hx, hy, handPaint)
        canvas.drawCircle(cx, cy, 6f, dotPaint)

        // ── Текст: 3 зоны ──
        val innerRadius = radius - 20f

        // Сверху: сет "3 / 5"
        if (labelTop.isNotEmpty()) {
            topPaint.textSize = radius * 0.22f
            canvas.drawText(labelTop, cx, cy - innerRadius * 0.42f, topPaint)
        }

        // Центр: время
        timePaint.textSize = radius * 0.40f
        val timeY = cy - (timePaint.descent() + timePaint.ascent()) / 2f
        canvas.drawText(timeText, cx, timeY, timePaint)

        // Снизу: остаток "ещё 12 мин"
        if (labelBottom.isNotEmpty()) {
            bottomPaint.textSize = radius * 0.18f
            canvas.drawText(labelBottom, cx, cy + innerRadius * 0.50f, bottomPaint)
        }
    }
}

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

    var accentColor: Int = Color.parseColor("#FFFFCC00")
        set(value) {
            field = value
            arcPaint.color = value
            handPaint.color = value
            topPaint.color = value
            invalidate()
        }

    var timeText: String = "0:00"
        set(v) { field = v; invalidate() }
    var progress: Float = 0f
        set(v) { field = v; invalidate() }
    var labelTop: String = ""
        set(v) { field = v; invalidate() }
    var labelBottom: String = ""
        set(v) { field = v; invalidate() }
    var intervalName: String = ""
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
        color = Color.parseColor("#FFFFCC00")
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
        color = Color.parseColor("#FFFFCC00")
        style = Paint.Style.STROKE
        strokeWidth = 3.5f
        strokeCap = Paint.Cap.ROUND
    }
    private val timePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textAlign = Paint.Align.CENTER
        typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
    }
    private val topPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#FFFFCC00")
        textAlign = Paint.Align.CENTER
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
    }
    private val bottomPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#CCFFFFFF")
        textAlign = Paint.Align.CENTER
        typeface = Typeface.DEFAULT
    }
    private val namePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#AAFFFFFF")
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

        canvas.drawCircle(cx, cy, radius, bgPaint)

        arcRect.set(cx - arcR, cy - arcR, cx + arcR, cy + arcR)
        canvas.drawArc(arcRect, -90f, 360f, false, trackPaint)

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

        if (progress > 0.001f) {
            canvas.drawArc(arcRect, -90f, progress * 360f, false, arcPaint)
        }

        val handAngle = Math.toRadians((progress * 360f - 90f).toDouble())
        val tipR  = radius - 8f
        val baseR = radius - 20f
        val tipX  = cx + (tipR  * cos(handAngle)).toFloat()
        val tipY  = cy + (tipR  * sin(handAngle)).toFloat()
        val baseX = cx + (baseR * cos(handAngle)).toFloat()
        val baseY = cy + (baseR * sin(handAngle)).toFloat()
        canvas.drawLine(baseX, baseY, tipX, tipY, handPaint)

        val innerRadius = radius - 20f
        val hasName = intervalName.isNotEmpty()

        // Сверху: "5 / 3"
        if (labelTop.isNotEmpty()) {
            topPaint.textSize = radius * 0.22f
            canvas.drawText(labelTop, cx, cy - innerRadius * (if (hasName) 0.55f else 0.42f), topPaint)
        }

        // Имя интервала (между счётчиком и временем)
        if (hasName) {
            namePaint.textSize = radius * 0.17f
            canvas.drawText(intervalName, cx, cy - innerRadius * 0.28f, namePaint)
        }

        // Центр: время
        timePaint.textSize = if (hasName) radius * 0.34f else radius * 0.40f
        val timeOffset = if (hasName) radius * 0.06f else 0f
        val timeY = cy - (timePaint.descent() + timePaint.ascent()) / 2f + timeOffset
        canvas.drawText(timeText, cx, timeY, timePaint)

        // Снизу: "ещё 12 мин"
        if (labelBottom.isNotEmpty()) {
            bottomPaint.textSize = radius * 0.18f
            canvas.drawText(labelBottom, cx, cy + innerRadius * 0.55f, bottomPaint)
        }
    }
}

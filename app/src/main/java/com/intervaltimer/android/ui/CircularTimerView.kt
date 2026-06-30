package com.intervaltimer.android.ui

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.View
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/**
 * Circular overlay timer view.
 * drawStyle: 1=Classic, 2=Minimal, 3=Neon, 4=Glass
 */
class CircularTimerView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : View(context, attrs) {

    // ── Public state ──────────────────────────────────────────────

    var accentColor: Int = Color.parseColor("#FFFFCC00")
        set(value) {
            field = value
            arcPaint.color = value
            handPaint.color = value
            topPaint.color = value
            invalidate()
        }

    var drawStyle: Int = 1
        set(v) { field = v; applyStyle(); invalidate() }

    var timeText: String   = "0:00" ; set(v) { field = v; invalidate() }
    var progress: Float    = 0f     ; set(v) { field = v; invalidate() }
    var labelTop: String   = ""     ; set(v) { field = v; invalidate() }
    var labelBottom: String = ""    ; set(v) { field = v; invalidate() }
    var intervalName: String = ""   ; set(v) { field = v; invalidate() }

    // ── Paints ────────────────────────────────────────────────────

    private val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#EE141820")
        style = Paint.Style.FILL
    }
    private val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#44FFFFFF")
        style = Paint.Style.STROKE
        strokeWidth = 1.5f
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
        strokeWidth = 5f
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
    }
    private val namePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#AAFFFFFF")
        textAlign = Paint.Align.CENTER
    }

    private val arcRect = RectF()

    // ── Style parameters ──────────────────────────────────────────

    private var showTicks = true
    private var showLabels = true
    private var arcStrokeW = 10f
    private var handTipInset = 4f    // from edge
    private var handBaseInset = 36f  // from edge (longer = more visible)
    private var handStrokeW = 5f
    private var bgAlpha = 0xEE
    private var showBorder = false

    private fun applyStyle() {
        when (drawStyle) {
            // ── 1. Classic: all elements ────────────────────────
            1 -> {
                bgPaint.color = Color.argb(0xEE, 0x14, 0x18, 0x20)
                arcStrokeW  = 10f; trackPaint.strokeWidth = 10f
                handTipInset = 4f; handBaseInset = 36f; handStrokeW = 5f
                showTicks   = true;  showLabels = true; showBorder = false
                timePaint.color = Color.WHITE
            }
            // ── 2. Minimal: arc + time only ─────────────────────
            2 -> {
                bgPaint.color = Color.argb(0xEE, 0x14, 0x18, 0x20)
                arcStrokeW  = 12f; trackPaint.strokeWidth = 12f
                handTipInset = 4f; handBaseInset = 36f; handStrokeW = 5f
                showTicks   = false; showLabels = false; showBorder = false
                timePaint.color = Color.WHITE
            }
            // ── 3. Neon: thick bright arc, big time ─────────────
            3 -> {
                bgPaint.color = Color.argb(0xF5, 0x06, 0x06, 0x0C)
                arcStrokeW  = 18f; trackPaint.strokeWidth = 4f
                handTipInset = 4f; handBaseInset = 40f; handStrokeW = 6f
                showTicks   = false; showLabels = false; showBorder = true
                timePaint.color = Color.WHITE
            }
            // ── 4. Glass: semi-transparent, all labels ──────────
            4 -> {
                bgPaint.color = Color.argb(0xBB, 0x14, 0x18, 0x20)
                arcStrokeW  = 8f; trackPaint.strokeWidth = 8f
                handTipInset = 4f; handBaseInset = 36f; handStrokeW = 4f
                showTicks   = true;  showLabels = true; showBorder = true
                timePaint.color = Color.WHITE
            }
        }
        arcPaint.strokeWidth  = arcStrokeW
        handPaint.strokeWidth = handStrokeW
    }

    init { applyStyle() }

    // ── Measure ───────────────────────────────────────────────────

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val size = resolveSize(300, widthMeasureSpec)
        setMeasuredDimension(size, size)
    }

    // ── Draw ──────────────────────────────────────────────────────

    override fun onDraw(canvas: Canvas) {
        val cx     = width / 2f
        val cy     = height / 2f
        val radius = min(cx, cy) - 14f
        val arcR   = radius - 5f

        // Background circle
        canvas.drawCircle(cx, cy, radius, bgPaint)
        if (showBorder) canvas.drawCircle(cx, cy, radius, borderPaint)

        // Track ring
        arcRect.set(cx - arcR, cy - arcR, cx + arcR, cy + arcR)
        canvas.drawArc(arcRect, -90f, 360f, false, trackPaint)

        // Clock ticks
        if (showTicks) {
            for (i in 0 until 12) {
                val angle  = Math.toRadians((i * 30 - 90).toDouble())
                val len    = if (i % 3 == 0) 14f else 8f
                val innerR = radius - len - 5f
                val outerR = radius - 5f
                canvas.drawLine(
                    cx + (innerR * cos(angle)).toFloat(), cy + (innerR * sin(angle)).toFloat(),
                    cx + (outerR * cos(angle)).toFloat(), cy + (outerR * sin(angle)).toFloat(),
                    tickPaint
                )
            }
        }

        // Progress arc
        if (progress > 0.001f) {
            canvas.drawArc(arcRect, -90f, progress * 360f, false, arcPaint)
        }

        // Hand tick (засечка у края)
        val handAngle = Math.toRadians((progress * 360f - 90f).toDouble())
        val tipX  = cx + ((radius - handTipInset)  * cos(handAngle)).toFloat()
        val tipY  = cy + ((radius - handTipInset)  * sin(handAngle)).toFloat()
        val baseX = cx + ((radius - handBaseInset) * cos(handAngle)).toFloat()
        val baseY = cy + ((radius - handBaseInset) * sin(handAngle)).toFloat()
        canvas.drawLine(baseX, baseY, tipX, tipY, handPaint)

        // ── Text ──────────────────────────────────────────────────
        if (!showLabels) {
            // Minimal / Neon: only time, centered
            val factor = if (drawStyle == 3) 0.44f else 0.40f
            timePaint.textSize = radius * factor
            val timeY = cy - (timePaint.descent() + timePaint.ascent()) / 2f
            canvas.drawText(timeText, cx, timeY, timePaint)
            return
        }

        val innerRadius = radius - 20f
        val hasName = intervalName.isNotEmpty()

        // Top: "5 / 3"
        if (labelTop.isNotEmpty()) {
            topPaint.textSize = radius * 0.22f
            canvas.drawText(labelTop, cx, cy - innerRadius * (if (hasName) 0.55f else 0.42f), topPaint)
        }

        // Interval name
        if (hasName) {
            namePaint.textSize = radius * 0.17f
            canvas.drawText(intervalName, cx, cy - innerRadius * 0.28f, namePaint)
        }

        // Time
        timePaint.textSize = if (hasName) radius * 0.34f else radius * 0.40f
        val timeOffset = if (hasName) radius * 0.06f else 0f
        val timeY = cy - (timePaint.descent() + timePaint.ascent()) / 2f + timeOffset
        canvas.drawText(timeText, cx, timeY, timePaint)

        // Bottom: "ещё 12 мин"
        if (labelBottom.isNotEmpty()) {
            bottomPaint.textSize = radius * 0.18f
            canvas.drawText(labelBottom, cx, cy + innerRadius * 0.55f, bottomPaint)
        }
    }
}

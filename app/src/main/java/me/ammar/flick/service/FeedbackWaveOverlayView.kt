package me.ammar.flick.service

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.view.View
import kotlin.math.max
import kotlin.math.min

/**
 * Full-screen visual feedback overlay for edge gesture wave animation.
 *
 * Configured with WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE so it
 * NEVER consumes touches or blocks clicks in any applications.
 */
class FeedbackWaveOverlayView(context: Context) : View(context) {

    private val density: Float = resources.displayMetrics.density

    private var isWaveActive = false
    private var edge = "right"
    private var pullDistance = 0f
    private var currentX = 0f
    private var currentY = 0f
    private var isPrimed = false
    private var scriptName = ""

    private val waveHeightHalf = 130f * density
    private val maxPullRadius = 260f * density

    // Paints
    private val waveFillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#EE1F1820") // MetroList plum surface
        style = Paint.Style.FILL
    }
    private val waveStrokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#D8B4D8") // Lilac border
        style = Paint.Style.STROKE
        strokeWidth = 2.5f * density
    }
    private val badgePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }
    private val arrowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
        strokeWidth = 3f * density
    }
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#F5EEF5")
        textSize = 12f * density
        textAlign = Paint.Align.CENTER
        isFakeBoldText = true
    }

    private val wavePath = Path()
    private val arrowPath = Path()

    fun updateWave(
        edge: String,
        pull: Float,
        x: Float,
        y: Float,
        primed: Boolean,
        script: String
    ) {
        this.isWaveActive = true
        this.edge = edge
        this.pullDistance = pull
        this.currentX = x
        this.currentY = y
        this.isPrimed = primed
        this.scriptName = script
        invalidate()
    }

    fun clearWave() {
        this.isWaveActive = false
        this.pullDistance = 0f
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (!isWaveActive || pullDistance <= 6f * density) return

        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0f || h <= 0f) return

        val clampedPull = min(pullDistance, maxPullRadius)
        val cy = currentY.coerceIn(waveHeightHalf, h - waveHeightHalf)

        wavePath.reset()

        if (edge.lowercase() == "right") {
            val edgeX = w
            val peakX = max(edgeX - clampedPull, 20f * density)
            val topY = cy - waveHeightHalf
            val bottomY = cy + waveHeightHalf

            wavePath.moveTo(edgeX, topY)
            wavePath.cubicTo(
                edgeX - clampedPull * 0.35f, topY + waveHeightHalf * 0.35f,
                peakX + 28f * density, cy - 20f * density,
                peakX, cy
            )
            wavePath.cubicTo(
                peakX + 28f * density, cy + 20f * density,
                edgeX - clampedPull * 0.35f, bottomY - waveHeightHalf * 0.35f,
                edgeX, bottomY
            )
            wavePath.lineTo(edgeX, bottomY)
            wavePath.close()

            canvas.drawPath(wavePath, waveFillPaint)
            canvas.drawPath(wavePath, waveStrokePaint)

            drawBadge(canvas, peakX + 32f * density, cy)
        } else if (edge.lowercase() == "left") {
            val edgeX = 0f
            val peakX = min(clampedPull, w - 20f * density)
            val topY = cy - waveHeightHalf
            val bottomY = cy + waveHeightHalf

            wavePath.moveTo(edgeX, topY)
            wavePath.cubicTo(
                clampedPull * 0.35f, topY + waveHeightHalf * 0.35f,
                peakX - 28f * density, cy - 20f * density,
                peakX, cy
            )
            wavePath.cubicTo(
                peakX - 28f * density, cy + 20f * density,
                clampedPull * 0.35f, bottomY - waveHeightHalf * 0.35f,
                edgeX, bottomY
            )
            wavePath.lineTo(edgeX, bottomY)
            wavePath.close()

            canvas.drawPath(wavePath, waveFillPaint)
            canvas.drawPath(wavePath, waveStrokePaint)

            drawBadge(canvas, peakX - 32f * density, cy)
        }
    }

    private fun drawBadge(canvas: Canvas, badgeCenterX: Float, badgeCenterY: Float) {
        val badgeRadius = 24f * density

        badgePaint.color = if (isPrimed) {
            Color.parseColor("#FFD8B4D8") // Full Lilac accent when primed
        } else {
            Color.parseColor("#44D8B4D8") // Subtle lilac tint
        }
        canvas.drawCircle(badgeCenterX, badgeCenterY, badgeRadius, badgePaint)

        arrowPaint.color = if (isPrimed) {
            Color.parseColor("#FF1F1820")
        } else {
            Color.parseColor("#FFFFFFFF")
        }

        arrowPath.reset()
        val arrowSize = 10f * density

        if (edge.lowercase() == "right") {
            arrowPath.moveTo(badgeCenterX + arrowSize * 0.5f, badgeCenterY - arrowSize)
            arrowPath.lineTo(badgeCenterX - arrowSize * 0.5f, badgeCenterY)
            arrowPath.lineTo(badgeCenterX + arrowSize * 0.5f, badgeCenterY + arrowSize)
        } else {
            arrowPath.moveTo(badgeCenterX - arrowSize * 0.5f, badgeCenterY - arrowSize)
            arrowPath.lineTo(badgeCenterX + arrowSize * 0.5f, badgeCenterY)
            arrowPath.lineTo(badgeCenterX - arrowSize * 0.5f, badgeCenterY + arrowSize)
        }
        canvas.drawPath(arrowPath, arrowPaint)

        val label = when {
            scriptName.contains("abdm", ignoreCase = true) -> "Add Download"
            scriptName.contains("snip", ignoreCase = true) -> "Snip"
            scriptName.contains("ask", ignoreCase = true) -> "Ask AI"
            scriptName.contains("toolbox", ignoreCase = true) -> "Toolbox"
            else -> scriptName
        }
        if (label.isNotBlank()) {
            canvas.drawText(label, badgeCenterX, badgeCenterY - badgeRadius - 8f * density, textPaint)
        }
    }
}

package me.ammar.flick.service

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * Interactive edge gesture overlay with UbikiTouch wave physics.
 *
 * 1. Idle state: 100% invisible/transparent so no bars appear on applications.
 * 2. Touch Down: Dynamically expands the window to full screen to capture all motion events without losing touch.
 * 3. Drag Inward: Draws an elastic cubic Bézier curve wave protruding from the edge following the finger.
 * 4. Accidental Touch Prevention: Touching or stationary holding does nothing. A deliberate inward pull is required.
 * 5. Large Swipe Radius: Substantial pull inward (> 100dp) prevents interference with native system back gesture.
 * 6. Priming & Release: Holding inward for ~1.5s primes with haptic feedback; release triggers the action.
 */
class UbikiWaveOverlayView(
    context: Context,
    val edge: String,
    val minFromPct: Int,
    val maxToPct: Int,
    val thicknessPx: Int,
    val scriptName: String,
    val pullDistanceDp: Int = 40,
    val holdDurationMs: Long = 700L,
    private val windowManager: WindowManager,
    private val idleLayoutParams: WindowManager.LayoutParams,
    private val onGestureAction: (edge: String, gesture: String) -> Unit
) : View(context) {

    private val density: Float = resources.displayMetrics.density
    private val mainHandler = Handler(Looper.getMainLooper())

    // Physics constants
    private val waveHeightHalf = 120f * density
    private val minPullToPrime = pullDistanceDp.toFloat() * density
    private val maxPullRadius = (pullDistanceDp * 2.8f).coerceAtLeast(100f) * density
    private val longHoldMs = holdDurationMs

    // State
    private var isDragging = false
    private var isPrimed = false
    private var actionDispatched = false
    private var startX = 0f
    private var startY = 0f
    private var currentX = 0f
    private var currentY = 0f
    private var startTime = 0L

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

    private val holdRunnable = Runnable {
        if (isDragging && !isPrimed) {
            val pull = calculatePullDistance()
            if (pull >= minPullToPrime) {
                isPrimed = true
                performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                invalidate()
            }
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0f || h <= 0f) return

        // In idle state, bars are 100% invisible/transparent
        if (!isDragging) {
            return
        }

        // Draw interactive wave when dragged
        drawWave(canvas, w, h)
    }

    private fun drawWave(canvas: Canvas, w: Float, h: Float) {
        val pull = calculatePullDistance()
        if (pull <= 10f * density) return

        val clampedPull = min(pull, maxPullRadius)
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

        if (isPrimed) {
            badgePaint.color = Color.parseColor("#D8B4D8") // Highlighted Lilac
            arrowPaint.color = Color.parseColor("#3A2338")
        } else {
            badgePaint.color = Color.parseColor("#382C36") // Dark Plum Surface
            arrowPaint.color = Color.parseColor("#F5EEF5")
        }

        canvas.drawCircle(badgeCenterX, badgeCenterY, badgeRadius, badgePaint)

        // Draw arrow icon inside badge
        val arrowSize = 9f * density
        arrowPath.reset()
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

        // Label text above badge
        val label = when {
            scriptName.contains("abdm", ignoreCase = true) -> "Add Download"
            scriptName.contains("snip", ignoreCase = true) -> "Snip"
            else -> scriptName
        }
        if (label.isNotBlank()) {
            canvas.drawText(label, badgeCenterX, badgeCenterY - badgeRadius - 8f * density, textPaint)
        }
    }

    private fun calculatePullDistance(): Float {
        return when (edge.lowercase()) {
            "right" -> max(0f, startX - currentX)
            "left" -> max(0f, currentX - startX)
            else -> max(0f, startY - currentY)
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                startX = event.rawX
                startY = event.rawY
                currentX = event.rawX
                currentY = event.rawY
                startTime = SystemClock.uptimeMillis()

                isDragging = true
                isPrimed = false
                actionDispatched = false

                // Expand window to fullscreen so touches aren't dropped when finger pulls inward
                expandToFullscreen()

                // Schedule hold timer for long swipe
                mainHandler.postDelayed(holdRunnable, longHoldMs)
                return true
            }

            MotionEvent.ACTION_MOVE -> {
                if (!isDragging) return false

                currentX = event.rawX
                currentY = event.rawY
                val pull = calculatePullDistance()

                // Check pull distance
                if (pull >= minPullToPrime && !isPrimed && !actionDispatched) {
                    val duration = SystemClock.uptimeMillis() - startTime
                    if (duration >= (longHoldMs * 0.7f).toLong()) {
                        isPrimed = true
                        performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                    }
                }

                invalidate()
                return true
            }

            MotionEvent.ACTION_UP -> {
                if (!isDragging) return false

                mainHandler.removeCallbacks(holdRunnable)
                val duration = SystemClock.uptimeMillis() - startTime
                val pull = calculatePullDistance()
                val dy = currentY - startY

                // Accidental touch / tapping prevention: must pull inward at least 60% of minPullToPrime
                if (pull < minPullToPrime * 0.6f && abs(dy) < 80f * density) {
                    // Ignored accidental touch or stationary tap
                    resetToIdle()
                    return true
                }

                val recognizedGesture: String = when {
                    abs(dy) > 100f * density && abs(dy) > pull * 1.4f -> {
                        if (dy < 0) "swipe-up" else "swipe-down"
                    }
                    isPrimed || duration >= longHoldMs -> {
                        "swipe-hold"
                    }
                    else -> {
                        "swipe-in"
                    }
                }

                if (!actionDispatched) {
                    actionDispatched = true
                    performHapticFeedback(HapticFeedbackConstants.CONFIRM)
                    onGestureAction(edge, recognizedGesture)
                }

                resetToIdle()
                return true
            }

            MotionEvent.ACTION_CANCEL -> {
                mainHandler.removeCallbacks(holdRunnable)
                resetToIdle()
                return true
            }
        }
        return super.onTouchEvent(event)
    }

    private fun expandToFullscreen() {
        try {
            val fullscreenParams = WindowManager.LayoutParams().apply {
                copyFrom(idleLayoutParams)
                width = WindowManager.LayoutParams.MATCH_PARENT
                height = WindowManager.LayoutParams.MATCH_PARENT
                x = 0
                y = 0
            }
            windowManager.updateViewLayout(this, fullscreenParams)
        } catch (t: Throwable) {
            // Silently handled
        }
    }

    private fun resetToIdle() {
        isDragging = false
        isPrimed = false
        actionDispatched = false
        mainHandler.removeCallbacks(holdRunnable)
        try {
            windowManager.updateViewLayout(this, idleLayoutParams)
        } catch (t: Throwable) {
            // Silently handled
        }
        invalidate()
    }
}

package me.ammar.flick.service

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.DisplayMetrics
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * Interactive edge gesture overlay matching the UbikiTouch wave/curve physics.
 *
 * 1. Idle state: Shows a sleek, subtle indicator bar on the screen edge so the trigger zone is visible.
 * 2. Touch Down: Dynamically expands the window to full screen to capture all motion events without losing touch.
 * 3. Drag Inward: Draws an elastic cubic Bézier curve wave protruding from the edge following the finger.
 * 4. Hold / Long Pull: When dragged past threshold or held for duration, primes with haptic feedback.
 * 5. Touch Up: Dispatches the matched action (swipe-hold, swipe-in, swipe-up, etc.) and collapses back to edge strip.
 */
class UbikiWaveOverlayView(
    context: Context,
    val edge: String,
    val minFromPct: Int,
    val maxToPct: Int,
    val thicknessPx: Int,
    val scriptName: String,
    private val windowManager: WindowManager,
    private val idleLayoutParams: WindowManager.LayoutParams,
    private val onGestureAction: (edge: String, gesture: String) -> Unit
) : View(context) {

    private val density: Float = resources.displayMetrics.density
    private val mainHandler = Handler(Looper.getMainLooper())

    // Physics constants
    private val waveHeightHalf = 110f * density
    private val minPullToPrime = 70f * density
    private val maxPullRadius = 220f * density
    private val longHoldMs = 1800L // 1.8 seconds hold to trigger long swipe action

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
    private val idleBarPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#99D8B4D8") // Semi-transparent lilac
        style = Paint.Style.FILL
    }
    private val idleBorderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#E6D8B4D8")
        style = Paint.Style.STROKE
        strokeWidth = 1.5f * density
    }
    private val waveFillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#EE1F1820") // MetroList plum surface
        style = Paint.Style.FILL
    }
    private val waveStrokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#D8B4D8") // Lilac border
        style = Paint.Style.STROKE
        strokeWidth = 2f * density
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
            if (pull >= minPullToPrime * 0.7f) {
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

        if (!isDragging) {
            // Draw idle edge indicator pill
            drawIdleIndicator(canvas, w, h)
            return
        }

        // Draw interactive wave following finger
        drawWave(canvas, w, h)
    }

    private fun drawIdleIndicator(canvas: Canvas, w: Float, h: Float) {
        val barWidth = 6f * density
        val rect = when (edge.lowercase()) {
            "right" -> RectF(w - barWidth - 2f, 8f, w - 2f, h - 8f)
            "left" -> RectF(2f, 8f, barWidth + 2f, h - 8f)
            else -> RectF(8f, h - barWidth - 2f, w - 8f, h - 2f)
        }
        val radius = 4f * density
        canvas.drawRoundRect(rect, radius, radius, idleBarPaint)
        canvas.drawRoundRect(rect, radius, radius, idleBorderPaint)
    }

    private fun drawWave(canvas: Canvas, w: Float, h: Float) {
        val pull = calculatePullDistance()
        if (pull <= 4f) return

        val clampedPull = min(pull, maxPullRadius)
        val cy = currentY.coerceIn(waveHeightHalf, h - waveHeightHalf)

        wavePath.reset()

        if (edge.lowercase() == "right") {
            val edgeX = w
            val peakX = max(edgeX - clampedPull, 20f * density)

            val topY = cy - waveHeightHalf
            val bottomY = cy + waveHeightHalf

            wavePath.moveTo(edgeX, topY)
            // Cubic bezier to peak
            wavePath.cubicTo(
                edgeX - clampedPull * 0.35f, topY + waveHeightHalf * 0.35f,
                peakX + 24f * density, cy - 20f * density,
                peakX, cy
            )
            // Cubic bezier from peak to bottom
            wavePath.cubicTo(
                peakX + 24f * density, cy + 20f * density,
                edgeX - clampedPull * 0.35f, bottomY - waveHeightHalf * 0.35f,
                edgeX, bottomY
            )
            wavePath.lineTo(edgeX, bottomY)
            wavePath.close()

            // Draw wave body and stroke
            canvas.drawPath(wavePath, waveFillPaint)
            canvas.drawPath(wavePath, waveStrokePaint)

            // Draw center icon badge
            drawBadge(canvas, peakX + 28f * density, cy)
        } else if (edge.lowercase() == "left") {
            val edgeX = 0f
            val peakX = min(clampedPull, w - 20f * density)

            val topY = cy - waveHeightHalf
            val bottomY = cy + waveHeightHalf

            wavePath.moveTo(edgeX, topY)
            wavePath.cubicTo(
                clampedPull * 0.35f, topY + waveHeightHalf * 0.35f,
                peakX - 24f * density, cy - 20f * density,
                peakX, cy
            )
            wavePath.cubicTo(
                peakX - 24f * density, cy + 20f * density,
                clampedPull * 0.35f, bottomY - waveHeightHalf * 0.35f,
                edgeX, bottomY
            )
            wavePath.lineTo(edgeX, bottomY)
            wavePath.close()

            canvas.drawPath(wavePath, waveFillPaint)
            canvas.drawPath(wavePath, waveStrokePaint)

            drawBadge(canvas, peakX - 28f * density, cy)
        }
    }

    private fun drawBadge(canvas: Canvas, badgeCenterX: Float, badgeCenterY: Float) {
        val badgeRadius = 22f * density

        // Badge background: Lilac when primed, deep charcoal when dragging
        if (isPrimed) {
            badgePaint.color = Color.parseColor("#D8B4D8")
            arrowPaint.color = Color.parseColor("#3A2338")
        } else {
            badgePaint.color = Color.parseColor("#382C36")
            arrowPaint.color = Color.parseColor("#F5EEF5")
        }

        canvas.drawCircle(badgeCenterX, badgeCenterY, badgeRadius, badgePaint)

        // Draw arrow icon inside badge
        val arrowSize = 8f * density
        arrowPath.reset()
        if (edge.lowercase() == "right") {
            // Pointing left (inward)
            arrowPath.moveTo(badgeCenterX + arrowSize * 0.5f, badgeCenterY - arrowSize)
            arrowPath.lineTo(badgeCenterX - arrowSize * 0.5f, badgeCenterY)
            arrowPath.lineTo(badgeCenterX + arrowSize * 0.5f, badgeCenterY + arrowSize)
        } else {
            // Pointing right (inward)
            arrowPath.moveTo(badgeCenterX - arrowSize * 0.5f, badgeCenterY - arrowSize)
            arrowPath.lineTo(badgeCenterX + arrowSize * 0.5f, badgeCenterY)
            arrowPath.lineTo(badgeCenterX - arrowSize * 0.5f, badgeCenterY + arrowSize)
        }
        canvas.drawPath(arrowPath, arrowPaint)

        // Script label text above badge
        if (scriptName.isNotBlank()) {
            val label = when {
                scriptName.contains("abdm", ignoreCase = true) -> "Add Download"
                scriptName.contains("snip", ignoreCase = true) -> "Snip"
                else -> scriptName
            }
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

                // Initial subtle touch tick
                performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)

                // Schedule hold timer for long swipe
                mainHandler.postDelayed(holdRunnable, longHoldMs)
                invalidate()
                return true
            }

            MotionEvent.ACTION_MOVE -> {
                if (!isDragging) return false

                currentX = event.rawX
                currentY = event.rawY
                val pull = calculatePullDistance()

                // If pulled past primary distance, trigger haptic tick once
                if (pull >= minPullToPrime && !isPrimed && !actionDispatched) {
                    val duration = SystemClock.uptimeMillis() - startTime
                    if (duration >= 800L) {
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

                // Determine gesture
                val recognizedGesture: String? = when {
                    // Vertical swipes along the edge
                    abs(dy) > 120f * density && abs(dy) > pull * 1.5f -> {
                        if (dy < 0) "swipe-up" else "swipe-down"
                    }
                    // Long swipe / hold (swiped inward and held or pulled deeply)
                    isPrimed || (pull >= minPullToPrime && duration >= 1200L) -> {
                        "swipe-hold"
                    }
                    // Normal inward swipe
                    pull >= minPullToPrime -> {
                        "swipe-in"
                    }
                    // Tap / short touch
                    pull < 20f * density && duration < 350L -> {
                        "tap"
                    }
                    else -> null
                }

                if (recognizedGesture != null && !actionDispatched) {
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
            // Log silently
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
            // Log silently
        }
        invalidate()
    }
}

package me.ammar.flick.service

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PixelFormat
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * High-performance edge gesture detector and fluid wave overlay.
 *
 * Idle state:
 * - Hugs the screen edge with an invisible 1-alpha touch surface (forces SurfaceFlinger buffer allocation).
 * - Leaves 97% of the screen completely uncovered.
 * - Non-swipe taps are forwarded directly to underlying apps using temporary FLAG_NOT_TOUCHABLE passthrough.
 *
 * Active gesture state:
 * - On ACTION_DOWN, dynamically expands to fullscreen so inward swipes can travel any distance across the display.
 * - Renders high-frame-rate fluid curved wave with directional arrow badge and script name label.
 * - Confirms priming and gesture recognition with tactile haptic feedback.
 * - On ACTION_UP / ACTION_CANCEL, immediately contracts back to the edge.
 */
class UbikiWaveOverlayView(
    context: Context,
    val edge: String,
    val minFromPct: Int,
    val maxToPct: Int,
    val thicknessPx: Int,
    val scriptName: String,
    val pullDistanceDp: Int = 32,
    val holdDurationMs: Long = 600L,
    private val onGestureAction: (edge: String, gesture: String) -> Unit
) : View(context) {

    companion object {
        private const val TAG = "UbikiWaveOverlay"
    }

    private val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private val density: Float = resources.displayMetrics.density
    private val mainHandler = Handler(Looper.getMainLooper())

    private val minPullToPrime = pullDistanceDp.toFloat() * density
    private val longHoldMs = holdDurationMs

    // Geometry constants
    private val waveHeightHalf = 130f * density
    private val maxPullRadius = 260f * density

    // Paints
    private val waveFillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#EE1F1820") // Flick dark plum surface
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

    // State
    private var isDragging = false
    private var isPrimed = false
    private var actionDispatched = false
    private var isForwardingTap = false
    private var startX = 0f
    private var startY = 0f
    private var currentX = 0f
    private var currentY = 0f
    private var startTime = 0L

    val idleLayoutParams = WindowManager.LayoutParams().apply {
        type = WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY
        flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
        format = PixelFormat.TRANSLUCENT

        val displayMetrics = resources.displayMetrics
        val screenWidth = displayMetrics.widthPixels
        val screenHeight = displayMetrics.heightPixels

        when (edge.lowercase()) {
            "left" -> {
                gravity = Gravity.LEFT or Gravity.TOP
                x = 0
                y = (screenHeight * minFromPct) / 100
                width = thicknessPx
                height = (screenHeight * (maxToPct - minFromPct)) / 100
            }
            "right" -> {
                gravity = Gravity.RIGHT or Gravity.TOP
                x = 0
                y = (screenHeight * minFromPct) / 100
                width = thicknessPx
                height = (screenHeight * (maxToPct - minFromPct)) / 100
            }
            else -> {
                gravity = Gravity.BOTTOM or Gravity.LEFT
                x = (screenWidth * minFromPct) / 100
                y = 0
                width = (screenWidth * (maxToPct - minFromPct)) / 100
                height = thicknessPx
            }
        }
    }

    private val fullscreenLayoutParams = WindowManager.LayoutParams().apply {
        type = WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY
        flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
        format = PixelFormat.TRANSLUCENT
        gravity = Gravity.TOP or Gravity.LEFT
        x = 0
        y = 0
        width = WindowManager.LayoutParams.MATCH_PARENT
        height = WindowManager.LayoutParams.MATCH_PARENT
    }

    init {
        // Set minimal alpha background so SurfaceFlinger allocates buffer for InputDispatcher hit testing
        setBackgroundColor(Color.parseColor("#01000000"))
        isClickable = true
        isFocusable = false
    }

    private val holdRunnable = Runnable {
        if (isDragging && !isPrimed) {
            val pull = calculatePullDistance()
            if (pull >= minPullToPrime * 0.65f) {
                isPrimed = true
                performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                invalidate()
            }
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
        if (isForwardingTap) return false

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

                // 1. Expand overlay window to fullscreen for continuous gesture tracking
                expandToFullscreen()

                // 2. Subtle initial touch tick
                performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)

                // 3. Start hold timer
                mainHandler.postDelayed(holdRunnable, longHoldMs)
                invalidate()
                return true
            }

            MotionEvent.ACTION_MOVE -> {
                if (!isDragging) return false

                currentX = event.rawX
                currentY = event.rawY
                val pull = calculatePullDistance()

                // Check pull distance and hold threshold to prime
                if (pull >= minPullToPrime && !isPrimed && !actionDispatched) {
                    val duration = SystemClock.uptimeMillis() - startTime
                    if (duration >= (longHoldMs * 0.6f).toLong()) {
                        isPrimed = true
                        performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
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

                // Non-swipe touches (taps on underlying application buttons):
                // Forward the tap to the underlying app using temporary NOT_TOUCHABLE passthrough
                if (pull < 14f * density && abs(dy) < 20f * density && duration < 320L) {
                    resetToIdleAndForwardTap(startX, startY)
                    return true
                }

                // If user started dragging but didn't pull far enough:
                if (pull < minPullToPrime * 0.4f) {
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
                resetToIdle()
                return true
            }
        }
        return super.onTouchEvent(event)
    }

    private fun expandToFullscreen() {
        if (isAttachedToWindow) {
            try {
                windowManager.updateViewLayout(this, fullscreenLayoutParams)
            } catch (t: Throwable) {
                Log.w(TAG, "Failed to expand overlay to fullscreen", t)
            }
        }
    }

    private fun resetToIdle() {
        isDragging = false
        isPrimed = false
        actionDispatched = false
        mainHandler.removeCallbacks(holdRunnable)
        invalidate()

        if (isAttachedToWindow) {
            try {
                windowManager.updateViewLayout(this, idleLayoutParams)
            } catch (t: Throwable) {
                Log.w(TAG, "Failed to reset overlay to idle", t)
            }
        }
    }

    private fun resetToIdleAndForwardTap(x: Float, y: Float) {
        isDragging = false
        isPrimed = false
        actionDispatched = false
        mainHandler.removeCallbacks(holdRunnable)
        invalidate()

        if (isAttachedToWindow) {
            isForwardingTap = true
            try {
                // Set FLAG_NOT_TOUCHABLE temporarily so the synthesized tap penetrates to underlying app
                val passthroughParams = WindowManager.LayoutParams().apply {
                    copyFrom(idleLayoutParams)
                    flags = idleLayoutParams.flags or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
                }
                windowManager.updateViewLayout(this, passthroughParams)

                // Dispatch tap via accessibility service
                FlickAccessibilityService.instance?.dispatchTap(x, y)

                // Restore touchable after 100ms
                mainHandler.postDelayed({
                    if (isAttachedToWindow) {
                        try {
                            windowManager.updateViewLayout(this, idleLayoutParams)
                        } catch (t: Throwable) {
                            // Ignored
                        }
                    }
                    isForwardingTap = false
                }, 100L)
            } catch (t: Throwable) {
                isForwardingTap = false
                resetToIdle()
            }
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (!isDragging) return

        val pull = calculatePullDistance()
        if (pull <= 4f * density) return

        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0f || h <= 0f) return

        val clampedPull = min(pull, maxPullRadius)
        wavePath.reset()

        if (edge.lowercase() == "right") {
            val cy = currentY.coerceIn(waveHeightHalf, h - waveHeightHalf)
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
            val cy = currentY.coerceIn(waveHeightHalf, h - waveHeightHalf)
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
        } else if (edge.lowercase() == "bottom") {
            val cx = currentX.coerceIn(waveHeightHalf, w - waveHeightHalf)
            val edgeY = h
            val peakY = max(edgeY - clampedPull, 20f * density)
            val leftX = cx - waveHeightHalf
            val rightX = cx + waveHeightHalf

            wavePath.moveTo(leftX, edgeY)
            wavePath.cubicTo(
                leftX + waveHeightHalf * 0.35f, edgeY - clampedPull * 0.35f,
                cx - 20f * density, peakY + 28f * density,
                cx, peakY
            )
            wavePath.cubicTo(
                cx + 20f * density, peakY + 28f * density,
                rightX - waveHeightHalf * 0.35f, edgeY - clampedPull * 0.35f,
                rightX, edgeY
            )
            wavePath.lineTo(rightX, edgeY)
            wavePath.close()

            canvas.drawPath(wavePath, waveFillPaint)
            canvas.drawPath(wavePath, waveStrokePaint)

            drawBadge(canvas, cx, peakY + 32f * density)
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

        when (edge.lowercase()) {
            "right" -> {
                arrowPath.moveTo(badgeCenterX + arrowSize * 0.5f, badgeCenterY - arrowSize)
                arrowPath.lineTo(badgeCenterX - arrowSize * 0.5f, badgeCenterY)
                arrowPath.lineTo(badgeCenterX + arrowSize * 0.5f, badgeCenterY + arrowSize)
            }
            "left" -> {
                arrowPath.moveTo(badgeCenterX - arrowSize * 0.5f, badgeCenterY - arrowSize)
                arrowPath.lineTo(badgeCenterX + arrowSize * 0.5f, badgeCenterY)
                arrowPath.lineTo(badgeCenterX - arrowSize * 0.5f, badgeCenterY + arrowSize)
            }
            else -> {
                arrowPath.moveTo(badgeCenterX - arrowSize, badgeCenterY + arrowSize * 0.5f)
                arrowPath.lineTo(badgeCenterX, badgeCenterY - arrowSize * 0.5f)
                arrowPath.lineTo(badgeCenterX + arrowSize, badgeCenterY + arrowSize * 0.5f)
            }
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

    override fun onDetachedFromWindow() {
        mainHandler.removeCallbacks(holdRunnable)
        super.onDetachedFromWindow()
    }
}

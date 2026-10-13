package me.ammar.flick.service

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import kotlin.math.abs
import kotlin.math.max

/**
 * Fixed edge touch detector view.
 *
 * This view stays fixed at the edge and NEVER resizes mid-gesture.
 * This guarantees that Android's InputDispatcher never cancels touch streams,
 * allowing smooth, uninterrupted inward dragging across the full screen.
 *
 * Visual waves are drawn on FeedbackWaveOverlayView, which covers the screen
 * with FLAG_NOT_TOUCHABLE.
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
    private val feedbackWave: FeedbackWaveOverlayView,
    private val onGestureAction: (edge: String, gesture: String) -> Unit
) : View(context) {

    private val density: Float = resources.displayMetrics.density
    private val mainHandler = Handler(Looper.getMainLooper())

    private val minPullToPrime = pullDistanceDp.toFloat() * density
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

    private val holdRunnable = Runnable {
        if (isDragging && !isPrimed) {
            val pull = calculatePullDistance()
            if (pull >= minPullToPrime * 0.7f) {
                isPrimed = true
                performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                feedbackWave.updateWave(edge, pull, currentX, currentY, isPrimed, scriptName)
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

                mainHandler.postDelayed(holdRunnable, longHoldMs)
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
                        performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                    }
                }

                if (pull > 5f * density) {
                    feedbackWave.updateWave(edge, pull, currentX, currentY, isPrimed, scriptName)
                }

                return true
            }

            MotionEvent.ACTION_UP -> {
                if (!isDragging) return false

                isDragging = false
                mainHandler.removeCallbacks(holdRunnable)
                feedbackWave.clearWave()

                val duration = SystemClock.uptimeMillis() - startTime
                val pull = calculatePullDistance()
                val dy = currentY - startY

                // Non-swipe touches (tap on screen buttons without pulling inward):
                // Forward/dispatch the tap to the underlying app so screen remains 100% clickable!
                if (pull < 16f * density && abs(dy) < 20f * density && duration < 320L) {
                    FlickAccessibilityService.instance?.dispatchTap(startX, startY)
                    return true
                }

                // If user didn't pull far enough inward, cancel without firing
                if (pull < minPullToPrime * 0.45f) {
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

                return true
            }

            MotionEvent.ACTION_CANCEL -> {
                isDragging = false
                mainHandler.removeCallbacks(holdRunnable)
                feedbackWave.clearWave()
                return true
            }
        }
        return super.onTouchEvent(event)
    }
}

package me.ammar.flick.service

import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import kotlin.math.abs
import kotlin.math.sqrt

class EdgeGestureDetector(
    private val edge: String,
    private val onGesture: (edge: String, gesture: String) -> Unit
) : View.OnTouchListener {

    private val handler = Handler(Looper.getMainLooper())
    private var startX = 0f
    private var startY = 0f
    private var startTime = 0L

    private var lastTapTime = 0L
    private var tapCount = 0

    private val touchSlop = 40f
    private val swipeThreshold = 100f
    private val longPressTimeout = 500L
    private val doubleTapTimeout = 300L

    private var isLongPressFired = false
    private val longPressRunnable = Runnable {
        isLongPressFired = true
        onGesture(edge, "long-press")
    }

    override fun onTouch(v: View, event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                startX = event.rawX
                startY = event.rawY
                startTime = SystemClock.uptimeMillis()
                isLongPressFired = false

                handler.postDelayed(longPressRunnable, longPressTimeout)
                return true
            }

            MotionEvent.ACTION_MOVE -> {
                val dx = event.rawX - startX
                val dy = event.rawY - startY
                val dist = sqrt(dx * dx + dy * dy)
                if (dist > touchSlop) {
                    handler.removeCallbacks(longPressRunnable)
                }
                return true
            }

            MotionEvent.ACTION_UP -> {
                handler.removeCallbacks(longPressRunnable)
                if (isLongPressFired) {
                    v.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                    return true
                }

                val endX = event.rawX
                val endY = event.rawY
                val dx = endX - startX
                val dy = endY - startY
                val duration = SystemClock.uptimeMillis() - startTime

                // Check Swipe gestures
                if (abs(dy) > swipeThreshold && abs(dy) > abs(dx)) {
                    v.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                    if (dy < -swipeThreshold) {
                        onGesture(edge, "swipe-up")
                    } else {
                        onGesture(edge, "swipe-down")
                    }
                    return true
                }

                // Check Inward swipe gestures
                if (edge == "right" && dx < -swipeThreshold) {
                    v.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                    onGesture(edge, "swipe-in")
                    return true
                }
                if (edge == "left" && dx > swipeThreshold) {
                    v.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                    onGesture(edge, "swipe-in")
                    return true
                }

                // Check Tap / Double Tap
                val dist = sqrt(dx * dx + dy * dy)
                if (dist < touchSlop && duration < 400) {
                    val now = SystemClock.uptimeMillis()
                    if (now - lastTapTime < doubleTapTimeout) {
                        v.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                        tapCount = 0
                        lastTapTime = 0L
                        onGesture(edge, "double-tap")
                    } else {
                        tapCount = 1
                        lastTapTime = now
                        handler.postDelayed({
                            if (tapCount == 1) {
                                v.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                                onGesture(edge, "tap")
                                tapCount = 0
                            }
                        }, doubleTapTimeout)
                    }
                    return true
                }

                return true
            }

            MotionEvent.ACTION_CANCEL -> {
                handler.removeCallbacks(longPressRunnable)
                return true
            }
        }
        return false
    }
}

package me.ammar.flick.service

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.graphics.PixelFormat
import android.util.DisplayMetrics
import android.util.Log
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import me.ammar.flick.model.TriggerModel

class EdgeOverlayManager(
    private val service: AccessibilityService,
    private val onGestureAction: (edge: String, gesture: String) -> Unit
) {
    companion object {
        private const val TAG = "EdgeOverlayManager"
    }

    private val windowManager = service.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private val activeViews = mutableListOf<View>()
    private var feedbackWaveView: FeedbackWaveOverlayView? = null

    fun updateOverlays(
        triggers: List<TriggerModel>,
        isServiceEnabled: Boolean,
        pullDistanceDp: Int = 40,
        holdDurationMs: Long = 700L
    ) {
        removeOverlays()

        if (!isServiceEnabled || triggers.isEmpty()) {
            Log.i(TAG, "Master toggle disabled or no triggers: no overlays attached")
            return
        }

        val displayMetrics = DisplayMetrics()
        @Suppress("DEPRECATION")
        windowManager.defaultDisplay.getRealMetrics(displayMetrics)
        val screenWidth = displayMetrics.widthPixels
        val screenHeight = displayMetrics.heightPixels

        // 1. Create and attach full-screen visual feedback wave overlay (FLAG_NOT_TOUCHABLE)
        val waveView = FeedbackWaveOverlayView(service)
        val waveParams = WindowManager.LayoutParams().apply {
            type = WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY
            flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
            format = PixelFormat.TRANSLUCENT
            width = WindowManager.LayoutParams.MATCH_PARENT
            height = WindowManager.LayoutParams.MATCH_PARENT
            gravity = Gravity.TOP or Gravity.LEFT
        }

        try {
            windowManager.addView(waveView, waveParams)
            feedbackWaveView = waveView
        } catch (t: Throwable) {
            Log.e(TAG, "Failed to attach FeedbackWaveOverlayView", t)
        }

        val activeWave = feedbackWaveView ?: waveView

        // 2. Attach fixed edge touch zone detectors
        val grouped = triggers.groupBy { it.edge.lowercase() }

        for ((edge, edgeTriggers) in grouped) {
            val minFrom = edgeTriggers.minOfOrNull { it.fromPct } ?: 20
            val maxTo = edgeTriggers.maxOfOrNull { it.toPct } ?: 65
            val thickness = edgeTriggers.maxOfOrNull { it.thicknessPx } ?: 22
            val thicknessPx = (thickness * displayMetrics.density).toInt().coerceIn(
                (12 * displayMetrics.density).toInt(),
                (36 * displayMetrics.density).toInt()
            )

            val primaryScript = edgeTriggers.firstOrNull()?.script ?: ""

            val idleParams = WindowManager.LayoutParams().apply {
                type = WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY
                flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                        WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
                format = PixelFormat.TRANSLUCENT

                when (edge) {
                    "left" -> {
                        gravity = Gravity.LEFT or Gravity.TOP
                        x = 0
                        y = (screenHeight * minFrom) / 100
                        width = thicknessPx
                        height = (screenHeight * (maxTo - minFrom)) / 100
                    }
                    "right" -> {
                        gravity = Gravity.RIGHT or Gravity.TOP
                        x = 0
                        y = (screenHeight * minFrom) / 100
                        width = thicknessPx
                        height = (screenHeight * (maxTo - minFrom)) / 100
                    }
                    else -> {
                        gravity = Gravity.BOTTOM or Gravity.LEFT
                        x = (screenWidth * minFrom) / 100
                        y = 0
                        width = (screenWidth * (maxTo - minFrom)) / 100
                        height = thicknessPx
                    }
                }
            }

            val touchZone = UbikiWaveOverlayView(
                context = service,
                edge = edge,
                minFromPct = minFrom,
                maxToPct = maxTo,
                thicknessPx = thicknessPx,
                scriptName = primaryScript,
                pullDistanceDp = pullDistanceDp,
                holdDurationMs = holdDurationMs,
                feedbackWave = activeWave,
                onGestureAction = onGestureAction
            )

            try {
                windowManager.addView(touchZone, idleParams)
                activeViews.add(touchZone)
                Log.i(TAG, "Attached edge touch detector for $edge: $minFrom%-$maxTo%, width=${thicknessPx}px")
            } catch (t: Throwable) {
                Log.e(TAG, "Failed to attach touch detector for edge: $edge", t)
            }
        }
    }

    fun removeOverlays() {
        for (view in activeViews) {
            try {
                windowManager.removeView(view)
            } catch (t: Throwable) {
                Log.w(TAG, "Failed to remove overlay view", t)
            }
        }
        activeViews.clear()

        feedbackWaveView?.let { wave ->
            try {
                windowManager.removeView(wave)
            } catch (t: Throwable) {
                // Silently ignored
            }
        }
        feedbackWaveView = null
    }
}

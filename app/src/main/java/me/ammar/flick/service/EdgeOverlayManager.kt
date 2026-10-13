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

    fun updateOverlays(triggers: List<TriggerModel>) {
        removeOverlays()

        val displayMetrics = DisplayMetrics()
        @Suppress("DEPRECATION")
        windowManager.defaultDisplay.getRealMetrics(displayMetrics)
        val screenWidth = displayMetrics.widthPixels
        val screenHeight = displayMetrics.heightPixels

        // Group triggers by edge
        val grouped = triggers.groupBy { it.edge.lowercase() }

        for ((edge, edgeTriggers) in grouped) {
            val minFrom = edgeTriggers.minOfOrNull { it.fromPct } ?: 20
            val maxTo = edgeTriggers.maxOfOrNull { it.toPct } ?: 60
            val thickness = edgeTriggers.maxOfOrNull { it.thicknessPx } ?: 24
            val thicknessPx = (thickness * displayMetrics.density).toInt().coerceAtLeast(32)

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

            val waveOverlay = UbikiWaveOverlayView(
                context = service,
                edge = edge,
                minFromPct = minFrom,
                maxToPct = maxTo,
                thicknessPx = thicknessPx,
                scriptName = primaryScript,
                windowManager = windowManager,
                idleLayoutParams = idleParams,
                onGestureAction = onGestureAction
            )

            try {
                windowManager.addView(waveOverlay, idleParams)
                activeViews.add(waveOverlay)
                Log.i(TAG, "Attached UbikiTouch wave overlay for edge: $edge ($minFrom% - $maxTo%)")
            } catch (t: Throwable) {
                Log.e(TAG, "Failed to attach overlay for edge: $edge", t)
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
    }
}

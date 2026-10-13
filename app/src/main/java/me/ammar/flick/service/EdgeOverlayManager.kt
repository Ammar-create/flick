package me.ammar.flick.service

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
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

    fun updateOverlays(triggers: List<TriggerModel>, showVisualZones: Boolean = false) {
        removeOverlays()

        val displayMetrics = DisplayMetrics()
        @Suppress("DEPRECATION")
        windowManager.defaultDisplay.getRealMetrics(displayMetrics)
        val screenWidth = displayMetrics.widthPixels
        val screenHeight = displayMetrics.heightPixels

        // Group triggers by edge and create overlay strips
        val grouped = triggers.groupBy { it.edge }

        for ((edge, edgeTriggers) in grouped) {
            val minFrom = edgeTriggers.minOfOrNull { it.fromPct } ?: 0
            val maxTo = edgeTriggers.maxOfOrNull { it.toPct } ?: 100
            val thickness = edgeTriggers.maxOfOrNull { it.thicknessPx } ?: 24
            val thicknessPx = (thickness * displayMetrics.density).toInt().coerceAtLeast(16)

            val stripView = View(service).apply {
                if (showVisualZones) {
                    background = GradientDrawable().apply {
                        setColor(0x1AD8B4D8.toInt()) // Soft lilac tint
                        setStroke((1 * displayMetrics.density).toInt(), 0x4DD8B4D8.toInt())
                    }
                } else {
                    setBackgroundColor(Color.TRANSPARENT)
                }
                setOnTouchListener(EdgeGestureDetector(edge, onGestureAction))
            }

            val params = WindowManager.LayoutParams().apply {
                type = WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY
                flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                        WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                        WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH
                format = PixelFormat.TRANSLUCENT

                when (edge.lowercase()) {
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
                    "bottom" -> {
                        gravity = Gravity.BOTTOM or Gravity.LEFT
                        x = (screenWidth * minFrom) / 100
                        y = 0
                        width = (screenWidth * (maxTo - minMin(minFrom, maxTo))) / 100
                        height = thicknessPx
                    }
                }
            }

            try {
                windowManager.addView(stripView, params)
                activeViews.add(stripView)
                Log.i(TAG, "Attached overlay strip for edge: $edge ($minFrom% - $maxTo%)")
            } catch (t: Throwable) {
                Log.e(TAG, "Failed to attach overlay for edge: $edge", t)
            }
        }
    }

    private fun minMin(a: Int, b: Int) = minOf(a, b)

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

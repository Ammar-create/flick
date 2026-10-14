package me.ammar.flick.service

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.util.DisplayMetrics
import android.util.Log
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

    private var lastTriggers: List<TriggerModel> = emptyList()
    private var lastIsEnabled: Boolean = false
    private var lastPullDistanceDp: Int = 32
    private var lastHoldDurationMs: Long = 600L

    fun updateOverlays(
        triggers: List<TriggerModel>,
        isServiceEnabled: Boolean,
        pullDistanceDp: Int = 32,
        holdDurationMs: Long = 600L
    ) {
        lastTriggers = triggers
        lastIsEnabled = isServiceEnabled
        lastPullDistanceDp = pullDistanceDp
        lastHoldDurationMs = holdDurationMs

        removeOverlays()

        if (!isServiceEnabled || triggers.isEmpty()) {
            Log.i(TAG, "Master toggle disabled or no triggers: no overlays attached")
            return
        }

        val displayMetrics = DisplayMetrics()
        @Suppress("DEPRECATION")
        windowManager.defaultDisplay.getRealMetrics(displayMetrics)

        val grouped = triggers.groupBy { it.edge.lowercase() }

        for ((edge, edgeTriggers) in grouped) {
            val minFrom = edgeTriggers.minOfOrNull { it.fromPct } ?: 20
            val maxTo = edgeTriggers.maxOfOrNull { it.toPct } ?: 70
            val thickness = edgeTriggers.maxOfOrNull { it.thicknessPx } ?: 28
            val thicknessPx = (thickness * displayMetrics.density).toInt().coerceIn(
                (18 * displayMetrics.density).toInt(),
                (48 * displayMetrics.density).toInt()
            )

            val primaryScript = edgeTriggers.firstOrNull()?.script ?: ""

            val touchZone = UbikiWaveOverlayView(
                context = service,
                edge = edge,
                minFromPct = minFrom,
                maxToPct = maxTo,
                thicknessPx = thicknessPx,
                scriptName = primaryScript,
                pullDistanceDp = pullDistanceDp,
                holdDurationMs = holdDurationMs,
                onGestureAction = onGestureAction
            )

            try {
                windowManager.addView(touchZone, touchZone.idleLayoutParams)
                activeViews.add(touchZone)
                Log.i(TAG, "Attached edge touch detector for $edge: $minFrom%-$maxTo%, width=${thicknessPx}px")
            } catch (t: Throwable) {
                Log.e(TAG, "Failed to attach touch detector for edge: $edge", t)
            }
        }
    }

    fun refreshOrientation() {
        if (lastTriggers.isNotEmpty() && lastIsEnabled) {
            updateOverlays(lastTriggers, lastIsEnabled, lastPullDistanceDp, lastHoldDurationMs)
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

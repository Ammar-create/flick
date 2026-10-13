package me.ammar.flick.service

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import me.ammar.flick.FlickApp
import me.ammar.flick.service.screenshot.ScreenshotManager

class FlickAccessibilityService : AccessibilityService() {

    companion object {
        private const val TAG = "FlickAccService"

        @Volatile
        var instance: FlickAccessibilityService? = null
            private set

        private val _isRunning = MutableStateFlow(false)
        val isRunning = _isRunning.asStateFlow()
    }

    private var overlayManager: EdgeOverlayManager? = null
    private val serviceScope = CoroutineScope(Dispatchers.Main)

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        _isRunning.value = true
        Log.i(TAG, "FlickAccessibilityService connected")

        NotificationHelper.createNotificationChannel(this)

        overlayManager = EdgeOverlayManager(this) { edge, gesture ->
            Log.i(TAG, "Detected gesture: $edge -> $gesture")
            FlickApp.instance.actionDispatcher.dispatchGesture(edge, gesture)
        }

        // Observe triggers and master service toggle
        serviceScope.launch {
            combine(
                FlickApp.instance.servicePreferences.triggers,
                FlickApp.instance.servicePreferences.isServiceEnabled
            ) { triggers, isEnabled ->
                triggers to isEnabled
            }.collectLatest { (triggers, isEnabled) ->
                overlayManager?.updateOverlays(triggers, isEnabled)
                val activeCount = if (isEnabled) triggers.size else 0
                val notification = NotificationHelper.buildNotification(this@FlickAccessibilityService, activeCount)
                val nm = getSystemService(NOTIFICATION_SERVICE) as android.app.NotificationManager
                nm.notify(NotificationHelper.NOTIFICATION_ID, notification)
            }
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // Accessibility event processing if needed
    }

    override fun onInterrupt() {
        Log.w(TAG, "Accessibility service interrupted")
    }

    override fun onUnbind(intent: Intent?): Boolean {
        Log.i(TAG, "FlickAccessibilityService unbound")
        instance = null
        _isRunning.value = false
        ScreenshotManager.dismissAllOverlays(this)
        overlayManager?.removeOverlays()
        serviceScope.cancel()
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        Log.i(TAG, "FlickAccessibilityService destroyed")
        instance = null
        _isRunning.value = false
        ScreenshotManager.dismissAllOverlays(this)
        overlayManager?.removeOverlays()
        serviceScope.cancel()
        super.onDestroy()
    }
}

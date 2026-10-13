package me.ammar.flick.engine

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import me.ammar.flick.data.ServicePreferences
import me.ammar.flick.model.TriggerModel
import me.ammar.flick.service.screenshot.ScreenshotManager

data class GestureEventLog(
    val triggerId: String,
    val edge: String,
    val gesture: String,
    val script: String,
    val timestamp: Long = System.currentTimeMillis(),
    val outcome: String
)

class ActionDispatcher(
    private val context: Context,
    private val servicePreferences: ServicePreferences,
    private val repoSyncManager: RepoSyncManager,
    private val accessibilityServiceSupplier: () -> AccessibilityService?
) {
    companion object {
        private const val TAG = "ActionDispatcher"
    }

    private val scope = CoroutineScope(Dispatchers.Default)
    private val bridge = ScriptBridge(context, accessibilityServiceSupplier)
    private val quickJsRunner = QuickJsRunner(bridge)

    private val _recentLogs = MutableStateFlow<List<GestureEventLog>>(emptyList())
    val recentLogs = _recentLogs.asStateFlow()

    val triggers: StateFlow<List<TriggerModel>> = servicePreferences.triggers

    fun dispatchGesture(edge: String, gesture: String) {
        scope.launch {
            val currentTriggers = triggers.value
            val matchedTrigger = currentTriggers.find {
                it.edge.equals(edge, ignoreCase = true) && it.gesture.equals(gesture, ignoreCase = true)
            } ?: currentTriggers.find {
                it.edge.equals(edge, ignoreCase = true) &&
                (gesture.startsWith("swipe") && it.gesture.startsWith("swipe"))
            } ?: currentTriggers.find {
                it.edge.equals(edge, ignoreCase = true)
            }

            if (matchedTrigger == null) {
                Log.d(TAG, "No trigger matched for edge: $edge, gesture: $gesture")
                return@launch
            }

            Log.i(TAG, "Dispatching trigger: ${matchedTrigger.id} -> ${matchedTrigger.script}")

            val extensionFile = repoSyncManager.getExtensionFile(matchedTrigger.script)
            val outcome: String

            if (extensionFile != null && extensionFile.exists()) {
                val scriptCode = extensionFile.readText()
                quickJsRunner.executeScript(scriptCode, matchedTrigger.args)
                outcome = "Executed ${matchedTrigger.script}.js via QuickJS"
            } else {
                outcome = executeV1Fallback(matchedTrigger)
            }

            addLog(
                GestureEventLog(
                    triggerId = matchedTrigger.id,
                    edge = matchedTrigger.edge,
                    gesture = matchedTrigger.gesture,
                    script = matchedTrigger.script,
                    outcome = outcome
                )
            )
        }
    }

    fun dispatchDirectAction(actionName: String) {
        scope.launch {
            Log.i(TAG, "Dispatching direct action: $actionName")
            val extensionFile = repoSyncManager.getExtensionFile(actionName)
            val outcome: String

            if (extensionFile != null && extensionFile.exists()) {
                val scriptCode = extensionFile.readText()
                quickJsRunner.executeScript(scriptCode)
                outcome = "Executed $actionName.js via QuickJS"
            } else {
                val dummyTrigger = TriggerModel(
                    id = "direct-$actionName",
                    edge = "overlay",
                    gesture = "tap",
                    fromPct = 0,
                    toPct = 100,
                    script = actionName
                )
                outcome = executeV1Fallback(dummyTrigger)
            }

            addLog(
                GestureEventLog(
                    triggerId = "direct",
                    edge = "overlay",
                    gesture = "tap",
                    script = actionName,
                    outcome = outcome
                )
            )
        }
    }

    private fun executeV1Fallback(trigger: TriggerModel): String {
        return when (trigger.script) {
            "snip-to-clipboard" -> {
                val service = accessibilityServiceSupplier()
                if (service != null) {
                    ScreenshotManager.triggerSnipWorkflow(service)
                    "Screenshot captured: 5s crop bar displayed"
                } else {
                    bridge.toast("Accessibility service not connected")
                    "Error: Service not connected"
                }
            }
            "ask-lastchat" -> {
                val text = bridge.getSelectedText().ifBlank { bridge.getClipboard() }
                if (text.isBlank()) {
                    bridge.toast("Select text first or copy text to clipboard")
                    "No text available for Ask LastChat"
                } else {
                    bridge.processText(
                        text = text,
                        targetPkg = "lastchat.rikkafork.cocolal",
                        targetCls = "me.rerere.rikkahub.ui.activity.TextSelectionActivity"
                    )
                    "Launched Ask LastChat with ${text.length} chars"
                }
            }
            "floating-toolbox" -> {
                val service = accessibilityServiceSupplier()
                if (service != null) {
                    val config = me.ammar.flick.service.overlay.OverlayMenuConfig(
                        title = "Flick Quick Actions",
                        subtitle = "Select an action to execute",
                        items = listOf(
                            me.ammar.flick.service.overlay.OverlayMenuItem(
                                id = "snip",
                                title = "Snip Screen to Clipboard",
                                subtitle = "In-memory crop & copy",
                                action = "snip-to-clipboard"
                            ),
                            me.ammar.flick.service.overlay.OverlayMenuItem(
                                id = "ask",
                                title = "Ask LastChat",
                                subtitle = "Process selected or clipboard text",
                                action = "ask-lastchat"
                            ),
                            me.ammar.flick.service.overlay.OverlayMenuItem(
                                id = "abdm",
                                title = "Add Download to AB",
                                subtitle = "Download URL from clipboard",
                                action = "abdm-add-download"
                            )
                        )
                    )
                    me.ammar.flick.service.overlay.FloatingOnScreenOverlayManager.show(service, config) { item ->
                        if (!item.action.isNullOrBlank()) {
                            dispatchDirectAction(item.action)
                        }
                    }
                    "Displayed on-screen toolbox"
                } else {
                    bridge.toast("Accessibility service not connected")
                    "Error: Service not connected"
                }
            }
            "abdm-add-download" -> {
                val clipText = bridge.getClipboard()
                if (clipText.isBlank() || !clipText.startsWith("http", ignoreCase = true)) {
                    bridge.toast("No http(s) URL on clipboard")
                    "Clipboard has no http(s) URL"
                } else {
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(clipText)).apply {
                        setClassName(
                            "com.abdownloadmanager",
                            "com.abdownloadmanager.android.pages.add.AddDownloadActivity"
                        )
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    try {
                        context.startActivity(intent)
                        bridge.toast("Opened AB Download Manager")
                        "Launched AddDownloadActivity"
                    } catch (t: Throwable) {
                        bridge.toast("Failed to launch AB Download Manager")
                        "Launch failed: ${t.message}"
                    }
                }
            }
            else -> {
                bridge.toast("Fired trigger ${trigger.id} -> ${trigger.script}")
                "Triggered: ${trigger.script}"
            }
        }
    }

    private fun addLog(log: GestureEventLog) {
        val current = _recentLogs.value.toMutableList()
        current.add(0, log)
        if (current.size > 20) {
            current.removeAt(current.size - 1)
        }
        _recentLogs.value = current
    }
}

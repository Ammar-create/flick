package me.ammar.flick.engine

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import android.widget.Toast
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import me.ammar.flick.model.TriggerModel
import java.io.File

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

    private val _triggers = MutableStateFlow<List<TriggerModel>>(emptyList())
    val triggers = _triggers.asStateFlow()

    init {
        loadTriggers()
    }

    fun loadTriggers() {
        try {
            // First check app-private storage, fallback to assets
            val configFile = File(context.filesDir, "triggers.json")
            val jsonString = if (configFile.exists()) {
                configFile.readText()
            } else {
                context.assets.open("triggers.json").bufferedReader().use { it.readText() }
            }
            val parsed = Json.decodeFromString<List<TriggerModel>>(jsonString)
            _triggers.value = parsed
        } catch (t: Throwable) {
            Log.w(TAG, "Failed to load triggers.json", t)
        }
    }

    fun dispatchGesture(edge: String, gesture: String) {
        scope.launch {
            val matchedTrigger = _triggers.value.find {
                it.edge.equals(edge, ignoreCase = true) && it.gesture.equals(gesture, ignoreCase = true)
            }

            if (matchedTrigger == null) {
                Log.d(TAG, "No trigger matched for edge: $edge, gesture: $gesture")
                return@launch
            }

            Log.i(TAG, "Dispatching trigger: ${matchedTrigger.id} -> ${matchedTrigger.script}")

            val extensionFile = repoSyncManager.getExtensionFile(matchedTrigger.script)
            val outcome: String

            if (extensionFile != null && extensionFile.exists()) {
                // Execute downloaded extension via QuickJS runtime
                val scriptCode = extensionFile.readText()
                quickJsRunner.executeScript(scriptCode, matchedTrigger.args)
                outcome = "Executed ${matchedTrigger.script}.js via QuickJS"
            } else {
                // V1 Skeleton Fallback: Execute hardcoded loop
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

    private fun executeV1Fallback(trigger: TriggerModel): String {
        return when (trigger.script) {
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
            "snip-to-clipboard" -> {
                bridge.toast("Snip to Clipboard requires capture.region() bridge call (V4)")
                "Stub: capture.region() pending V4"
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

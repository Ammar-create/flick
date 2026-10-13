package me.ammar.flick.data

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import me.ammar.flick.model.TriggerModel
import java.io.File

class ServicePreferences(private val context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("flick_prefs", Context.MODE_PRIVATE)
    private val json = Json { ignoreUnknownKeys = true; isLenient = true; prettyPrint = true }

    private val _isServiceEnabled = MutableStateFlow(prefs.getBoolean(KEY_SERVICE_ENABLED, true))
    val isServiceEnabled = _isServiceEnabled.asStateFlow()

    private val _triggers = MutableStateFlow<List<TriggerModel>>(loadTriggersInternal())
    val triggers = _triggers.asStateFlow()

    fun setServiceEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_SERVICE_ENABLED, enabled).apply()
        _isServiceEnabled.value = enabled
    }

    fun saveTriggers(triggersList: List<TriggerModel>) {
        try {
            val jsonString = json.encodeToString(triggersList)
            File(context.filesDir, "triggers.json").writeText(jsonString)
            _triggers.value = triggersList
        } catch (t: Throwable) {
            // Silently log
        }
    }

    fun addOrUpdateTrigger(trigger: TriggerModel) {
        val current = _triggers.value.toMutableList()
        val index = current.indexOfFirst { it.id == trigger.id }
        if (index >= 0) {
            current[index] = trigger
        } else {
            current.add(trigger)
        }
        saveTriggers(current)
    }

    fun deleteTrigger(triggerId: String) {
        val current = _triggers.value.toMutableList()
        current.removeAll { it.id == triggerId }
        saveTriggers(current)
    }

    private fun loadTriggersInternal(): List<TriggerModel> {
        val file = File(context.filesDir, "triggers.json")
        val jsonStr = if (file.exists()) {
            file.readText()
        } else {
            try {
                context.assets.open("triggers.json").bufferedReader().use { it.readText() }
            } catch (t: Throwable) {
                "[]"
            }
        }
        return try {
            json.decodeFromString(jsonStr)
        } catch (t: Throwable) {
            emptyList()
        }
    }

    companion object {
        private const val KEY_SERVICE_ENABLED = "flick_service_enabled"
    }
}

package me.ammar.flick.ui

import android.app.Application
import android.content.Context
import android.os.PowerManager
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import me.ammar.flick.FlickApp
import me.ammar.flick.engine.GestureEventLog
import me.ammar.flick.engine.ShizukuBridge
import me.ammar.flick.engine.SyncState
import me.ammar.flick.model.PluginEntry
import me.ammar.flick.model.TriggerModel
import me.ammar.flick.service.FlickAccessibilityService

data class MainUiState(
    val isAccessibilityActive: Boolean = false,
    val isShizukuAvailable: Boolean = false,
    val hasShizukuPermission: Boolean = false,
    val isBatteryOptimizedIgnored: Boolean = false,
    val triggers: List<TriggerModel> = emptyList(),
    val installedPlugins: List<PluginEntry> = emptyList(),
    val syncState: SyncState = SyncState.Idle,
    val recentLogs: List<GestureEventLog> = emptyList()
)

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val app = application as FlickApp
    private val repoSyncManager = app.repoSyncManager
    private val actionDispatcher = app.actionDispatcher

    private val _isBatteryIgnored = MutableStateFlow(checkBatteryExemption())
    val isBatteryIgnored = _isBatteryIgnored.asStateFlow()

    private data class ServiceStatus(
        val isAccessibilityActive: Boolean,
        val isShizukuAvailable: Boolean,
        val hasShizukuPermission: Boolean,
        val isBatteryOptimizedIgnored: Boolean
    )

    private val serviceStatusFlow = combine(
        FlickAccessibilityService.isRunning,
        ShizukuBridge.isAvailable,
        ShizukuBridge.hasPermission,
        _isBatteryIgnored
    ) { accRunning, shizukuAvail, shizukuPerm, batteryIgnored ->
        ServiceStatus(accRunning, shizukuAvail, shizukuPerm, batteryIgnored)
    }

    private val repoFlow = combine(
        repoSyncManager.installedPlugins,
        repoSyncManager.syncState
    ) { plugins, sync ->
        Pair(plugins, sync)
    }

    val uiState: StateFlow<MainUiState> = combine(
        serviceStatusFlow,
        actionDispatcher.triggers,
        repoFlow,
        actionDispatcher.recentLogs
    ) { status, triggers, repoData, logs ->
        val (plugins, sync) = repoData
        MainUiState(
            isAccessibilityActive = status.isAccessibilityActive,
            isShizukuAvailable = status.isShizukuAvailable,
            hasShizukuPermission = status.hasShizukuPermission,
            isBatteryOptimizedIgnored = status.isBatteryOptimizedIgnored,
            triggers = triggers,
            installedPlugins = plugins,
            syncState = sync,
            recentLogs = logs
        )
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        MainUiState()
    )

    fun refreshStatus() {
        ShizukuBridge.checkStatus()
        _isBatteryIgnored.value = checkBatteryExemption()
        actionDispatcher.loadTriggers()
    }

    fun requestShizukuPermission() {
        ShizukuBridge.requestPermission()
    }

    fun syncRepo(baseUrl: String = "http://127.0.0.1:8090") {
        viewModelScope.launch {
            repoSyncManager.syncRepo(baseUrl)
        }
    }

    fun testTrigger(trigger: TriggerModel) {
        actionDispatcher.dispatchGesture(trigger.edge, trigger.gesture)
    }

    private fun checkBatteryExemption(): Boolean {
        val pm = app.getSystemService(Context.POWER_SERVICE) as PowerManager
        return pm.isIgnoringBatteryOptimizations(app.packageName)
    }
}

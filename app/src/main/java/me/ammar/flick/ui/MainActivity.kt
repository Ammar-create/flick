package me.ammar.flick.ui

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.BatteryChargingFull
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.Extension
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Swipe
import androidx.compose.material.icons.outlined.Terminal
import androidx.compose.material.icons.outlined.TouchApp
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import me.ammar.flick.engine.SyncState
import me.ammar.flick.model.PluginEntry
import me.ammar.flick.model.TriggerModel
import me.ammar.flick.ui.components.GroupedCard
import me.ammar.flick.ui.components.GroupedPosition
import me.ammar.flick.ui.components.MorphingPill
import me.ammar.flick.ui.components.SquircleIconBadge
import me.ammar.flick.ui.components.StatusPill
import me.ammar.flick.ui.theme.AppShapes
import me.ammar.flick.ui.theme.AppSize
import me.ammar.flick.ui.theme.AppSpacing
import me.ammar.flick.ui.theme.FlickTheme
import me.ammar.flick.ui.theme.PlumOutline
import me.ammar.flick.ui.theme.StatusGreen
import me.ammar.flick.ui.theme.StatusOrange
import me.ammar.flick.ui.theme.StatusRed

class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            FlickTheme {
                MainScreen(viewModel)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        viewModel.refreshStatus()
    }
}

@Composable
fun MainScreen(viewModel: MainViewModel) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    LaunchedEffect(Unit) {
        viewModel.refreshStatus()
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = AppSpacing.md),
            verticalArrangement = Arrangement.spacedBy(AppSpacing.md)
        ) {
            item {
                Spacer(modifier = Modifier.height(AppSpacing.md))
                HeaderSection()
            }

            // Section 1: System and Service Status
            item {
                SectionTitle("SERVICE STATUS & PRIVILEGES")
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    // Accessibility Service
                    GroupedCard(
                        position = GroupedPosition.First,
                        onClick = {
                            if (!state.isAccessibilityActive) {
                                val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
                                context.startActivity(intent)
                            }
                        }
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            SquircleIconBadge(
                                imageVector = Icons.Outlined.TouchApp,
                                contentDescription = "Accessibility",
                                containerColor = if (state.isAccessibilityActive) MaterialTheme.colorScheme.surfaceContainerHighest else MaterialTheme.colorScheme.surfaceContainerHigh
                            )
                            Spacer(modifier = Modifier.width(AppSpacing.sm))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Accessibility Service",
                                    style = MaterialTheme.typography.titleSmall,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = if (state.isAccessibilityActive) "Monitoring edge touch strips" else "Tap to open Accessibility settings",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            StatusPill(
                                label = if (state.isAccessibilityActive) "Active" else "Disabled",
                                activeColor = if (state.isAccessibilityActive) StatusGreen else StatusRed
                            )
                        }
                    }

                    // Shizuku Privileges
                    GroupedCard(
                        position = GroupedPosition.Middle,
                        onClick = {
                            if (state.isShizukuAvailable && !state.hasShizukuPermission) {
                                viewModel.requestShizukuPermission()
                            }
                        }
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            SquircleIconBadge(
                                imageVector = Icons.Outlined.Terminal,
                                contentDescription = "Shizuku"
                            )
                            Spacer(modifier = Modifier.width(AppSpacing.sm))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Shizuku Privilege",
                                    style = MaterialTheme.typography.titleSmall,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = when {
                                        state.hasShizukuPermission -> "Privileged shell ready"
                                        state.isShizukuAvailable -> "Tap to request permission"
                                        else -> "Shizuku service not detected"
                                    },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            StatusPill(
                                label = when {
                                    state.hasShizukuPermission -> "Granted"
                                    state.isShizukuAvailable -> "Prompt"
                                    else -> "Offline"
                                },
                                activeColor = when {
                                    state.hasShizukuPermission -> StatusGreen
                                    state.isShizukuAvailable -> StatusOrange
                                    else -> StatusRed
                                }
                            )
                        }
                    }

                    // Battery Optimization
                    GroupedCard(
                        position = GroupedPosition.Last,
                        onClick = {
                            if (!state.isBatteryOptimizedIgnored) {
                                val intent = Intent(
                                    Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                                    Uri.parse("package:${context.packageName}")
                                )
                                context.startActivity(intent)
                            }
                        }
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            SquircleIconBadge(
                                imageVector = Icons.Outlined.BatteryChargingFull,
                                contentDescription = "Battery"
                            )
                            Spacer(modifier = Modifier.width(AppSpacing.sm))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Battery Exemption",
                                    style = MaterialTheme.typography.titleSmall,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = if (state.isBatteryOptimizedIgnored) "Protected against Android LMK" else "Tap to exempt from battery restrictions",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            StatusPill(
                                label = if (state.isBatteryOptimizedIgnored) "Exempt" else "Standard",
                                activeColor = if (state.isBatteryOptimizedIgnored) StatusGreen else StatusOrange
                            )
                        }
                    }
                }
            }

            // Section 2: Active Gesture Triggers
            item {
                SectionTitle("ACTIVE EDGE TRIGGERS")
                if (state.triggers.isEmpty()) {
                    GroupedCard(position = GroupedPosition.Single) {
                        Text(
                            text = "No triggers loaded from triggers.json",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        state.triggers.forEachIndexed { index, trigger ->
                            val pos = when {
                                state.triggers.size == 1 -> GroupedPosition.Single
                                index == 0 -> GroupedPosition.First
                                index == state.triggers.size - 1 -> GroupedPosition.Last
                                else -> GroupedPosition.Middle
                            }
                            TriggerItemCard(
                                trigger = trigger,
                                position = pos,
                                onTest = { viewModel.testTrigger(trigger) }
                            )
                        }
                    }
                }
            }

            // Section 3: Extension Repository & Local Sync
            item {
                SectionTitle("LOCAL EXTENSION REPOSITORY")
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    GroupedCard(position = GroupedPosition.First) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            SquircleIconBadge(
                                imageVector = Icons.Outlined.Code,
                                contentDescription = "Repo"
                            )
                            Spacer(modifier = Modifier.width(AppSpacing.sm))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "http://127.0.0.1:8090",
                                    style = MaterialTheme.typography.titleSmall,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = "Hot-reloads from workspace repo/ folder",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            when (state.syncState) {
                                is SyncState.Syncing -> {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(20.dp),
                                        strokeWidth = 2.dp,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                                is SyncState.Success -> {
                                    StatusPill(label = "Synced", activeColor = StatusGreen)
                                }
                                is SyncState.Error -> {
                                    StatusPill(label = "Failed", activeColor = StatusRed)
                                }
                                SyncState.Idle -> {
                                    StatusPill(label = "Ready", activeColor = StatusGreen)
                                }
                            }
                        }
                        Spacer(modifier = Modifier.height(AppSpacing.sm))
                        Row(modifier = Modifier.fillMaxWidth()) {
                            MorphingPill(
                                onClick = { viewModel.syncRepo() },
                                containerColor = MaterialTheme.colorScheme.primary,
                                contentColor = MaterialTheme.colorScheme.onPrimary,
                                idleWeight = 1f,
                                pressedWeight = 2f
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.Refresh,
                                    contentDescription = "Sync",
                                    modifier = Modifier.size(AppSize.IconSmall)
                                )
                                Spacer(modifier = Modifier.width(AppSpacing.xs))
                                Text(
                                    text = "Sync & Verify Plugins",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                    }

                    // Synced plugins list
                    if (state.installedPlugins.isNotEmpty()) {
                        state.installedPlugins.forEachIndexed { idx, plugin ->
                            val pos = if (idx == state.installedPlugins.size - 1) GroupedPosition.Last else GroupedPosition.Middle
                            PluginItemCard(plugin = plugin, position = pos)
                        }
                    } else {
                        GroupedCard(position = GroupedPosition.Last) {
                            Text(
                                text = "Run ./scripts/serve-repo.sh and tap Sync to hot-load plugins",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            // Section 4: Event Logs
            item {
                SectionTitle("GESTURE DISPATCH LOG")
                if (state.recentLogs.isEmpty()) {
                    GroupedCard(position = GroupedPosition.Single) {
                        Text(
                            text = "No gesture events dispatched yet",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        state.recentLogs.take(5).forEachIndexed { idx, log ->
                            val pos = when {
                                state.recentLogs.size == 1 -> GroupedPosition.Single
                                idx == 0 -> GroupedPosition.First
                                idx == 4 || idx == state.recentLogs.size - 1 -> GroupedPosition.Last
                                else -> GroupedPosition.Middle
                            }
                            GroupedCard(position = pos) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = "${log.edge.uppercase()} • ${log.gesture} → ${log.script}",
                                            style = MaterialTheme.typography.titleSmall,
                                            fontWeight = FontWeight.SemiBold,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                        Text(
                                            text = log.outcome,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            item {
                Spacer(modifier = Modifier.height(AppSpacing.xl))
            }
        }
    }
}

@Composable
private fun HeaderSection() {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "flick",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
            Text(
                text = "Edge Gestures & Extension Host",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        StatusPill(
            label = "V1 HOST",
            activeColor = MaterialTheme.colorScheme.primary,
            containerColor = MaterialTheme.colorScheme.surfaceContainerHighest
        )
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = 1.sp,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = AppSpacing.xs, bottom = AppSpacing.xxs)
    )
}

@Composable
private fun TriggerItemCard(
    trigger: TriggerModel,
    position: GroupedPosition,
    onTest: () -> Unit
) {
    GroupedCard(position = position) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            SquircleIconBadge(
                imageVector = Icons.Outlined.Swipe,
                contentDescription = "Trigger"
            )
            Spacer(modifier = Modifier.width(AppSpacing.sm))
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "${trigger.edge.replaceFirstChar { it.uppercase() }} ${trigger.gesture}",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.width(AppSpacing.xs))
                    Surface(
                        shape = AppShapes.Chip,
                        color = MaterialTheme.colorScheme.surfaceContainerHigh
                    ) {
                        Text(
                            text = "${trigger.fromPct}%-${trigger.toPct}%",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
                Text(
                    text = "Runs: ${trigger.script}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Surface(
                onClick = onTest,
                shape = AppShapes.ButtonPill,
                color = MaterialTheme.colorScheme.surfaceContainerHighest,
                contentColor = MaterialTheme.colorScheme.primary
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = AppSpacing.sm, vertical = AppSpacing.xxs),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Outlined.PlayArrow,
                        contentDescription = "Test",
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(text = "Test", style = MaterialTheme.typography.labelSmall)
                }
            }
        }
    }
}

@Composable
private fun PluginItemCard(
    plugin: PluginEntry,
    position: GroupedPosition
) {
    GroupedCard(position = position) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            SquircleIconBadge(
                imageVector = Icons.Outlined.Extension,
                contentDescription = "Plugin"
            )
            Spacer(modifier = Modifier.width(AppSpacing.sm))
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = plugin.name,
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.width(AppSpacing.xs))
                    Text(
                        text = "v${plugin.version}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                plugin.description?.let { desc ->
                    Text(
                        text = desc,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1
                    )
                }
            }
            StatusPill(
                label = "Verified",
                activeColor = StatusGreen
            )
        }
    }
}

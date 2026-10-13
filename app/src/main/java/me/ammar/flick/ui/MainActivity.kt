package me.ammar.flick.ui

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.BatteryChargingFull
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.Crop
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Extension
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.PowerSettingsNew
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Screenshot
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Swipe
import androidx.compose.material.icons.outlined.Terminal
import androidx.compose.material.icons.outlined.TouchApp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlin.math.max
import kotlin.math.min
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
    var selectedTab by remember { mutableIntStateOf(0) }
    var editingTrigger by remember { mutableStateOf<TriggerModel?>(null) }
    var isAddingNewTrigger by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        viewModel.refreshStatus()
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // Header with Master Switch
            HeaderSection(
                isMasterEnabled = state.isServiceMasterEnabled,
                onToggleMaster = { viewModel.toggleServiceMasterEnabled(it) }
            )

            // Navigation Tabs
            TabRow(
                selectedTabIndex = selectedTab,
                containerColor = MaterialTheme.colorScheme.background,
                contentColor = MaterialTheme.colorScheme.primary,
                indicator = { tabPositions ->
                    TabRowDefaults.SecondaryIndicator(
                        Modifier.tabIndicatorOffset(tabPositions[selectedTab]),
                        color = MaterialTheme.colorScheme.primary,
                        height = 3.dp
                    )
                },
                divider = {
                    Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(MaterialTheme.colorScheme.outlineVariant))
                }
            ) {
                Tab(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    text = { Text("Dashboard", style = MaterialTheme.typography.titleSmall) }
                )
                Tab(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    text = { Text("Zone Positions", style = MaterialTheme.typography.titleSmall) }
                )
                Tab(
                    selected = selectedTab == 2,
                    onClick = { selectedTab = 2 },
                    text = { Text("Repo & Plugins", style = MaterialTheme.typography.titleSmall) }
                )
            }

            // Tab Content
            when (selectedTab) {
                0 -> DashboardTab(state, viewModel, onNavigateToZones = { selectedTab = 1 })
                1 -> ZoneEditorTab(
                    triggers = state.triggers,
                    pullDistanceDp = state.pullDistanceDp,
                    holdDurationMs = state.holdDurationMs,
                    onUpdatePullDistance = { viewModel.setPullDistanceDp(it) },
                    onUpdateHoldDuration = { viewModel.setHoldDurationMs(it) },
                    onEdit = { editingTrigger = it },
                    onDelete = { viewModel.deleteTrigger(it.id) },
                    onAddNew = { isAddingNewTrigger = true },
                    onTest = { viewModel.testTrigger(it) }
                )
                2 -> RepoPluginsTab(state, viewModel)
            }
        }
    }

    // Trigger Edit/Add Dialog
    if (editingTrigger != null || isAddingNewTrigger) {
        val triggerToEdit = editingTrigger ?: TriggerModel(
            id = "trigger-${System.currentTimeMillis() % 10000}",
            edge = "right",
            gesture = "swipe-hold",
            fromPct = 25,
            toPct = 65,
            thicknessPx = 16,
            script = if (state.installedPlugins.isNotEmpty()) state.installedPlugins.first().id else "abdm-add-download"
        )

        TriggerEditDialog(
            trigger = triggerToEdit,
            availablePlugins = state.installedPlugins,
            isNew = isAddingNewTrigger,
            onDismiss = {
                editingTrigger = null
                isAddingNewTrigger = false
            },
            onSave = { updated ->
                viewModel.addOrUpdateTrigger(updated)
                editingTrigger = null
                isAddingNewTrigger = false
            }
        )
    }
}

@Composable
private fun HeaderSection(
    isMasterEnabled: Boolean,
    onToggleMaster: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = AppSpacing.md, vertical = AppSpacing.sm),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "Flick",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
            Text(
                text = "Edge Gestures & Extension Host",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        // Master Service Switch
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .background(MaterialTheme.colorScheme.surfaceContainerHigh, AppShapes.ButtonPill)
                .padding(horizontal = 12.dp, vertical = 4.dp)
        ) {
            Text(
                text = if (isMasterEnabled) "Active" else "Disabled",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.SemiBold,
                color = if (isMasterEnabled) StatusGreen else StatusRed
            )
            Spacer(modifier = Modifier.width(8.dp))
            Switch(
                checked = isMasterEnabled,
                onCheckedChange = onToggleMaster,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
                    checkedTrackColor = MaterialTheme.colorScheme.primary,
                    uncheckedThumbColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    uncheckedTrackColor = MaterialTheme.colorScheme.surfaceContainerHighest
                )
            )
        }
    }
}

@Composable
private fun DashboardTab(
    state: MainUiState,
    viewModel: MainViewModel,
    onNavigateToZones: () -> Unit
) {
    val context = LocalContext.current

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = AppSpacing.md),
        verticalArrangement = Arrangement.spacedBy(AppSpacing.md)
    ) {
        item {
            Spacer(modifier = Modifier.height(AppSpacing.xs))
            SectionTitle("SYSTEM STATUS & PRIVILEGES")
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
                            contentDescription = "Accessibility"
                        )
                        Spacer(modifier = Modifier.width(AppSpacing.sm))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Accessibility Service",
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = if (state.isAccessibilityActive) "Monitoring edge gesture zones" else "Tap to grant Accessibility permission",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        StatusPill(
                            label = if (state.isAccessibilityActive) "Granted" else "Disabled",
                            activeColor = if (state.isAccessibilityActive) StatusGreen else StatusRed
                        )
                    }
                }

                // Shizuku Privilege
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
                                text = "Shizuku Shell Access",
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = when {
                                    state.hasShizukuPermission -> "Privileged shell ready"
                                    state.isShizukuAvailable -> "Tap to authorize"
                                    else -> "Shizuku not running"
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        StatusPill(
                            label = when {
                                state.hasShizukuPermission -> "Authorized"
                                state.isShizukuAvailable -> "Request"
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

        // Test Feature Workflows Card
        item {
            SectionTitle("QUICK ACTION TESTS")
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                // Test Screenshot & Snip Workflow
                GroupedCard(
                    position = GroupedPosition.First,
                    onClick = { viewModel.testScreenshotWorkflow() }
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        SquircleIconBadge(
                            imageVector = Icons.Outlined.Screenshot,
                            contentDescription = "Snip"
                        )
                        Spacer(modifier = Modifier.width(AppSpacing.sm))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Test Screenshot & Snip",
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "Captures screen in-memory, shows 5s crop bar, never writes storage",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Surface(
                            shape = AppShapes.ButtonPill,
                            color = MaterialTheme.colorScheme.surfaceContainerHighest,
                            contentColor = MaterialTheme.colorScheme.primary
                        ) {
                            Text(
                                text = "Trigger",
                                style = MaterialTheme.typography.labelSmall,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                            )
                        }
                    }
                }

                // Test Zone Editor shortcut
                GroupedCard(
                    position = GroupedPosition.Last,
                    onClick = onNavigateToZones
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        SquircleIconBadge(
                            imageVector = Icons.Outlined.Swipe,
                            contentDescription = "Zones"
                        )
                        Spacer(modifier = Modifier.width(AppSpacing.sm))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Configure Trigger Positions",
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "${state.triggers.size} active zones • Invisible on other apps",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Surface(
                            shape = AppShapes.ButtonPill,
                            color = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary
                        ) {
                            Text(
                                text = "Edit",
                                style = MaterialTheme.typography.labelSmall,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                            )
                        }
                    }
                }
            }
        }

        // Live Event Logs
        item {
            SectionTitle("RECENT DISPATCH LOG")
            if (state.recentLogs.isEmpty()) {
                GroupedCard(position = GroupedPosition.Single) {
                    Text(
                        text = "No gestures fired yet. Swipe inward from configured zones to trigger.",
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
                            Column {
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

        item { Spacer(modifier = Modifier.height(AppSpacing.xl)) }
    }
}

@Composable
private fun ZoneEditorTab(
    triggers: List<TriggerModel>,
    pullDistanceDp: Int,
    holdDurationMs: Long,
    onUpdatePullDistance: (Int) -> Unit,
    onUpdateHoldDuration: (Long) -> Unit,
    onEdit: (TriggerModel) -> Unit,
    onDelete: (TriggerModel) -> Unit,
    onAddNew: () -> Unit,
    onTest: (TriggerModel) -> Unit
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = AppSpacing.md),
        verticalArrangement = Arrangement.spacedBy(AppSpacing.md)
    ) {
        item {
            Spacer(modifier = Modifier.height(AppSpacing.xs))
            SectionTitle("VISUAL ZONE POSITION PREVIEW")
            DeviceScreenSchematic(triggers = triggers)
        }

        item {
            SectionTitle("SWIPE SENSITIVITY & REACH")
            GroupedCard(position = GroupedPosition.Single) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "Swipe Inward Reach: ${pullDistanceDp}dp",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurface,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = "Controls how far into the screen you must pull your finger. Lower values require much less physical stretching (ideal for tablets).",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Slider(
                        value = pullDistanceDp.toFloat(),
                        onValueChange = { onUpdatePullDistance(it.toInt()) },
                        valueRange = 24f..100f,
                        colors = SliderDefaults.colors(
                            thumbColor = MaterialTheme.colorScheme.primary,
                            activeTrackColor = MaterialTheme.colorScheme.primary
                        )
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    Text(
                        text = "Hold Trigger Delay: ${holdDurationMs}ms",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurface,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = "How long you hold after swiping inward before releasing to activate the trigger.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Slider(
                        value = holdDurationMs.toFloat(),
                        onValueChange = { onUpdateHoldDuration(it.toLong()) },
                        valueRange = 300f..2000f,
                        colors = SliderDefaults.colors(
                            thumbColor = MaterialTheme.colorScheme.primary,
                            activeTrackColor = MaterialTheme.colorScheme.primary
                        )
                    )
                }
            }
        }

        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                SectionTitle("ACTIVE TRIGGER ZONES")
                Surface(
                    onClick = onAddNew,
                    shape = AppShapes.ButtonPill,
                    color = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Outlined.Add, contentDescription = "Add", modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Add Zone", style = MaterialTheme.typography.labelSmall)
                    }
                }
            }

            if (triggers.isEmpty()) {
                GroupedCard(position = GroupedPosition.Single) {
                    Text(
                        text = "No trigger zones configured. Tap 'Add Zone' above to create one.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    triggers.forEachIndexed { index, trigger ->
                        val pos = when {
                            triggers.size == 1 -> GroupedPosition.Single
                            index == 0 -> GroupedPosition.First
                            index == triggers.size - 1 -> GroupedPosition.Last
                            else -> GroupedPosition.Middle
                        }
                        TriggerEditorCard(
                            trigger = trigger,
                            position = pos,
                            onEdit = { onEdit(trigger) },
                            onDelete = { onDelete(trigger) },
                            onTest = { onTest(trigger) }
                        )
                    }
                }
            }
        }

        item {
            GroupedCard(position = GroupedPosition.Single) {
                Text(
                    text = "Note: Trigger zones are 100% invisible when outside this app. Swiping inward with a deliberate pull triggers the action. Short stationary touches do not activate to prevent accidental triggers and native system back clashes.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(modifier = Modifier.height(AppSpacing.xl))
        }
    }
}

@Composable
private fun DeviceScreenSchematic(triggers: List<TriggerModel>) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .height(260.dp),
        shape = AppShapes.CardLarge,
        color = MaterialTheme.colorScheme.surfaceContainer,
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            contentAlignment = Alignment.Center
        ) {
            // Simulated Device Canvas
            Box(
                modifier = Modifier
                    .width(170.dp)
                    .height(230.dp)
                    .background(Color(0xFF0E0B0E), RoundedCornerShape(16.dp))
                    .border(1.5.dp, Color(0xFF382C36), RoundedCornerShape(16.dp))
            ) {
                // Top Camera Notch
                Box(
                    modifier = Modifier
                        .size(6.dp)
                        .background(Color(0xFF382C36), CircleShape)
                        .align(Alignment.TopCenter)
                        .padding(top = 4.dp)
                )

                // Render active edge zones on schematic
                triggers.forEach { t ->
                    val minPct = t.fromPct / 100f
                    val maxPct = t.toPct / 100f
                    val span = (maxPct - minPct).coerceAtLeast(0.1f)

                    when (t.edge.lowercase()) {
                        "right" -> {
                            Box(
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .padding(top = (230 * minPct).dp)
                                    .width(6.dp)
                                    .height((230 * span).dp)
                                    .background(Color(0xFFD8B4D8), RoundedCornerShape(topStart = 4.dp, bottomStart = 4.dp))
                            )
                        }
                        "left" -> {
                            Box(
                                modifier = Modifier
                                    .align(Alignment.TopStart)
                                    .padding(top = (230 * minPct).dp)
                                    .width(6.dp)
                                    .height((230 * span).dp)
                                    .background(Color(0xFFD8B4D8), RoundedCornerShape(topEnd = 4.dp, bottomEnd = 4.dp))
                            )
                        }
                        "bottom" -> {
                            Box(
                                modifier = Modifier
                                    .align(Alignment.BottomStart)
                                    .padding(start = (170 * minPct).dp)
                                    .height(6.dp)
                                    .width((170 * span).dp)
                                    .background(Color(0xFFD8B4D8), RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp))
                            )
                        }
                    }
                }

                Text(
                    text = "Display Preview",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color(0xFF706070),
                    modifier = Modifier.align(Alignment.Center)
                )
            }
        }
    }
}

@Composable
private fun TriggerEditorCard(
    trigger: TriggerModel,
    position: GroupedPosition,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
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
                        text = "${trigger.edge.replaceFirstChar { it.uppercase() }} Edge",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Surface(
                        shape = AppShapes.Chip,
                        color = MaterialTheme.colorScheme.surfaceContainerHigh
                    ) {
                        Text(
                            text = "${trigger.fromPct}%-${trigger.toPct}% (${trigger.thicknessPx}dp)",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
                Text(
                    text = "${trigger.gesture} → ${trigger.script}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            Row {
                IconButton(onClick = onTest) {
                    Icon(Icons.Outlined.PlayArrow, contentDescription = "Test", tint = MaterialTheme.colorScheme.primary)
                }
                IconButton(onClick = onEdit) {
                    Icon(Icons.Outlined.Edit, contentDescription = "Edit", tint = MaterialTheme.colorScheme.onSurface)
                }
                IconButton(onClick = onDelete) {
                    Icon(Icons.Outlined.Delete, contentDescription = "Delete", tint = StatusRed)
                }
            }
        }
    }
}

@Composable
private fun RepoPluginsTab(
    state: MainUiState,
    viewModel: MainViewModel
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = AppSpacing.md),
        verticalArrangement = Arrangement.spacedBy(AppSpacing.md)
    ) {
        item {
            Spacer(modifier = Modifier.height(AppSpacing.xs))
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
                                text = "Served via ./scripts/serve-repo.sh",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        when (state.syncState) {
                            is SyncState.Syncing -> CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                            is SyncState.Success -> StatusPill("Synced", StatusGreen)
                            is SyncState.Error -> StatusPill("Failed", StatusRed)
                            SyncState.Idle -> StatusPill("Ready", StatusGreen)
                        }
                    }
                    Spacer(modifier = Modifier.height(AppSpacing.sm))
                    Row(modifier = Modifier.fillMaxWidth()) {
                        MorphingPill(
                            onClick = { viewModel.syncRepo() },
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary
                        ) {
                            Icon(Icons.Outlined.Refresh, contentDescription = "Sync", modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Sync Local Repo & Plugins", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }

                if (state.installedPlugins.isNotEmpty()) {
                    state.installedPlugins.forEachIndexed { idx, plugin ->
                        val pos = if (idx == state.installedPlugins.size - 1) GroupedPosition.Last else GroupedPosition.Middle
                        GroupedCard(position = pos) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                SquircleIconBadge(Icons.Outlined.Extension, contentDescription = "Plugin")
                                Spacer(modifier = Modifier.width(AppSpacing.sm))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(text = "${plugin.name} (v${plugin.version})", style = MaterialTheme.typography.titleSmall)
                                    Text(text = "ID: ${plugin.id}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                StatusPill("Verified", StatusGreen)
                            }
                        }
                    }
                } else {
                    GroupedCard(position = GroupedPosition.Last) {
                        Text("No plugins installed yet. Tap Sync above to pull from localhost.", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }

        item {
            GroupedCard(position = GroupedPosition.Single) {
                Text(
                    text = "Any new plugin (.js) dropped into repo/extensions/ and synced will immediately appear as an available option in the Trigger Zones editor.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(modifier = Modifier.height(AppSpacing.xl))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TriggerEditDialog(
    trigger: TriggerModel,
    availablePlugins: List<PluginEntry>,
    isNew: Boolean,
    onDismiss: () -> Unit,
    onSave: (TriggerModel) -> Unit
) {
    var edge by remember { mutableStateOf(trigger.edge) }
    var gesture by remember { mutableStateOf(trigger.gesture) }
    var script by remember { mutableStateOf(trigger.script) }
    var fromPct by remember { mutableFloatStateOf(trigger.fromPct.toFloat()) }
    var toPct by remember { mutableFloatStateOf(trigger.toPct.toFloat()) }
    var thickness by remember { mutableFloatStateOf(trigger.thicknessPx.toFloat()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (isNew) "Add Trigger Zone" else "Edit Trigger Zone", style = MaterialTheme.typography.titleMedium) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                // Edge Selection
                Text("Screen Edge", style = MaterialTheme.typography.labelMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("right", "left", "bottom").forEach { e ->
                        Surface(
                            onClick = { edge = e },
                            shape = AppShapes.ButtonPill,
                            color = if (edge == e) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHighest,
                            contentColor = if (edge == e) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
                        ) {
                            Text(e.replaceFirstChar { it.uppercase() }, modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp), style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }

                // Gesture Selection
                Text("Gesture Type", style = MaterialTheme.typography.labelMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("swipe-hold", "swipe-in", "swipe-up").forEach { g ->
                        Surface(
                            onClick = { gesture = g },
                            shape = AppShapes.ButtonPill,
                            color = if (gesture == g) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHighest,
                            contentColor = if (gesture == g) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
                        ) {
                            val label = when (g) {
                                "swipe-hold" -> "Swipe & Hold"
                                "swipe-in" -> "Swipe In"
                                else -> "Swipe Up"
                            }
                            Text(label, modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp), style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }

                // Action / Script Picker
                Text("Trigger Action / Plugin", style = MaterialTheme.typography.labelMedium)
                val actionOptions = mutableListOf("abdm-add-download", "snip-to-clipboard", "ask-lastchat", "floating-toolbox")
                availablePlugins.forEach { if (!actionOptions.contains(it.id)) actionOptions.add(it.id) }

                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    actionOptions.forEach { act ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(
                                    if (script == act) MaterialTheme.colorScheme.surfaceContainerHigh else Color.Transparent,
                                    RoundedCornerShape(8.dp)
                                )
                                .clickable { script = act }
                                .padding(horizontal = 8.dp, vertical = 6.dp)
                        ) {
                            Text(
                                text = when (act) {
                                    "abdm-add-download" -> "AB Download Manager (Add Download)"
                                    "snip-to-clipboard" -> "Snip Screenshot to Clipboard"
                                    "ask-lastchat" -> "Ask LastChat (Selected or Clipboard Text)"
                                    "floating-toolbox" -> "Floating On-Screen Toolbox"
                                    else -> act
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = if (script == act) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                                fontWeight = if (script == act) FontWeight.SemiBold else FontWeight.Normal
                            )
                        }
                    }
                }

                // Position Sliders
                Text("Start Position: ${fromPct.toInt()}%", style = MaterialTheme.typography.labelSmall)
                Slider(
                    value = fromPct,
                    onValueChange = { fromPct = min(it, toPct - 10f) },
                    valueRange = 0f..90f,
                    colors = SliderDefaults.colors(thumbColor = MaterialTheme.colorScheme.primary, activeTrackColor = MaterialTheme.colorScheme.primary)
                )

                Text("End Position: ${toPct.toInt()}%", style = MaterialTheme.typography.labelSmall)
                Slider(
                    value = toPct,
                    onValueChange = { toPct = max(it, fromPct + 10f) },
                    valueRange = 10f..100f,
                    colors = SliderDefaults.colors(thumbColor = MaterialTheme.colorScheme.primary, activeTrackColor = MaterialTheme.colorScheme.primary)
                )

                Text("Touch Zone Thickness: ${thickness.toInt()}dp (Recommended: 14-18dp)", style = MaterialTheme.typography.labelSmall)
                Slider(
                    value = thickness,
                    onValueChange = { thickness = it },
                    valueRange = 8f..32f,
                    colors = SliderDefaults.colors(thumbColor = MaterialTheme.colorScheme.primary, activeTrackColor = MaterialTheme.colorScheme.primary)
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onSave(
                        trigger.copy(
                            edge = edge,
                            gesture = gesture,
                            script = script,
                            fromPct = fromPct.toInt(),
                            toPct = toPct.toInt(),
                            thicknessPx = thickness.toInt()
                        )
                    )
                }
            ) {
                Text("Save Zone", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        },
        containerColor = MaterialTheme.colorScheme.surfaceContainer
    )
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

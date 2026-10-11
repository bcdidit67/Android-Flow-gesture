package com.flowgesture.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.flowgesture.app.R
import com.flowgesture.app.core.DaemonController
import com.flowgesture.app.core.RootManager
import com.flowgesture.app.core.ServiceBridge
import com.flowgesture.app.data.GestureDirection
import com.flowgesture.app.data.GestureSettings
import com.flowgesture.app.data.SettingsRepository
import com.flowgesture.app.service.GestureService
import com.flowgesture.app.ui.component.ActionPickerSheet
import com.flowgesture.app.ui.component.ServiceStatusCard
import com.flowgesture.app.ui.screen.AdvancedGesturesScreen
import com.flowgesture.app.ui.screen.GesturesTab
import com.flowgesture.app.ui.screen.SettingsTab
import com.flowgesture.app.ui.screen.fingersLabelOf
import kotlinx.coroutines.launch

private data class PickerTarget(val fingers: Int, val direction: GestureDirection)

/**
 * 应用骨架：
 * 一级 Tab（手势 / 设置）+ 主界面顶部的服务状态卡片 + 一个「高级手势」二级页
 * + 全局动作选择弹层。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FlowGestureApp() {
    val context = LocalContext.current
    val repository = remember { SettingsRepository.get(context) }
    val scope = rememberCoroutineScope()

    val settings by repository.settingsFlow.collectAsState(initial = emptyMap())
    val serviceRunning by ServiceBridge.serviceRunning.collectAsState()
    val daemonRunning by ServiceBridge.daemonRunning.collectAsState()
    val lastError by ServiceBridge.lastError.collectAsState()

    var rootAvailable by remember { mutableStateOf<Boolean?>(null) }
    var tab by remember { mutableIntStateOf(0) }
    var showAdvanced by remember { mutableStateOf(false) }
    var picker by remember { mutableStateOf<PickerTarget?>(null) }
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(Unit) {
        rootAvailable = RootManager.isRootAvailable()
        if (!ServiceBridge.serviceRunning.value) {
            ServiceBridge.daemonRunning.value = DaemonController.isDaemonRunning()
        }
    }

    LaunchedEffect(lastError) {
        val message = lastError
        if (!message.isNullOrBlank()) {
            ServiceBridge.lastError.value = null
            snackbarHostState.showSnackbar(message)
        }
    }

    BackHandler(enabled = showAdvanced) { showAdvanced = false }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = when {
                            showAdvanced -> "高级手势"
                            tab == 0 -> stringResource(R.string.tab_gestures)
                            else -> stringResource(R.string.tab_settings)
                        }
                    )
                }
            )
        },
        bottomBar = {
            if (!showAdvanced) {
                NavigationBar {
                    NavigationBarItem(
                        selected = tab == 0,
                        onClick = { tab = 0 },
                        icon = {
                            Icon(
                                painter = painterResource(R.drawable.ic_tab_gesture),
                                contentDescription = null,
                            )
                        },
                        label = { Text(stringResource(R.string.tab_gestures)) },
                    )
                    NavigationBarItem(
                        selected = tab == 1,
                        onClick = { tab = 1 },
                        icon = {
                            Icon(
                                painter = painterResource(R.drawable.ic_tab_settings),
                                contentDescription = null,
                            )
                        },
                        label = { Text(stringResource(R.string.tab_settings)) },
                    )
                }
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        Box(modifier = Modifier.padding(padding)) {
            when {
                showAdvanced -> AdvancedGesturesScreen(
                    settings = settings,
                    onBack = { showAdvanced = false },
                    onPick = { fingers, direction -> picker = PickerTarget(fingers, direction) },
                )

                tab == 0 -> Column(modifier = Modifier.fillMaxSize()) {
                    ServiceStatusCard(
                        rootAvailable = rootAvailable,
                        serviceRunning = serviceRunning,
                        daemonRunning = daemonRunning,
                        onStart = {
                            scope.launch {
                                if (!RootManager.isRootAvailable()) {
                                    ServiceBridge.lastError.value =
                                        context.getString(R.string.error_no_root)
                                } else {
                                    repository.setServiceEnabled(true)
                                    GestureService.start(context)
                                }
                            }
                        },
                        onStop = {
                            scope.launch {
                                repository.setServiceEnabled(false)
                                GestureService.stop(context)
                            }
                        },
                        onRestartDaemon = { GestureService.restartDaemon(context) },
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                    )
                    GesturesTab(
                        settings = settings,
                        onPick = { fingers, direction -> picker = PickerTarget(fingers, direction) },
                        onOpenAdvanced = { showAdvanced = true },
                        modifier = Modifier.weight(1f),
                    )
                }

                else -> SettingsTab(
                    repository = repository,
                    onMessage = { message ->
                        scope.launch { snackbarHostState.showSnackbar(message) }
                    },
                )
            }
        }
    }

    picker?.let { target ->
        val key = "g${target.fingers}_${target.direction.id}"
        val current: GestureSettings = settings[key] ?: GestureSettings()
        ActionPickerSheet(
            fingersLabel = fingersLabelOf(target.fingers),
            directionLabel = target.direction.label,
            current = current,
            onDismiss = { picker = null },
            onSelectAction = { action ->
                scope.launch { repository.setAction(target.fingers, target.direction, action) }
            },
            onSaveCustom = { command ->
                scope.launch {
                    repository.setCustomCommand(target.fingers, target.direction, command)
                }
            },
        )
    }
}
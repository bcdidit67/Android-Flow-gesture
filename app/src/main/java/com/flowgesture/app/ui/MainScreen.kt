package com.flowgesture.app.ui

import android.os.Build
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.flowgesture.app.core.ActionType
import com.flowgesture.app.core.DaemonController
import com.flowgesture.app.core.NativeBridge
import com.flowgesture.app.core.RootManager
import com.flowgesture.app.core.ServiceBridge
import com.flowgesture.app.data.GestureDirection
import com.flowgesture.app.data.GestureSettings
import com.flowgesture.app.data.SettingsRepository
import com.flowgesture.app.service.GestureService
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * 主界面：
 * - 运行状态：Root / 前台服务 / 守护进程 与开关；
 * - 外观：动态取色（Material You）开关；
 * - 手势映射：三指 / 四指 / 五指 × 点击与四向轻扫；
 * - 运行日志：守护进程日志尾部。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen() {
    val context = LocalContext.current
    val repository = remember { SettingsRepository.get(context) }
    val scope = rememberCoroutineScope()

    val settings by repository.settingsFlow.collectAsState(initial = emptyMap())
    val dynamicColor by repository.dynamicColorFlow.collectAsState(initial = true)
    val landscapeMirror by repository.landscapeMirrorFlow.collectAsState(initial = false)
    val serviceRunning by ServiceBridge.serviceRunning.collectAsState()
    val daemonRunning by ServiceBridge.daemonRunning.collectAsState()
    val lastError by ServiceBridge.lastError.collectAsState()

    var rootAvailable by remember { mutableStateOf<Boolean?>(null) }
    var selectedFingers by remember { mutableIntStateOf(3) }
    var logText by remember { mutableStateOf("") }

    // 首次进入时探测 Root 与守护进程状态
    LaunchedEffect(Unit) {
        rootAvailable = RootManager.isRootAvailable()
        if (!ServiceBridge.serviceRunning.value) {
            ServiceBridge.daemonRunning.value = DaemonController.isDaemonRunning()
        }
    }

    // 周期性刷新守护进程日志
    LaunchedEffect(Unit) {
        while (true) {
            logText = DaemonController.readLogTail(context)
            delay(3000)
        }
    }

    Scaffold(
        topBar = { TopAppBar(title = { Text("Flow Gesture 手势增强") }) },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            StatusCard(
                rootAvailable = rootAvailable,
                serviceRunning = serviceRunning,
                daemonRunning = daemonRunning,
                onStartService = {
                    scope.launch {
                        // 未获取 Root 时不启动服务：守护进程必然失败，
                        // 也避免无谓的前台服务与通知打扰用户
                        if (!RootManager.isRootAvailable()) {
                            ServiceBridge.lastError.value = "未获取 Root 权限：手势服务无法启动"
                        } else {
                            repository.setServiceEnabled(true)
                            GestureService.start(context)
                        }
                    }
                },
                onStopService = {
                    scope.launch {
                        repository.setServiceEnabled(false)
                        GestureService.stop(context)
                    }
                },
                onRestartDaemon = { GestureService.restartDaemon(context) },
            )

            AppearanceCard(
                dynamicColor = dynamicColor,
                supported = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S,
                onToggle = { enabled ->
                    scope.launch { repository.setDynamicColor(enabled) }
                },
                landscapeMirror = landscapeMirror,
                onToggleLandscapeMirror = { enabled ->
                    scope.launch { repository.setLandscapeMirror(enabled) }
                },
            )

            MappingCard(
                settings = settings,
                selectedFingers = selectedFingers,
                onSelectFingers = { selectedFingers = it },
                onActionChange = { fingers, direction, action ->
                    scope.launch { repository.setAction(fingers, direction, action) }
                },
                onCustomChange = { fingers, direction, command ->
                    scope.launch { repository.setCustomCommand(fingers, direction, command) }
                },
            )

            lastError?.let { error ->
                Card(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = "⚠ $error",
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(16.dp),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }

            LogCard(logText = logText)

            Text(
                text = "native core: ${NativeBridge.version()}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.outline,
            )
        }
    }
}

@Composable
private fun StatusCard(
    rootAvailable: Boolean?,
    serviceRunning: Boolean,
    daemonRunning: Boolean,
    onStartService: () -> Unit,
    onStopService: () -> Unit,
    onRestartDaemon: () -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text("运行状态", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            StatusLine(
                label = "Root 权限",
                value = when (rootAvailable) {
                    null -> "检测中…"
                    true -> "已获取"
                    false -> "未获取"
                },
                ok = rootAvailable == true,
            )
            StatusLine(
                label = "前台服务",
                value = if (serviceRunning) "运行中" else "未运行",
                ok = serviceRunning,
            )
            StatusLine(
                label = "手势守护进程",
                value = if (daemonRunning) "运行中" else "未运行",
                ok = daemonRunning,
            )

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onStartService) { Text("启动服务") }
                OutlinedButton(onClick = onStopService) { Text("停止服务") }
                OutlinedButton(onClick = onRestartDaemon) { Text("重启守护") }
            }

            if (rootAvailable == false) {
                Text(
                    text = "未获取 Root 权限时无法读取触摸设备，请先在 Root 管理器中授权本应用。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }
    }
}

@Composable
private fun StatusLine(label: String, value: String, ok: Boolean) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(text = "$label: ", style = MaterialTheme.typography.bodyMedium)
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            color = if (ok) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
        )
    }
}

@Composable
private fun AppearanceCard(
    dynamicColor: Boolean,
    supported: Boolean,
    onToggle: (Boolean) -> Unit,
    landscapeMirror: Boolean,
    onToggleLandscapeMirror: (Boolean) -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text("外观", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("动态取色", style = MaterialTheme.typography.bodyLarge)
                    Text(
                        text = if (supported) {
                            "跟随系统壁纸（Material You）"
                        } else {
                            "需要 Android 12 及以上"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.outline,
                    )
                }
                Switch(
                    checked = dynamicColor && supported,
                    onCheckedChange = onToggle,
                    enabled = supported,
                )
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 2.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("横屏方向镜像", style = MaterialTheme.typography.bodyLarge)
                    Text(
                        text = "横屏下左右 / 上下判定相反时开启（快速纠偏）",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.outline,
                    )
                }
                Switch(
                    checked = landscapeMirror,
                    onCheckedChange = onToggleLandscapeMirror,
                )
            }
        }
    }
}

@Composable
private fun MappingCard(
    settings: Map<String, GestureSettings>,
    selectedFingers: Int,
    onSelectFingers: (Int) -> Unit,
    onActionChange: (Int, GestureDirection, ActionType) -> Unit,
    onCustomChange: (Int, GestureDirection, String) -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text("手势映射", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)

            TabRow(selectedTabIndex = selectedFingers - SettingsRepository.MIN_FINGERS) {
                for (fingers in SettingsRepository.MIN_FINGERS..SettingsRepository.MAX_FINGERS) {
                    Tab(
                        selected = selectedFingers == fingers,
                        onClick = { onSelectFingers(fingers) },
                        text = { Text("$fingers 指") },
                    )
                }
            }

            for (direction in GestureDirection.entries) {
                val key = "g${selectedFingers}_${direction.id}"
                val current = settings[key] ?: GestureSettings()
                GestureRow(
                    rowKey = key,
                    direction = direction,
                    current = current,
                    onActionChange = { action -> onActionChange(selectedFingers, direction, action) },
                    onCustomChange = { command -> onCustomChange(selectedFingers, direction, command) },
                )
            }
        }
    }
}

@Composable
private fun GestureRow(
    rowKey: String,
    direction: GestureDirection,
    current: GestureSettings,
    onActionChange: (ActionType) -> Unit,
    onCustomChange: (String) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = direction.label,
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.bodyLarge,
            )
            Box {
                OutlinedButton(onClick = { expanded = true }) {
                    Text(current.action.label)
                }
                DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                    ActionType.entries.forEach { action ->
                        DropdownMenuItem(
                            text = { Text(action.label) },
                            onClick = {
                                expanded = false
                                onActionChange(action)
                            },
                        )
                    }
                }
            }
        }

        if (current.action == ActionType.CUSTOM) {
            var text by remember(rowKey) { mutableStateOf(current.customCommand) }
            LaunchedEffect(text) {
                if (text != current.customCommand) {
                    delay(600)
                    onCustomChange(text)
                }
            }
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp),
                label = { Text("Shell 命令（以 Root 身份执行）") },
                singleLine = true,
            )
        }

        HorizontalDivider(modifier = Modifier.padding(vertical = 6.dp))
    }
}

@Composable
private fun LogCard(logText: String) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = "运行日志（最后 200 行）",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = logText.ifBlank { "暂无日志。启动服务后，这里会显示守护进程的触摸与手势记录。" },
                style = MaterialTheme.typography.bodySmall.copy(
                    fontFamily = FontFamily.Monospace,
                    fontSize = 11.sp,
                ),
                maxLines = 20,
            )
        }
    }
}
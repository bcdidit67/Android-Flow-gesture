package com.flowgesture.app.ui.screen

import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.flowgesture.app.R
import com.flowgesture.app.core.DaemonController
import com.flowgesture.app.core.NativeBridge
import com.flowgesture.app.core.RotationReporter
import com.flowgesture.app.data.SettingsRepository
import com.flowgesture.app.ui.component.SectionHeader
import com.flowgesture.app.ui.component.SettingRow
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private const val GITHUB_URL = "https://github.com/bcdidit67/Android-Flow-gesture"

/**
 * 设置页（单页）：外观 / 方向校正 / 手势映射 / 行为 / 诊断与日志 / 关于。
 * 服务状态与启停已移到主界面顶部的状态卡片。
 */
@Composable
fun SettingsTab(
    repository: SettingsRepository,
    onMessage: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val dynamicColor by repository.dynamicColorFlow.collectAsState(initial = true)
    val landscapeFlip by repository.landscapeFlipFlow.collectAsState(initial = false)
    val landscapeRotation by repository.landscapeRotationFlow.collectAsState(initial = -1)
    val bootAutoStart by repository.bootAutoStartFlow.collectAsState(initial = true)

    var logExpanded by remember { mutableStateOf(false) }
    var logPaused by remember { mutableStateOf(false) }
    var logText by remember { mutableStateOf("") }
    var systemRotation by remember { mutableIntStateOf(0) }

    val versionName = remember {
        runCatching {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName
        }.getOrNull() ?: "-"
    }

    // 实时显示系统旋转（诊断用）
    LaunchedEffect(Unit) {
        while (true) {
            systemRotation = RotationReporter.currentRotation(context)
            delay(1000)
        }
    }

    // 仅在日志展开且未暂停时轮询
    LaunchedEffect(logExpanded, logPaused) {
        if (logExpanded && !logPaused) {
            while (true) {
                logText = DaemonController.readLogTail(context)
                delay(2000)
            }
        }
    }

    val effectiveRotation = effectiveRotation(systemRotation, landscapeRotation, landscapeFlip)
    val rotationLabel = rotationLabelOf(landscapeRotation)

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(bottom = 24.dp),
    ) {
        // ---------------- 外观 ----------------
        SectionHeader("外观")
        val dynamicSupported = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
        SettingRow(
            title = "动态取色",
            description = if (dynamicSupported) {
                stringResource(R.string.settings_dynamic_color_desc)
            } else {
                stringResource(R.string.settings_dynamic_color_unsupported)
            },
            onClick = {
                if (dynamicSupported) {
                    scope.launch { repository.setDynamicColor(!dynamicColor) }
                }
            },
            trailing = {
                Switch(
                    checked = dynamicColor && dynamicSupported,
                    enabled = dynamicSupported,
                    onCheckedChange = { enabled ->
                        scope.launch { repository.setDynamicColor(enabled) }
                    },
                )
            },
        )

        // ---------------- 方向校正 ----------------
        SectionHeader("方向校正")
        var rotationMenu by remember { mutableStateOf(false) }
        SettingRow(
            title = "横屏旋转档位",
            description = "仅作用于横屏；竖屏始终按 0° 处理",
            onClick = { rotationMenu = true },
            trailing = {
                Box {
                    TextButton(onClick = { rotationMenu = true }) {
                        Text(rotationLabel)
                    }
                    DropdownMenu(
                        expanded = rotationMenu,
                        onDismissRequest = { rotationMenu = false },
                    ) {
                        ROTATION_OPTIONS.forEach { (value, label) ->
                            DropdownMenuItem(
                                text = { Text(label) },
                                onClick = {
                                    rotationMenu = false
                                    scope.launch { repository.setLandscapeRotation(value) }
                                },
                            )
                        }
                    }
                }
            },
        )
        SettingRow(
            title = "横屏左右反向",
            description = "横屏下左右 / 上下方向相反时开启",
            onClick = { scope.launch { repository.setLandscapeFlip(!landscapeFlip) } },
            trailing = {
                Switch(
                    checked = landscapeFlip,
                    onCheckedChange = { enabled ->
                        scope.launch { repository.setLandscapeFlip(enabled) }
                    },
                )
            },
        )
        Text(
            text = stringResource(
                R.string.settings_rotation_state,
                systemRotation,
                effectiveRotation,
            ),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.outline,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
        )

        // ---------------- 手势映射 ----------------
        SectionHeader("手势映射")
        SettingRow(
            title = "恢复默认映射",
            description = "清空全部映射，仅保留「三指上滑 / 三指下滑」",
            onClick = {
                scope.launch {
                    repository.resetMappings()
                    onMessage("已恢复默认映射")
                }
            },
            trailing = {
                Icon(
                    painter = painterResource(R.drawable.ic_chevron_right),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.outline,
                    modifier = Modifier.size(18.dp),
                )
            },
        )

        // ---------------- 行为 ----------------
        SectionHeader("行为")
        SettingRow(
            title = "开机自启",
            description = "仅在手动开启过服务后生效",
            onClick = { scope.launch { repository.setBootAutoStart(!bootAutoStart) } },
            trailing = {
                Switch(
                    checked = bootAutoStart,
                    onCheckedChange = { enabled ->
                        scope.launch { repository.setBootAutoStart(enabled) }
                    },
                )
            },
        )

        // ---------------- 诊断与日志 ----------------
        SectionHeader("诊断与日志")
        SettingRow(
            title = "手势日志",
            description = if (logExpanded) "展开时每 2 秒刷新" else "默认折叠，展开后才读取",
            onClick = { logExpanded = !logExpanded },
            trailing = {
                Text(
                    text = if (logExpanded) "收起" else "展开",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
            },
        )
        if (logExpanded) {
            Card(
                shape = androidx.compose.foundation.shape.RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text(
                        text = logText.ifBlank {
                            "暂无日志。启动服务后，这里会显示触摸与手势记录。"
                        },
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.sp,
                        ),
                        maxLines = 40,
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp),
                        horizontalArrangement = Arrangement.End,
                    ) {
                        TextButton(onClick = { logPaused = !logPaused }) {
                            Text(if (logPaused) "继续" else "暂停")
                        }
                        TextButton(onClick = { logText = "" }) {
                            Text("清空")
                        }
                    }
                }
            }
        }

        // ---------------- 关于 ----------------
        SectionHeader("关于")
        SettingRow(
            title = "Flow Gesture",
            description = "版本 $versionName · native core ${NativeBridge.version()}",
        )
        SettingRow(
            title = "GitHub 开源地址",
            description = GITHUB_URL,
            onClick = {
                runCatching {
                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(GITHUB_URL)))
                }.onFailure { onMessage("无法打开浏览器") }
            },
            trailing = {
                Icon(
                    painter = painterResource(R.drawable.ic_chevron_right),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.outline,
                    modifier = Modifier.size(18.dp),
                )
            },
        )
        SettingRow(
            title = "开源许可",
            description = stringResource(R.string.settings_license),
        )
    }
}

private val ROTATION_OPTIONS = listOf(
    -1 to "自动",
    0 to "强制 0°",
    1 to "强制 90°",
    2 to "强制 180°",
    3 to "强制 270°",
)

private fun rotationLabelOf(value: Int): String =
    ROTATION_OPTIONS.firstOrNull { it.first == value }?.second ?: "自动"

/** 与守护进程的变换逻辑保持一致，便于用户核对 */
private fun effectiveRotation(systemRotation: Int, landscapeRotation: Int, flip: Boolean): Int {
    if (systemRotation != 1 && systemRotation != 3) return 0
    var rot = if (landscapeRotation in 0..3) landscapeRotation else systemRotation
    if (flip && (rot == 1 || rot == 3)) rot = if (rot == 1) 3 else 1
    return rot
}
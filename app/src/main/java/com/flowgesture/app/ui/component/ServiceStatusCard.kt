package com.flowgesture.app.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/**
 * 服务状态卡片（主界面顶部常驻）。
 *
 * 一眼看到服务状态 + 直接启停，无需进入设置页。
 */
@Composable
fun ServiceStatusCard(
    rootAvailable: Boolean?,
    serviceRunning: Boolean,
    daemonRunning: Boolean,
    onStart: () -> Unit,
    onStop: () -> Unit,
    onRestartDaemon: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val noRoot = rootAvailable == false
    val container = when {
        noRoot -> MaterialTheme.colorScheme.errorContainer
        serviceRunning -> MaterialTheme.colorScheme.secondaryContainer
        else -> MaterialTheme.colorScheme.surfaceVariant
    }
    val dotColor = when {
        noRoot -> MaterialTheme.colorScheme.error
        serviceRunning -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.outline
    }
    val title = when {
        noRoot -> "无法启动手势服务"
        serviceRunning -> "手势服务运行中"
        else -> "手势服务未运行"
    }
    val subtitle = when {
        rootAvailable == null -> "正在检测 Root 权限…"
        noRoot -> "未获取 Root 权限"
        daemonRunning -> "Root 已授权 · 守护进程正常"
        else -> "Root 已授权 · 守护进程未运行"
    }

    Card(
        colors = CardDefaults.cardColors(containerColor = container),
        shape = RoundedCornerShape(20.dp),
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .background(dotColor, CircleShape)
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                if (serviceRunning) {
                    Button(onClick = onStop) { Text("停止服务") }
                } else {
                    Button(onClick = onStart, enabled = !noRoot) { Text("启动服务") }
                }
                FilledTonalButton(onClick = onRestartDaemon) { Text("重启守护") }
            }

            if (noRoot) {
                Text(
                    text = "需要 Root 权限才能读取触摸设备，请在 Root 管理器中授权本应用。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }
    }
}
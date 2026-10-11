package com.flowgesture.app.ui.screen

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.flowgesture.app.R
import com.flowgesture.app.core.ActionType
import com.flowgesture.app.data.GestureDirection
import com.flowgesture.app.data.GestureSettings
import com.flowgesture.app.data.SettingsRepository

/**
 * 高级手势页：三 / 四 / 五指各一张卡片，卡内折叠展开全部映射，一页装完。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdvancedGesturesScreen(
    settings: Map<String, GestureSettings>,
    onBack: () -> Unit,
    onPick: (Int, GestureDirection) -> Unit,
) {
    val expanded = remember { mutableStateMapOf(3 to true, 4 to false, 5 to false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("高级手势") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            painter = painterResource(R.drawable.ic_chevron_right),
                            contentDescription = "返回",
                            modifier = Modifier
                                .size(22.dp)
                                .rotate(180f),
                        )
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            for (fingers in SettingsRepository.MIN_FINGERS..SettingsRepository.MAX_FINGERS) {
                val open = expanded[fingers] ?: false

                Card(
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    ),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { expanded[fingers] = !open }
                            .padding(horizontal = 20.dp, vertical = 16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "${fingersLabelOf(fingers)}手势",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                            )
                            Text(
                                text = summaryOf(settings, fingers),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Icon(
                            painter = painterResource(R.drawable.ic_expand_more),
                            contentDescription = if (open) "收起" else "展开",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier
                                .size(22.dp)
                                .rotate(if (open) 0f else -90f),
                        )
                    }

                    if (open) {
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        for (direction in ALL_ORDER) {
                            val action = settings["g${fingers}_${direction.id}"]?.action
                                ?: ActionType.NONE
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onPick(fingers, direction) }
                                    .padding(horizontal = 20.dp, vertical = 14.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                Text(
                                    text = direction.label,
                                    style = MaterialTheme.typography.bodyLarge,
                                    modifier = Modifier.weight(1f),
                                )
                                Text(
                                    text = action.label,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = if (action == ActionType.NONE) {
                                        MaterialTheme.colorScheme.outline
                                    } else {
                                        MaterialTheme.colorScheme.primary
                                    },
                                )
                                Icon(
                                    painter = painterResource(R.drawable.ic_chevron_right),
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.outline,
                                    modifier = Modifier.size(18.dp),
                                )
                            }
                        }
                        Column(modifier = Modifier.padding(bottom = 6.dp)) {}
                    }
                }
            }

            Text(
                text = "此处与手势总览页共享同一份映射，改动即时生效。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.outline,
                modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp),
            )
        }
    }
}

private val ALL_ORDER = listOf(
    GestureDirection.TAP,
    GestureDirection.UP,
    GestureDirection.DOWN,
    GestureDirection.LEFT,
    GestureDirection.RIGHT,
)

/** 折叠状态下也有信息：列出已配置的项 */
private fun summaryOf(settings: Map<String, GestureSettings>, fingers: Int): String {
    val parts = listOf(
        GestureDirection.UP,
        GestureDirection.DOWN,
        GestureDirection.LEFT,
        GestureDirection.RIGHT,
    ).mapNotNull { direction ->
        val action = settings["g${fingers}_${direction.id}"]?.action ?: ActionType.NONE
        if (action == ActionType.NONE) null else "${shortLabelOf(direction)}${action.label}"
    }
    return if (parts.isEmpty()) "尚未配置手势" else parts.joinToString(" · ")
}

private fun shortLabelOf(direction: GestureDirection): String = when (direction) {
    GestureDirection.UP -> "上滑 "
    GestureDirection.DOWN -> "下滑 "
    GestureDirection.LEFT -> "左滑 "
    GestureDirection.RIGHT -> "右滑 "
    GestureDirection.TAP -> "点击 "
}
package com.flowgesture.app.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.flowgesture.app.R
import com.flowgesture.app.core.ActionType
import com.flowgesture.app.data.GestureDirection
import com.flowgesture.app.data.GestureSettings
import com.flowgesture.app.data.SettingsRepository
import com.flowgesture.app.ui.component.GesturePreview

/**
 * 手势总览页：三指 / 四指 / 五指**依次排列在同一页**（对齐 Windows 触摸板）。
 * 每张卡片 = 触点示意图 + 四向轻扫 + 点击；点任意一行即可修改动作。
 */
@Composable
fun GesturesTab(
    settings: Map<String, GestureSettings>,
    onPick: (Int, GestureDirection) -> Unit,
    onOpenAdvanced: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp)
            .padding(bottom = 20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        for (fingers in SettingsRepository.MIN_FINGERS..SettingsRepository.MAX_FINGERS) {
            GestureCard(
                fingers = fingers,
                settings = settings,
                onPick = onPick,
            )
        }

        FilledTonalButton(
            onClick = onOpenAdvanced,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("高级手势")
            Icon(
                painter = painterResource(R.drawable.ic_chevron_right),
                contentDescription = null,
                modifier = Modifier
                    .padding(start = 6.dp)
                    .size(18.dp),
            )
        }

        Text(
            text = "手势在系统任意界面生效；改动即时保存，无需重启服务。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.outline,
            modifier = Modifier.padding(horizontal = 4.dp),
        )
    }
}

@Composable
private fun GestureCard(
    fingers: Int,
    settings: Map<String, GestureSettings>,
    onPick: (Int, GestureDirection) -> Unit,
) {
    var highlight by remember { mutableStateOf<String?>(null) }

    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        ),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = titleFor(fingers),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )

            Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                GesturePreview(
                    fingers = fingers,
                    highlight = highlight,
                    modifier = Modifier
                        .width(112.dp)
                        .height(92.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                        .padding(4.dp),
                )

                Column(modifier = Modifier.weight(1f)) {
                    for (direction in SWIPE_ORDER) {
                        DirectionRow(
                            direction = direction,
                            action = actionOf(settings, fingers, direction),
                            onClick = {
                                highlight = direction.id
                                onPick(fingers, direction)
                            },
                        )
                    }
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

            DirectionRow(
                direction = GestureDirection.TAP,
                action = actionOf(settings, fingers, GestureDirection.TAP),
                onClick = {
                    highlight = null
                    onPick(fingers, GestureDirection.TAP)
                },
            )
        }
    }
}

@Composable
private fun DirectionRow(
    direction: GestureDirection,
    action: ActionType,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 7.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(
            text = arrowOf(direction),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.width(18.dp),
        )
        Text(
            text = direction.label,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = action.label,
            style = MaterialTheme.typography.bodyMedium,
            color = if (action == ActionType.NONE) {
                MaterialTheme.colorScheme.outline
            } else {
                MaterialTheme.colorScheme.onSurface
            },
        )
        Icon(
            painter = painterResource(R.drawable.ic_chevron_right),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.outline,
            modifier = Modifier.size(16.dp),
        )
    }
}

private val SWIPE_ORDER = listOf(
    GestureDirection.UP,
    GestureDirection.DOWN,
    GestureDirection.LEFT,
    GestureDirection.RIGHT,
)

/** 手指数中文名（供弹层标题等复用） */
internal fun fingersLabelOf(fingers: Int): String = when (fingers) {
    3 -> "三指"
    4 -> "四指"
    else -> "五指"
}

private fun titleFor(fingers: Int): String = when (fingers) {
    3 -> "三指手势"
    4 -> "四指手势"
    else -> "五指手势"
}

private fun arrowOf(direction: GestureDirection): String = when (direction) {
    GestureDirection.TAP -> "●"
    GestureDirection.UP -> "↑"
    GestureDirection.DOWN -> "↓"
    GestureDirection.LEFT -> "←"
    else -> "→"
}

private fun actionOf(
    settings: Map<String, GestureSettings>,
    fingers: Int,
    direction: GestureDirection,
): ActionType = settings["g${fingers}_${direction.id}"]?.action ?: ActionType.NONE
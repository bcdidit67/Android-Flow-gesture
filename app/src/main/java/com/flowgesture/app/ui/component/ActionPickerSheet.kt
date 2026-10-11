package com.flowgesture.app.ui.component

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.flowgesture.app.R
import com.flowgesture.app.core.ActionGroup
import com.flowgesture.app.core.ActionType
import com.flowgesture.app.data.GestureSettings

/**
 * 动作选择弹层（底部弹出）。
 *
 * - 按「系统 / 媒体 / 其他」分组，支持搜索；
 * - 当前动作打勾；
 * - 选择「自定义快捷方式」切换到命令编辑态，**显式保存**后才落盘。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ActionPickerSheet(
    fingersLabel: String,
    directionLabel: String,
    current: GestureSettings,
    onDismiss: () -> Unit,
    onSelectAction: (ActionType) -> Unit,
    onSaveCustom: (String) -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var query by remember { mutableStateOf("") }
    var editingCustom by remember { mutableStateOf(false) }
    var customText by remember { mutableStateOf(current.customCommand) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(bottom = 28.dp)
                .verticalScroll(rememberScrollState()),
        ) {
            if (editingCustom) {
                Text(
                    text = stringResource(R.string.action_custom_title),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                OutlinedTextField(
                    value = customText,
                    onValueChange = { customText = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp),
                    label = { Text(stringResource(R.string.action_custom_hint)) },
                    textStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
                    singleLine = true,
                )
                Text(
                    text = stringResource(R.string.action_custom_examples),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.outline,
                    modifier = Modifier.padding(top = 8.dp),
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 16.dp),
                    horizontalArrangement = Arrangement.End,
                ) {
                    TextButton(onClick = { editingCustom = false }) {
                        Text(stringResource(R.string.action_cancel))
                    }
                    Button(
                        onClick = {
                            onSaveCustom(customText.trim())
                            onDismiss()
                        },
                        modifier = Modifier.padding(start = 8.dp),
                    ) {
                        Text(stringResource(R.string.action_save))
                    }
                }
                return@Column
            }

            Text(
                text = stringResource(R.string.action_picker_title, fingersLabel, directionLabel),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )

            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp),
                label = { Text(stringResource(R.string.action_search_hint)) },
                singleLine = true,
            )

            for (group in ActionGroup.entries) {
                val items = ActionType.entries.filter { action ->
                    action.group == group &&
                        (query.isBlank() || action.label.contains(query, ignoreCase = true))
                }
                if (items.isEmpty()) continue

                Text(
                    text = group.label,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(top = 16.dp, bottom = 4.dp),
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                items.forEach { action ->
                    val selected = !editingCustom && action == current.action
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                when (action) {
                                    ActionType.CUSTOM -> editingCustom = true
                                    else -> {
                                        onSelectAction(action)
                                        onDismiss()
                                    }
                                }
                            }
                            .padding(vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = if (selected) "✓" else "",
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.width(24.dp),
                        )
                        Text(
                            text = action.label,
                            style = MaterialTheme.typography.bodyLarge,
                            color = if (action == ActionType.NONE) {
                                MaterialTheme.colorScheme.outline
                            } else {
                                MaterialTheme.colorScheme.onSurface
                            },
                        )
                    }
                }
            }
        }
    }
}
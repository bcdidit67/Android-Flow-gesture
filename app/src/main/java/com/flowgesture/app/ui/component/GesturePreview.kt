package com.flowgesture.app.ui.component

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp

/**
 * 手指触点示意图（纯 Canvas 绘制，无图片资源）。
 *
 * 三种手指数对应不同触点布局，并在四边绘制方向箭头：
 * 当前被操作的方向（[highlight]）以主题色高亮，其余为浅色。
 *
 * @param fingers 3 / 4 / 5
 * @param highlight 需要高亮的方向："up" / "down" / "left" / "right"，null 表示不高亮
 */
@Composable
fun GesturePreview(
    fingers: Int,
    modifier: Modifier = Modifier,
    highlight: String? = null,
) {
    val dotColor = MaterialTheme.colorScheme.primary
    val activeColor = MaterialTheme.colorScheme.primary
    val inactiveColor = MaterialTheme.colorScheme.outlineVariant

    Box(modifier = modifier) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height
            val dotRadius = 9.dp.toPx()
            val centerY = h * 0.52f

            // ---- 触点布局（对齐 Windows 触摸板图示）----
            val points: List<Offset> = when (fingers) {
                3 -> {
                    val gap = w * 0.22f
                    listOf(
                        Offset(w / 2 - gap, centerY),
                        Offset(w / 2, centerY),
                        Offset(w / 2 + gap, centerY),
                    )
                }

                4 -> {
                    val gap = w * 0.20f
                    listOf(
                        Offset(w / 2 - gap * 1.4f, centerY - h * 0.06f),
                        Offset(w / 2 - gap * 0.4f, centerY - h * 0.10f),
                        Offset(w / 2 + gap * 0.6f, centerY - h * 0.06f),
                        Offset(w / 2 + gap * 1.5f, centerY + h * 0.12f),
                    )
                }

                else -> {
                    val gap = w * 0.17f
                    listOf(
                        Offset(w / 2 - gap * 2, centerY + h * 0.06f),
                        Offset(w / 2 - gap, centerY - h * 0.06f),
                        Offset(w / 2, centerY - h * 0.12f),
                        Offset(w / 2 + gap, centerY - h * 0.06f),
                        Offset(w / 2 + gap * 2, centerY + h * 0.06f),
                    )
                }
            }

            points.forEach { p ->
                drawCircle(color = dotColor, radius = dotRadius, center = p)
            }

            // ---- 四边方向箭头 ----
            val arrowLen = w * 0.13f
            val arrowInset = h * 0.06f
            drawArrow(
                from = Offset(w * 0.5f, arrowInset + arrowLen),
                to = Offset(w * 0.5f, arrowInset),
                color = if (highlight == "up") activeColor else inactiveColor,
            )
            drawArrow(
                from = Offset(w * 0.5f, h - arrowInset - arrowLen),
                to = Offset(w * 0.5f, h - arrowInset),
                color = if (highlight == "down") activeColor else inactiveColor,
            )
            drawArrow(
                from = Offset(arrowInset + arrowLen, h * 0.5f),
                to = Offset(arrowInset, h * 0.5f),
                color = if (highlight == "left") activeColor else inactiveColor,
            )
            drawArrow(
                from = Offset(w - arrowInset - arrowLen, h * 0.5f),
                to = Offset(w - arrowInset, h * 0.5f),
                color = if (highlight == "right") activeColor else inactiveColor,
            )
        }
    }
}

/** 画一条带箭头的线 */
private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawArrow(
    from: Offset,
    to: Offset,
    color: Color,
) {
    val stroke = 2.dp.toPx()
    drawLine(color = color, start = from, end = to, strokeWidth = stroke)

    // 箭头头部
    val head = 6.dp.toPx()
    val dx = to.x - from.x
    val dy = to.y - from.y
    val len = kotlin.math.sqrt(dx * dx + dy * dy).coerceAtLeast(0.001f)
    val ux = dx / len
    val uy = dy / len
    val px = -uy
    val py = ux

    val path = Path().apply {
        moveTo(to.x, to.y)
        lineTo(to.x - ux * head + px * head * 0.7f, to.y - uy * head + py * head * 0.7f)
        lineTo(to.x - ux * head - px * head * 0.7f, to.y - uy * head - py * head * 0.7f)
        close()
    }
    drawPath(path = path, color = color)
    // 保证描边风格一致（避免不同设备上纯填充路径噪点）
    drawPath(path = path, color = color, style = Stroke(width = 0.5f))
}
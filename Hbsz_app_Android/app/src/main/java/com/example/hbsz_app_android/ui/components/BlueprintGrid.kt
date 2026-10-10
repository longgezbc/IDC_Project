package com.example.hbsz_app_android.ui.components

import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/**
 * 工程蓝图网格纹理 —— 对齐 Web 端首页 Engineering Blueprint Banner 的 grid 纹理。
 * 在品牌渐变头部叠加低透明度白色网格线，强化"综合布线工程"的专业语境。
 *
 * @param color 网格线颜色（默认白色，由调用方控制透明度）
 * @param cellSize 网格单元边长
 */
fun Modifier.blueprintGrid(
    color: Color = Color.White.copy(alpha = 0.08f),
    cellSize: Float = 24.dp.value
): Modifier = drawBehind {
    val step = cellSize * density // dp → px
    val w = size.width
    val h = size.height
    var x = 0f
    while (x <= w) {
        drawLine(
            color = color,
            start = Offset(x, 0f),
            end = Offset(x, h),
            strokeWidth = 1f
        )
        x += step
    }
    var y = 0f
    while (y <= h) {
        drawLine(
            color = color,
            start = Offset(0f, y),
            end = Offset(w, y),
            strokeWidth = 1f
        )
        y += step
    }
}

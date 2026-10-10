package com.example.hbsz_app_android.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/**
 * Shimmer 加载微光效果 —— 零依赖实现（infiniteTransition + 线性渐变扫光）。
 * 用作骨架屏占位块的 Modifier，扫光色随主题自适应（深色模式用更亮的表面色）。
 */
fun Modifier.shimmer(): Modifier = composed {
    val transition = rememberInfiniteTransition(label = "shimmer")
    val progress by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "shimmerProgress"
    )
    val base = MaterialTheme.colorScheme.surfaceVariant
    val highlight = if (MaterialTheme.colorScheme.surface.luminance() > 0.5f)
        Color.White else Color.White.copy(alpha = 0.08f)
    val brush = Brush.linearGradient(
        colors = listOf(base, highlight, base),
        start = Offset(x = progress * 2000f - 1000f, y = 0f),
        end = Offset(x = progress * 2000f, y = 120f)
    )
    background(brush)
}

/** 主题表面色的亮度估算（用于深色模式自适应扫光色） */
private fun Color.luminance(): Float = (0.299f * red + 0.587f * green + 0.114f * blue)

/** 单个骨架占位块 */
@Composable
private fun SkeletonBlock(modifier: Modifier, cornerRadius: Int = 8) {
    Box(
        modifier = modifier.background(
            MaterialTheme.colorScheme.surfaceVariant,
            androidx.compose.foundation.shape.RoundedCornerShape(cornerRadius.dp)
        )
    )
}

/**
 * 通用列表骨架屏 —— 模拟"标题行 + 两行摘要"的卡片布局，
 * 用于项目/通知/日志等列表的首屏加载态（替代全屏转圈，内容位置预不变跳）。
 *
 * @param count 骨架卡片数量
 */
@Composable
fun SkeletonList(
    count: Int = 4,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        repeat(count) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        MaterialTheme.colorScheme.surface,
                        MaterialTheme.shapes.medium
                    )
                    .padding(14.dp)
            ) {
                // 标题行：圆点图标位 + 标题条
                Row(verticalAlignment = Alignment.CenterVertically) {
                    SkeletonBlock(
                        modifier = Modifier.size(36.dp).shimmer(),
                        cornerRadius = 36
                    )
                    Spacer(Modifier.width(10.dp))
                    SkeletonBlock(
                        modifier = Modifier.fillMaxWidth(0.55f).height(16.dp).shimmer()
                    )
                }
                Spacer(Modifier.height(12.dp))
                // 摘要两行
                SkeletonBlock(
                    modifier = Modifier.fillMaxWidth(0.9f).height(12.dp).shimmer()
                )
                Spacer(Modifier.height(8.dp))
                SkeletonBlock(
                    modifier = Modifier.fillMaxWidth(0.65f).height(12.dp).shimmer()
                )
            }
        }
    }
}

package com.example.hbsz_app_android.ui.components

import androidx.compose.animation.core.animate
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

/**
 * 轻量下拉刷新容器 —— 项目当前 material3 1.3.1 无官方 PullToRefreshBox
 * （该 API 在 1.4.0 才正式提供），此处基于 nestedScroll 自实现：
 * 列表滚动到顶后继续下拉出现 M3 风格圆形指示器，拖过阈值松手触发刷新；
 * isRefreshing=true 期间指示器固定显示转圈，结束后动画收起。
 *
 * 注意：仅当内容为可滚动组件（LazyColumn / verticalScroll）时手势生效，
 * 空态/错误态无滚动容器，请通过重试按钮刷新。
 */
@Composable
fun PullRefreshBox(
    isRefreshing: Boolean,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit
) {
    val density = LocalDensity.current
    val thresholdPx = with(density) { 72.dp.toPx() }
    val maxDragPx = with(density) { 160.dp.toPx() }
    val indicatorSizePx = with(density) { 40.dp.toPx() }

    /** 当前拖拽距离（px），0 = 收起 */
    var dragDistance by remember { mutableFloatStateOf(0f) }
    val currentRefreshing by rememberUpdatedState(isRefreshing)
    val currentOnRefresh by rememberUpdatedState(onRefresh)

    // 刷新结束 → 动画收起指示器
    LaunchedEffect(isRefreshing) {
        if (!isRefreshing && dragDistance > 0f) {
            animate(dragDistance, 0f) { v, _ -> dragDistance = v }
        }
    }

    val nestedScrollConnection = remember {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                // 上滑优先收起指示器（刷新中保持固定，不响应）
                if (available.y < 0f && dragDistance > 0f && !currentRefreshing) {
                    val prev = dragDistance
                    dragDistance = (dragDistance + available.y).coerceAtLeast(0f)
                    return Offset(0f, dragDistance - prev)
                }
                return Offset.Zero
            }

            override fun onPostScroll(
                consumed: Offset,
                available: Offset,
                source: NestedScrollSource
            ): Offset {
                // 子组件已滚到顶，剩余下拉量用于增大拖拽距离
                if (available.y > 0f && !currentRefreshing) {
                    val prev = dragDistance
                    dragDistance = (dragDistance + available.y).coerceAtMost(maxDragPx)
                    return Offset(0f, dragDistance - prev)
                }
                return Offset.Zero
            }

            override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity {
                if (dragDistance > 0f && !currentRefreshing) {
                    if (dragDistance >= thresholdPx) {
                        currentOnRefresh()
                        // 触发后指示器停在阈值位置等待 isRefreshing
                        animate(dragDistance, thresholdPx) { v, _ -> dragDistance = v }
                    } else {
                        animate(dragDistance, 0f) { v, _ -> dragDistance = v }
                    }
                }
                return Velocity.Zero
            }
        }
    }

    Box(modifier = modifier.nestedScroll(nestedScrollConnection)) {
        content()

        val showIndicator = isRefreshing || dragDistance > 0f
        if (showIndicator) {
            // 程序触发刷新（如标题栏按钮）时 dragDistance 为 0，按阈值位置展示
            val effective = if (isRefreshing && dragDistance <= 0f) thresholdPx else dragDistance
            val progress = (effective / thresholdPx).coerceIn(0f, 1f)
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.surface,
                shadowElevation = 6.dp,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .offset { IntOffset(0, (effective - indicatorSizePx).roundToInt()) }
                    .size(40.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    if (isRefreshing) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(24.dp),
                            strokeWidth = 2.5.dp
                        )
                    } else {
                        CircularProgressIndicator(
                            progress = { progress },
                            modifier = Modifier.size(24.dp),
                            strokeWidth = 2.5.dp,
                            trackColor = Color.Transparent
                        )
                    }
                }
            }
        }
    }
}

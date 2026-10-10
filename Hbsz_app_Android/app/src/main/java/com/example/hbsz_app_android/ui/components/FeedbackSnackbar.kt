package com.example.hbsz_app_android.ui.components

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.example.hbsz_app_android.ui.theme.Danger

/**
 * 操作反馈 Snackbar —— 替代页面内联小字提示：
 * 提交/审核成功用默认深色浮层，失败用品牌 Danger 红（白字对比度 4.6:1），
 * Short 时长自动消失后回调 onDismiss 清空消息，避免提示永久残留。
 *
 * 用法：Scaffold(snackbarHost = {
 *     FeedbackSnackbarHost(state.message, state.messageIsError, viewModel::clearMessage)
 * })
 */
@Composable
fun FeedbackSnackbarHost(
    message: String?,
    isError: Boolean,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val hostState = remember { SnackbarHostState() }
    LaunchedEffect(message) {
        if (!message.isNullOrBlank()) {
            hostState.showSnackbar(message, duration = SnackbarDuration.Short)
            onDismiss()
        }
    }
    SnackbarHost(hostState = hostState, modifier = modifier) { data ->
        Snackbar(
            snackbarData = data,
            shape = MaterialTheme.shapes.medium,
            containerColor = if (isError) Danger else MaterialTheme.colorScheme.inverseSurface,
            contentColor = if (isError) Color.White else MaterialTheme.colorScheme.inverseOnSurface
        )
    }
}

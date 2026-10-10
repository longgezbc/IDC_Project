package com.example.hbsz_app_android.ui.update

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle

/**
 * 更新弹窗宿主：启动即静默检查，发现新版本弹窗。
 * 强制更新（forceUpdate）：无"以后再说"按钮、点返回不关闭；
 * 非强制更新：可跳过，下次启动再提示。
 * 下载完成自动拉起系统安装器。
 */
@Composable
fun UpdateCheckHost(viewModel: UpdateViewModel) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val info = state.info ?: return
    // 安装器拉起由 ViewModel 在下载完成回调内直接触发（见 UpdateViewModel.installFile），
    // 不在此处用 LaunchedEffect 监听 apkFile——重复下载路径相同会被 StateFlow 判等去重。

    AlertDialog(
        onDismissRequest = viewModel::dismiss,
        title = {
            Text(
                text = "发现新版本 v${info.versionName}",
                fontWeight = FontWeight.SemiBold
            )
        },
        text = {
            Column {
                if (info.updateLog.isNotBlank()) {
                    Text(
                        text = info.updateLog,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(12.dp))
                }
                if (state.downloading) {
                    Text(
                        text = "正在下载更新包… ${state.progress}%",
                        style = MaterialTheme.typography.bodySmall
                    )
                    Spacer(Modifier.height(8.dp))
                    LinearProgressIndicator(
                        progress = { state.progress / 100f },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                state.downloadError?.let { err ->
                    Text(
                        text = err,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        },
        confirmButton = {
            if (state.downloading) {
                TextButton(onClick = {}, enabled = false) { Text("下载中…") }
            } else {
                TextButton(onClick = viewModel::startDownload) { Text("立即更新") }
            }
        },
        dismissButton = {
            // 强制更新不给跳过入口
            if (!info.forceUpdate && !state.downloading) {
                TextButton(onClick = viewModel::dismiss) { Text("以后再说") }
            }
        }
    )
}

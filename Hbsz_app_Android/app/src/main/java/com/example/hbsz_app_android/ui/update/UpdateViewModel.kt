package com.example.hbsz_app_android.ui.update

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.hbsz_app_android.data.update.AppUpdateManager
import com.example.hbsz_app_android.data.update.AppUpdateManager.AppLatestInfo
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

/**
 * 版本更新状态机（第二批优化 #3）。
 *
 * Idle →(检查到新版本)→ Available →(点更新)→ Downloading(progress)→ Ready(触发安装)
 *                ↘(无新版/检查失败)→ 终态（不弹窗，静默）
 * 强制更新（forceUpdate=true）：弹窗不可关闭，仅"立即更新"一条路径。
 */
data class UpdateUiState(
    /** 待安装的新版本信息；null = 无更新或尚未检查 */
    val info: AppLatestInfo? = null,
    val downloading: Boolean = false,
    val progress: Int = 0,
    val downloadError: String? = null,
    /** APK 已就绪，由 UI 层触发系统安装 */
    val apkFile: File? = null
)

@HiltViewModel
class UpdateViewModel @Inject constructor(
    private val updateManager: AppUpdateManager,
    @ApplicationContext private val context: Context
) : ViewModel() {

    private val _uiState = MutableStateFlow(UpdateUiState())
    val uiState: StateFlow<UpdateUiState> = _uiState.asStateFlow()

    /** 启动时静默检查（失败不提示，不影响使用） */
    fun checkOnStartup() {
        viewModelScope.launch {
            val latest = updateManager.fetchLatest() ?: return@launch
            if (updateManager.isNewer(latest)) {
                _uiState.update { it.copy(info = latest) }
            }
        }
    }

    /** 下载 APK 到应用专属外部目录并触发系统安装 */
    fun startDownload() {
        val info = _uiState.value.info ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(downloading = true, progress = 0, downloadError = null) }
            runCatching {
                updateManager.downloadApk(info, context.getExternalFilesDir("update") ?: File(context.cacheDir, "update")) { pct ->
                    _uiState.update { it.copy(progress = pct) }
                }
            }.onSuccess { file ->
                _uiState.update { it.copy(downloading = false, apkFile = file) }
                // 下载完成立即拉起安装器。
                // 注意不能依赖 Compose 侧 LaunchedEffect(apkFile) 触发：重复下载的 File 路径相同，
                // StateFlow 以 equals 判等会去重导致不重发，安装器不会被再次拉起。
                installFile(file)
            }.onFailure { e ->
                _uiState.update { it.copy(downloading = false, downloadError = e.message ?: "下载失败，请重试") }
            }
        }
    }

    /** 拉起系统安装器（FileProvider content URI，免存储权限） */
    fun install() {
        installFile(_uiState.value.apkFile ?: return)
    }

    private fun installFile(file: File) {
        val uri: Uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        runCatching { context.startActivity(intent) }
    }

    /** 非强制更新：本次跳过（下次启动再提示） */
    fun dismiss() {
        if (_uiState.value.info?.forceUpdate == true) return // 强制更新不可关闭
        _uiState.update { UpdateUiState() }
    }
}

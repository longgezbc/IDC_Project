package com.example.hbsz_app_android.ui.profile

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.example.hbsz_app_android.common.Resource
import com.example.hbsz_app_android.data.remote.dto.NotificationSettingsDto
import com.example.hbsz_app_android.data.repository.UserRepository
import com.example.hbsz_app_android.ui.components.ErrorState
import com.example.hbsz_app_android.ui.components.LoadingState
import com.example.hbsz_app_android.ui.components.SectionHeader
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

// ==================== ViewModel ====================

data class NotificationSettingsUiState(
    val loading: Boolean = true,
    val saving: Boolean = false,
    val error: String? = null,
    val saved: Boolean = false,
    val settings: NotificationSettingsDto? = null
)

/** 设置开关字段枚举（对应 NotificationSettingsDto 的可配置项），收敛原先的裸字符串 key */
enum class SettingField { IN_APP, EMAIL, SMS, PROGRESS, TASK_DUE, DOC_UPDATE, ARCHIVE }

@HiltViewModel
class NotificationSettingsViewModel @Inject constructor(
    private val userRepository: UserRepository
) : ViewModel() {

    private val _state = MutableStateFlow(NotificationSettingsUiState())
    val state: StateFlow<NotificationSettingsUiState> = _state

    init { load() }

    fun load() {
        viewModelScope.launch {
            _state.value = _state.value.copy(loading = true, error = null)
            when (val res = userRepository.getNotificationSettings()) {
                is Resource.Success -> _state.value = _state.value.copy(
                    loading = false,
                    settings = res.data
                )
                is Resource.Error -> _state.value = _state.value.copy(
                    loading = false,
                    error = res.message
                )
                Resource.Loading -> Unit
            }
        }
    }

    /** 设置变更代次：快速连续 toggle 多个开关时，旧请求失败只回滚自己，不覆盖后续乐观更新 */
    private var toggleEpoch = 0L

    /** 开关变更后立即 PUT /user/notifications 持久化（失败回滚为旧值） */
    fun toggle(field: SettingField, value: Boolean) {
        val current = _state.value.settings ?: return
        toggleEpoch++
        val epoch = toggleEpoch
        val updated = when (field) {
            SettingField.IN_APP -> current.copy(inApp = value)
            SettingField.EMAIL -> current.copy(email = value)
            SettingField.SMS -> current.copy(sms = value)
            SettingField.PROGRESS -> current.copy(progress = value)
            SettingField.TASK_DUE -> current.copy(taskDue = value)
            SettingField.DOC_UPDATE -> current.copy(docUpdate = value)
            SettingField.ARCHIVE -> current.copy(archive = value)
        }
        _state.value = _state.value.copy(settings = updated, saved = false)
        viewModelScope.launch {
            _state.value = _state.value.copy(saving = true)
            when (val res = userRepository.updateNotificationSettings(updated)) {
                is Resource.Success -> _state.value = _state.value.copy(
                    saving = false,
                    saved = true,
                    settings = res.data
                )
                is Resource.Error -> {
                    val next = _state.value.copy(saving = false, error = res.message)
                    // 保存失败回滚为更新前的旧值；若期间又有新的 toggle（epoch 变化），
                    // 只提示错误不回滚，避免覆盖后续开关的乐观状态
                    _state.value = if (epoch == toggleEpoch) next.copy(settings = current) else next
                }
                Resource.Loading -> Unit
            }
        }
    }
}

// ==================== UI ====================

/**
 * 通知设置页 —— 对接后端 GET/PUT /user/notifications（NotificationSettingDTO）：
 * 通知渠道（站内/邮件/短信）+ 通知类型（进度/任务到期/文档/归档）开关，变更即时保存到服务端。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotificationSettingsScreen(
    onBack: () -> Unit = {},
    viewModel: NotificationSettingsViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val settings = state.settings

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("通知设置", fontWeight = FontWeight.SemiBold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        when {
            state.loading -> LoadingState(Modifier.padding(padding))
            state.error != null -> ErrorState(
                Modifier.padding(padding),
                message = state.error ?: "加载失败",
                onRetry = { viewModel.load() }
            )
            settings == null -> LoadingState(Modifier.padding(padding))
            else -> Column(
                modifier = Modifier
                    .padding(padding)
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp)
            ) {
                // ===== 通知渠道 =====
                SectionHeader(title = "通知渠道")
                Surface(
                    shape = MaterialTheme.shapes.medium,
                    color = MaterialTheme.colorScheme.surface,
                    tonalElevation = 1.dp,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column {
                        SettingRow("站内通知", "在消息中心接收站内推送", settings.inApp) {
                            viewModel.toggle(SettingField.IN_APP, it)
                        }
                        SettingRow("邮件通知", "重要事项同步发送到邮箱", settings.email) {
                            viewModel.toggle(SettingField.EMAIL, it)
                        }
                        SettingRow("短信通知", "紧急事项通过短信提醒", settings.sms) {
                            viewModel.toggle(SettingField.SMS, it)
                        }
                    }
                }

                Spacer(Modifier.height(14.dp))

                // ===== 通知类型 =====
                SectionHeader(title = "通知类型")
                Surface(
                    shape = MaterialTheme.shapes.medium,
                    color = MaterialTheme.colorScheme.surface,
                    tonalElevation = 1.dp,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column {
                        SettingRow("进度更新", "项目/任务进度变化时通知", settings.progress) {
                            viewModel.toggle(SettingField.PROGRESS, it)
                        }
                        SettingRow("任务到期提醒", "任务临近截止日期时推送提醒", settings.taskDue) {
                            viewModel.toggle(SettingField.TASK_DUE, it)
                        }
                        SettingRow("文档更新", "项目资料有新版本时通知", settings.docUpdate) {
                            viewModel.toggle(SettingField.DOC_UPDATE, it)
                        }
                        SettingRow("项目归档", "项目完成归档时通知", settings.archive) {
                            viewModel.toggle(SettingField.ARCHIVE, it)
                        }
                    }
                }

                Spacer(Modifier.height(14.dp))
                Text(
                    text = if (state.saving) "正在保存…"
                    else if (state.saved) "设置已同步到服务端"
                    else "开关变更后自动保存到服务端通知偏好。",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (state.saved) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun SettingRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(Modifier.width(12.dp))
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

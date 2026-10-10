package com.example.hbsz_app_android.ui.profile

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.outlined.LockReset
import androidx.compose.material.icons.outlined.PhoneIphone
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.example.hbsz_app_android.common.Resource
import com.example.hbsz_app_android.data.repository.AuthRepository
import com.example.hbsz_app_android.data.repository.UserRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

// ==================== ViewModel ====================

data class AccountSecurityUiState(
    val phone: String? = null,
    val submitting: Boolean = false,
    val result: String? = null,
    /** 修改成功后置 true，对话框据此自动关闭 */
    val success: Boolean = false
)

@HiltViewModel
class AccountSecurityViewModel @Inject constructor(
    private val userRepository: UserRepository,
    private val authRepository: AuthRepository
) : ViewModel() {

    private val _state = MutableStateFlow(AccountSecurityUiState())
    val state: StateFlow<AccountSecurityUiState> = _state

    init {
        viewModelScope.launch {
            when (val res = userRepository.getProfile()) {
                is Resource.Success -> _state.value = _state.value.copy(phone = res.data.phone)
                else -> Unit
            }
        }
    }

    /** PUT /user/password：修改密码（前端先做与后端一致的强度校验） */
    fun changePassword(oldPassword: String, newPassword: String) {
        if (_state.value.submitting) return
        viewModelScope.launch {
            _state.value = _state.value.copy(submitting = true, result = null, success = false)
            when (val res = userRepository.changePassword(oldPassword, newPassword)) {
                is Resource.Success -> _state.value = _state.value.copy(
                    submitting = false,
                    success = true,
                    result = "密码修改成功，请使用新密码重新登录"
                )
                is Resource.Error -> _state.value = _state.value.copy(
                    submitting = false,
                    result = res.message
                )
                Resource.Loading -> Unit
            }
        }
    }

    fun consumeResult() {
        _state.value = _state.value.copy(result = null, success = false)
    }

    /** 改密成功弹窗「知道了」：登出并清 token（旧登录态已失效），由全局状态切到登录页 */
    fun onConfirmPasswordChanged() {
        consumeResult()
        viewModelScope.launch { authRepository.logout() }
    }
}

/** 与后端 @Pattern 一致的强度校验：8-32 位，含大小写字母、数字和特殊字符 */
private fun isValidPassword(pwd: String): Boolean {
    if (pwd.length !in 8..32) return false
    val hasLower = pwd.any { it.isLowerCase() }
    val hasUpper = pwd.any { it.isUpperCase() }
    val hasDigit = pwd.any { it.isDigit() }
    val hasSpecial = pwd.any { !it.isLetterOrDigit() }
    return hasLower && hasUpper && hasDigit && hasSpecial
}

// ==================== UI ====================

/**
 * 账号安全页 —— 对接后端 PUT /user/password（ChangePasswordDTO）：
 * 旧密码 + 新密码 + 确认新密码，前端校验与后端 @Pattern 规则一致。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AccountSecurityScreen(
    onBack: () -> Unit = {},
    viewModel: AccountSecurityViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var showPwdDialog by remember { mutableStateOf(false) }

    // 改密成功：关闭改密输入弹窗，仅保留成功提示弹窗，避免双弹窗叠加
    LaunchedEffect(state.success) {
        if (state.success) showPwdDialog = false
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("账号安全", fontWeight = FontWeight.SemiBold) },
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
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
        ) {
            Surface(
                shape = MaterialTheme.shapes.medium,
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 1.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column {
                    SecurityRow(
                        icon = Icons.Outlined.LockReset,
                        title = "登录密码",
                        subtitle = "定期更换密码可提升账号安全性"
                    ) { showPwdDialog = true }
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    SecurityRow(
                        icon = Icons.Outlined.PhoneIphone,
                        title = "手机绑定",
                        subtitle = state.phone?.takeIf { it.isNotBlank() }?.let { "已绑定 ${maskPhone(it)}" } ?: "未绑定手机号"
                    ) { /* 换绑流程暂未开放 */ }
                }
            }

            Spacer(Modifier.height(14.dp))
            Text(
                text = "安全提示：密码长度 8-32 位，需包含大小写字母、数字和特殊字符；请勿与其他平台使用相同密码。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }

    if (showPwdDialog) {
        ChangePasswordDialog(
            submitting = state.submitting,
            error = state.result?.takeIf { !state.success },
            onDismiss = {
                viewModel.consumeResult()
                showPwdDialog = false
            },
            // 用户重新输入时清除上一次的服务端错误，避免旧错误一直挂在表单上
            onClearError = { viewModel.consumeResult() },
            onSubmit = { oldPwd, newPwd -> viewModel.changePassword(oldPwd, newPwd) }
        )
    }

    // 修改成功提示：点「知道了」后再登出跳登录页（AppViewModel 切页后弹窗随之消失，属预期）
    if (state.success) {
        AlertDialog(
            onDismissRequest = { viewModel.onConfirmPasswordChanged() },
            title = { Text("提示", fontWeight = FontWeight.SemiBold) },
            text = { Text(state.result ?: "密码修改成功，请使用新密码重新登录") },
            confirmButton = {
                Button(onClick = { viewModel.onConfirmPasswordChanged() }) { Text("知道了") }
            }
        )
    }
}

/** 手机号脱敏展示：任意长度号码均脱敏，避免隐私泄漏 */
private fun maskPhone(phone: String): String = when {
    phone.length >= 7 -> "${phone.take(3)}****${phone.takeLast(4)}"
    phone.length >= 4 -> "${phone.take(1)}${"*".repeat(phone.length - 2)}${phone.takeLast(1)}"
    else -> "****"
}

@Composable
private fun SecurityRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(20.dp)
        )
        Spacer(Modifier.width(12.dp))
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
        Icon(
            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(18.dp)
        )
    }
}

@Composable
private fun ChangePasswordDialog(
    submitting: Boolean,
    error: String?,
    onDismiss: () -> Unit,
    onClearError: () -> Unit = {},
    onSubmit: (oldPwd: String, newPwd: String) -> Unit
) {
    var oldPwd by remember { mutableStateOf("") }
    var newPwd by remember { mutableStateOf("") }
    var confirmPwd by remember { mutableStateOf("") }
    var localError by remember { mutableStateOf<String?>(null) }

    val shownError = localError ?: error

    AlertDialog(
        onDismissRequest = { if (!submitting) onDismiss() },
        title = { Text("修改密码", fontWeight = FontWeight.SemiBold) },
        text = {
            Column {
                OutlinedTextField(
                    value = oldPwd,
                    onValueChange = { oldPwd = it; localError = null; onClearError() },
                    label = { Text("旧密码") },
                    enabled = !submitting,
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = newPwd,
                    onValueChange = { newPwd = it; localError = null; onClearError() },
                    label = { Text("新密码（8-32位，含大小写/数字/特殊字符）") },
                    enabled = !submitting,
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = confirmPwd,
                    onValueChange = { confirmPwd = it; localError = null; onClearError() },
                    label = { Text("确认新密码") },
                    enabled = !submitting,
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth()
                )
                shownError?.let {
                    Spacer(Modifier.height(8.dp))
                    Text(it, color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = {
            Button(
                enabled = !submitting,
                onClick = {
                    when {
                        oldPwd.isBlank() -> localError = "请输入旧密码"
                        !isValidPassword(newPwd) -> localError = "新密码需 8-32 位，且包含大小写字母、数字和特殊字符"
                        newPwd != confirmPwd -> localError = "两次输入的新密码不一致"
                        else -> onSubmit(oldPwd, newPwd)
                    }
                }
            ) {
                if (submitting) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                    Spacer(Modifier.width(8.dp))
                }
                Text(if (submitting) "提交中…" else "确认修改")
            }
        },
        dismissButton = {
            androidx.compose.material3.OutlinedButton(
                enabled = !submitting,
                onClick = onDismiss
            ) { Text("取消") }
        }
    )
}

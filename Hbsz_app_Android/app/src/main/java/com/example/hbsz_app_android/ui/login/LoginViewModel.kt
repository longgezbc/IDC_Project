package com.example.hbsz_app_android.ui.login

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.hbsz_app_android.common.Resource
import com.example.hbsz_app_android.data.local.CredentialVault
import com.example.hbsz_app_android.data.local.UserPreferences
import com.example.hbsz_app_android.data.repository.AuthRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.crypto.Cipher
import javax.inject.Inject

data class LoginUiState(
    val loading: Boolean = false,
    val username: String = "",
    val password: String = "",
    val captcha: String = "",
    val captchaId: String? = null,
    val captchaImage: String? = null,
    /** 旧版后端返回的算术题文本（无图片时降级显示） */
    val captchaQuestion: String? = null,
    val captchaLoading: Boolean = false,
    val loginError: String? = null,
    // ===== 生物识别快速登录（第二批 #4）=====
    /** 设备支持强生物识别（BIOMETRIC_STRONG） */
    val biometricCapable: Boolean = false,
    /** 已开启快速登录（本地存有加密凭据） */
    val biometricReady: Boolean = false,
    /** 用户勾选"开启指纹快速登录"（本次登录成功后保存凭据） */
    val biometricOptIn: Boolean = false,
    /** 待执行的加密验证请求（登录成功且勾选后置位，UI 弹 BiometricPrompt）*/
    val biometricEncryptRequest: Pair<String, String>? = null,
    /** 待执行的解密验证请求（点击"指纹快速登录"后置位：username to passwordEnc）*/
    val biometricDecryptRequest: Pair<String, String>? = null
)

@HiltViewModel
class LoginViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val userPreferences: UserPreferences,
    val credentialVault: CredentialVault
) : ViewModel() {

    private val _uiState = MutableStateFlow(LoginUiState())
    val uiState: StateFlow<LoginUiState> = _uiState.asStateFlow()

    /** 由 UI 层（持有 Activity）在进入登录页时设置设备能力 */
    fun setBiometricCapable(capable: Boolean) {
        _uiState.update { it.copy(biometricCapable = capable) }
    }

    init {
        refreshCaptcha()
        // 已开启快速登录：恢复 ready 标记（凭据存在性检查）
        viewModelScope.launch {
            val enabled = userPreferences.readBiometricCredential() != null
            _uiState.update { it.copy(biometricReady = enabled) }
        }
    }

    fun refreshCaptcha() {
        viewModelScope.launch {
            _uiState.update { it.copy(captchaLoading = true, captchaImage = null, captchaQuestion = null) }
            when (val result = authRepository.getCaptcha()) {
                is Resource.Success -> _uiState.update {
                    it.copy(
                        captchaId = result.data.captchaId,
                        captchaImage = result.data.image,
                        captchaQuestion = result.data.question,
                        captchaLoading = false
                    )
                }
                is Resource.Error -> _uiState.update {
                    it.copy(captchaLoading = false, loginError = result.message)
                }
                Resource.Loading -> Unit
            }
        }
    }

    fun onUsernameChange(value: String) = _uiState.update { it.copy(username = value, loginError = null) }

    fun onPasswordChange(value: String) = _uiState.update { it.copy(password = value, loginError = null) }

    fun onCaptchaChange(value: String) = _uiState.update { it.copy(captcha = value, loginError = null) }

    fun onBiometricOptInChange(value: Boolean) = _uiState.update { it.copy(biometricOptIn = value) }

    fun login() {
        val state = _uiState.value
        when {
            state.username.isBlank() -> _uiState.update { it.copy(loginError = "请输入用户名") }
            state.password.isBlank() -> _uiState.update { it.copy(loginError = "请输入密码") }
            state.captcha.isBlank() -> _uiState.update { it.copy(loginError = "请输入验证码") }
            else -> {
                // 验证码未就绪（如刚进页面尚未拉取成功）：给出提示并自动刷新，避免静默无反馈
                val captchaId = state.captchaId
                if (captchaId == null) {
                    _uiState.update { it.copy(loginError = "验证码未就绪，请稍候再试") }
                    refreshCaptcha()
                    return
                }
                viewModelScope.launch {
                    _uiState.update { it.copy(loading = true, loginError = null) }
                    when (val result = authRepository.login(state.username, state.password, captchaId, state.captcha)) {
                        is Resource.Success -> {
                            // 勾选了"指纹快速登录"且设备支持 → 通知 UI 弹加密验证（验证通过才落盘凭据）
                            val needSetup = state.biometricOptIn && state.biometricCapable
                            _uiState.update {
                                it.copy(
                                    loading = false,
                                    biometricEncryptRequest = if (needSetup) state.username to state.password else null
                                )
                            }
                        }
                        is Resource.Error -> {
                            _uiState.update { it.copy(loading = false, loginError = result.message) }
                            refreshCaptcha()
                        }
                        Resource.Loading -> Unit
                    }
                }
            }
        }
    }

    /** 用户点击"指纹快速登录"按钮：读取凭据并通知 UI 弹解密验证 */
    fun requestBiometricLogin() {
        viewModelScope.launch {
            val credential = userPreferences.readBiometricCredential()
            if (credential == null) {
                _uiState.update { it.copy(biometricReady = false, loginError = "快速登录未开启，请先登录开启") }
                return@launch
            }
            _uiState.update { it.copy(biometricDecryptRequest = credential.first to credential.second) }
        }
    }

    /** 加密验证通过（开启流程）：加密凭据并落盘 */
    fun onBiometricEncryptSuccess(cipher: Cipher) {
        val request = _uiState.value.biometricEncryptRequest ?: return
        viewModelScope.launch {
            runCatching { credentialVault.encrypt(cipher, request.second) }
                .onSuccess { enc ->
                    userPreferences.saveBiometricCredential(request.first, enc)
                    _uiState.update { it.copy(biometricEncryptRequest = null, biometricReady = true) }
                }
                .onFailure {
                    _uiState.update { it.copy(biometricEncryptRequest = null, loginError = "指纹登录开启失败") }
                }
        }
    }

    /** 解密验证通过（快速登录流程）：解出密码自动填充表单 */
    fun onBiometricDecryptSuccess(cipher: Cipher) {
        val request = _uiState.value.biometricDecryptRequest ?: return
        viewModelScope.launch {
            val password = credentialVault.decrypt(cipher, request.second)
            if (password == null) {
                // 密钥失效（如新录入指纹）：清除旧凭据，引导重新开启
                userPreferences.clearBiometricCredential()
                _uiState.update {
                    it.copy(
                        biometricDecryptRequest = null,
                        biometricReady = false,
                        loginError = "指纹信息已变化，请重新登录并开启快速登录"
                    )
                }
            } else {
                _uiState.update {
                    it.copy(
                        biometricDecryptRequest = null,
                        username = request.first,
                        password = password,
                        loginError = null
                    )
                }
                refreshCaptcha()
            }
        }
    }

    /** 验证失败/取消：清除待处理请求 */
    fun onBiometricPromptDismissed(error: String?) {
        _uiState.update {
            it.copy(
                biometricEncryptRequest = null,
                biometricDecryptRequest = null,
                loginError = error
            )
        }
    }

    /**
     * 密钥已永久失效（用户重录/删除过指纹导致密文不可解）：
     * 清除本地凭据，按钮消失，引导重新登录开启。
     */
    fun onBiometricCredentialInvalid() {
        viewModelScope.launch {
            userPreferences.clearBiometricCredential()
            _uiState.update {
                it.copy(
                    biometricDecryptRequest = null,
                    biometricReady = false,
                    loginError = "指纹信息已变化，请重新登录并开启快速登录"
                )
            }
        }
    }
}

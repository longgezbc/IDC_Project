package com.example.hbsz_app_android.ui.login

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.hbsz_app_android.data.local.BiometricLoginManager

/**
 * 生物识别"开启凭据保存"桥接（AppRoot 级，第二批 #4）。
 *
 * 为什么不放在 LoginScreen：登录成功后 AppRoot 立即切换到 MainScreen，
 * LoginScreen 卸载会取消其 LaunchedEffect，加密验证弹窗来不及弹出。
 * 挂在 AppRoot 层（LoginViewModel 为 Activity 级单例，此处为同一实例），
 * 登录成功进入主页后弹窗叠加展示，验证通过即完成凭据加密落盘。
 */
@Composable
fun BiometricSetupHost(viewModel: LoginViewModel) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val activity = remember(context) { context as? FragmentActivity }
    val biometricManager = remember(activity) {
        activity?.let { BiometricLoginManager(it, viewModel.credentialVault) }
    }

    LaunchedEffect(uiState.biometricEncryptRequest) {
        val request = uiState.biometricEncryptRequest ?: return@LaunchedEffect
        val manager = biometricManager
        if (manager == null || !manager.canUse()) {
            viewModel.onBiometricPromptDismissed(null)
            return@LaunchedEffect
        }
        manager.promptEncrypt(
            onSuccess = { cipher -> viewModel.onBiometricEncryptSuccess(cipher) },
            onError = { msg -> viewModel.onBiometricPromptDismissed(msg) }
        )
    }
}

package com.example.hbsz_app_android

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.fragment.app.FragmentActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.hbsz_app_android.ui.AppViewModel
import com.example.hbsz_app_android.ui.components.LoadingState
import com.example.hbsz_app_android.ui.login.BiometricSetupHost
import com.example.hbsz_app_android.ui.login.LoginScreen
import com.example.hbsz_app_android.ui.login.LoginViewModel
import com.example.hbsz_app_android.ui.main.MainScreen
import com.example.hbsz_app_android.ui.theme.HbszTheme
import com.example.hbsz_app_android.ui.update.UpdateCheckHost
import com.example.hbsz_app_android.ui.update.UpdateViewModel
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : FragmentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            HbszTheme {
                AppRoot()
            }
        }
    }
}

/**
 * 应用根节点：根据登录状态在 登录页 / 主页面 之间切换。
 * 登录成功或退出登录后由 token 状态流自动驱动切换，无需手动导航。
 * 版本更新检查（第二批 #3）挂在根节点：任何登录状态下发现新版本均可弹窗提示，
 * 强制更新可在登录页即拦截用户继续使用旧版本。
 */
@Composable
fun AppRoot(
    viewModel: AppViewModel = hiltViewModel(),
    updateViewModel: UpdateViewModel = hiltViewModel(),
    loginViewModel: LoginViewModel = hiltViewModel()
) {
    LaunchedEffect(Unit) { updateViewModel.checkOnStartup() }

    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    when (uiState) {
        AppViewModel.UiState.Loading -> LoadingState()
        AppViewModel.UiState.LoggedOut -> LoginScreen(viewModel = loginViewModel)
        AppViewModel.UiState.LoggedIn -> MainScreen()
    }

    UpdateCheckHost(updateViewModel)
    // 生物识别开启验证（登录成功后弹）：必须在 AppRoot 层，
    // 避免 LoginScreen 因登录态切换卸载导致弹窗被取消
    BiometricSetupHost(loginViewModel)
}

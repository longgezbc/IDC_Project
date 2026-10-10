package com.example.hbsz_app_android.ui.login

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Base64
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.outlined.Apartment
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.hbsz_app_android.data.local.BiometricLoginManager
import com.example.hbsz_app_android.ui.components.blueprintGrid
import com.example.hbsz_app_android.ui.theme.Primary
import com.example.hbsz_app_android.ui.theme.PrimaryDark
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 登录页 —— 对齐 Web 端品牌设计：
 * 品牌渐变头部（#1890FF）+ Logo 标识 + 副标题，下方表单区（用户名/密码/图形验证码）。
 * 登录成功后由 AppViewModel 感知 token 变化自动进入主页。
 *
 * 生物识别快速登录（第二批 #4）：
 * - 设备支持强生物识别时显示"开启指纹快速登录"勾选框，登录成功后弹指纹验证加密保存凭据；
 * - 已开启时（token 失效回到登录页场景）显示"指纹快速登录"按钮，
 *   验证通过自动填充用户名/密码，仅需输入验证码（保留人机校验）。
 */
@Composable
fun LoginScreen(viewModel: LoginViewModel = hiltViewModel()) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    // ===== 生物识别桥接：Composable 层持有 Activity，创建 BiometricLoginManager =====
    val context = LocalContext.current
    val activity = remember(context) { context as? FragmentActivity }
    val biometricManager = remember(activity) {
        activity?.let { BiometricLoginManager(it, viewModel.credentialVault) }
    }
    // 进入登录页时上报设备生物识别能力（一次性）
    LaunchedEffect(biometricManager) {
        viewModel.setBiometricCapable(biometricManager?.canUse() == true)
    }
    // 点击"指纹快速登录" → 弹解密验证（自动填充）。
    // 注意：加密验证（登录成功后保存凭据）在 AppRoot 层处理——登录成功会切换到
    // MainScreen 导致本页面卸载，LaunchedEffect 会被取消来不及弹窗。
    LaunchedEffect(uiState.biometricDecryptRequest) {
        val request = uiState.biometricDecryptRequest ?: return@LaunchedEffect
        val manager = biometricManager
        if (manager == null || !manager.canUse()) {
            viewModel.onBiometricPromptDismissed("设备不支持指纹快速登录")
            return@LaunchedEffect
        }
        // 密文格式 base64(iv):base64(ciphertext)，解密需先取 IV 构造 Cipher
        val iv = runCatching {
            Base64.decode(request.second.substringBefore(":"), Base64.NO_WRAP)
        }.getOrNull()
        if (iv == null) {
            viewModel.onBiometricPromptDismissed("快速登录数据异常，请重新开启")
            return@LaunchedEffect
        }
        manager.promptDecrypt(
            iv,
            onCredentialInvalid = { viewModel.onBiometricCredentialInvalid() },
            onSuccess = { cipher -> viewModel.onBiometricDecryptSuccess(cipher) },
            onError = { msg -> viewModel.onBiometricPromptDismissed(msg) }
        )
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
        ) {
            // ===== 品牌渐变头部（深→浅品牌渐变 + 工程蓝图网格纹理，对齐首页问候区） =====
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .blueprintGrid()
                    .background(
                        Brush.verticalGradient(
                            listOf(PrimaryDark, Primary)
                        )
                    )
                    .padding(horizontal = 24.dp)
                    .padding(top = 72.dp, bottom = 44.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    // Logo 标识
                    Box(
                        modifier = Modifier
                            .size(72.dp)
                            .background(Color.White.copy(alpha = 0.22f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Apartment,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(36.dp)
                        )
                    }
                    Spacer(Modifier.height(16.dp))
                    Text(
                        text = "IDC 项目管家",
                        style = MaterialTheme.typography.headlineMedium,
                        color = Color.White,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = "数据中心综合布线施工管理系统",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.White.copy(alpha = 0.88f)
                    )
                }
            }

            // ===== 表单区 =====
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp)
                    .offset(y = (-24).dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Surface(
                    shape = MaterialTheme.shapes.large,
                    color = MaterialTheme.colorScheme.surface,
                    tonalElevation = 2.dp,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(Modifier.padding(20.dp)) {
                        Text(
                            text = "账号登录",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(Modifier.height(16.dp))

                        OutlinedTextField(
                            value = uiState.username,
                            onValueChange = viewModel::onUsernameChange,
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text("用户名") },
                            singleLine = true,
                            leadingIcon = { Icon(Icons.Default.Person, contentDescription = null) }
                        )

                        Spacer(Modifier.height(14.dp))
                        var passwordVisible by rememberSaveable { mutableStateOf(false) }
                        OutlinedTextField(
                            value = uiState.password,
                            onValueChange = viewModel::onPasswordChange,
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text("密码") },
                            singleLine = true,
                            visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                            trailingIcon = {
                                IconButton(onClick = { passwordVisible = !passwordVisible }) {
                                    Icon(
                                        imageVector = if (passwordVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                        contentDescription = if (passwordVisible) "隐藏密码" else "显示密码"
                                    )
                                }
                            }
                        )

                        Spacer(Modifier.height(14.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            OutlinedTextField(
                                value = uiState.captcha,
                                onValueChange = viewModel::onCaptchaChange,
                                modifier = Modifier.weight(1f),
                                label = { Text("验证码") },
                                singleLine = true
                            )
                            Spacer(Modifier.width(12.dp))
                            CaptchaImage(
                                base64 = uiState.captchaImage,
                                question = uiState.captchaQuestion,
                                loading = uiState.captchaLoading,
                                modifier = Modifier.size(width = 110.dp, height = 56.dp),
                                onClick = viewModel::refreshCaptcha
                            )
                        }

                        uiState.loginError?.let { error ->
                            Spacer(Modifier.height(10.dp))
                            Text(
                                text = error,
                                color = MaterialTheme.colorScheme.error,
                                textAlign = TextAlign.Center,
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }

                        Spacer(Modifier.height(12.dp))
                        // 设备支持指纹时显示"开启指纹快速登录"勾选（本次登录成功后保存加密凭据）
                        if (uiState.biometricCapable) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .toggleable(
                                        value = uiState.biometricOptIn,
                                        onValueChange = viewModel::onBiometricOptInChange,
                                        role = Role.Checkbox
                                    )
                            ) {
                                Checkbox(
                                    checked = uiState.biometricOptIn,
                                    onCheckedChange = null // 点击事件由 Row.toggleable 统一处理
                                )
                                Text(
                                    text = "开启指纹快速登录（下次免输密码）",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        Spacer(Modifier.height(8.dp))
                        Button(
                            onClick = viewModel::login,
                            enabled = !uiState.loading,
                            shape = MaterialTheme.shapes.medium,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                        ) {
                            if (uiState.loading) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(20.dp),
                                    strokeWidth = 2.dp,
                                    color = MaterialTheme.colorScheme.onPrimary
                                )
                            } else {
                                Text("登录", style = MaterialTheme.typography.labelLarge)
                            }
                        }

                        // 已开启快速登录：指纹按钮（验证通过自动填充账号密码）
                        if (uiState.biometricReady && uiState.biometricCapable) {
                            Spacer(Modifier.height(10.dp))
                            OutlinedButton(
                                onClick = viewModel::requestBiometricLogin,
                                shape = MaterialTheme.shapes.medium,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(44.dp)
                            ) {
                                Icon(
                                    Icons.Default.Fingerprint,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp),
                                    tint = MaterialTheme.colorScheme.primary
                                )
                                Spacer(Modifier.width(6.dp))
                                Text(
                                    "指纹快速登录",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }
                }

                Spacer(Modifier.height(20.dp))
                Text(
                    text = "数据中心施工全流程管理平台",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(16.dp))
            }
        }
    }
}

/**
 * 验证码展示组件，按优先级四态展示：
 * 图片（新后端）→ 算术题文本（旧后端降级）→ 点击刷新（无数据）→ 加载中（请求中）。
 */
@Composable
private fun CaptchaImage(
    base64: String?,
    question: String?,
    loading: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        modifier = modifier,
        shape = MaterialTheme.shapes.small,
        border = ButtonDefaults.outlinedButtonBorder()
    ) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            when {
                base64 != null -> {
                    // Base64 解码耗时，切到后台线程避免阻塞主线程；解码完成前 value 为 null，走占位逻辑
                    val bitmapState = produceState<Bitmap?>(initialValue = null, base64) {
                        value = withContext(Dispatchers.Default) { decodeBase64(base64) }
                    }
                    val decoded = bitmapState.value
                    if (decoded != null) {
                        Image(
                            bitmap = decoded.asImageBitmap(),
                            contentDescription = "点击刷新验证码",
                            modifier = Modifier.fillMaxSize()
                        )
                    } else {
                        Text(
                            text = "点击刷新",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
                // 旧版后端返回算术题文本，降级展示题目（点击可刷新换题）
                question != null -> Text(
                    text = question,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.SemiBold,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1
                )
                loading -> Text(
                    text = "加载中",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall
                )
                else -> Text(
                    text = "点击刷新",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
    }
}

private fun decodeBase64(base64: String): Bitmap? = try {
    // 兼容 data:image/png;base64,xxx 前缀
    val cleaned = base64.substringAfter(",")
    val bytes = Base64.decode(cleaned, Base64.DEFAULT)
    BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
} catch (e: Exception) {
    null
}

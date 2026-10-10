package com.example.hbsz_app_android.data.local

import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import javax.crypto.Cipher

/**
 * 生物识别登录管理器（第二批优化 #4）。
 *
 * 统一封装 BiometricManager 能力检查与 BiometricPrompt 弹窗：
 * - [canUse]：设备已录入 Class 3（强）生物特征且可用；
 * - [promptEncrypt]：开启快速登录时验证身份并返回加密 Cipher；
 * - [promptDecrypt]：快速登录时验证身份并返回解密 Cipher。
 *
 * 安全语义：CryptoObject 绑定 Keystore 密钥（见 [CredentialVault]），
 * 指纹验证通过后系统才允许 Cipher 工作——App 自身无法绕过。
 * 仅支持 BIOMETRIC_STRONG（Class 3，硬件级防伪造）；不满足的设备不显示入口。
 */
class BiometricLoginManager(
    private val activity: FragmentActivity,
    private val vault: CredentialVault
) {

    /** 设备是否支持且已录入强生物特征（指纹/面部 Class 3） */
    fun canUse(): Boolean =
        BiometricManager.from(activity).canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG) ==
                BiometricManager.BIOMETRIC_SUCCESS

    /**
     * 弹出生物识别验证（加密场景：登录成功后保存凭据）。
     * @param onSuccess 验证通过，返回可用的加密 Cipher（在回调内立即使用）
     * @param onError 验证失败/取消/不可用
     */
    fun promptEncrypt(
        onSuccess: (Cipher) -> Unit,
        onError: (String) -> Unit
    ) {
        // createEncryptCipher 内部已处理"密钥失效→删旧建新"，此处兜底其他异常，
        // 避免未捕获异常导致弹窗弹不出/应用崩溃
        val cipher = runCatching { vault.createEncryptCipher() }
            .getOrElse {
                onError("指纹登录开启失败，请重试")
                return
            }
        prompt(cipher, "开启指纹快速登录", "验证指纹以加密保存登录凭据", onSuccess, onError)
    }

    /**
     * 弹出生物识别验证（解密场景：快速登录）。
     * @param iv 开启时保存的 GCM IV（存于密文前缀）
     * @param onCredentialInvalid 密钥已永久失效（用户重录/删除过指纹）：
     *        旧密文不可解，须清除本地凭据并引导重新开启
     */
    fun promptDecrypt(
        iv: ByteArray,
        onCredentialInvalid: () -> Unit,
        onSuccess: (Cipher) -> Unit,
        onError: (String) -> Unit
    ) {
        val cipher = runCatching { vault.createDecryptCipher(iv) }
            .getOrElse {
                // 密钥永久失效：删除密钥（下次开启生成新钥）并通知上层清除凭据，
                // 修复"提示重新开启但旧凭据残留 → 反复失败"的死循环
                vault.deleteKey()
                onCredentialInvalid()
                return
            }
        prompt(cipher, "指纹快速登录", "验证指纹以自动填充登录信息", onSuccess, onError)
    }

    private fun prompt(
        cipher: Cipher,
        title: String,
        subtitle: String,
        onSuccess: (Cipher) -> Unit,
        onError: (String) -> Unit
    ) {
        val prompt = BiometricPrompt(
            activity,
            ContextCompat.getMainExecutor(activity),
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    val c = result.cryptoObject?.cipher
                    if (c != null) onSuccess(c) else onError("验证异常，请重试")
                }

                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    // errorCode 13（用户取消）/ 10（点"取消"）为正常退出，不作为错误提示
                    if (errorCode != BiometricPrompt.ERROR_USER_CANCELED &&
                        errorCode != BiometricPrompt.ERROR_NEGATIVE_BUTTON
                    ) {
                        onError(errString.toString())
                    }
                }

                override fun onAuthenticationFailed() {
                    // 单次指纹不匹配：系统会自动重试，静默
                }
            }
        )
        val info = BiometricPrompt.PromptInfo.Builder()
            .setTitle(title)
            .setSubtitle(subtitle)
            .setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_STRONG)
            .setNegativeButtonText("取消")
            .build()
        prompt.authenticate(info, BiometricPrompt.CryptoObject(cipher))
    }
}

package com.example.hbsz_app_android.data.local

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyPermanentlyInvalidatedException
import android.security.keystore.KeyProperties
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.inject.Inject
import javax.inject.Singleton
import android.util.Base64

/**
 * 生物识别凭据保险库（第二批优化 #4）。
 *
 * 原理：Android Keystore 生成 AES-256-GCM 密钥并绑定生物识别
 * （setUserAuthenticationRequired(true)）——密钥的使用（加密/解密）
 * 必须通过 BiometricPrompt 的 CryptoObject 完成，系统级保证：
 * 未通过指纹/面部验证，任何进程（含本 App）都无法解出密码明文。
 *
 * 安全属性：
 * - 密钥材料永不离开 TEE/StrongBox，无法导出；
 * - 新录入生物特征后密钥失效（KeyPermanentlyInvalidatedException），
 *   旧密文不可解，需重新登录开启；
 * - GCM 自带完整性校验，密文被篡改时解密失败。
 */
@Singleton
class CredentialVault @Inject constructor() {

    companion object {
        private const val KEYSTORE = "AndroidKeyStore"
        private const val KEY_ALIAS = "hbsz_biometric_login_key"
        private const val IV_SEPARATOR = ":"
    }

    /** 取出（必要时生成）绑定生物识别的 AES 密钥；取不到/已损坏时删除重建 */
    private fun getOrCreateKey(): SecretKey {
        val keyStore = KeyStore.getInstance(KEYSTORE).apply { load(null) }
        // getEntry 对损坏的密钥条目可能抛异常（罕见）：删除后走重建
        val existing = runCatching {
            keyStore.getEntry(KEY_ALIAS, null) as? KeyStore.SecretKeyEntry
        }.getOrNull()
        if (existing != null) return existing.secretKey
        return generateKey()
    }

    /**
     * 删除当前密钥（密钥因生物特征重录/删除而永久失效时调用），
     * 下次 [getOrCreateKey] 会重新生成，打破"失效密钥无法自愈"的死循环。
     */
    fun deleteKey() {
        runCatching {
            val keyStore = KeyStore.getInstance(KEYSTORE).apply { load(null) }
            keyStore.deleteEntry(KEY_ALIAS)
        }
    }

    private fun generateKey(): SecretKey {
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE)
        generator.init(
            KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                // 核心：密钥使用必须经过生物识别验证（BiometricPrompt.CryptoObject）
                .setUserAuthenticationRequired(true)
                // 指纹被移除后密钥失效（防新录入指纹解旧密文）
                .setInvalidatedByBiometricEnrollment(true)
                .build()
        )
        return generator.generateKey()
    }

    /**
     * 构建加密 Cipher（须包进 BiometricPrompt.CryptoObject 在验证成功回调中使用）。
     * 密钥已永久失效（用户重录/删除过指纹）时：删除旧密钥并立即生成新密钥重试，
     * 保证"重新开启快速登录"当轮即可成功，无需先失败一次。
     */
    fun createEncryptCipher(): Cipher {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        return try {
            cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey())
            cipher
        } catch (e: KeyPermanentlyInvalidatedException) {
            deleteKey()
            Cipher.getInstance("AES/GCM/NoPadding").apply {
                init(Cipher.ENCRYPT_MODE, generateKey())
            }
        }
    }

    /** 构建解密 Cipher（须包进 BiometricPrompt.CryptoObject 在验证成功回调中使用） */
    fun createDecryptCipher(iv: ByteArray): Cipher {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, getOrCreateKey(), GCMParameterSpec(128, iv))
        return cipher
    }

    /** 用验证通过的 Cipher 加密密码；输出格式 base64(iv):base64(ciphertext) */
    fun encrypt(cipher: Cipher, password: String): String {
        val ciphertext = cipher.doFinal(password.toByteArray(Charsets.UTF_8))
        return Base64.encodeToString(cipher.iv, Base64.NO_WRAP) +
                IV_SEPARATOR +
                Base64.encodeToString(ciphertext, Base64.NO_WRAP)
    }

    /** 用验证通过的 Cipher 解密密码；输入损坏/密钥失效返回 null（调用方应引导重新开启） */
    fun decrypt(cipher: Cipher, stored: String): String? = runCatching {
        val parts = stored.split(IV_SEPARATOR)
        if (parts.size != 2) return null
        val plaintext = cipher.doFinal(Base64.decode(parts[1], Base64.NO_WRAP))
        String(plaintext, Charsets.UTF_8)
    }.getOrNull()
}

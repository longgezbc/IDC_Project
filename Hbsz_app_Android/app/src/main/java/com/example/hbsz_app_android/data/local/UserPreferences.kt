package com.example.hbsz_app_android.data.local

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.userDataStore by preferencesDataStore(name = "hbsz_user")

/**
 * 用户本地偏好存储（token、用户名、角色等），基于 DataStore。
 */
@Singleton
class UserPreferences @Inject constructor(
    @ApplicationContext private val context: Context
) {

    private object Keys {
        val TOKEN = stringPreferencesKey("token")
        val REFRESH_TOKEN = stringPreferencesKey("refresh_token")
        val USERNAME = stringPreferencesKey("username")
        val ROLES = stringSetPreferencesKey("roles")
        // 生物识别快速登录（第二批 #4）：开关 + 用户名记忆 + Keystore 加密后的密码密文
        val BIOMETRIC_ENABLED = booleanPreferencesKey("biometric_enabled")
        val BIOMETRIC_USERNAME = stringPreferencesKey("biometric_username")
        val BIOMETRIC_PASSWORD_ENC = stringPreferencesKey("biometric_password_enc")
    }

    /** 登录 token，未登录时为 null */
    val tokenFlow: Flow<String?> = context.userDataStore.data.map { it[Keys.TOKEN] }

    /** 当前登录用户名 */
    val usernameFlow: Flow<String?> = context.userDataStore.data.map { it[Keys.USERNAME] }

    /** 当前用户角色码集合（与后端 sys_role.code 一致，如 admin/project_manager/engineer） */
    val rolesFlow: Flow<Set<String>> = context.userDataStore.data.map { it[Keys.ROLES] ?: emptySet() }

    /** 生物识别快速登录是否已开启（凭据已加密保存） */
    val biometricEnabledFlow: Flow<Boolean> = context.userDataStore.data.map { it[Keys.BIOMETRIC_ENABLED] ?: false }

    suspend fun saveLoginInfo(
        token: String,
        refreshToken: String?,
        username: String?,
        roles: Set<String> = emptySet()
    ) {
        context.userDataStore.edit { prefs ->
            prefs[Keys.TOKEN] = token
            // refreshToken/username 为空时移除旧值，避免换账号登录后残留上一个账号的数据
            if (refreshToken != null) prefs[Keys.REFRESH_TOKEN] = refreshToken
            else prefs.remove(Keys.REFRESH_TOKEN)
            if (username != null) prefs[Keys.USERNAME] = username
            else prefs.remove(Keys.USERNAME)
            // 角色为空时同样移除旧值，防止上一个账号的权限残留
            if (roles.isNotEmpty()) prefs[Keys.ROLES] = roles
            else prefs.remove(Keys.ROLES)
        }
    }

    /**
     * 保存生物识别快速登录凭据（Keystore 加密后的密码密文）。
     * 调用前提：BiometricPrompt(CryptoObject) 验证已通过。
     */
    suspend fun saveBiometricCredential(username: String, encryptedPassword: String) {
        context.userDataStore.edit { prefs ->
            prefs[Keys.BIOMETRIC_ENABLED] = true
            prefs[Keys.BIOMETRIC_USERNAME] = username
            prefs[Keys.BIOMETRIC_PASSWORD_ENC] = encryptedPassword
        }
    }

    /** 读取生物识别凭据（用户名 + 密码密文）；未开启时返回 null */
    suspend fun readBiometricCredential(): Pair<String, String>? {
        val prefs = context.userDataStore.data.first()
        val username = prefs[Keys.BIOMETRIC_USERNAME] ?: return null
        val enc = prefs[Keys.BIOMETRIC_PASSWORD_ENC] ?: return null
        return username to enc
    }

    /**
     * 关闭生物识别快速登录并清除加密凭据
     * （登出/密钥失效/用户主动关闭时调用）。
     */
    suspend fun clearBiometricCredential() {
        context.userDataStore.edit { prefs ->
            prefs.remove(Keys.BIOMETRIC_ENABLED)
            prefs.remove(Keys.BIOMETRIC_USERNAME)
            prefs.remove(Keys.BIOMETRIC_PASSWORD_ENC)
        }
    }

    /**
     * 清除登录态（登出/token 失效时调用）。
     * 注意：保留生物识别快速登录凭据（biometric_*）——密码密文受 Keystore+
     * 指纹验证保护，且"指纹快速登录"的使用场景正是登出后的免密重登；
     * 凭据失效由 Keystore 密钥失配自动检测（见 CredentialVault）。
     */
    suspend fun clear() {
        context.userDataStore.edit { prefs ->
            prefs.remove(Keys.TOKEN)
            prefs.remove(Keys.REFRESH_TOKEN)
            prefs.remove(Keys.USERNAME)
            prefs.remove(Keys.ROLES)
        }
    }
}

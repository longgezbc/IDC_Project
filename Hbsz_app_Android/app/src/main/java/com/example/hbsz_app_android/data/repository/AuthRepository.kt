package com.example.hbsz_app_android.data.repository

import com.example.hbsz_app_android.common.Resource
import com.example.hbsz_app_android.common.runCatchingApi
import kotlinx.coroutines.CancellationException
import com.example.hbsz_app_android.data.local.UserPreferences
import com.example.hbsz_app_android.data.remote.ApiService
import com.example.hbsz_app_android.data.remote.dto.CaptchaResponse
import com.example.hbsz_app_android.data.remote.dto.LoginRequest
import com.example.hbsz_app_android.data.remote.dto.LoginResponse
import javax.inject.Inject
import javax.inject.Singleton

interface AuthRepository {
    suspend fun getCaptcha(): Resource<CaptchaResponse>
    suspend fun login(username: String, password: String, captchaId: String, captcha: String): Resource<LoginResponse>
    suspend fun logout()
}

@Singleton
class AuthRepositoryImpl @Inject constructor(
    private val apiService: ApiService,
    private val userPreferences: UserPreferences
) : AuthRepository {

    override suspend fun getCaptcha(): Resource<CaptchaResponse> = runCatchingApi {
        val resp = apiService.getCaptcha()
        if (resp.isSuccess && resp.data != null) {
            Resource.Success(resp.data)
        } else {
            Resource.Error(resp.message ?: "获取验证码失败")
        }
    }

    override suspend fun login(
        username: String,
        password: String,
        captchaId: String,
        captcha: String
    ): Resource<LoginResponse> = runCatchingApi {
        val resp = apiService.login(LoginRequest(username, password, captchaId, captcha))
        val body = resp.data
        if (resp.isSuccess && body?.token != null) {
            userPreferences.saveLoginInfo(
                token = body.token,
                refreshToken = body.refreshToken,
                username = username,
                roles = body.userInfo?.roles?.toSet() ?: emptySet()
            )
            Resource.Success(body)
        } else {
            Resource.Error(resp.message ?: "登录失败")
        }
    }

    override suspend fun logout() {
        try {
            apiService.logout()
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            // 登出接口失败不阻塞本地清理
        }
        userPreferences.clear()
    }
}

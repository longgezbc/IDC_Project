package com.example.hbsz_app_android.data.remote.dto

import kotlinx.serialization.Serializable

/**
 * 登录请求（后端要求验证码必填：captchaId + captcha）。
 */
@Serializable
data class LoginRequest(
    val username: String,
    val password: String,
    val captchaId: String,
    val captcha: String
)

/**
 * 登录响应：JWT token + 过期秒数 + 用户信息（对齐后端 LoginVO）。
 */
@Serializable
data class LoginResponse(
    val token: String? = null,
    val refreshToken: String? = null,
    val expiresIn: Int? = null,
    val userInfo: LoginUserInfoDto? = null
)

/** 后端 LoginVO.userInfo 嵌套对象 */
@Serializable
data class LoginUserInfoDto(
    val id: Long? = null,
    val username: String? = null,
    val name: String? = null,
    val avatar: String? = null,
    val roles: List<String> = emptyList(),
    val permissions: List<String> = emptyList()
)

/**
 * 图形验证码：captchaId + Base64 图片。
 * question 为旧版后端的算术题文本（如 "6 + 2 = ?"），服务端升级后不再返回，
 * 保留字段用于兼容旧部署：无 image 时降级显示题目文本。
 */
@Serializable
data class CaptchaResponse(
    val captchaId: String? = null,
    val image: String? = null,
    val question: String? = null
)

package com.example.hbsz_app_android.data.remote

import kotlinx.serialization.Serializable

/**
 * 后端统一响应结构（camelCase）：
 * { "code": 200, "message": "success", "data": { ... } }
 */
@Serializable
data class ApiResponse<T>(
    val code: Int = -1,
    val message: String? = null,
    val data: T? = null
) {
    val isSuccess: Boolean get() = code == 200
}

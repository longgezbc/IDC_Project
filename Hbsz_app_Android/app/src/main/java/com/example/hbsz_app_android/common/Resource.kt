package com.example.hbsz_app_android.common

import kotlinx.coroutines.CancellationException
import retrofit2.HttpException
import java.io.IOException

/**
 * 通用结果封装：网络请求 / 业务操作的统一返回模型。
 */
sealed interface Resource<out T> {
    data class Success<T>(val data: T) : Resource<T>
    data class Error(val message: String) : Resource<Nothing>
    data object Loading : Resource<Nothing>
}

/**
 * 网络请求统一异常兜底：
 * - CancellationException → 协程取消，必须原样抛出，不可吞掉
 * - IOException  → 网络不可用
 * - HttpException → 服务端 HTTP 错误码
 * - 其他异常     → 未知错误
 */
suspend fun <T> runCatchingApi(block: suspend () -> Resource<T>): Resource<T> = try {
    block()
} catch (e: CancellationException) {
    throw e
} catch (e: IOException) {
    Resource.Error("网络连接失败，请检查网络后重试")
} catch (e: HttpException) {
    Resource.Error("服务器异常（${e.code()}），请稍后重试")
} catch (e: Exception) {
    Resource.Error(e.message ?: "请求失败，请稍后重试")
}

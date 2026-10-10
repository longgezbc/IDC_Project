package com.example.hbsz_app_android

import com.example.hbsz_app_android.common.Resource
import com.example.hbsz_app_android.common.runCatchingApi
import com.example.hbsz_app_android.common.sanitizeFileName
import com.example.hbsz_app_android.data.remote.ApiResponse
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import retrofit2.HttpException
import retrofit2.Response
import java.io.IOException

/**
 * 核心逻辑单元测试：Resource 异常兜底 / ApiResponse 判定 / 文件名净化。
 */
class ExampleUnitTest {

    // ==================== ApiResponse ====================

    @Test
    fun apiResponse_isSuccess_onlyWhenCode200() {
        assertTrue(ApiResponse<String>(code = 200).isSuccess)
        assertFalse(ApiResponse<String>(code = 401).isSuccess)
        assertFalse(ApiResponse<String>(code = 500).isSuccess)
    }

    // ==================== runCatchingApi ====================

    @Test
    fun runCatchingApi_ioException_returnsNetworkError() = runBlocking {
        val result = runCatchingApi<String> { throw IOException("no network") }
        assertTrue(result is Resource.Error)
        assertEquals("网络连接失败，请检查网络后重试", (result as Resource.Error).message)
    }

    @Test
    fun runCatchingApi_httpException_returnsServerErrorWithCode() = runBlocking {
        val body = "err".toResponseBody("text/plain".toMediaType())
        val result = runCatchingApi<String> { throw HttpException(Response.error<Any>(500, body)) }
        assertTrue(result is Resource.Error)
        assertEquals("服务器异常（500），请稍后重试", (result as Resource.Error).message)
    }

    @Test
    fun runCatchingApi_genericException_returnsErrorMessage() = runBlocking {
        val result = runCatchingApi<String> { throw IllegalStateException("boom") }
        assertTrue(result is Resource.Error)
        assertEquals("boom", (result as Resource.Error).message)
    }

    @Test(expected = CancellationException::class)
    fun runCatchingApi_cancellationException_isRethrown(): Unit = runBlocking {
        // 协程取消必须向上传播，不可被吞掉
        runCatchingApi<String> { throw CancellationException("cancelled") }
    }

    @Test
    fun runCatchingApi_success_passthrough() = runBlocking {
        val result = runCatchingApi { Resource.Success("ok") }
        assertEquals("ok", (result as Resource.Success).data)
    }

    // ==================== sanitizeFileName ====================

    @Test
    fun sanitizeFileName_removesIllegalCharacters() {
        assertEquals("a_b_c", sanitizeFileName("a/b\\c"))
        assertEquals("报告_2026_.xlsx", sanitizeFileName("报告:2026?.xlsx"))
        assertEquals("施工_日报_.xlsx", sanitizeFileName("施工\r日报\n.xlsx"))
    }

    @Test
    fun sanitizeFileName_blankOrTrailingDots_fallsBack() {
        assertEquals("download", sanitizeFileName("///"))
        assertEquals("download", sanitizeFileName("   "))
        assertEquals("name", sanitizeFileName("name...", fallback = "name"))
    }
}

package com.example.hbsz_app_android.data.update

import com.example.hbsz_app_android.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 版本自更新管理器（第二批优化 #3）。
 *
 * 分发设计：版本清单与 APK 同源同目录（nginx 静态 /downloads/），
 * 杜绝"接口已宣告新版本但 APK 未就位"的分裂状态；
 * 停止推新（kill switch）= 从服务器更新 app-latest.json 的 forceUpdate/versionCode。
 *
 * 协议：GET {API_BASE_URL 推导出的 host}/downloads/app-latest.json
 * {
 *   "versionCode": 3,
 *   "versionName": "1.0.2",
 *   "apkUrl": "/downloads/Hbsz_IDC_v1.0.2.apk",
 *   "forceUpdate": false,
 *   "updateLog": "..."
 * }
 *
 * 容错策略：检查失败静默跳过（不影响启动），下载失败允许重试。
 */
@Singleton
class AppUpdateManager @Inject constructor(
    private val client: OkHttpClient,
    private val json: Json
) {

    @Serializable
    data class AppLatestInfo(
        val versionCode: Int,
        val versionName: String,
        val apkUrl: String,
        val forceUpdate: Boolean = false,
        val updateLog: String = ""
    )

    /** 当前版本是否落后于远端（versionCode 单调递增比对） */
    fun isNewer(remote: AppLatestInfo): Boolean = remote.versionCode > BuildConfig.VERSION_CODE

    /**
     * 拉取远端版本清单。
     * @return null 表示检查失败（网络异常/清单缺失/格式错误），调用方静默跳过
     */
    suspend fun fetchLatest(): AppLatestInfo? = withContext(Dispatchers.IO) {
        runCatching {
            val request = Request.Builder().url(buildUrl(LATEST_JSON_PATH)).get().build()
            client.newCall(request).execute().use { resp ->
                if (!resp.isSuccessful) return@runCatching null
                val body = resp.body?.string() ?: return@runCatching null
                json.decodeFromString(AppLatestInfo.serializer(), body)
            }
        }.getOrNull()
    }

    /**
     * 下载 APK 到应用专属外部目录（无需存储权限，卸载自动清理）。
     * @param onProgress 0..100
     * @return 已下载文件；失败抛异常由调用方提示
     */
    suspend fun downloadApk(
        info: AppLatestInfo,
        targetDir: File,
        onProgress: (Int) -> Unit
    ): File = withContext(Dispatchers.IO) {
        val url = if (info.apkUrl.startsWith("http")) info.apkUrl else buildUrl(info.apkUrl)
        val request = Request.Builder().url(url).get().build()
        client.newCall(request).execute().use { resp ->
            if (!resp.isSuccessful) throw IllegalStateException("下载失败（HTTP ${resp.code}）")
            val body = resp.body ?: throw IllegalStateException("下载失败：响应为空")
            val total = body.contentLength()
            val file = File(targetDir, APK_FILE_NAME)
            targetDir.mkdirs()
            var lastReported = -1
            body.byteStream().use { input ->
                file.outputStream().use { output ->
                    val buffer = ByteArray(16 * 1024)
                    var downloaded = 0L
                    while (true) {
                        val read = input.read(buffer)
                        if (read == -1) break
                        output.write(buffer, 0, read)
                        downloaded += read
                        if (total > 0) {
                            val pct = (downloaded * 100 / total).toInt()
                            // 进度回调降频：仅在整数百分比变化时回调，避免重组风暴
                            if (pct != lastReported) {
                                lastReported = pct
                                onProgress(pct)
                            }
                        }
                    }
                }
            }
            file
        }
    }

    /** 从 API_BASE_URL（http://host/api/）推导下载基地址（http://host/） */
    private fun buildUrl(path: String): String {
        val base = BuildConfig.API_BASE_URL.substringBefore("/api/")
        return base.trimEnd('/') + "/" + path.trimStart('/')
    }

    companion object {
        private const val LATEST_JSON_PATH = "downloads/app-latest.json"
        private const val APK_FILE_NAME = "hbsz_update.apk"
    }
}

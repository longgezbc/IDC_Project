package com.example.hbsz_app_android.common

import android.Manifest
import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.InputStream

/** 净化文件名：去除路径分隔符等非法字符，防止写入异常位置 */
fun sanitizeFileName(raw: String, fallback: String = "download"): String {
    val cleaned = raw.replace(Regex("[\\\\/:*?\"<>|\\r\\n\\t]"), "_").trim().trimEnd('.')
    return if (cleaned.isBlank() || cleaned.all { it == '_' }) fallback else cleaned
}

/**
 * 下载文件保存工具：写入系统公共「下载」目录，用户可在文件管理器/下载应用中直接找到。
 * - API 29+：MediaStore.Downloads，无需任何权限
 * - API 26~28：写入公共 Download 目录，需调用方先申请 WRITE_EXTERNAL_STORAGE 运行时权限
 */
object DownloadSaver {

    /** API 26~28 是否已授予写存储权限（API 29+ 恒为 true） */
    fun hasWritePermission(context: Context): Boolean =
        Build.VERSION.SDK_INT >= 29 ||
            ContextCompat.checkSelfPermission(
                context, Manifest.permission.WRITE_EXTERNAL_STORAGE
            ) == PackageManager.PERMISSION_GRANTED

    /**
     * 将输入流保存到公共下载目录。
     * @return 成功时返回文件位置描述（如「下载/xxx.xlsx」），失败抛出异常（调用方自行捕获）。
     */
    suspend fun saveToDownloads(
        context: Context,
        fileName: String,
        mimeType: String,
        input: InputStream
    ): String = withContext(Dispatchers.IO) {
        val safeName = sanitizeFileName(fileName)
        if (Build.VERSION.SDK_INT >= 29) {
            val resolver = context.contentResolver
            val values = ContentValues().apply {
                put(MediaStore.Downloads.DISPLAY_NAME, safeName)
                put(MediaStore.Downloads.MIME_TYPE, mimeType)
                put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
                put(MediaStore.Downloads.IS_PENDING, 1)
            }
            val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                ?: error("创建下载记录失败")
            try {
                resolver.openOutputStream(uri)?.use { out -> input.copyTo(out) }
                    ?: error("打开输出流失败")
                values.clear()
                values.put(MediaStore.Downloads.IS_PENDING, 0)
                resolver.update(uri, values, null, null)
            } catch (e: Exception) {
                resolver.delete(uri, null, null)
                throw e
            }
            "${Environment.DIRECTORY_DOWNLOADS}/$safeName"
        } else {
            val dir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            dir.mkdirs()
            val file = File(dir, safeName)
            file.outputStream().use { out -> input.copyTo(out) }
            file.absolutePath
        }
    }
}

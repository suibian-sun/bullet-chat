package com.suibian.bulletchat.update

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.core.content.FileProvider
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL

/** APK 下载与调起系统安装器。 */
object UpdateInstaller {

    private const val TIMEOUT_CONNECT = 15000
    private const val TIMEOUT_READ = 30000

    fun download(
        context: Context,
        url: String,
        version: String,
        onProgress: (percent: Int, downloaded: Long, total: Long) -> Unit,
    ): File {
        val dir = File(context.cacheDir, "updates").apply { mkdirs() }
        val target = File(dir, "BulletChat-$version.apk")
        if (target.exists()) target.delete()

        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            instanceFollowRedirects = true
            connectTimeout = TIMEOUT_CONNECT
            readTimeout = TIMEOUT_READ
            setRequestProperty("User-Agent", "BulletChat-Android")
            setRequestProperty("Accept", "application/octet-stream")
        }
        try {
            if (connection.responseCode !in 200..299) error("HTTP ${connection.responseCode}")
            val total = connection.contentLength.toLong().takeIf { it > 0 } ?: 0L
            var downloaded = 0L
            var lastPercent = -1

            connection.inputStream.use { input ->
                FileOutputStream(target).use { output ->
                    val buffer = ByteArray(64 * 1024)
                    while (true) {
                        val read = input.read(buffer)
                        if (read <= 0) break
                        output.write(buffer, 0, read)
                        downloaded += read
                        val percent = if (total > 0) (downloaded * 100 / total).toInt() else -1
                        if (percent != lastPercent) {
                            lastPercent = percent
                            onProgress(percent, downloaded, total)
                        }
                    }
                    output.flush()
                }
            }
            return target
        } finally {
            connection.disconnect()
        }
    }

    /** 调起系统安装器；Android 8.0+ 需已授予「安装未知应用」。 */
    fun install(context: Context, file: File) {
        val uri: Uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file,
        )
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }
        context.startActivity(intent)
    }

    fun formatSize(bytes: Long): String = when {
        bytes <= 0L -> "未知大小"
        bytes < 1024 * 1024 -> String.format(java.util.Locale.US, "%.0f KB", bytes / 1024f)
        else -> String.format(java.util.Locale.US, "%.1f MB", bytes / 1024f / 1024f)
    }
}

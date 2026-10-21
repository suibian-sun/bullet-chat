package com.suibian.bulletchat.update

import android.content.Context
import org.json.JSONObject
import java.io.BufferedReader
import java.net.HttpURLConnection
import java.net.URL

data class ReleaseInfo(
    val version: String,
    val tagName: String,
    val notes: String,
    val apkUrl: String,
    val apkSize: Long,
    val publishedAt: String,
)

/**
 * 更新检查：读取 GitHub Releases 的最新版本。
 * 只做 GET 请求，不上传任何本机数据。
 */
object UpdateChecker {

    private const val API_URL = "https://api.github.com/repos/suibian-sun/bullet-chat/releases/latest"
    private const val PREFS = "bullet_chat_update"
    private const val KEY_LAST_CHECK = "last_check"
    private const val AUTO_INTERVAL = 6L * 60 * 60 * 1000

    private var prefs: android.content.SharedPreferences? = null

    fun init(context: Context) {
        if (prefs == null) {
            prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        }
    }

    fun shouldAutoCheck(): Boolean {
        val last = prefs?.getLong(KEY_LAST_CHECK, 0L) ?: 0L
        return System.currentTimeMillis() - last > AUTO_INTERVAL
    }

    fun markChecked() {
        prefs?.edit()?.putLong(KEY_LAST_CHECK, System.currentTimeMillis())?.apply()
    }

    /** 阻塞请求，需在子线程调用。 */
    fun fetchLatest(): Result<ReleaseInfo> = runCatching {
        val connection = (URL(API_URL).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 15000
            readTimeout = 20000
            setRequestProperty("Accept", "application/vnd.github+json")
            setRequestProperty("User-Agent", "BulletChat-Android")
        }
        try {
            val code = connection.responseCode
            if (code !in 200..299) error("HTTP $code")
            val body = connection.inputStream.bufferedReader().use(BufferedReader::readText)
            parse(body)
        } finally {
            connection.disconnect()
        }
    }

    private fun parse(body: String): ReleaseInfo {
        val json = JSONObject(body)
        val tag = json.optString("tag_name")
        val notes = json.optString("body").ifBlank { "本次更新包含稳定性改进与细节优化。" }
        val published = json.optString("published_at")

        var apkUrl = ""
        var apkSize = 0L
        val assets = json.optJSONArray("assets")
        if (assets != null) {
            for (index in 0 until assets.length()) {
                val asset = assets.optJSONObject(index) ?: continue
                val name = asset.optString("name")
                if (name.endsWith(".apk", ignoreCase = true)) {
                    apkUrl = asset.optString("browser_download_url")
                    apkSize = asset.optLong("size", 0L)
                    break
                }
            }
        }
        return ReleaseInfo(
            version = tag.removePrefix("v").removePrefix("V"),
            tagName = tag,
            notes = notes,
            apkUrl = apkUrl,
            apkSize = apkSize,
            publishedAt = published,
        )
    }

    /** 语义化比较：remote 比 current 新才返回 true。 */
    fun isNewer(remote: String, current: String): Boolean {
        val left = remote.split(".", "-").mapNotNull { it.toIntOrNull() }
        val right = current.split(".", "-").mapNotNull { it.toIntOrNull() }
        val size = maxOf(left.size, right.size)
        for (index in 0 until size) {
            val a = left.getOrElse(index) { 0 }
            val b = right.getOrElse(index) { 0 }
            if (a != b) return a > b
        }
        return false
    }
}

package com.suibian.bulletchat.data

import android.content.Context
import android.content.SharedPreferences
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class UsageStats(
    val totalForwarded: Int = 0,
    val todayForwarded: Int = 0,
    val totalRunMillis: Long = 0L,
    val sessionStart: Long = 0L,
    val lastApp: String = "",
    val lastTitle: String = "",
    val lastText: String = "",
    val lastTime: Long = 0L,
)

/**
 * 运行统计：累计/今日转发条数、累计运行时长、本次会话起始时间、最近一条消息。
 * 与服务生命周期解耦，进程被杀也能保留数据。
 */
object StatsStore {

    private const val PREFS = "bullet_chat_stats"
    private const val KEY_TOTAL = "total_forwarded"
    private const val KEY_TODAY = "today_forwarded"
    private const val KEY_TODAY_DATE = "today_date"
    private const val KEY_TOTAL_RUN = "total_run_ms"
    private const val KEY_SESSION_START = "session_start"
    private const val KEY_LAST_APP = "last_app"
    private const val KEY_LAST_TITLE = "last_title"
    private const val KEY_LAST_TEXT = "last_text"
    private const val KEY_LAST_TIME = "last_time"

    private lateinit var prefs: SharedPreferences
    private var initialized = false

    @Synchronized
    fun init(context: Context) {
        if (initialized) return
        prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        initialized = true
        rolloverIfNeeded()
    }

    private fun todayKey(): String =
        SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())

    private fun rolloverIfNeeded() {
        val stored = prefs.getString(KEY_TODAY_DATE, null)
        if (stored != todayKey()) {
            prefs.edit().putString(KEY_TODAY_DATE, todayKey()).putInt(KEY_TODAY, 0).apply()
        }
    }

    fun snapshot(): UsageStats {
        if (!initialized) return UsageStats()
        rolloverIfNeeded()
        return UsageStats(
            totalForwarded = prefs.getInt(KEY_TOTAL, 0),
            todayForwarded = prefs.getInt(KEY_TODAY, 0),
            totalRunMillis = prefs.getLong(KEY_TOTAL_RUN, 0L),
            sessionStart = prefs.getLong(KEY_SESSION_START, 0L),
            lastApp = prefs.getString(KEY_LAST_APP, "").orEmpty(),
            lastTitle = prefs.getString(KEY_LAST_TITLE, "").orEmpty(),
            lastText = prefs.getString(KEY_LAST_TEXT, "").orEmpty(),
            lastTime = prefs.getLong(KEY_LAST_TIME, 0L),
        )
    }

    @Synchronized
    fun recordForwarded(appLabel: String, title: String, text: String) {
        if (!initialized) return
        rolloverIfNeeded()
        prefs.edit()
            .putInt(KEY_TOTAL, prefs.getInt(KEY_TOTAL, 0) + 1)
            .putInt(KEY_TODAY, prefs.getInt(KEY_TODAY, 0) + 1)
            .putString(KEY_LAST_APP, appLabel)
            .putString(KEY_LAST_TITLE, title)
            .putString(KEY_LAST_TEXT, text)
            .putLong(KEY_LAST_TIME, System.currentTimeMillis())
            .apply()
    }

    @Synchronized
    fun sessionStart() {
        if (!initialized) return
        prefs.edit().putLong(KEY_SESSION_START, System.currentTimeMillis()).apply()
    }

    @Synchronized
    fun sessionEnd() {
        if (!initialized) return
        val start = prefs.getLong(KEY_SESSION_START, 0L)
        if (start <= 0L) return
        val elapsed = (System.currentTimeMillis() - start).coerceIn(0L, 7L * 24 * 60 * 60 * 1000)
        val total = prefs.getLong(KEY_TOTAL_RUN, 0L) + elapsed
        prefs.edit()
            .putLong(KEY_TOTAL_RUN, total)
            .putLong(KEY_SESSION_START, 0L)
            .apply()
    }

    /** 本次运行时长；会话异常残留时最多按 12 小时计。 */
    fun sessionMillis(stats: UsageStats): Long {
        if (stats.sessionStart <= 0L) return 0L
        return (System.currentTimeMillis() - stats.sessionStart).coerceIn(0L, 12L * 60 * 60 * 1000)
    }
}

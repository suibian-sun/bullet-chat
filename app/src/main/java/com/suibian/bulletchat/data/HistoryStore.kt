package com.suibian.bulletchat.data

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject

/** 一条已转发的弹幕记录。 */
data class HistoryEntry(
    val packageName: String,
    val appLabel: String,
    val title: String,
    val text: String,
    val time: Long,
) {
    val displayText: String
        get() = when {
            title.isBlank() -> text
            text.isBlank() -> title
            text.startsWith(title) -> text
            else -> "$title：$text"
        }
}

/**
 * 弹幕历史：只保留最近 [MAX_ENTRIES] 条，全部存在本机。
 * 与统计一样是「本地可清理」的数据，不联网、不上传。
 */
object HistoryStore {

    private const val PREFS = "bullet_chat_history"
    private const val KEY_ENTRIES = "entries"
    const val MAX_ENTRIES = 60

    private lateinit var prefs: SharedPreferences
    private var initialized = false
    private var cache: MutableList<HistoryEntry> = ArrayList()

    @Synchronized
    fun init(context: Context) {
        if (initialized) return
        prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        cache = read()
        initialized = true
    }

    /** 最新的在前。 */
    fun snapshot(): List<HistoryEntry> {
        if (!initialized) return emptyList()
        return synchronized(this) { ArrayList(cache) }
    }

    fun count(): Int = if (!initialized) 0 else synchronized(this) { cache.size }

    @Synchronized
    fun record(entry: HistoryEntry) {
        if (!initialized) return
        cache.add(0, entry)
        while (cache.size > MAX_ENTRIES) cache.removeAt(cache.size - 1)
        write()
    }

    @Synchronized
    fun clear() {
        if (!initialized) return
        cache.clear()
        write()
    }

    private fun read(): MutableList<HistoryEntry> {
        val raw = prefs.getString(KEY_ENTRIES, null) ?: return ArrayList()
        return runCatching {
            val array = JSONArray(raw)
            val list = ArrayList<HistoryEntry>(array.length())
            for (index in 0 until array.length()) {
                val item = array.optJSONObject(index) ?: continue
                list.add(
                    HistoryEntry(
                        packageName = item.optString("pkg"),
                        appLabel = item.optString("app"),
                        title = item.optString("title"),
                        text = item.optString("text"),
                        time = item.optLong("time"),
                    ),
                )
            }
            list
        }.getOrElse { ArrayList() }
    }

    private fun write() {
        val array = JSONArray()
        cache.forEach { entry ->
            array.put(
                JSONObject().apply {
                    put("pkg", entry.packageName)
                    put("app", entry.appLabel)
                    put("title", entry.title)
                    put("text", entry.text)
                    put("time", entry.time)
                },
            )
        }
        prefs.edit().putString(KEY_ENTRIES, array.toString()).apply()
    }
}

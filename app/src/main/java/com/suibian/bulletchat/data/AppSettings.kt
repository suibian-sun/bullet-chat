package com.suibian.bulletchat.data

import android.content.Context
import android.content.SharedPreferences
import android.content.res.Configuration
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class DisplayOrientation(val label: String) {
    ALL("全部方向"),
    PORTRAIT("仅竖屏"),
    LANDSCAPE("仅横屏");

    fun matches(orientation: Int): Boolean = when (this) {
        ALL -> true
        PORTRAIT -> orientation == Configuration.ORIENTATION_PORTRAIT
        LANDSCAPE -> orientation == Configuration.ORIENTATION_LANDSCAPE
    }
}

/** 应用级过滤：在「通知来源」之外再叠一层白/黑名单。 */
enum class FilterMode(val label: String, val short: String) {
    OFF("不过滤", "关闭"),
    WHITELIST("仅白名单内应用", "白名单"),
    BLACKLIST("排除黑名单应用", "黑名单"),
}

/** 弹幕展示哪部分内容。 */
enum class ContentMode(val label: String) {
    TITLE_AND_TEXT("标题 + 正文"),
    TEXT_ONLY("仅正文"),
    TITLE_ONLY("仅标题"),
}

/** 弹幕卡片外观。 */
enum class CardStyle(val label: String, val desc: String) {
    BUBBLE("胶囊气泡", "全圆角 + 轻投影，最接近聊天气泡"),
    CARD("通知卡片", "左侧蓝色强调条，接近系统通知"),
    MINIMAL("极简文字", "无底色，仅文字描边，最不遮挡画面"),
}

/** 关键词策略：过滤含关键词的消息，或只显示含关键词的消息。 */
enum class KeywordMode(val label: String) {
    BLOCK("过滤含关键词的消息"),
    ALLOW("仅显示含关键词的消息"),
}

/**
 * 卡片底色方案。
 * 浅色卡在浅色应用上容易糊在一起，深色卡在深色应用上同理，
 * 因此提供手动切换与「跟随系统深色模式」。
 */
enum class CardTheme(val label: String, val desc: String) {
    LIGHT("浅色卡片", "白色底 + 深色文字，适合浅色界面"),
    DARK("深色卡片", "深色底 + 浅色文字，适合深色界面与视频"),
    AUTO("跟随系统", "系统开启深色模式时自动使用深色卡片"),
}

/** 每分钟最多显示多少条弹幕，0 表示不限制。 */
val THROTTLE_OPTIONS = intArrayOf(0, 30, 60, 120)

/**
 * 应用设置，全部保存在本地，不联网。
 */
data class AppSettings(
    val enabled: Boolean = false,
    val selectedPackages: Set<String> = DEFAULT_SELECTED_PACKAGES,
    val onlyWhenScreenOn: Boolean = true,
    val showAppIcon: Boolean = true,
    val skipGroupSummary: Boolean = true,
    val displayOrientation: DisplayOrientation = DisplayOrientation.ALL,
    val clickToOpen: Boolean = false,
    val tryFloatingWindow: Boolean = true,
    val positionPercent: Int = 12,
    val laneCount: Int = 5,
    val alphaPercent: Int = 100,
    val fontSp: Int = 16,
    val speedPercent: Int = 100,
    val blacklist: String = "",
    val keywordMode: KeywordMode = KeywordMode.BLOCK,
    val filterMode: FilterMode = FilterMode.OFF,
    val filterPackages: Set<String> = emptySet(),
    val cardStyle: CardStyle = CardStyle.BUBBLE,
    val cardAlphaPercent: Int = 92,
    val cardTheme: CardTheme = CardTheme.LIGHT,
    val contentMode: ContentMode = ContentMode.TITLE_AND_TEXT,
    val quietEnabled: Boolean = false,
    val quietStartMinute: Int = 23 * 60,
    val quietEndMinute: Int = 7 * 60,
    /** 每分钟最多转发条数；0 表示不限。 */
    val throttlePerMinute: Int = 0,
) {
    /**
     * 是否允许该应用的通知进入弹幕。
     *
     * - 关闭过滤：只看「通知来源」
     * - 白名单：白名单优先于「通知来源」，加进白名单的应用直接放行
     * - 黑名单：在「通知来源」的基础上排除名单内的应用
     */
    fun accepts(packageName: String): Boolean = when (filterMode) {
        FilterMode.OFF -> packageName in selectedPackages
        FilterMode.WHITELIST -> packageName in filterPackages
        FilterMode.BLACKLIST -> packageName in selectedPackages && packageName !in filterPackages
    }

    /** 当前时刻（0 点起的分钟数）是否处于安静时段，支持跨零点。 */
    fun inQuietHours(minuteOfDay: Int): Boolean {
        if (!quietEnabled) return false
        val start = quietStartMinute.coerceIn(0, 1439)
        val end = quietEndMinute.coerceIn(0, 1439)
        if (start == end) return false
        return if (start < end) minuteOfDay in start until end else minuteOfDay >= start || minuteOfDay < end
    }

    /** 关键词是否命中；返回 true 表示应当丢弃这条消息。 */
    fun keywordBlocks(title: String, text: String): Boolean {
        if (blacklist.isBlank()) return false
        val keywords = parseKeywords(blacklist)
        if (keywords.isEmpty()) return false
        val haystack = "$title\n$text".lowercase()
        val hit = keywords.any { haystack.contains(it) }
        return when (keywordMode) {
            KeywordMode.BLOCK -> hit
            KeywordMode.ALLOW -> !hit
        }
    }

    /** 按当前配置与系统深色模式解析出实际使用的卡片底色。 */
    fun resolveDark(uiMode: Int): Boolean = when (cardTheme) {
        CardTheme.LIGHT -> false
        CardTheme.DARK -> true
        CardTheme.AUTO ->
            (uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
    }

    companion object {
        /** 关键词按中英文逗号、分号、顿号与换行分隔。 */
        private val SPLITTER = Regex("[,，;；、\\n]+")

        // 通知可能每秒来好几条，缓存上一次的解析结果，避免反复编译正则
        private var cachedRaw: String = ""
        private var cachedKeywords: List<String> = emptyList()

        fun parseKeywords(raw: String): List<String> {
            if (raw == cachedRaw) return cachedKeywords
            val parsed = raw.split(SPLITTER)
                .map { it.trim().lowercase() }
                .filter { it.isNotEmpty() }
            cachedRaw = raw
            cachedKeywords = parsed
            return parsed
        }
    }
}

object ChatPackages {
    const val WECHAT = "com.tencent.mm"
    const val QQ = "com.tencent.mobileqq"
    const val TIM = "com.tencent.tim"
    const val SYSTEM_UI = "com.android.systemui"

    val DEFAULT_SELECTED = linkedSetOf(WECHAT, QQ)
}

val DEFAULT_SELECTED_PACKAGES: Set<String> = ChatPackages.DEFAULT_SELECTED

object SettingsStore {
    private const val PREFS_NAME = "bullet_chat_settings"

    private const val KEY_ENABLED = "enabled"
    private const val KEY_SELECTED = "selected_packages"
    private const val KEY_ONLY_SCREEN_ON = "only_screen_on"
    private const val KEY_SHOW_ICON = "show_app_icon"
    private const val KEY_SKIP_GROUP = "skip_group_summary"
    private const val KEY_ORIENTATION = "display_orientation"
    private const val KEY_CLICK_OPEN = "click_to_open"
    private const val KEY_FLOATING = "try_floating_window"
    private const val KEY_POSITION = "position_percent"
    private const val KEY_LANES = "lane_count"
    private const val KEY_ALPHA = "alpha_percent"
    private const val KEY_FONT = "font_sp"
    private const val KEY_SPEED = "speed_percent"
    private const val KEY_BLACKLIST = "blacklist"
    private const val KEY_KEYWORD_MODE = "keyword_mode"
    private const val KEY_FILTER_MODE = "filter_mode"
    private const val KEY_FILTER_PACKAGES = "filter_packages"
    private const val KEY_CARD_STYLE = "card_style"
    private const val KEY_CARD_ALPHA = "card_alpha_percent"
    private const val KEY_CARD_THEME = "card_theme"
    private const val KEY_THROTTLE = "throttle_per_minute"
    private const val KEY_CONTENT_MODE = "content_mode"
    private const val KEY_QUIET_ENABLED = "quiet_enabled"
    private const val KEY_QUIET_START = "quiet_start"
    private const val KEY_QUIET_END = "quiet_end"

    private lateinit var prefs: SharedPreferences
    private val _settings = MutableStateFlow(AppSettings())
    val settings: StateFlow<AppSettings> = _settings.asStateFlow()
    private var initialized = false

    @Synchronized
    fun init(context: Context) {
        if (initialized) return
        prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        _settings.value = load()
        initialized = true
    }

    private inline fun <reified T : Enum<T>> readEnum(key: String, fallback: T): T {
        val raw = prefs.getString(key, null) ?: return fallback
        return runCatching { enumValueOf<T>(raw) }.getOrDefault(fallback)
    }

    private fun load(): AppSettings {
        val defaults = AppSettings()
        return AppSettings(
            enabled = prefs.getBoolean(KEY_ENABLED, defaults.enabled),
            selectedPackages = prefs.getStringSet(KEY_SELECTED, null)?.toSet()
                ?: defaults.selectedPackages,
            onlyWhenScreenOn = prefs.getBoolean(KEY_ONLY_SCREEN_ON, defaults.onlyWhenScreenOn),
            showAppIcon = prefs.getBoolean(KEY_SHOW_ICON, defaults.showAppIcon),
            skipGroupSummary = prefs.getBoolean(KEY_SKIP_GROUP, defaults.skipGroupSummary),
            displayOrientation = readEnum(KEY_ORIENTATION, defaults.displayOrientation),
            clickToOpen = prefs.getBoolean(KEY_CLICK_OPEN, defaults.clickToOpen),
            tryFloatingWindow = prefs.getBoolean(KEY_FLOATING, defaults.tryFloatingWindow),
            positionPercent = prefs.getInt(KEY_POSITION, defaults.positionPercent),
            laneCount = prefs.getInt(KEY_LANES, defaults.laneCount),
            alphaPercent = prefs.getInt(KEY_ALPHA, defaults.alphaPercent),
            fontSp = prefs.getInt(KEY_FONT, defaults.fontSp),
            speedPercent = prefs.getInt(KEY_SPEED, defaults.speedPercent),
            blacklist = prefs.getString(KEY_BLACKLIST, defaults.blacklist).orEmpty(),
            keywordMode = readEnum(KEY_KEYWORD_MODE, defaults.keywordMode),
            filterMode = readEnum(KEY_FILTER_MODE, defaults.filterMode),
            filterPackages = prefs.getStringSet(KEY_FILTER_PACKAGES, null)?.toSet()
                ?: defaults.filterPackages,
            cardStyle = readEnum(KEY_CARD_STYLE, defaults.cardStyle),
            cardAlphaPercent = prefs.getInt(KEY_CARD_ALPHA, defaults.cardAlphaPercent),
            cardTheme = readEnum(KEY_CARD_THEME, defaults.cardTheme),
            contentMode = readEnum(KEY_CONTENT_MODE, defaults.contentMode),
            quietEnabled = prefs.getBoolean(KEY_QUIET_ENABLED, defaults.quietEnabled),
            quietStartMinute = prefs.getInt(KEY_QUIET_START, defaults.quietStartMinute),
            quietEndMinute = prefs.getInt(KEY_QUIET_END, defaults.quietEndMinute),
            throttlePerMinute = prefs.getInt(KEY_THROTTLE, defaults.throttlePerMinute),
        )
    }

    fun update(transform: (AppSettings) -> AppSettings, persist: Boolean = true) {
        if (!initialized) return
        val updated = transform(_settings.value)
        if (updated == _settings.value) return
        _settings.value = updated
        if (persist) write(updated)
    }

    fun persist() {
        if (initialized) write(_settings.value)
    }

    private fun write(value: AppSettings) {
        with(prefs.edit()) {
            putBoolean(KEY_ENABLED, value.enabled)
            putStringSet(KEY_SELECTED, HashSet(value.selectedPackages))
            putBoolean(KEY_ONLY_SCREEN_ON, value.onlyWhenScreenOn)
            putBoolean(KEY_SHOW_ICON, value.showAppIcon)
            putBoolean(KEY_SKIP_GROUP, value.skipGroupSummary)
            putString(KEY_ORIENTATION, value.displayOrientation.name)
            putBoolean(KEY_CLICK_OPEN, value.clickToOpen)
            putBoolean(KEY_FLOATING, value.tryFloatingWindow)
            putInt(KEY_POSITION, value.positionPercent)
            putInt(KEY_LANES, value.laneCount)
            putInt(KEY_ALPHA, value.alphaPercent)
            putInt(KEY_FONT, value.fontSp)
            putInt(KEY_SPEED, value.speedPercent)
            putString(KEY_BLACKLIST, value.blacklist)
            putString(KEY_KEYWORD_MODE, value.keywordMode.name)
            putString(KEY_FILTER_MODE, value.filterMode.name)
            putStringSet(KEY_FILTER_PACKAGES, HashSet(value.filterPackages))
            putString(KEY_CARD_STYLE, value.cardStyle.name)
            putInt(KEY_CARD_ALPHA, value.cardAlphaPercent)
            putString(KEY_CARD_THEME, value.cardTheme.name)
            putString(KEY_CONTENT_MODE, value.contentMode.name)
            putBoolean(KEY_QUIET_ENABLED, value.quietEnabled)
            putInt(KEY_QUIET_START, value.quietStartMinute)
            putInt(KEY_QUIET_END, value.quietEndMinute)
            putInt(KEY_THROTTLE, value.throttlePerMinute)
            apply()
        }
    }
}

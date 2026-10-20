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
    val alphaPercent: Int = 85,
    val fontSp: Int = 16,
    val speedPercent: Int = 100,
    val blacklist: String = "",
) {
    fun accepts(packageName: String): Boolean = packageName in selectedPackages
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

    private fun load(): AppSettings {
        val defaults = AppSettings()
        val orientation = runCatching {
            DisplayOrientation.valueOf(
                prefs.getString(KEY_ORIENTATION, defaults.displayOrientation.name)
                    ?: defaults.displayOrientation.name,
            )
        }.getOrDefault(defaults.displayOrientation)

        return AppSettings(
            enabled = prefs.getBoolean(KEY_ENABLED, defaults.enabled),
            selectedPackages = prefs.getStringSet(KEY_SELECTED, null)?.toSet()
                ?: defaults.selectedPackages,
            onlyWhenScreenOn = prefs.getBoolean(KEY_ONLY_SCREEN_ON, defaults.onlyWhenScreenOn),
            showAppIcon = prefs.getBoolean(KEY_SHOW_ICON, defaults.showAppIcon),
            skipGroupSummary = prefs.getBoolean(KEY_SKIP_GROUP, defaults.skipGroupSummary),
            displayOrientation = orientation,
            clickToOpen = prefs.getBoolean(KEY_CLICK_OPEN, defaults.clickToOpen),
            tryFloatingWindow = prefs.getBoolean(KEY_FLOATING, defaults.tryFloatingWindow),
            positionPercent = prefs.getInt(KEY_POSITION, defaults.positionPercent),
            laneCount = prefs.getInt(KEY_LANES, defaults.laneCount),
            alphaPercent = prefs.getInt(KEY_ALPHA, defaults.alphaPercent),
            fontSp = prefs.getInt(KEY_FONT, defaults.fontSp),
            speedPercent = prefs.getInt(KEY_SPEED, defaults.speedPercent),
            blacklist = prefs.getString(KEY_BLACKLIST, defaults.blacklist).orEmpty(),
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
            apply()
        }
    }
}

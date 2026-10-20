package com.suibian.bulletchat.data

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 应用设置。所有值都保存在本地 SharedPreferences 中，不联网、不上传。
 */
data class AppSettings(
    val enabled: Boolean = false,
    val wechatEnabled: Boolean = true,
    val qqEnabled: Boolean = true,
    val otherAppsEnabled: Boolean = false,
    val onlyWhenScreenOn: Boolean = true,
    val showAppIcon: Boolean = true,
    val skipGroupSummary: Boolean = true,
    val positionPercent: Int = 16,
    val laneCount: Int = 4,
    val alphaPercent: Int = 85,
    val fontSp: Int = 16,
    val speedPercent: Int = 100,
    val blacklist: String = "",
) {
    fun isSourceEnabled(packageName: String): Boolean = when {
        ChatPackages.isWeChat(packageName) -> wechatEnabled
        ChatPackages.isQq(packageName) -> qqEnabled
        else -> otherAppsEnabled
    }
}

object ChatPackages {
    const val WECHAT = "com.tencent.mm"
    private val QQ_PACKAGES = setOf("com.tencent.mobileqq", "com.tencent.tim")

    fun isWeChat(packageName: String): Boolean = packageName == WECHAT
    fun isQq(packageName: String): Boolean = packageName in QQ_PACKAGES
}

object SettingsStore {
    private const val PREFS_NAME = "bullet_chat_settings"

    private const val KEY_ENABLED = "enabled"
    private const val KEY_WECHAT = "wechat_enabled"
    private const val KEY_QQ = "qq_enabled"
    private const val KEY_OTHER = "other_apps_enabled"
    private const val KEY_ONLY_SCREEN_ON = "only_screen_on"
    private const val KEY_SHOW_ICON = "show_app_icon"
    private const val KEY_SKIP_GROUP = "skip_group_summary"
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
        return AppSettings(
            enabled = prefs.getBoolean(KEY_ENABLED, defaults.enabled),
            wechatEnabled = prefs.getBoolean(KEY_WECHAT, defaults.wechatEnabled),
            qqEnabled = prefs.getBoolean(KEY_QQ, defaults.qqEnabled),
            otherAppsEnabled = prefs.getBoolean(KEY_OTHER, defaults.otherAppsEnabled),
            onlyWhenScreenOn = prefs.getBoolean(KEY_ONLY_SCREEN_ON, defaults.onlyWhenScreenOn),
            showAppIcon = prefs.getBoolean(KEY_SHOW_ICON, defaults.showAppIcon),
            skipGroupSummary = prefs.getBoolean(KEY_SKIP_GROUP, defaults.skipGroupSummary),
            positionPercent = prefs.getInt(KEY_POSITION, defaults.positionPercent),
            laneCount = prefs.getInt(KEY_LANES, defaults.laneCount),
            alphaPercent = prefs.getInt(KEY_ALPHA, defaults.alphaPercent),
            fontSp = prefs.getInt(KEY_FONT, defaults.fontSp),
            speedPercent = prefs.getInt(KEY_SPEED, defaults.speedPercent),
            blacklist = prefs.getString(KEY_BLACKLIST, defaults.blacklist).orEmpty(),
        )
    }

    fun update(transform: (AppSettings) -> AppSettings) {
        if (!initialized) return
        val updated = transform(_settings.value)
        if (updated == _settings.value) return
        _settings.value = updated
        with(prefs.edit()) {
            putBoolean(KEY_ENABLED, updated.enabled)
            putBoolean(KEY_WECHAT, updated.wechatEnabled)
            putBoolean(KEY_QQ, updated.qqEnabled)
            putBoolean(KEY_OTHER, updated.otherAppsEnabled)
            putBoolean(KEY_ONLY_SCREEN_ON, updated.onlyWhenScreenOn)
            putBoolean(KEY_SHOW_ICON, updated.showAppIcon)
            putBoolean(KEY_SKIP_GROUP, updated.skipGroupSummary)
            putInt(KEY_POSITION, updated.positionPercent)
            putInt(KEY_LANES, updated.laneCount)
            putInt(KEY_ALPHA, updated.alphaPercent)
            putInt(KEY_FONT, updated.fontSp)
            putInt(KEY_SPEED, updated.speedPercent)
            putString(KEY_BLACKLIST, updated.blacklist)
            apply()
        }
    }
}

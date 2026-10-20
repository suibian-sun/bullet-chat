package com.suibian.bulletchat.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Settings
import com.suibian.bulletchat.data.SettingsStore

/**
 * 开机 / 更新后自动恢复弹幕服务（用户已开启且权限仍在时）。
 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        val action = intent?.action ?: return
        if (action != Intent.ACTION_BOOT_COMPLETED && action != Intent.ACTION_MY_PACKAGE_REPLACED) return

        SettingsStore.init(context)
        if (!SettingsStore.settings.value.enabled) return
        if (!Settings.canDrawOverlays(context)) return
        OverlayService.start(context)
    }
}

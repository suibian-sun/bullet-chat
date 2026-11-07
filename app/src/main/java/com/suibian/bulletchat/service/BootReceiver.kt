package com.suibian.bulletchat.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.suibian.bulletchat.data.SettingsStore
import com.suibian.bulletchat.util.Permissions

/**
 * 开机 / 更新后自动恢复弹幕服务（用户已开启且权限仍在时）。
 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        val action = intent?.action ?: return
        if (action != Intent.ACTION_BOOT_COMPLETED && action != Intent.ACTION_MY_PACKAGE_REPLACED) return

        SettingsStore.init(context)
        if (!SettingsStore.settings.value.enabled) return
        if (!Permissions.canDrawOverlays(context)) return
        if (!OverlayService.start(context)) {
            // 开机阶段被系统拒绝时保持 enabled，等用户下次打开应用再恢复
            android.util.Log.w("BootReceiver", "开机恢复弹幕服务失败")
        }
    }
}

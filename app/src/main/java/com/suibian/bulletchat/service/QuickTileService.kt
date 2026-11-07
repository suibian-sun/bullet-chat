package com.suibian.bulletchat.service

import android.annotation.TargetApi
import android.app.PendingIntent
import android.content.Intent
import android.graphics.drawable.Icon
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import com.suibian.bulletchat.MainActivity
import com.suibian.bulletchat.R
import com.suibian.bulletchat.data.SettingsStore
import com.suibian.bulletchat.util.Permissions

/**
 * 快捷设置磁贴：下拉通知栏即可一键开关弹幕。
 * 缺少必要权限时直接打开应用，由主界面引导授权。
 */
@TargetApi(Build.VERSION_CODES.N)
class QuickTileService : TileService() {

    companion object {
        /** 应用内切换开关后，让磁贴状态尽快同步；系统未添加磁贴时调用也无副作用。 */
        fun requestRefresh(context: android.content.Context) {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) return
            runCatching {
                requestListeningState(
                    context,
                    android.content.ComponentName(context, QuickTileService::class.java),
                )
            }
        }
    }

    override fun onStartListening() {
        super.onStartListening()
        SettingsStore.init(this)
        updateTile()
    }

    override fun onClick() {
        super.onClick()
        SettingsStore.init(this)
        val enabled = SettingsStore.settings.value.enabled

        if (enabled) {
            SettingsStore.update({ it.copy(enabled = false) })
            OverlayService.stop(this)
            updateTile()
            return
        }

        if (!Permissions.canDrawOverlays(this) || !Permissions.hasNotificationAccess(this)) {
            openApp()
            return
        }

        SettingsStore.update({ it.copy(enabled = true) })
        if (!OverlayService.start(this)) {
            // 系统限制后台启动前台服务：回滚开关并引导到应用内开启
            SettingsStore.update({ it.copy(enabled = false) })
            openApp()
            return
        }
        updateTile()
    }

    private fun updateTile() {
        val tile: Tile = qsTile ?: return
        val enabled = SettingsStore.settings.value.enabled
        tile.state = if (enabled) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
        tile.label = getString(R.string.app_name)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            tile.subtitle = if (enabled) "运行中" else "已停止"
        }
        runCatching {
            tile.icon = Icon.createWithResource(this, R.drawable.ic_notification)
        }
        tile.updateTile()
    }

    @android.annotation.SuppressLint("StartActivityAndCollapseDeprecated")
    private fun openApp() {
        val intent = Intent(this, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            val pending = PendingIntent.getActivity(
                this,
                21,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            startActivityAndCollapse(pending)
        } else {
            @Suppress("DEPRECATION")
            startActivityAndCollapse(intent)
        }
    }
}

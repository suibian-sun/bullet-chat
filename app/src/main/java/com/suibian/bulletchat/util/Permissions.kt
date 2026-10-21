package com.suibian.bulletchat.util

import android.app.Activity
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.core.app.NotificationManagerCompat

/**
 * 权限与系统页跳转集合，集中处理各 Android 版本的差异。
 */
object Permissions {

    /** 悬浮窗权限；Android 6.0 以下安装即授予。 */
    fun canDrawOverlays(context: Context): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Settings.canDrawOverlays(context)
        } else {
            true
        }

    fun hasNotificationAccess(context: Context): Boolean =
        NotificationManagerCompat.getEnabledListenerPackages(context)
            .contains(context.packageName)

    /** 免打扰访问权限，Android 6.0 起才有。 */
    fun hasDndAccess(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return false
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
        return manager?.isNotificationPolicyAccessGranted == true
    }

    fun isIgnoringBattery(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return false
        val power = context.getSystemService(Context.POWER_SERVICE) as? PowerManager ?: return false
        return power.isIgnoringBatteryOptimizations(context.packageName)
    }

    fun openNotificationAccess(activity: Activity) {
        safeStart(activity, Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)) {
            safeStart(activity, Intent(Settings.ACTION_SETTINGS)) {}
        }
    }

    fun openOverlaySettings(activity: Activity) {
        val intent = Intent(
            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
            Uri.parse("package:${activity.packageName}"),
        )
        safeStart(activity, intent) {
            safeStart(activity, Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION)) {}
        }
    }

    fun openDndAccess(activity: Activity) {
        safeStart(activity, Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS)) {}
    }

    fun requestBatteryExemption(activity: Activity) {
        val intent = Intent(
            Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
            Uri.parse("package:${activity.packageName}"),
        )
        safeStart(activity, intent) {
            safeStart(activity, Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)) {}
        }
    }

    /** Android 8.0+ 安装 APK 需要「安装未知应用」授权。 */
    fun canInstallPackages(context: Context): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.packageManager.canRequestPackageInstalls()
        } else {
            true
        }

    fun openInstallPermission(activity: Activity) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        safeStart(
            activity,
            Intent(
                Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                Uri.parse("package:${activity.packageName}"),
            ),
        ) {}
    }

    private inline fun safeStart(activity: Activity, intent: Intent, fallback: () -> Unit) {
        try {
            activity.startActivity(intent)
        } catch (_: Throwable) {
            fallback()
        }
    }
}

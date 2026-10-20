package com.suibian.bulletchat.service

import android.app.Notification
import android.content.Context
import android.os.PowerManager
import android.os.SystemClock
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import androidx.core.app.NotificationCompat
import com.suibian.bulletchat.data.SettingsStore
import com.suibian.bulletchat.model.DanmakuMessage
import com.suibian.bulletchat.util.AppIconLoader

/**
 * 监听系统通知，把微信 / QQ（以及可选的其他应用）的新消息转成弹幕。
 * 不对通知做任何回复或修改，只读取标题与正文。
 */
class BulletNotificationListener : NotificationListenerService() {

    private val recent = LinkedHashMap<String, Long>()
    private val labelCache = HashMap<String, String>()

    override fun onNotificationPosted(sbn: StatusBarNotification?, rankingMap: RankingMap?) {
        val notification = sbn?.notification ?: return
        try {
            handle(sbn, notification)
        } catch (t: Throwable) {
            Log.w(TAG, "处理通知失败", t)
        }
    }

    private fun handle(sbn: StatusBarNotification, notification: Notification) {
        val settings = SettingsStore.settings.value
        if (!settings.enabled) return

        val pkg = sbn.packageName ?: return
        if (pkg == packageName) return
        if (pkg == "android" || pkg == "com.android.systemui") return
        if (!settings.isSourceEnabled(pkg)) return

        // 常驻通知（音乐、下载、充电等）不是聊天消息
        if (sbn.isOngoing) return
        if (notification.flags and Notification.FLAG_ONGOING_EVENT != 0) return
        if (settings.skipGroupSummary && notification.flags and Notification.FLAG_GROUP_SUMMARY != 0) return

        if (settings.onlyWhenScreenOn) {
            val powerManager = getSystemService(Context.POWER_SERVICE) as? PowerManager
            if (powerManager != null && !powerManager.isInteractive) return
        }

        val (title, text) = extractContent(notification)
        if (title.isBlank() && text.isBlank()) return
        if (isBlacklisted("$title $text", settings.blacklist)) return
        if (isDuplicate("$pkg|$title|$text")) return

        val appLabel = resolveAppLabel(pkg)
        val icon = if (settings.showAppIcon) AppIconLoader.load(this, pkg) else null
        DanmakuBus.emit(
            DanmakuMessage(
                packageName = pkg,
                appLabel = appLabel,
                title = title,
                text = text,
                icon = icon,
            ),
        )
    }

    private fun extractContent(notification: Notification): Pair<String, String> {
        val extras = notification.extras ?: return "" to ""

        val title = sequenceOf(
            Notification.EXTRA_TITLE,
            Notification.EXTRA_CONVERSATION_TITLE,
            Notification.EXTRA_TITLE_BIG,
        ).mapNotNull { key ->
            extras.getCharSequence(key)?.toString()?.trim()?.takeIf { it.isNotBlank() }
        }.firstOrNull().orEmpty()

        var text = sequenceOf(
            Notification.EXTRA_TEXT,
            Notification.EXTRA_BIG_TEXT,
            Notification.EXTRA_SUB_TEXT,
        ).mapNotNull { key ->
            extras.getCharSequence(key)?.toString()?.trim()?.takeIf { it.isNotBlank() }
        }.firstOrNull().orEmpty()

        if (text.isBlank()) {
            text = try {
                NotificationCompat.MessagingStyle
                    .extractMessagingStyleFromNotification(notification)
                    ?.messages
                    ?.lastOrNull()
                    ?.text
                    ?.toString()
                    ?.trim()
                    .orEmpty()
            } catch (_: Throwable) {
                ""
            }
        }

        return title.cleanWhitespace() to text.cleanWhitespace()
    }

    private fun String.cleanWhitespace(): String = replace(Regex("\\s+"), " ").trim()

    private fun isBlacklisted(content: String, blacklist: String): Boolean {
        if (blacklist.isBlank()) return false
        val haystack = content.lowercase()
        return blacklist
            .split(Regex("[,，;；\\s]+"))
            .filter { it.isNotBlank() }
            .any { haystack.contains(it.lowercase()) }
    }

    private fun isDuplicate(key: String): Boolean {
        val now = SystemClock.elapsedRealtime()
        val last = recent[key]
        if (last != null && now - last < 2500L) return true

        recent[key] = now
        if (recent.size > 60) {
            val iterator = recent.entries.iterator()
            while (iterator.hasNext()) {
                if (now - iterator.next().value > 15_000L) iterator.remove()
            }
            if (recent.size > 120) recent.clear()
        }
        return false
    }

    private fun resolveAppLabel(pkg: String): String {
        labelCache[pkg]?.let { return it }
        val label = try {
            val info = packageManager.getApplicationInfo(pkg, 0)
            packageManager.getApplicationLabel(info).toString()
        } catch (_: Throwable) {
            pkg
        }
        if (labelCache.size >= 64) labelCache.clear()
        labelCache[pkg] = label
        return label
    }

    companion object {
        private const val TAG = "BulletListener"
    }
}

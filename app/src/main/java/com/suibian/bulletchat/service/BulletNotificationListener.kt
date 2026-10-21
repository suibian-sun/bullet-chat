package com.suibian.bulletchat.service

import android.app.Notification
import android.content.ComponentName
import android.content.Context
import android.os.Build
import android.os.PowerManager
import android.os.SystemClock
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import androidx.core.app.NotificationCompat
import com.suibian.bulletchat.data.SettingsStore
import com.suibian.bulletchat.data.StatsStore
import com.suibian.bulletchat.model.DanmakuMessage
import com.suibian.bulletchat.util.AppIconLoader

/**
 * 监听系统通知，把微信 / QQ（以及可选的其他应用）的新消息转成弹幕。
 * 不对通知做任何回复或修改，只读取标题与正文。
 */
class BulletNotificationListener : NotificationListenerService() {

    override fun onCreate() {
        super.onCreate()
        SettingsStore.init(this)
        StatsStore.init(this)
    }

    override fun onListenerConnected() {
        super.onListenerConnected()
        SettingsStore.init(this)
        StatsStore.init(this)
        // 部分 ROM 在勿扰 / 省电时回收前台服务，这里用常驻的通知监听把弹幕服务拉回来
        if (SettingsStore.settings.value.enabled && !OverlayService.isRunning) {
            Log.w(TAG, "弹幕服务未运行，尝试恢复")
            OverlayService.start(this)
        }
    }

    override fun onListenerDisconnected() {
        super.onListenerDisconnected()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            runCatching {
                requestRebind(ComponentName(this, BulletNotificationListener::class.java))
            }
        }
    }

    private val recent = LinkedHashMap<String, Long>()
    private var cachedBlacklist: Pair<String, List<String>>? = null
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
        if (!settings.accepts(pkg)) return

        // 常驻通知（音乐、下载、充电等）不是聊天消息
        if (sbn.isOngoing) return
        if (notification.flags and Notification.FLAG_ONGOING_EVENT != 0) return

        if (settings.onlyWhenScreenOn) {
            val powerManager = getSystemService(Context.POWER_SERVICE) as? PowerManager
            if (powerManager != null && !powerManager.isInteractive) return
        }

        val (title, text) = extractContent(notification)
        if (title.isBlank() && text.isBlank()) return

        // 群聊汇总：系统 flag + 文案特征双判定（微信/QQ 的「N 条新消息」不带系统 flag）
        if (settings.skipGroupSummary && isGroupSummary(notification, title, text)) {
            Log.d(TAG, "跳过群聊汇总：$title / $text")
            return
        }

        if (isBlacklisted(title, text, settings.blacklist)) {
            Log.d(TAG, "关键词过滤：$title / $text")
            return
        }
        if (isDuplicate("$pkg|$title|$text")) return

        val appLabel = resolveAppLabel(pkg)
        val icon = if (settings.showAppIcon) AppIconLoader.load(this, pkg, 36) else null
        StatsStore.recordForwarded(appLabel, title, text)
        val message = DanmakuMessage(
            packageName = pkg,
            appLabel = appLabel,
            title = title,
            text = text,
            icon = icon,
            contentIntent = notification.contentIntent,
        )

        // 服务被回收时先拉起，本条消息立即投递；投不出去就丢弃，不做延迟补发
        if (!OverlayService.isRunning) OverlayService.start(this)
        if (!DanmakuBus.emit(message)) {
            Log.d(TAG, "悬浮层未就绪，本条消息未投递：$title")
        }
    }

    /**
     * 群聊汇总判定。
     * FLAG_GROUP_SUMMARY 只覆盖系统折叠出的 summary；微信 / QQ 自己发的
     * 「3 条新消息」「你收到 5 条新消息」并不带这个 flag，所以还要按文案识别。
     *
     * 为避免误伤正常消息，文案规则以「整句匹配」为主，只对极短文本做有限放宽。
     */
    private fun isGroupSummary(notification: Notification, title: String, text: String): Boolean {
        if (notification.flags and Notification.FLAG_GROUP_SUMMARY != 0) return true

        val content = (if (text.isNotBlank()) text else title).trim()
        if (content.isEmpty() || content.length > 32) return false
        return SUMMARY_PATTERNS.any { it.matches(content) }
    }

    /**
     * 提取标题与正文。
     * 关键点：微信 / QQ 的长消息会把截断版本放在 EXTRA_TEXT，全文放在 EXTRA_BIG_TEXT，
     * 因此不能用「第一个非空」策略，而要取更完整（更长）的那一份，保证弹幕不丢内容。
     */
    private fun extractContent(notification: Notification): Pair<String, String> {
        val extras = notification.extras ?: return "" to ""

        val title = sequenceOf(
            Notification.EXTRA_CONVERSATION_TITLE,
            Notification.EXTRA_TITLE,
            Notification.EXTRA_TITLE_BIG,
        ).mapNotNull { key ->
            extras.getCharSequence(key)?.toString()?.cleanWhitespace()?.takeIf { it.isNotBlank() }
        }.firstOrNull().orEmpty()

        val shortText = sequenceOf(
            Notification.EXTRA_TEXT,
            Notification.EXTRA_SUB_TEXT,
            Notification.EXTRA_SUMMARY_TEXT,
        ).mapNotNull { key ->
            extras.getCharSequence(key)?.toString()?.cleanWhitespace()?.takeIf { it.isNotBlank() }
        }.firstOrNull().orEmpty()

        val bigText = extras.getCharSequence(Notification.EXTRA_BIG_TEXT)
            ?.toString()
            ?.cleanWhitespace()
            ?.takeIf { it.isNotBlank() }
            .orEmpty()

        var text = if (bigText.length > shortText.length) bigText else shortText

        val lastMessage = runCatching {
            NotificationCompat.MessagingStyle
                .extractMessagingStyleFromNotification(notification)
                ?.messages
                ?.lastOrNull()
                ?.text
                ?.toString()
                ?.cleanWhitespace()
                ?.takeIf { it.isNotBlank() }
        }.getOrNull().orEmpty()

        // 多条消息的通知（如 QQ 会话累计）时长文本更完整，优先使用
        if (lastMessage.length > text.length) text = lastMessage

        return title to text
    }

    /** 合并多余空格，但保留换行，长消息在弹幕里仍按原段落换行显示。 */
    private fun String.cleanWhitespace(): String = replace(Regex("[\\t\\u00A0\\u3000 ]+"), " ")
        .replace(Regex("\\s*\\n\\s*"), "\n")
        .trim()

    private fun isBlacklisted(title: String, text: String, blacklist: String): Boolean {
        if (blacklist.isBlank()) return false
        val keywords = parseKeywords(blacklist)
        if (keywords.isEmpty()) return false
        val haystack = "$title\n$text".lowercase()
        return keywords.any { haystack.contains(it) }
    }

    /** 关键词缓存：设置变化时重新解析，避免每条通知都编译正则。 */
    private fun parseKeywords(raw: String): List<String> {
        val cached = cachedBlacklist
        if (cached != null && cached.first == raw) return cached.second
        val list = raw.split(Regex("[,，;；、\\n]+"))
            .map { it.trim().lowercase() }
            .filter { it.isNotEmpty() }
        cachedBlacklist = raw to list
        return list
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

        /** 各类 ROM / 应用常见的「汇总」文案；均为整句匹配，避免误杀正常消息。 */
        private val SUMMARY_PATTERNS = listOf(
            Regex("^(?:[^:：]{1,10}[:：]\\s*)?\\d+\\s*条(?:新)?消息$"),
            Regex("^(?:[^:：]{1,10}[:：]\\s*)?\\d+\\s*条未读(?:消息)?$"),
            Regex("^(?:你(?:收到|有)\\s*)?\\d+\\s*条(?:新)?消息$"),
            Regex("^\\[\\s*\\d+\\s*条\\s*\\]$"),
            Regex("^(?:[^:：]{1,10}[:：]\\s*)?\\d+\\s*new messages?$", RegexOption.IGNORE_CASE),
            Regex("^\\d+\\s*条$"),
            // 「3条新消息，点击查看」这类带固定后缀的汇总
            Regex("^.{0,4}\\d+\\s*条新消息(?:\\s*[，,。]?\\s*点击查看)?$"),
        )
    }
}

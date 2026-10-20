package com.suibian.bulletchat.model

import android.app.PendingIntent
import android.graphics.Bitmap

/**
 * 一条待显示的弹幕。
 * contentIntent 为该通知原始点击意图，用于点击弹幕时直达对应会话。
 */
data class DanmakuMessage(
    val packageName: String,
    val appLabel: String,
    val title: String,
    val text: String,
    val icon: Bitmap? = null,
    val contentIntent: PendingIntent? = null,
    val timestamp: Long = System.currentTimeMillis(),
) {
    val displayText: String
        get() = when {
            title.isBlank() -> text
            text.isBlank() -> title
            text.startsWith(title) -> text
            else -> "$title：$text"
        }
}

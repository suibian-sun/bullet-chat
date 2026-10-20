package com.suibian.bulletchat.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas

/**
 * 读取应用图标并缓存，用于弹幕左侧的小图标。
 */
object AppIconLoader {
    private val cache = HashMap<String, Bitmap?>()
    private const val MAX_CACHE = 48

    fun load(context: Context, packageName: String): Bitmap? {
        if (cache.containsKey(packageName)) return cache[packageName]

        val bitmap = try {
            val drawable = context.packageManager.getApplicationIcon(packageName)
            val size = (context.resources.displayMetrics.density * 48).toInt().coerceAtLeast(48)
            Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888).also { target ->
                val canvas = Canvas(target)
                drawable.setBounds(0, 0, target.width, target.height)
                drawable.draw(canvas)
            }
        } catch (_: Throwable) {
            null
        }

        if (cache.size >= MAX_CACHE) cache.clear()
        cache[packageName] = bitmap
        return bitmap
    }
}

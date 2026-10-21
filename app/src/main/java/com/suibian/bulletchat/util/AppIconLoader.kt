package com.suibian.bulletchat.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.PorterDuff

/**
 * 应用图标加载器：小尺寸、RGB_565、LRU 上限，避免大量图标占用内存。
 */
object AppIconLoader {
    private const val MAX_CACHE = 48
    private val cache = object : LinkedHashMap<String, Bitmap?>(16, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Bitmap?>?): Boolean =
            size > MAX_CACHE
    }

    @Synchronized
    fun load(context: Context, packageName: String, sizeDp: Int = 36): Bitmap? {
        val key = "$packageName@$sizeDp"
        if (cache.containsKey(key)) return cache[key]

        val bitmap = try {
            val drawable = context.packageManager.getApplicationIcon(packageName)
            val size = (context.resources.displayMetrics.density * sizeDp).toInt().coerceAtLeast(24)
            Bitmap.createBitmap(size, size, Bitmap.Config.RGB_565).also { target ->
                val canvas = Canvas(target)
                canvas.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR)
                drawable.setBounds(0, 0, target.width, target.height)
                drawable.draw(canvas)
            }
        } catch (_: Throwable) {
            null
        }

        cache[key] = bitmap
        return bitmap
    }

    @Synchronized
    fun clear() {
        cache.clear()
    }
}

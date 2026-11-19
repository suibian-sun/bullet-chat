package com.suibian.bulletchat.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.PorterDuff
import android.os.Handler
import android.os.Looper
import java.util.concurrent.Executors

/**
 * 应用图标加载器。
 *
 * - ARGB_8888：图标大多是圆角/异形，RGB_565 没有 alpha 通道，透明区域会被画成黑色方块
 * - 后台线程 + 主线程回调：应用列表滚动时不再在主线程解码，避免掉帧
 * - LRU 上限 48，控制常驻内存
 */
object AppIconLoader {
    private const val MAX_CACHE = 48

    private val cache = object : LinkedHashMap<String, Bitmap?>(16, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Bitmap?>?): Boolean =
            size > MAX_CACHE
    }

    private val executor = Executors.newFixedThreadPool(2) { runnable ->
        Thread(runnable, "app-icon").apply { isDaemon = true }
    }
    private val main = Handler(Looper.getMainLooper())

    /** 同步加载，仅用于已经缓存或必须立即绘制的场景（如弹幕投递）。 */
    @Synchronized
    fun load(context: Context, packageName: String, sizeDp: Int = 36): Bitmap? {
        val key = "$packageName@$sizeDp"
        if (cache.containsKey(key)) return cache[key]
        val bitmap = decode(context.applicationContext, packageName, sizeDp)
        cache[key] = bitmap
        return bitmap
    }

    /**
     * 异步加载：命中缓存时同步回调，否则后台解码后回主线程。
     * 回调可能返回 null（应用已卸载 / 无图标）。
     */
    fun loadAsync(
        context: Context,
        packageName: String,
        sizeDp: Int = 36,
        onLoaded: (Bitmap?) -> Unit,
    ) {
        val key = "$packageName@$sizeDp"
        synchronized(this) {
            if (cache.containsKey(key)) {
                val cached = cache[key]
                onLoaded(cached)
                return
            }
        }
        val appContext = context.applicationContext
        executor.execute {
            val bitmap = decode(appContext, packageName, sizeDp)
            synchronized(this) { cache[key] = bitmap }
            main.post { onLoaded(bitmap) }
        }
    }

    private fun decode(context: Context, packageName: String, sizeDp: Int): Bitmap? = try {
        val drawable = context.packageManager.getApplicationIcon(packageName)
        val size = (context.resources.displayMetrics.density * sizeDp).toInt().coerceAtLeast(24)
        Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888).also { target ->
            val canvas = Canvas(target)
            // 先清成完全透明，避免复用的 bitmap 残留上一次的内容
            canvas.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR)
            drawable.setBounds(0, 0, target.width, target.height)
            drawable.draw(canvas)
        }
    } catch (_: Throwable) {
        null
    }

    @Synchronized
    fun clear() {
        cache.clear()
    }
}

package com.suibian.bulletchat.service

import android.os.Handler
import android.os.Looper
import com.suibian.bulletchat.model.DanmakuMessage
import com.suibian.bulletchat.ui.DanmakuView
import java.lang.ref.WeakReference

/**
 * 通知监听与悬浮层之间的直投通道。
 *
 * 不使用 Flow 缓冲：悬浮层挂载后直接持有视图引用，消息到达即调用 push。
 * 没有挂载（服务未启动 / 方向不允许）时返回 false，消息直接丢弃。
 */
object DanmakuBus {

    private val mainHandler = Handler(Looper.getMainLooper())

    @Volatile
    private var target: WeakReference<DanmakuView>? = null

    fun attach(view: DanmakuView) {
        target = WeakReference(view)
    }

    fun detach(view: DanmakuView) {
        if (target?.get() === view) target = null
    }

    /**
     * @return true 表示已交给悬浮层渲染；false 表示当前没有可用的悬浮层。
     */
    fun emit(message: DanmakuMessage): Boolean {
        val view = target?.get() ?: return false
        if (Looper.myLooper() == Looper.getMainLooper()) {
            view.push(message)
        } else {
            mainHandler.post {
                if (target?.get() === view) view.push(message)
            }
        }
        return true
    }
}

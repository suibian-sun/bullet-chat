package com.suibian.bulletchat.service

import com.suibian.bulletchat.model.DanmakuMessage
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * 通知监听服务与悬浮窗服务之间的事件通道（同进程）。
 */
object DanmakuBus {
    /**
     * 不设缓冲：有订阅者就立即投递，没有订阅者（悬浮层未就绪）就直接丢弃。
     */
    private val _events = MutableSharedFlow<DanmakuMessage>(
        replay = 0,
        extraBufferCapacity = 0,
    )
    val events = _events.asSharedFlow()

    /** @return true 表示已交给悬浮层；false 表示当前没有订阅者，消息被丢弃。 */
    fun emit(message: DanmakuMessage): Boolean = _events.tryEmit(message)
}

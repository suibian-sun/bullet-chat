package com.suibian.bulletchat.service

import com.suibian.bulletchat.model.DanmakuMessage
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * 通知监听服务与悬浮窗服务之间的事件通道（同进程）。
 */
object DanmakuBus {
    private val _events = MutableSharedFlow<DanmakuMessage>(
        extraBufferCapacity = 64,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    val events = _events.asSharedFlow()

    fun emit(message: DanmakuMessage) {
        _events.tryEmit(message)
    }
}

package com.suibian.bulletchat.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.text.TextPaint
import android.util.AttributeSet
import android.util.TypedValue
import android.view.Choreographer
import android.view.MotionEvent
import android.view.View
import com.suibian.bulletchat.model.DanmakuMessage
import kotlin.math.max
import kotlin.math.min
import kotlin.random.Random

/**
 * 轻量弹幕视图（单行模式）：
 * - 每条消息只占一行，不换行；文本超过屏幕宽度时按完整宽度绘制，靠滚动逐字露出
 * - 窗口高度等于弹幕带高度，Surface 更小、显存更省
 * - 多轨分配 + 碰撞避让，空闲自动停帧
 */
class DanmakuView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : View(context, attrs) {

    data class Config(
        val laneCount: Int = 5,
        val alphaPercent: Int = 85,
        val fontSp: Int = 16,
        val speedPercent: Int = 100,
        val showAppIcon: Boolean = true,
        val clickable: Boolean = false,
    )

    private class Item(
        val message: DanmakuMessage,
        val paint: TextPaint,
        val icon: Bitmap?,
        val text: String,
        val width: Float,
        val height: Float,
        val speed: Float,
        val baselineOffset: Float,
    ) {
        var lane: Int = 0
        var x: Float = 0f
        var top: Float = 0f
        var pressed: Boolean = false
    }

    var config: Config = Config()
        set(value) {
            val layoutChanged = field.laneCount != value.laneCount ||
                field.fontSp != value.fontSp ||
                field.showAppIcon != value.showAppIcon
            field = value
            isClickable = value.clickable
            applyPaints()
            if (layoutChanged) {
                clearAll()
                drainPending()
            }
            invalidate()
        }

    var onDanmakuClick: ((DanmakuMessage) -> Unit)? = null

    private val density = resources.displayMetrics.density
    private val spToPx = TypedValue.applyDimension(
        TypedValue.COMPLEX_UNIT_SP,
        1f,
        resources.displayMetrics,
    )

    private val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val iconPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val pressedPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 1.6f * density
    }

    private val items = ArrayList<Item>(12)
    private val waiting = ArrayList<Item>(8)
    private val pending = ArrayDeque<DanmakuMessage>()
    private val laneLast = HashMap<Int, Item>()
    private val rect = RectF()
    private val iconRect = RectF()

    private var laneHeight = 1f
    private var alpha = 255
    private var attached = false
    private var paused = false
    private var running = false
    private var lastFrameNs = 0L
    private var pressedItem: Item? = null

    init {
        applyPaints()
    }

    private fun applyPaints() {
        alpha = (config.alphaPercent.coerceIn(5, 100) / 100f * 255f).toInt()
        bgPaint.color = Color.argb((alpha * 0.78f).toInt(), 8, 14, 24)
        iconPaint.alpha = alpha
        pressedPaint.color = Color.argb((alpha * 0.9f).toInt(), 95, 227, 243)
        items.forEach { it.paint.alpha = alpha }
        waiting.forEach { it.paint.alpha = alpha }
    }

    fun push(message: DanmakuMessage) {
        if (width <= 0 || height <= 0) {
            if (pending.size >= 20) pending.removeFirst()
            pending.addLast(message)
            return
        }
        enqueue(buildItem(message))
    }

    /** 当前屏幕上正在飘的弹幕，旋转前用于留存重放。 */
    fun snapshotMessages(): List<DanmakuMessage> = items.map { it.message }

    fun clearAll() {
        items.clear()
        waiting.clear()
        laneLast.clear()
        pressedItem = null
        invalidate()
    }

    private fun buildItem(message: DanmakuMessage): Item {
        val icon = if (config.showAppIcon) message.icon else null
        val padH = 12f * density
        val padV = 6f * density
        val iconSize = if (icon != null) config.fontSp * spToPx * 1.05f else 0f
        val iconGap = if (icon != null) 7f * density else 0f

        val paint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            color = Color.WHITE
            textSize = config.fontSp * spToPx
            alpha = this@DanmakuView.alpha
            isSubpixelText = true
        }

        // 单行文本：宽度按实际像素测量，不换行，也不截断
        val text = message.displayText.replace('\n', ' ')
        val textWidth = paint.measureText(text)
        val metrics = paint.fontMetrics
        val textHeight = metrics.descent - metrics.ascent

        val itemHeight = max(textHeight, iconSize) + padV * 2
        val itemWidth = padH * 2 + iconSize + iconGap + textWidth

        // 以「穿越屏幕所需时长」为基准：竖屏、横屏、超长文本的观感一致，
        // 横屏时不会因为移动距离变长而长期占用轨道、把后续消息挤掉。
        val screenWidth = width.coerceAtLeast(1).toFloat()
        val speedFactor = config.speedPercent.coerceIn(30, 300) / 100f
        val overflow = (itemWidth / screenWidth - 1f).coerceIn(0f, 2.5f)
        val seconds = (7.4f / speedFactor) * (1f + overflow * 0.10f)
        val speed = (screenWidth + itemWidth) / seconds * (0.96f + Random.nextFloat() * 0.08f)

        return Item(
            message = message,
            paint = paint,
            icon = icon,
            text = text,
            width = itemWidth,
            height = itemHeight,
            speed = speed,
            baselineOffset = padV - metrics.ascent,
        )
    }

    private fun enqueue(item: Item) {
        if (assignLane(item)) {
            items.add(item)
        } else {
            if (waiting.size >= 24) waiting.removeAt(0)
            waiting.add(item)
        }
        startLoop()
        invalidate()
    }

    private fun laneCount(): Int = config.laneCount.coerceIn(1, 8)

    private fun assignLane(item: Item): Boolean {
        if (width <= 0) return false
        val gap = 26f * density
        val total = laneCount()

        for (lane in 0 until total) {
            val last = laneLast[lane]
            if (last != null && last.x + last.width + gap >= width) continue
            item.lane = lane
            item.x = width.toFloat()
            item.top = lane * laneHeight + (laneHeight - item.height) / 2f
            laneLast[lane] = item
            return true
        }
        return false
    }

    private fun removeItem(item: Item) {
        items.remove(item)
        if (pressedItem === item) pressedItem = null
        if (laneLast[item.lane] === item) laneLast.remove(item.lane)
    }

    private fun step(dt: Float) {
        val iterator = items.iterator()
        while (iterator.hasNext()) {
            val item = iterator.next()
            item.x -= item.speed * dt
            if (item.x + item.width < -8f * density) {
                iterator.remove()
                if (laneLast[item.lane] === item) laneLast.remove(item.lane)
            }
        }

        var moved = 0
        while (waiting.isNotEmpty() && moved < 3) {
            val candidate = waiting.first()
            if (!assignLane(candidate)) break
            waiting.removeAt(0)
            items.add(candidate)
            moved++
        }
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        laneHeight = (h.toFloat() / laneCount()).coerceAtLeast(1f)
        clearAll()
        drainPending()
    }

    private fun drainPending() {
        if (width <= 0 || height <= 0) return
        while (pending.isNotEmpty()) {
            enqueue(buildItem(pending.removeFirst()))
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (items.isEmpty()) return

        val padH = 12f * density

        for (item in items) {
            rect.set(item.x, item.top, item.x + item.width, item.top + item.height)
            val radius = min(item.height / 2f, 15f * density)
            canvas.drawRoundRect(rect, radius, radius, bgPaint)
            if (item.pressed) {
                canvas.drawRoundRect(rect, radius, radius, pressedPaint)
            }

            var contentX = item.x + padH
            val icon = item.icon
            if (icon != null) {
                val iconSize = config.fontSp * spToPx * 1.05f
                val iconTop = item.top + (item.height - iconSize) / 2f
                iconRect.set(contentX, iconTop, contentX + iconSize, iconTop + iconSize)
                canvas.drawBitmap(icon, null, iconRect, iconPaint)
                contentX += iconSize + 7f * density
            }

            canvas.drawText(item.text, contentX, item.top + item.baselineOffset, item.paint)
        }
    }

    private fun findItemAt(x: Float, y: Float): Item? {
        for (index in items.indices.reversed()) {
            val item = items[index]
            if (x >= item.x && x <= item.x + item.width &&
                y >= item.top && y <= item.top + item.height
            ) {
                return item
            }
        }
        return null
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (!config.clickable) return false

        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                val hit = findItemAt(event.x, event.y)
                if (hit == null) {
                    pressedItem?.pressed = false
                    pressedItem = null
                    return false
                }
                pressedItem?.pressed = false
                pressedItem = hit
                hit.pressed = true
                invalidate()
                return true
            }

            MotionEvent.ACTION_MOVE -> {
                val hit = findItemAt(event.x, event.y)
                if (hit !== pressedItem) {
                    pressedItem?.pressed = false
                    pressedItem = null
                    invalidate()
                }
                return true
            }

            MotionEvent.ACTION_UP -> {
                val hit = findItemAt(event.x, event.y)
                val target = pressedItem
                pressedItem?.pressed = false
                pressedItem = null
                if (target != null && target === hit) {
                    removeItem(target)
                    performClick()
                    onDanmakuClick?.invoke(target.message)
                }
                invalidate()
                return true
            }

            MotionEvent.ACTION_CANCEL -> {
                pressedItem?.pressed = false
                pressedItem = null
                invalidate()
                return true
            }
        }
        return super.onTouchEvent(event)
    }

    override fun performClick(): Boolean {
        super.performClick()
        return true
    }

    private val frameCallback = object : Choreographer.FrameCallback {
        override fun doFrame(frameTimeNanos: Long) {
            if (!running) return
            val dt = if (lastFrameNs == 0L) 0f else (frameTimeNanos - lastFrameNs) / 1_000_000_000f
            lastFrameNs = frameTimeNanos
            step(dt.coerceIn(0f, 0.06f))
            invalidate()

            if (items.isEmpty() && waiting.isEmpty()) {
                running = false
            } else {
                Choreographer.getInstance().postFrameCallback(this)
            }
        }
    }

    private fun startLoop() {
        if (running || paused || !attached) return
        if (items.isEmpty() && waiting.isEmpty()) return
        running = true
        lastFrameNs = 0L
        Choreographer.getInstance().postFrameCallback(frameCallback)
    }

    private fun stopLoop() {
        running = false
        Choreographer.getInstance().removeFrameCallback(frameCallback)
    }

    fun pauseRendering() {
        paused = true
        stopLoop()
    }

    fun resumeRendering() {
        paused = false
        startLoop()
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        attached = true
        startLoop()
    }

    override fun onDetachedFromWindow() {
        attached = false
        stopLoop()
        super.onDetachedFromWindow()
    }
}

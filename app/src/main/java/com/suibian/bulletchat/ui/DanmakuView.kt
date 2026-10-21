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
 * - 所有弹幕共用一支 TextPaint，避免每条消息各建一支画笔
 * - 尺寸变化（横竖屏切换）时保留在屏弹幕并按比例重排，不丢消息也不闪屏
 * - 空闲自动停帧；文字带深色描边，浅色背景上依然清晰
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
        val icon: Bitmap?,
        val text: String,
        val width: Float,
        val height: Float,
        val speed: Float,
        val baselineOffset: Float,
        val iconSize: Float,
        val iconGap: Float,
    ) {
        var lane: Int = 0
        var x: Float = 0f
        var top: Float = 0f
        var pressed: Boolean = false

        /** 0→1 进场淡入，避免整条突然出现 */
        var appear: Float = 0f
    }

    var config: Config = Config()
        set(value) {
            val fontChanged = field.fontSp != value.fontSp || field.showAppIcon != value.showAppIcon
            val laneChanged = field.laneCount != value.laneCount
            field = value
            isClickable = value.clickable
            applyPaints()
            when {
                fontChanged -> {
                    clearAll()
                    drainPending()
                }
                laneChanged -> relayout()
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
    private val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 1f * density
    }
    private val iconPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val pressedPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 1.6f * density
    }
    private val textPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        color = Color.WHITE
        isSubpixelText = true
    }
    private val strokePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        color = 0xFF050B14.toInt()
        style = Paint.Style.STROKE
        strokeJoin = Paint.Join.ROUND
    }

    private val items = ArrayList<Item>(16)
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
        val textSize = config.fontSp * spToPx
        textPaint.textSize = textSize
        textPaint.alpha = alpha
        strokePaint.textSize = textSize
        strokePaint.strokeWidth = (textSize * 0.09f).coerceIn(1f, 4f * density)
        strokePaint.alpha = alpha
        iconPaint.alpha = alpha
        pressedPaint.color = Color.argb((alpha * 0.9f).toInt(), 95, 227, 243)
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

        // 单行文本：宽度按实际像素测量，不换行，也不截断
        val text = message.displayText.replace('\n', ' ')
        val textWidth = textPaint.measureText(text)
        val metrics = textPaint.fontMetrics
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
            icon = icon,
            text = text,
            width = itemWidth,
            height = itemHeight,
            speed = speed,
            baselineOffset = padV - metrics.ascent,
            iconSize = iconSize,
            iconGap = iconGap,
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

    /** 仅在轨道数变化时重排，不丢弃已显示的弹幕。 */
    private fun relayout() {
        val total = laneCount()
        val newLane = (height.toFloat() / total).coerceAtLeast(1f)
        laneHeight = newLane
        laneLast.clear()
        items.forEach { item ->
            item.lane = item.lane.coerceIn(0, total - 1)
            item.top = item.lane * newLane + (newLane - item.height) / 2f
            laneLast[item.lane] = item
        }
        invalidate()
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
            if (item.appear < 1f) item.appear = (item.appear + dt * 6f).coerceAtMost(1f)
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
        val total = laneCount()
        val newLane = (h.toFloat() / total).coerceAtLeast(1f)

        if (oldw > 0 && oldh > 0 && items.isNotEmpty()) {
            // 横竖屏切换：按新宽度缩放横向进度，保留正在显示的弹幕
            val ratio = w.toFloat() / oldw
            laneLast.clear()
            items.forEach { item ->
                item.x = (item.x * ratio).coerceAtLeast(-item.width)
                item.lane = item.lane.coerceIn(0, total - 1)
                item.top = item.lane * newLane + (newLane - item.height) / 2f
                laneLast[item.lane] = item
            }
        }

        laneHeight = newLane
        drainPending()
        if (items.isNotEmpty()) startLoop()
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

        for (item in items) {
            val itemAlpha = (alpha * item.appear).toInt().coerceIn(0, 255)
            if (itemAlpha <= 0) continue

            rect.set(item.x, item.top, item.x + item.width, item.top + item.height)
            val radius = item.height / 2f

            bgPaint.color = Color.argb((itemAlpha * 0.80f).toInt(), 7, 13, 22)
            canvas.drawRoundRect(rect, radius, radius, bgPaint)

            borderPaint.color = Color.argb((itemAlpha * 0.14f).toInt(), 255, 255, 255)
            canvas.drawRoundRect(rect, radius, radius, borderPaint)

            if (item.pressed) {
                canvas.drawRoundRect(rect, radius, radius, pressedPaint)
            }

            var contentX = item.x + 12f * density
            val icon = item.icon
            if (icon != null) {
                val iconTop = item.top + (item.height - item.iconSize) / 2f
                iconRect.set(contentX, iconTop, contentX + item.iconSize, iconTop + item.iconSize)
                iconPaint.alpha = itemAlpha
                canvas.drawBitmap(icon, null, iconRect, iconPaint)
                contentX += item.iconSize + item.iconGap
            }

            val baseline = item.top + item.baselineOffset
            textPaint.alpha = itemAlpha
            strokePaint.alpha = itemAlpha
            // 先描边再填充，浅色背景上也能看清
            canvas.drawText(item.text, contentX, baseline, strokePaint)
            canvas.drawText(item.text, contentX, baseline, textPaint)
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

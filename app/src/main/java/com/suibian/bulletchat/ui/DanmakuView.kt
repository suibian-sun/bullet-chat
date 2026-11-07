package com.suibian.bulletchat.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.text.TextPaint
import android.util.AttributeSet
import android.util.TypedValue
import android.view.Choreographer
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import com.suibian.bulletchat.data.CardStyle
import com.suibian.bulletchat.model.DanmakuMessage
import kotlin.math.max
import kotlin.math.min
import kotlin.random.Random

/**
 * 轻量弹幕视图（单行模式）：
 * - 每条消息只占一行，不换行；文本超过屏幕宽度时按完整宽度绘制，靠滚动逐字露出
 * - 三种卡片外观：胶囊气泡 / 通知卡片 / 极简文字
 * - 卡片透明度与文字透明度分开控制
 * - 尺寸变化（横竖屏切换）时保留在屏弹幕并按比例重排，不丢消息也不闪屏
 * - 空闲自动停帧；长按可复制弹幕内容
 */
class DanmakuView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : View(context, attrs) {

    data class Config(
        val laneCount: Int = 5,
        val alphaPercent: Int = 100,
        val fontSp: Int = 16,
        val speedPercent: Int = 100,
        val showAppIcon: Boolean = true,
        val clickable: Boolean = false,
        val cardStyle: CardStyle = CardStyle.BUBBLE,
        val cardAlphaPercent: Int = 92,
    )

    private class Item(
        val message: DanmakuMessage,
        val icon: Bitmap?,
        val text: String,
        val width: Float,
        val height: Float,
        var speed: Float,
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

        /** 入队时刻，用于丢弃排队过久、已经过时的消息 */
        val bornAt: Long = SystemClock.elapsedRealtime()
    }

    var config: Config = Config()
        set(value) {
            val fontChanged = field.fontSp != value.fontSp || field.showAppIcon != value.showAppIcon
            val laneChanged = field.laneCount != value.laneCount
            field = value
            isClickable = value.clickable
            applyPaints()
            when {
                fontChanged -> clearAll()
                laneChanged -> relayout()
            }
            invalidate()
        }

    var onDanmakuClick: ((DanmakuMessage) -> Unit)? = null
    var onDanmakuLongClick: ((DanmakuMessage) -> Unit)? = null

    private val density = resources.displayMetrics.density
    private val spToPx = TypedValue.applyDimension(
        TypedValue.COMPLEX_UNIT_SP,
        1f,
        resources.displayMetrics,
    )

    private val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val shadowPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val accentBarPaint = Paint(Paint.ANTI_ALIAS_FLAG)
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
        color = 0xFF101B2D.toInt()
        isSubpixelText = true
    }
    private val haloPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        color = Color.WHITE
        style = Paint.Style.STROKE
        strokeJoin = Paint.Join.ROUND
    }

    private val items = ArrayList<Item>(16)
    private val waiting = ArrayList<Item>(8)
    private val laneLast = HashMap<Int, Item>()
    private val rect = RectF()
    private val innerRect = RectF()
    private val iconRect = RectF()

    private var laneHeight = 1f
    private var alpha = 255
    private var cardAlpha = 235
    private var attached = false
    private var paused = false
    private var running = false
    private var lastFrameNs = 0L
    private var pressedItem: Item? = null
    private var longPressFired = false
    private var readyAction: (() -> Unit)? = null
    private val handler = Handler(Looper.getMainLooper())
    private val longPressAction = Runnable {
        val target = pressedItem ?: return@Runnable
        longPressFired = true
        target.pressed = false
        pressedItem = null
        performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
        onDanmakuLongClick?.invoke(target.message)
        invalidate()
    }

    init {
        applyPaints()
    }

    private fun applyPaints() {
        alpha = (config.alphaPercent.coerceIn(5, 100) / 100f * 255f).toInt()
        cardAlpha = (config.cardAlphaPercent.coerceIn(0, 100) / 100f * 255f).toInt()
        val textSize = config.fontSp * spToPx
        textPaint.textSize = textSize
        textPaint.alpha = alpha
        haloPaint.textSize = textSize
        haloPaint.strokeWidth = (textSize * 0.20f).coerceIn(2f, 5f * density)
        haloPaint.alpha = alpha
        iconPaint.alpha = alpha
        pressedPaint.color = Color.argb((alpha * 0.9f).toInt(), 37, 99, 235)
    }

    /**
     * 立即渲染一条弹幕。
     * 视图尚未完成布局时直接丢弃——不缓存、不在之后补播。
     */
    fun push(message: DanmakuMessage): Boolean {
        if (width <= 0 || height <= 0) return false
        enqueue(buildItem(message))
        return true
    }

    /** 视图可绘制后再执行一次动作（用于测试弹幕，不缓存任何消息）。 */
    fun whenReady(action: () -> Unit) {
        if (width > 0 && height > 0) {
            action()
        } else {
            readyAction = action
        }
    }

    fun clearAll() {
        items.clear()
        waiting.clear()
        laneLast.clear()
        pressedItem = null
        handler.removeCallbacks(longPressAction)
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
            // 同轨道后发弹幕不得快于前一条，否则会在滚动途中追尾重叠
            if (last != null) item.speed = min(item.speed, last.speed)
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

        // 排队超过 6 秒仍未上轨的消息已过时，直接丢弃，不延后补发
        val now = SystemClock.elapsedRealtime()
        waiting.removeAll { now - it.bornAt > 6_000L }

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
        readyAction?.let {
            readyAction = null
            it()
        }
        if (items.isNotEmpty()) startLoop()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (items.isEmpty()) return

        val style = config.cardStyle
        for (item in items) {
            val itemAlpha = (alpha * item.appear).toInt().coerceIn(0, 255)
            if (itemAlpha <= 0) continue

            rect.set(item.x, item.top, item.x + item.width, item.top + item.height)
            val radius = when (style) {
                CardStyle.BUBBLE -> item.height / 2f
                CardStyle.CARD -> 11f * density
                CardStyle.MINIMAL -> 0f
            }

            if (style != CardStyle.MINIMAL) {
                val fillAlpha = (cardAlpha * item.appear).toInt().coerceIn(0, 255)

                // 轻投影：白色卡片放在任意应用上都需要一点边界感
                if (fillAlpha > 60) {
                    shadowPaint.color = Color.argb((itemAlpha * 0.10f).toInt(), 15, 40, 80)
                    canvas.drawRoundRect(
                        rect.left,
                        rect.top + 1.5f * density,
                        rect.right,
                        rect.bottom + 1.5f * density,
                        radius,
                        radius,
                        shadowPaint,
                    )
                }

                bgPaint.color = Color.argb(fillAlpha, 255, 255, 255)
                canvas.drawRoundRect(rect, radius, radius, bgPaint)

                borderPaint.color = Color.argb((itemAlpha * 0.22f).toInt(), 30, 64, 120)
                canvas.drawRoundRect(rect, radius, radius, borderPaint)

                if (style == CardStyle.CARD) {
                    // 左侧强调条，接近系统通知的视觉语言
                    accentBarPaint.color = Color.argb(itemAlpha, 37, 99, 235)
                    val inset = 6f * density
                    innerRect.set(
                        rect.left + inset,
                        rect.top + inset,
                        rect.left + inset + 3.5f * density,
                        rect.bottom - inset,
                    )
                    canvas.drawRoundRect(innerRect, 2f * density, 2f * density, accentBarPaint)
                }

                if (item.pressed) {
                    canvas.drawRoundRect(rect, radius, radius, pressedPaint)
                }
            }

            var contentX = item.x + 12f * density
            val icon = item.icon
            if (icon != null) {
                val iconTop = item.top + (item.height - item.iconSize) / 2f
                iconRect.set(contentX, iconTop, contentX + item.iconSize, iconTop + item.iconSize)
                if (style == CardStyle.MINIMAL) {
                    // 极简模式没有卡片底色，给图标垫一层白色圆角，避免在深色应用上看不清
                    bgPaint.color = Color.argb((itemAlpha * 0.86f).toInt(), 255, 255, 255)
                    canvas.drawRoundRect(iconRect, 5f * density, 5f * density, bgPaint)
                }
                iconPaint.alpha = itemAlpha
                canvas.drawBitmap(icon, null, iconRect, iconPaint)
                contentX += item.iconSize + item.iconGap
            }

            val baseline = item.top + item.baselineOffset
            textPaint.alpha = itemAlpha
            if (style == CardStyle.MINIMAL) {
                // 无底色时用白色描边保证在深色 / 浅色应用上都可读
                haloPaint.alpha = itemAlpha
                canvas.drawText(item.text, contentX, baseline, haloPaint)
            }
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
                longPressFired = false
                hit.pressed = true
                handler.removeCallbacks(longPressAction)
                handler.postDelayed(longPressAction, 480L)
                invalidate()
                return true
            }

            MotionEvent.ACTION_MOVE -> {
                val hit = findItemAt(event.x, event.y)
                if (hit !== pressedItem) {
                    pressedItem?.pressed = false
                    pressedItem = null
                    handler.removeCallbacks(longPressAction)
                    invalidate()
                }
                return true
            }

            MotionEvent.ACTION_UP -> {
                handler.removeCallbacks(longPressAction)
                val hit = findItemAt(event.x, event.y)
                val target = pressedItem
                pressedItem?.pressed = false
                pressedItem = null
                if (!longPressFired && target != null && target === hit) {
                    removeItem(target)
                    performClick()
                    onDanmakuClick?.invoke(target.message)
                }
                longPressFired = false
                invalidate()
                return true
            }

            MotionEvent.ACTION_CANCEL -> {
                handler.removeCallbacks(longPressAction)
                pressedItem?.pressed = false
                pressedItem = null
                longPressFired = false
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
        handler.removeCallbacks(longPressAction)
        super.onDetachedFromWindow()
    }
}

package com.suibian.bulletchat.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.util.AttributeSet
import android.util.TypedValue
import android.view.Choreographer
import android.view.MotionEvent
import android.view.View
import com.suibian.bulletchat.model.DanmakuMessage
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min
import kotlin.random.Random

/**
 * 轻量弹幕视图：
 * - 窗口高度即弹幕带高度，避免整屏 Surface 带来的显存占用
 * - StaticLayout 自动换行 + 超长文本自动缩小字号，保证内容完整显示
 * - 支持多行弹幕占多条轨道、点击命中检测
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
        val layout: StaticLayout,
        val paint: TextPaint,
        val icon: Bitmap?,
        val width: Float,
        val height: Float,
        val laneSpan: Int,
        val speed: Float,
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
        bgPaint.color = Color.argb((alpha * 0.76f).toInt(), 10, 16, 27)
        iconPaint.alpha = alpha
        pressedPaint.color = Color.argb((alpha * 0.9f).toInt(), 95, 227, 243)
        items.forEach { it.paint.alpha = alpha }
        waiting.forEach { it.paint.alpha = alpha }
    }

    fun push(message: DanmakuMessage) {
        if (width <= 0 || height <= 0) {
            if (pending.size >= 12) pending.removeFirst()
            pending.addLast(message)
            return
        }
        enqueue(buildItem(message))
    }

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
        val iconGap = if (icon != null) 6f * density else 0f
        val maxBubbleWidth = width * 0.84f
        val textWidth = (maxBubbleWidth - padH * 2 - iconSize - iconGap)
            .toInt()
            .coerceAtLeast((width * 0.3f).toInt())
        val maxHeight = (height - padV * 2).coerceAtLeast(laneHeight * 0.9f)

        var fontSize = config.fontSp * spToPx
        val minFont = 9f * spToPx
        var layout: StaticLayout
        var paint: TextPaint
        val baseAlpha = alpha
        val text = message.displayText

        while (true) {
            paint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                color = Color.WHITE
                textSize = fontSize
                alpha = baseAlpha
                isSubpixelText = true
            }
            layout = buildLayout(text, paint, textWidth)
            if (layout.height <= maxHeight || fontSize <= minFont) break
            fontSize -= spToPx
        }

        // 缩到最小字号仍然超高：用整屏可用宽度重排，减少行数，避免内容被窗口裁掉
        var usedWidth = textWidth
        if (layout.height > maxHeight) {
            val wide = (width - padH * 2).toInt().coerceAtLeast(textWidth)
            if (wide > textWidth) {
                val retry = buildLayout(text, paint, wide)
                if (retry.lineCount < layout.lineCount) {
                    layout = retry
                    usedWidth = wide
                }
            }
        }

        var lineWidth = 0f
        for (index in 0 until layout.lineCount) {
            lineWidth = max(lineWidth, layout.getLineWidth(index))
        }
        lineWidth = lineWidth.coerceAtMost(usedWidth.toFloat())

        val itemHeight = layout.height + padV * 2
        val span = min(
            config.laneCount.coerceIn(1, 8),
            max(1, ceil((itemHeight / laneHeight).toDouble()).toInt()),
        )
        val wantedWidth = padH * 2 + iconSize + iconGap + lineWidth
        val itemWidth = min(width.toFloat(), wantedWidth)
        val baseSpeed = 145f * density * (config.speedPercent.coerceIn(30, 300) / 100f)
        val speed = baseSpeed * (0.9f + Random.nextFloat() * 0.2f)

        return Item(
            message = message,
            layout = layout,
            paint = paint,
            icon = icon,
            width = itemWidth,
            height = itemHeight,
            laneSpan = span,
            speed = speed,
        )
    }

    private fun buildLayout(text: String, paint: TextPaint, widthPx: Int): StaticLayout =
        StaticLayout.Builder.obtain(text, 0, text.length, paint, widthPx)
            .setAlignment(Layout.Alignment.ALIGN_NORMAL)
            .setLineSpacing(0f, 1.08f)
            .setIncludePad(false)
            .setBreakStrategy(Layout.BREAK_STRATEGY_SIMPLE)
            .setHyphenationFrequency(Layout.HYPHENATION_FREQUENCY_NONE)
            .build()

    private fun enqueue(item: Item) {
        if (assignLane(item)) {
            items.add(item)
        } else {
            if (waiting.size >= 12) waiting.removeAt(0)
            waiting.add(item)
        }
        startLoop()
        invalidate()
    }

    private fun laneCount(): Int = config.laneCount.coerceIn(1, 8)

    private fun assignLane(item: Item): Boolean {
        if (width <= 0) return false
        val gap = 24f * density
        val total = laneCount()
        val span = item.laneSpan.coerceAtMost(total)

        for (start in 0..(total - span)) {
            var free = true
            for (lane in start until start + span) {
                val last = laneLast[lane]
                if (last != null && last.x + last.width + gap >= width) {
                    free = false
                    break
                }
            }
            if (free) {
                item.lane = start
                item.x = width.toFloat()
                item.top = start * laneHeight + (span * laneHeight - item.height) / 2f
                for (lane in start until start + span) {
                    laneLast[lane] = item
                }
                return true
            }
        }
        return false
    }

    private fun removeItem(item: Item) {
        items.remove(item)
        if (pressedItem === item) pressedItem = null
        for (lane in item.lane until item.lane + item.laneSpan) {
            if (laneLast[lane] === item) laneLast.remove(lane)
        }
    }

    private fun step(dt: Float) {
        val iterator = items.iterator()
        while (iterator.hasNext()) {
            val item = iterator.next()
            item.x -= item.speed * dt
            if (item.x + item.width < -8f * density) {
                iterator.remove()
                for (lane in item.lane until item.lane + item.laneSpan) {
                    if (laneLast[lane] === item) laneLast.remove(lane)
                }
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
        val padV = 6f * density
        val iconGap = 6f * density

        for (item in items) {
            rect.set(item.x, item.top, item.x + item.width, item.top + item.height)
            val radius = min(item.height / 2f, 14f * density)
            canvas.drawRoundRect(rect, radius, radius, bgPaint)
            if (item.pressed) {
                canvas.drawRoundRect(rect, radius, radius, pressedPaint)
            }

            var contentX = item.x + padH
            val icon = item.icon
            if (icon != null) {
                val iconSize = config.fontSp * spToPx * 1.05f
                iconRect.set(
                    contentX,
                    item.top + padV,
                    contentX + iconSize,
                    item.top + padV + iconSize,
                )
                canvas.drawBitmap(icon, null, iconRect, iconPaint)
                contentX += iconSize + iconGap
            }

            canvas.save()
            canvas.translate(contentX, item.top + padV)
            item.layout.draw(canvas)
            canvas.restore()
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

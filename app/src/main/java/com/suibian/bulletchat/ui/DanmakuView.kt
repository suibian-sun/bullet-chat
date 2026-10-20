package com.suibian.bulletchat.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.text.TextPaint
import android.text.TextUtils
import android.util.AttributeSet
import android.util.TypedValue
import android.view.Choreographer
import android.view.View
import com.suibian.bulletchat.model.DanmakuMessage
import kotlin.random.Random

/**
 * 轻量弹幕渲染视图：用 Canvas 逐帧绘制，避免为每条消息创建 TextView。
 * 支持分轨道、速度、字号、整体透明度与垂直位置调节。
 */
class DanmakuView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : View(context, attrs) {

    data class Config(
        val positionPercent: Int = 16,
        val laneCount: Int = 4,
        val alphaPercent: Int = 85,
        val fontSp: Int = 16,
        val speedPercent: Int = 100,
        val showAppIcon: Boolean = true,
    )

    private class Item(
        val text: String,
        val icon: Bitmap?,
        val width: Float,
        val height: Float,
        val speed: Float,
    ) {
        var lane: Int = 0
        var x: Float = 0f
        var top: Float = 0f
    }

    var config: Config = Config()
        set(value) {
            val layoutChanged = field.fontSp != value.fontSp ||
                field.laneCount != value.laneCount ||
                field.positionPercent != value.positionPercent
            field = value
            applyPaints()
            if (layoutChanged) {
                items.clear()
                waiting.clear()
                laneLast.clear()
                recalcLanes()
                drainPending()
            }
            invalidate()
        }

    private val density = resources.displayMetrics.density
    private val spToPx = TypedValue.applyDimension(
        TypedValue.COMPLEX_UNIT_SP,
        1f,
        resources.displayMetrics,
    )

    private val textPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = android.graphics.Typeface.create(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD)
        color = Color.WHITE
        isSubpixelText = true
    }
    private val outlinePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeJoin = Paint.Join.ROUND
        color = Color.BLACK
        isSubpixelText = true
    }
    private val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val iconPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)

    private val items = ArrayList<Item>(16)
    private val waiting = ArrayList<Item>(16)
    private val pending = ArrayDeque<DanmakuMessage>()
    private val laneLast = HashMap<Int, Item>()

    private val rect = RectF()
    private val iconRect = RectF()

    private var bandTop = 0f
    private var laneHeight = 0f
    private var attached = false
    private var paused = false
    private var running = false
    private var lastFrameNs = 0L
    private var alpha = 255

    init {
        applyPaints()
    }

    private fun applyPaints() {
        val size = config.fontSp.coerceIn(10, 40) * spToPx
        textPaint.textSize = size
        outlinePaint.textSize = size
        outlinePaint.strokeWidth = (size * 0.11f).coerceAtLeast(2f * density)

        alpha = (config.alphaPercent.coerceIn(5, 100) / 100f * 255f).toInt()
        textPaint.alpha = alpha
        outlinePaint.alpha = (alpha * 0.6f).toInt()
        iconPaint.alpha = alpha
        bgPaint.color = Color.argb((alpha * 0.74f).toInt(), 10, 16, 27)
    }

    private fun recalcLanes() {
        if (width <= 0 || height <= 0) return
        bandTop = height * (config.positionPercent.coerceIn(0, 90) / 100f)
        val lanes = config.laneCount.coerceIn(1, 8)
        val available = (height - bandTop - 6f * density).coerceAtLeast(textPaint.textSize)
        val perLane = available / lanes
        laneHeight = perLane.coerceAtMost(textPaint.textSize * 2.4f).coerceAtLeast(textPaint.textSize * 1.3f)
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
        invalidate()
    }

    private fun buildItem(message: DanmakuMessage): Item {
        val icon = if (config.showAppIcon) message.icon else null
        val textSize = textPaint.textSize
        val iconSize = if (icon != null) textSize * 1.05f else 0f
        val iconGap = if (icon != null) 6f * density else 0f
        val padH = 12f * density
        val padV = 5.5f * density

        val maxText = (width * 0.82f - padH * 2 - iconSize - iconGap).coerceAtLeast(width * 0.35f)
        val display = TextUtils.ellipsize(
            message.displayText,
            TextPaint(textPaint),
            maxText,
            TextUtils.TruncateAt.END,
        ).toString()

        val textWidth = textPaint.measureText(display)
        val itemWidth = padH * 2 + iconSize + iconGap + textWidth
        val itemHeight = textSize + padV * 2
        val baseSpeed = 145f * density * (config.speedPercent.coerceIn(30, 300) / 100f)
        val speed = baseSpeed * (0.9f + Random.nextFloat() * 0.2f)

        return Item(display, icon, itemWidth, itemHeight, speed)
    }

    private fun enqueue(item: Item) {
        if (assignLane(item)) {
            items.add(item)
        } else {
            if (waiting.size >= 18) waiting.removeAt(0)
            waiting.add(item)
        }
        startLoop()
        invalidate()
    }

    private fun assignLane(item: Item): Boolean {
        if (width <= 0) return false
        val gap = 26f * density
        for (lane in 0 until config.laneCount.coerceIn(1, 8)) {
            val last = laneLast[lane]
            if (last == null || last.x + last.width + gap < width) {
                item.lane = lane
                item.x = width.toFloat()
                item.top = bandTop + lane * laneHeight + ((laneHeight - item.height) / 2f).coerceAtLeast(0f)
                laneLast[lane] = item
                return true
            }
        }
        return false
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
        items.clear()
        waiting.clear()
        laneLast.clear()
        recalcLanes()
        drainPending()
    }

    private fun drainPending() {
        if (width <= 0) return
        while (pending.isNotEmpty()) {
            enqueue(buildItem(pending.removeFirst()))
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (items.isEmpty()) return

        val padH = 12f * density
        val iconGap = 6f * density
        val textSize = textPaint.textSize

        for (item in items) {
            rect.set(item.x, item.top, item.x + item.width, item.top + item.height)
            canvas.drawRoundRect(rect, item.height / 2f, item.height / 2f, bgPaint)

            var contentX = item.x + padH
            val icon = item.icon
            if (icon != null) {
                val iconSize = textSize * 1.05f
                iconRect.set(
                    contentX,
                    item.top + (item.height - iconSize) / 2f,
                    contentX + iconSize,
                    item.top + (item.height + iconSize) / 2f,
                )
                canvas.drawBitmap(icon, null, iconRect, iconPaint)
                contentX += iconSize + iconGap
            }

            val baseline = item.top + item.height / 2f - (textPaint.ascent() + textPaint.descent()) / 2f
            canvas.drawText(item.text, contentX, baseline, outlinePaint)
            canvas.drawText(item.text, contentX, baseline, textPaint)
        }
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

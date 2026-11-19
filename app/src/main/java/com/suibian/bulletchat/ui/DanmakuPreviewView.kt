package com.suibian.bulletchat.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.text.TextPaint
import android.util.AttributeSet
import android.util.TypedValue
import android.view.View
import com.suibian.bulletchat.data.CardStyle

/**
 * 设置页里的弹幕卡片实时预览。
 * 与 DanmakuView 使用同一套绘制规则（圆角、描边、强调条、透明度），
 * 调整滑块时能立刻看到效果，而不用回到桌面。
 */
class DanmakuPreviewView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : View(context, attrs) {

    var style: CardStyle = CardStyle.BUBBLE
        set(value) {
            field = value
            invalidate()
        }

    var cardAlphaPercent: Int = 92
        set(value) {
            field = value.coerceIn(0, 100)
            invalidate()
        }

    var textAlphaPercent: Int = 100
        set(value) {
            field = value.coerceIn(5, 100)
            invalidate()
        }

    var showIcon: Boolean = true
        set(value) {
            field = value
            invalidate()
        }

    /** 深色卡片：深底 + 浅字，用于预览「深色卡片 / 跟随系统」的效果。 */
    var dark: Boolean = false
        set(value) {
            field = value
            invalidate()
        }

    private val density = resources.displayMetrics.density
    private val spToPx = TypedValue.applyDimension(
        TypedValue.COMPLEX_UNIT_SP,
        1f,
        resources.displayMetrics,
    )

    private val backdropPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val cardPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val shadowPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 1f * density
    }
    private val accentPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val iconPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val textPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        color = 0xFF101B2D.toInt()
    }
    private val haloPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        color = Color.WHITE
        style = Paint.Style.STROKE
        strokeJoin = Paint.Join.ROUND
    }

    private val rect = RectF()
    private val barRect = RectF()
    private val iconRect = RectF()

    private val sample = "小林：晚上一起吃饭吗？"

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val width = MeasureSpec.getSize(widthMeasureSpec)
        setMeasuredDimension(
            width,
            resolveSize((76 * density).toInt(), heightMeasureSpec),
        )
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        // 模拟一块应用界面：浅色卡片配浅色底，深色卡片配深色底，直观看出对比度
        backdropPaint.shader = if (dark) {
            LinearGradient(
                0f, 0f, width.toFloat(), height.toFloat(),
                intArrayOf(0xFF1B2434.toInt(), 0xFF121A28.toInt()),
                null,
                Shader.TileMode.CLAMP,
            )
        } else {
            LinearGradient(
                0f, 0f, width.toFloat(), height.toFloat(),
                intArrayOf(0xFFEDF2FB.toInt(), 0xFFE6EEFB.toInt()),
                null,
                Shader.TileMode.CLAMP,
            )
        }
        canvas.drawRoundRect(
            0f, 0f, width.toFloat(), height.toFloat(),
            16f * density, 16f * density, backdropPaint,
        )

        val textSize = 16f * spToPx
        textPaint.textSize = textSize
        textPaint.color = if (dark) 0xFFF2F6FF.toInt() else 0xFF101B2D.toInt()
        haloPaint.textSize = textSize
        haloPaint.color = if (dark) 0xFF10131A.toInt() else Color.WHITE
        haloPaint.strokeWidth = (textSize * 0.20f).coerceIn(2f, 5f * density)

        var metrics = textPaint.fontMetrics
        val padH = 12f * density
        val padV = 6f * density
        val iconSize = if (showIcon) textSize * 1.05f else 0f
        val iconGap = if (showIcon) 7f * density else 0f
        var textWidth = textPaint.measureText(sample)
        val maxWidth = this.width - 8f * density
        val fixed = padH * 2 + iconSize + iconGap
        if (fixed + textWidth > maxWidth) {
            // 预览只示意外观：文字按可用宽度等比缩小，不改变真实弹幕的单行策略
            val ratio = ((maxWidth - fixed) / textWidth).coerceIn(0.45f, 1f)
            textPaint.textSize = textSize * ratio
            haloPaint.textSize = textSize * ratio
            haloPaint.strokeWidth = (textPaint.textSize * 0.20f).coerceIn(1.5f, 5f * density)
            metrics = textPaint.fontMetrics
            textWidth = textPaint.measureText(sample)
        }
        val height = (metrics.descent - metrics.ascent) + padV * 2
        val width = fixed + textWidth

        val left = (this.width - width) / 2f
        val top = (this.height - height) / 2f
        rect.set(left, top, left + width, top + height)

        val radius = when (style) {
            CardStyle.BUBBLE -> height / 2f
            CardStyle.CARD -> 11f * density
            CardStyle.MINIMAL -> 0f
        }

        val fillAlpha = (cardAlphaPercent / 100f * 255f).toInt()
        val textAlpha = (textAlphaPercent / 100f * 255f).toInt()

        if (style != CardStyle.MINIMAL) {
            if (fillAlpha > 60) {
                shadowPaint.color = if (dark) {
                    Color.argb((fillAlpha * 0.32f).toInt(), 0, 0, 0)
                } else {
                    Color.argb((fillAlpha * 0.10f).toInt(), 15, 40, 80)
                }
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
            cardPaint.color = if (dark) {
                Color.argb(fillAlpha, 20, 26, 38)
            } else {
                Color.argb(fillAlpha, 255, 255, 255)
            }
            canvas.drawRoundRect(rect, radius, radius, cardPaint)

            borderPaint.color = if (dark) {
                Color.argb((fillAlpha * 0.22f).toInt(), 255, 255, 255)
            } else {
                Color.argb((fillAlpha * 0.22f).toInt(), 30, 64, 120)
            }
            canvas.drawRoundRect(rect, radius, radius, borderPaint)

            if (style == CardStyle.CARD) {
                accentPaint.color = if (dark) {
                    Color.argb(fillAlpha, 96, 150, 255)
                } else {
                    Color.argb(fillAlpha, 37, 99, 235)
                }
                val inset = 6f * density
                barRect.set(
                    rect.left + inset,
                    rect.top + inset,
                    rect.left + inset + 3.5f * density,
                    rect.bottom - inset,
                )
                canvas.drawRoundRect(barRect, 2f * density, 2f * density, accentPaint)
            }
        }

        var contentX = rect.left + padH
        if (showIcon) {
            val iconTop = rect.top + (height - iconSize) / 2f
            iconRect.set(contentX, iconTop, contentX + iconSize, iconTop + iconSize)
            iconPaint.shader = LinearGradient(
                iconRect.left, iconRect.top, iconRect.right, iconRect.bottom,
                intArrayOf(0xFF4ECB71.toInt(), 0xFF22A45D.toInt()),
                null,
                Shader.TileMode.CLAMP,
            )
            canvas.drawRoundRect(iconRect, 4f * density, 4f * density, iconPaint)
            contentX += iconSize + iconGap
        }

        val baseline = rect.top + padV - metrics.ascent
        textPaint.alpha = textAlpha
        if (style == CardStyle.MINIMAL) {
            haloPaint.alpha = textAlpha
            canvas.drawText(sample, contentX, baseline, haloPaint)
        }
        canvas.drawText(sample, contentX, baseline, textPaint)
    }
}

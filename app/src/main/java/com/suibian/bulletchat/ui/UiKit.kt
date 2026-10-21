package com.suibian.bulletchat.ui

import android.animation.ValueAnimator
import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorFilter
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.RadialGradient
import android.graphics.Rect
import android.graphics.Shader
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.util.AttributeSet
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import java.util.Locale

object Palette {
    val BG = 0xFF06080D.toInt()
    val BG_TOP = 0xFF0B1524.toInt()
    val SURFACE = 0xFF0E1826.toInt()
    val SURFACE_2 = 0xFF16222F.toInt()
    val TEXT = 0xFFF3F7FC.toInt()
    val TEXT_DIM = 0xFFA3B2C6.toInt()
    val MUTED = 0xFF6B7C91.toInt()
    val ACCENT = 0xFF5FE3F3.toInt()
    val ACCENT_2 = 0xFF8B7CFF.toInt()
    val ACCENT_3 = 0xFF4EA8FF.toInt()
    val SUCCESS = 0xFF3DDC97.toInt()
    val WARN = 0xFFFFC470.toInt()
    val DANGER = 0xFFFF6B81.toInt()
    val OUTLINE = 0x1FFFFFFF.toInt()
}

val ACCENT_GRADIENT = intArrayOf(
    0xFF5FE3F3.toInt(),
    0xFF63C8FF.toInt(),
    0xFF8B7CFF.toInt(),
)

/** 全屏极光背景：深色渐变 + 两处柔光，避免纯黑死板。 */
class AuroraDrawable(
    private val top: Int = Palette.BG_TOP,
    private val bottom: Int = Palette.BG,
) : Drawable() {

    private val base = Paint(Paint.ANTI_ALIAS_FLAG)
    private val glowTop = Paint(Paint.ANTI_ALIAS_FLAG)
    private val glowBottom = Paint(Paint.ANTI_ALIAS_FLAG)

    override fun onBoundsChange(bounds: Rect) {
        val w = bounds.width().toFloat().coerceAtLeast(1f)
        val h = bounds.height().toFloat().coerceAtLeast(1f)
        base.shader = LinearGradient(0f, 0f, w * 0.35f, h, top, bottom, Shader.TileMode.CLAMP)
        glowTop.shader = RadialGradient(
            w * 0.88f, h * 0.02f, w * 0.85f,
            intArrayOf(0x4D5FE3F3, 0x145FE3F3, Color.TRANSPARENT),
            floatArrayOf(0f, 0.45f, 1f),
            Shader.TileMode.CLAMP,
        )
        glowBottom.shader = RadialGradient(
            w * 0.02f, h * 0.58f, w * 0.8f,
            intArrayOf(0x3D8B7CFF, 0x0F8B7CFF, Color.TRANSPARENT),
            floatArrayOf(0f, 0.42f, 1f),
            Shader.TileMode.CLAMP,
        )
    }

    override fun draw(canvas: Canvas) {
        canvas.drawRect(bounds, base)
        canvas.drawRect(bounds, glowTop)
        canvas.drawRect(bounds, glowBottom)
    }

    override fun setAlpha(alpha: Int) = Unit
    override fun setColorFilter(colorFilter: ColorFilter?) = Unit

    @Deprecated("Deprecated in Drawable")
    override fun getOpacity(): Int = PixelFormat.OPAQUE
}

fun Context.dp(value: Float): Int = (value * resources.displayMetrics.density).toInt()
fun Context.dp(value: Int): Int = dp(value.toFloat())

fun Context.sp(value: Float): Float =
    TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, value, resources.displayMetrics)

/** 玻璃拟态卡片背景。 */
fun Context.glass(
    radiusDp: Float = 24f,
    fill: Int = 0xD90D1725.toInt(),
    stroke: Int = 0x1FFFFFFF.toInt(),
    strokeWidthDp: Float = 1f,
): GradientDrawable = GradientDrawable().apply {
    shape = GradientDrawable.RECTANGLE
    cornerRadius = dp(radiusDp).toFloat()
    setColor(fill)
    setStroke(dp(strokeWidthDp).coerceAtLeast(1), stroke)
}

fun Context.gradientFill(radiusDp: Float, colors: IntArray = ACCENT_GRADIENT): GradientDrawable =
    GradientDrawable(GradientDrawable.Orientation.TL_BR, colors).apply {
        shape = GradientDrawable.RECTANGLE
        cornerRadius = dp(radiusDp).toFloat()
    }

fun Context.circleFill(sizeDp: Int, colors: IntArray = ACCENT_GRADIENT): GradientDrawable =
    GradientDrawable(GradientDrawable.Orientation.TL_BR, colors).apply {
        shape = GradientDrawable.OVAL
        setSize(dp(sizeDp), dp(sizeDp))
    }

fun Context.rippleBackground(content: Drawable, color: Int = 0x33FFFFFF): Drawable =
    RippleDrawable(ColorStateList.valueOf(color), content, null)

fun Context.textView(
    text: String,
    sizeSp: Float,
    color: Int = Palette.TEXT,
    bold: Boolean = false,
    letterSpacing: Float = 0f,
): TextView = TextView(this).apply {
    this.text = text
    setTextSize(TypedValue.COMPLEX_UNIT_SP, sizeSp)
    setTextColor(color)
    if (bold) typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
    this.letterSpacing = letterSpacing
    includeFontPadding = false
    gravity = Gravity.START
}

/** 小号分组标签。 */
fun Context.eyebrow(text: String, color: Int = Palette.ACCENT): TextView =
    textView(text.uppercase(Locale.getDefault()), 10f, color, true, 0.16f)

fun Context.divider(color: Int = 0x14FFFFFF): View =
    View(this).apply { setBackgroundColor(color) }

fun ImageView.tint(color: Int) {
    imageTintList = ColorStateList.valueOf(color)
}

fun formatDuration(millis: Long): String {
    val total = (millis / 1000).coerceAtLeast(0)
    val hours = total / 3600
    val minutes = total % 3600 / 60
    val seconds = total % 60
    return if (hours > 0) {
        String.format(Locale.US, "%d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format(Locale.US, "%02d:%02d", minutes, seconds)
    }
}

fun formatClock(timestamp: Long): String {
    if (timestamp <= 0) return "--:--"
    return java.text.SimpleDateFormat("MM-dd HH:mm", Locale.getDefault())
        .format(java.util.Date(timestamp))
}

/**
 * 手绘开关：跑道 + 圆形滑块，带位移动画，视觉比系统 Switch 更贴合深色界面。
 */
class ToggleView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : View(context, attrs) {

    private val trackPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val thumbPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val haloPaint = Paint(Paint.ANTI_ALIAS_FLAG)

    private var progress = 0f
    private var animator: ValueAnimator? = null

    var isChecked: Boolean = false
        private set

    var onCheckedChange: ((Boolean) -> Unit)? = null

    private val offTrack = 0xFF25313F.toInt()
    private val onTrack = Palette.ACCENT

    init {
        isClickable = true
        isFocusable = true
        setOnClickListener { setChecked(!isChecked, true) }
    }

    fun setChecked(value: Boolean, notify: Boolean = false) {
        if (isChecked == value) {
            val target = if (value) 1f else 0f
            if (progress != target) animateTo(target)
            return
        }
        isChecked = value
        if (notify) onCheckedChange?.invoke(value)
        animateTo(if (value) 1f else 0f)
    }

    private fun animateTo(target: Float) {
        animator?.cancel()
        animator = ValueAnimator.ofFloat(progress, target).apply {
            duration = 160L
            addUpdateListener {
                progress = it.animatedValue as Float
                invalidate()
            }
            start()
        }
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val density = resources.displayMetrics.density
        setMeasuredDimension(
            resolveSize((44 * density).toInt(), widthMeasureSpec),
            resolveSize((26 * density).toInt(), heightMeasureSpec),
        )
    }

    override fun onDraw(canvas: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        val radius = h / 2f
        val pad = h * 0.12f

        trackPaint.color = blend(offTrack, onTrack, progress)
        canvas.drawRoundRect(pad, pad, w - pad, h - pad, radius, radius, trackPaint)

        if (progress > 0.02f) {
            haloPaint.color = Color.argb((44 * progress).toInt(), 95, 227, 243)
            canvas.drawRoundRect(0f, 0f, w, h, radius, radius, haloPaint)
        }

        val thumbRadius = radius - pad - dpx(1.5f)
        val startX = pad + thumbRadius + dpx(2f)
        val endX = w - pad - thumbRadius - dpx(2f)
        val cx = startX + (endX - startX) * progress

        thumbPaint.color = blend(0xFF8A97A6.toInt(), 0xFFFFFFFF.toInt(), progress)
        canvas.drawCircle(cx, h / 2f, thumbRadius, thumbPaint)
    }

    private fun dpx(value: Float): Float = value * resources.displayMetrics.density

    private fun blend(from: Int, to: Int, ratio: Float): Int {
        val r = Color.red(from) + ((Color.red(to) - Color.red(from)) * ratio).toInt()
        val g = Color.green(from) + ((Color.green(to) - Color.green(from)) * ratio).toInt()
        val b = Color.blue(from) + ((Color.blue(to) - Color.blue(from)) * ratio).toInt()
        return Color.rgb(r, g, b)
    }
}

/** 通用设置行：左标题/副标题，右侧任意控件。 */
fun Context.settingRow(
    title: String,
    subtitle: String? = null,
    trailing: View,
    onClick: (() -> Unit)? = null,
): LinearLayout {
    val row = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        setPadding(0, dp(11), 0, dp(11))
        if (onClick != null) {
            isClickable = true
            setOnClickListener { onClick() }
        }
    }
    val column = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
    column.addView(textView(title, 14f, Palette.TEXT))
    if (!subtitle.isNullOrBlank()) {
        column.addView(
            textView(subtitle, 11f, Palette.MUTED).apply {
                setPadding(0, dp(4), 0, 0)
                setLineSpacing(0f, 1.15f)
            },
        )
    }
    row.addView(
        column,
        LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
            marginEnd = dp(12)
        },
    )
    row.addView(trailing)
    row.layoutParams = LinearLayout.LayoutParams(
        LinearLayout.LayoutParams.MATCH_PARENT,
        LinearLayout.LayoutParams.WRAP_CONTENT,
    )
    return row
}

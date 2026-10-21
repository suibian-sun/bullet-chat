package com.suibian.bulletchat.ui

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Outline
import android.graphics.Paint
import android.graphics.Shader
import android.graphics.drawable.Drawable
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewOutlineProvider
import android.view.animation.DecelerateInterpolator
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import androidx.core.content.ContextCompat
import com.suibian.bulletchat.R

/** 圆形启动按钮：渐变实心 + 光环 + 播放/停止图标，圆形阴影与按压缩放。 */
class CircleButton(context: Context) : View(context) {

    private val haloPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val ringPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG)

    private val playIcon: Drawable? = ContextCompat.getDrawable(context, R.drawable.ic_play)
    private val stopIcon: Drawable? = ContextCompat.getDrawable(context, R.drawable.ic_stop)

    var running: Boolean = false
        set(value) {
            if (field == value) return
            field = value
            invalidate()
            animate().scaleX(1f).scaleY(1f).setDuration(220L)
                .setInterpolator(DecelerateInterpolator()).start()
        }

    private var pressed = false

    init {
        isClickable = true
        isFocusable = true
        outlineProvider = object : ViewOutlineProvider() {
            override fun getOutline(view: View, outline: Outline) {
                outline.setOval(0, 0, view.width, view.height)
            }
        }
        clipToOutline = false
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        fillPaint.shader = LinearGradient(
            0f, 0f, w.toFloat(), h.toFloat(),
            intArrayOf(0xFF6EE9F7.toInt(), 0xFF63C8FF.toInt(), 0xFF8B7CFF.toInt()),
            floatArrayOf(0f, 0.55f, 1f),
            Shader.TileMode.CLAMP,
        )
    }

    override fun onDraw(canvas: Canvas) {
        val cx = width / 2f
        val cy = height / 2f
        val r = minOf(width, height) / 2f

        haloPaint.color = if (running) 0x335FE3F3 else 0x1F5FE3F3
        canvas.drawCircle(cx, cy, r - dp(1f), haloPaint)

        ringPaint.strokeWidth = dp(1.2f)
        ringPaint.color = if (running) 0x885FE3F3.toInt() else 0x445FE3F3
        canvas.drawCircle(cx, cy, r - dp(2f), ringPaint)

        canvas.drawCircle(cx, cy, r - dp(9f), fillPaint)

        val icon = if (running) stopIcon else playIcon
        val size = dp(if (running) 18f else 20f)
        val half = size / 2f
        val offset = if (running) 0f else dp(1.2f)
        icon?.let {
            it.setTint(Color.WHITE)
            it.setBounds(
                (cx - half).toInt(),
                (cy - half + offset).toInt(),
                (cx + half).toInt(),
                (cy + half + offset).toInt(),
            )
            it.draw(canvas)
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                pressed = true
                animate().scaleX(0.9f).scaleY(0.9f).setDuration(90L).start()
            }
            MotionEvent.ACTION_UP -> {
                if (pressed) performClick()
                pressed = false
                animate().scaleX(1f).scaleY(1f).setDuration(180L)
                    .setInterpolator(DecelerateInterpolator()).start()
            }
            MotionEvent.ACTION_CANCEL -> {
                pressed = false
                animate().scaleX(1f).scaleY(1f).setDuration(180L).start()
            }
        }
        return true
    }

    override fun performClick(): Boolean {
        super.performClick()
        return true
    }

    private fun dp(value: Float): Float = value * resources.displayMetrics.density
}

/**
 * 底部导航：主页 / 圆形启动按钮 / 设置。
 *
 * 层级说明：FrameLayout 内按 Z 轴（elevation）排序绘制，因此底座必须保持 0dp，
 * 圆钮单独给高 elevation，否则底座会把图标与圆钮盖住。
 */
class NavBar(
    context: Context,
    private val onSelect: (Int) -> Unit,
) : FrameLayout(context) {

    private class NavCell(
        val root: LinearLayout,
        val icon: ImageView,
        val label: android.widget.TextView,
    )

    private val homeCell: NavCell
    private val settingsCell: NavCell
    val fab: CircleButton
    private var selectedIndex = 0

    init {
        clipChildren = false
        clipToPadding = false

        val base = View(context).apply {
            background = context.glass(30f, 0xF20A1420.toInt(), 0x26FFFFFF)
            elevation = 0f
        }
        addView(
            base,
            LayoutParams(LayoutParams.MATCH_PARENT, dp(64)).apply {
                gravity = Gravity.BOTTOM
                leftMargin = dp(14)
                rightMargin = dp(14)
                bottomMargin = dp(4)
            },
        )

        val row = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        homeCell = buildCell(R.drawable.ic_nav_home, "主页") { onSelect(0) }
        settingsCell = buildCell(R.drawable.ic_nav_settings, "设置") { onSelect(2) }

        row.addView(homeCell.root, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f))
        row.addView(View(context), LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f))
        row.addView(settingsCell.root, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f))

        addView(
            row,
            LayoutParams(LayoutParams.MATCH_PARENT, dp(64)).apply {
                gravity = Gravity.BOTTOM
                leftMargin = dp(14)
                rightMargin = dp(14)
                bottomMargin = dp(4)
            },
        )

        fab = CircleButton(context).apply {
            elevation = dp(10f).toFloat()
            setOnClickListener { onSelect(1) }
        }
        // 圆钮浮在底座之上，向上凸出，形成悬浮式启动按钮
        addView(
            fab,
            LayoutParams(dp(62), dp(62)).apply {
                gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
                topMargin = dp(2)
            },
        )

        setSelected(0)
    }

    private fun buildCell(iconRes: Int, label: String, action: () -> Unit): NavCell {
        val root = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            isClickable = true
            setOnClickListener { action() }
        }
        val icon = ImageView(context).apply { setImageResource(iconRes) }
        root.addView(icon, LinearLayout.LayoutParams(dp(21), dp(21)))
        val text = context.textView(label, 10f, Palette.MUTED, true, 0.04f).apply {
            gravity = Gravity.CENTER
            setPadding(0, dp(5), 0, 0)
        }
        root.addView(text)
        return NavCell(root, icon, text)
    }

    fun setSelected(index: Int) {
        val changed = selectedIndex != index
        selectedIndex = index
        styleCell(homeCell, index == 0, changed)
        styleCell(settingsCell, index == 2, changed)

        val middleActive = index == 1
        fab.animate().cancel()
        fab.animate()
            .scaleX(if (middleActive) 1.08f else 1f)
            .scaleY(if (middleActive) 1.08f else 1f)
            .setDuration(260L)
            .setInterpolator(DecelerateInterpolator())
            .start()
    }

    private fun styleCell(cell: NavCell, active: Boolean, animate: Boolean) {
        val color = if (active) Palette.ACCENT else Palette.MUTED
        cell.icon.imageTintList = ColorStateList.valueOf(color)
        cell.label.setTextColor(color)
        cell.icon.alpha = if (active) 1f else 0.82f

        cell.icon.animate().cancel()
        cell.icon.animate()
            .scaleX(if (active) 1.1f else 1f)
            .scaleY(if (active) 1.1f else 1f)
            .setDuration(if (animate) 220L else 0L)
            .setInterpolator(DecelerateInterpolator())
            .start()

        cell.root.animate().cancel()
        cell.root.animate()
            .translationY(if (active) -dp(1f).toFloat() else 0f)
            .setDuration(if (animate) 200L else 0L)
            .start()
    }

    fun setFabRunning(running: Boolean) {
        fab.running = running
    }

    private fun dp(value: Float): Int = (value * resources.displayMetrics.density).toInt()
    private fun dp(value: Int): Int = dp(value.toFloat())
}

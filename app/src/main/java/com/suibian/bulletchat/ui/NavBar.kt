package com.suibian.bulletchat.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import android.graphics.drawable.Drawable
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import androidx.core.content.ContextCompat
import com.suibian.bulletchat.R

/** 圆形启动按钮：渐变实心 + 光环 + 播放/停止图标，带按压缩放。 */
class CircleButton(context: Context) : View(context) {

    private val haloPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val ringPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG)

    private val playIcon: Drawable? = ContextCompat.getDrawable(context, R.drawable.ic_play)
    private val stopIcon: Drawable? = ContextCompat.getDrawable(context, R.drawable.ic_stop)

    var running: Boolean = false
        set(value) {
            field = value
            invalidate()
        }

    private var pressed = false

    init {
        isClickable = true
        isFocusable = true
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
                animate().scaleX(0.93f).scaleY(0.93f).setDuration(90).start()
            }
            MotionEvent.ACTION_UP -> {
                if (pressed) performClick()
                pressed = false
                animate().scaleX(1f).scaleY(1f).setDuration(120).start()
            }
            MotionEvent.ACTION_CANCEL -> {
                pressed = false
                animate().scaleX(1f).scaleY(1f).setDuration(120).start()
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

/** 底部导航：主页 / 圆形启动按钮 / 设置。 */
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
            background = context.glass(28f, 0xED0A1420.toInt(), 0x1AFFFFFF)
            elevation = dp(12f).toFloat()
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
            setOnClickListener {
                onSelect(1)
            }
        }
        addView(
            fab,
            LayoutParams(dp(62), dp(62)).apply {
                gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
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
        val icon = ImageView(context).apply {
            setImageResource(iconRes)
            setPadding(0, 0, 0, 0)
        }
        root.addView(icon, LinearLayout.LayoutParams(dp(21), dp(21)))
        val text = context.textView(label, 10f, Palette.MUTED, true, 0.04f).apply {
            gravity = Gravity.CENTER
            setPadding(0, dp(5), 0, 0)
        }
        root.addView(text)
        return NavCell(root, icon, text)
    }

    fun setSelected(index: Int) {
        selectedIndex = index
        styleCell(homeCell, index == 0)
        styleCell(settingsCell, index == 2)
        val middleActive = index == 1
        fab.scaleX = if (middleActive) 1.06f else 1f
        fab.scaleY = if (middleActive) 1.06f else 1f
    }

    private fun styleCell(cell: NavCell, active: Boolean) {
        val color = if (active) Palette.ACCENT else Palette.MUTED
        cell.icon.tint(color)
        cell.label.setTextColor(color)
        cell.icon.alpha = if (active) 1f else 0.85f
        cell.root.animate()
            .translationY(if (active) -dp(1f).toFloat() else 0f)
            .setDuration(140)
            .start()
    }

    fun setFabRunning(running: Boolean) {
        fab.running = running
    }

    private fun dp(value: Number): Int = (value.toFloat() * resources.displayMetrics.density).toInt()

    @Suppress("unused")
    private fun current(): Int = selectedIndex
}

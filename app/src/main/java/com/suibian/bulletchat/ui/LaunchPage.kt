package com.suibian.bulletchat.ui

import android.animation.ValueAnimator
import android.app.Activity
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
import android.view.animation.LinearInterpolator
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.core.content.ContextCompat
import com.suibian.bulletchat.R
import com.suibian.bulletchat.data.SettingsStore
import com.suibian.bulletchat.data.StatsStore
import com.suibian.bulletchat.util.Permissions

/** 大号圆形启动按钮，带呼吸光环与按压反馈。 */
class LaunchButton(context: Context) : View(context) {

    private val haloPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val ringPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val playIcon: Drawable? = ContextCompat.getDrawable(context, R.drawable.ic_play)
    private val stopIcon: Drawable? = ContextCompat.getDrawable(context, R.drawable.ic_stop)

    private var pulse = 0f
    private var animator: ValueAnimator? = null
    private var pressed = false

    var running: Boolean = false
        set(value) {
            if (field == value) return
            field = value
            if (value) startPulse() else stopPulse()
            invalidate()
        }

    init {
        isClickable = true
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        fillPaint.shader = LinearGradient(
            w * 0.2f, h * 0.1f, w * 0.85f, h * 0.95f,
            intArrayOf(0xFF6EE9F7.toInt(), 0xFF5BB8FF.toInt(), 0xFF8B7CFF.toInt()),
            floatArrayOf(0f, 0.5f, 1f),
            Shader.TileMode.CLAMP,
        )
    }

    override fun onDraw(canvas: Canvas) {
        val cx = width / 2f
        val cy = height / 2f
        val outer = minOf(width, height) / 2f

        if (running) {
            haloPaint.color = Color.argb((60 * (1f - pulse)).toInt(), 95, 227, 243)
            canvas.drawCircle(cx, cy, outer * (0.82f + 0.16f * pulse), haloPaint)
        }
        haloPaint.color = if (running) 0x2E5FE3F3 else 0x145FE3F3
        canvas.drawCircle(cx, cy, outer * 0.8f, haloPaint)

        ringPaint.strokeWidth = dp(1f)
        ringPaint.color = if (running) 0x665FE3F3 else 0x2E5FE3F3
        canvas.drawCircle(cx, cy, outer * 0.9f, ringPaint)
        canvas.drawCircle(cx, cy, outer * 0.72f, ringPaint)

        canvas.drawCircle(cx, cy, outer * 0.62f, fillPaint)

        val icon = if (running) stopIcon else playIcon
        val size = dp(if (running) 34f else 38f)
        icon?.let {
            it.setTint(Color.WHITE)
            val half = size / 2f
            val offset = if (running) 0f else dp(2f)
            it.setBounds(
                (cx - half).toInt(),
                (cy - half + offset).toInt(),
                (cx + half).toInt(),
                (cy + half + offset).toInt(),
            )
            it.draw(canvas)
        }
    }

    private fun startPulse() {
        animator?.cancel()
        animator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 1600L
            repeatCount = ValueAnimator.INFINITE
            interpolator = LinearInterpolator()
            addUpdateListener {
                pulse = it.animatedValue as Float
                invalidate()
            }
            start()
        }
    }

    private fun stopPulse() {
        animator?.cancel()
        animator = null
        pulse = 0f
    }

    override fun onDetachedFromWindow() {
        stopPulse()
        super.onDetachedFromWindow()
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                pressed = true
                animate().scaleX(0.94f).scaleY(0.94f).setDuration(110).start()
            }
            MotionEvent.ACTION_UP -> {
                if (pressed) performClick()
                pressed = false
                animate().scaleX(1f).scaleY(1f).setDuration(140).start()
            }
            MotionEvent.ACTION_CANCEL -> {
                pressed = false
                animate().scaleX(1f).scaleY(1f).setDuration(140).start()
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

/** 启动页：中央圆形按钮，一键启停弹幕悬浮窗。 */
class LaunchPage(
    private val activity: Activity,
    private val onToggle: () -> Unit,
    private val onPermissionClick: (Int) -> Unit,
) {

    val view: View

    private lateinit var button: LaunchButton
    private lateinit var statusTitle: TextView
    private lateinit var statusSub: TextView
    private lateinit var todayText: TextView
    private lateinit var totalText: TextView
    private val permissionLabels = ArrayList<TextView>()
    private var introPlayed = false
    private lateinit var buttonHolder: FrameLayout

    init {
        view = build()
    }

    /** 首次进入启动页：按钮从中心放大浮现，其余内容依次上浮。 */
    fun playIntro() {
        if (introPlayed) return
        introPlayed = true
        buttonHolder.alpha = 0f
        buttonHolder.scaleX = 0.78f
        buttonHolder.scaleY = 0.78f
        buttonHolder.animate()
            .alpha(1f)
            .scaleX(1f)
            .scaleY(1f)
            .setDuration(460L)
            .setInterpolator(android.view.animation.OvershootInterpolator(0.9f))
            .start()
        statusTitle.enterFromBottom(delay = 120L, distanceDp = 16)
        statusSub.enterFromBottom(delay = 170L, distanceDp = 14)
        view.postDelayed({
            val inner = (view as? android.view.ViewGroup)?.getChildAt(0) as? android.view.ViewGroup
            if (inner != null) {
                for (index in 0 until inner.childCount) {
                    val child = inner.getChildAt(index)
                    if (child === buttonHolder || child === statusTitle || child === statusSub) continue
                    child.enterFromBottom(delay = 55L * index, distanceDp = 18)
                }
            }
        }, 60L)
    }

    fun refresh() {
        val settings = SettingsStore.settings.value
        button.running = settings.enabled
        val stats = StatsStore.snapshot()

        statusTitle.text = if (settings.enabled) "弹幕已开启" else "弹幕已关闭"
        statusTitle.setTextColor(if (settings.enabled) Palette.TEXT else Palette.TEXT_DIM)
        statusSub.text = if (settings.enabled) {
            "轻点按钮可随时停止，消息会继续在后台记录"
        } else {
            "轻点按钮开启，微信 / QQ 消息将化为弹幕"
        }

        todayText.text = stats.todayForwarded.toString()
        totalText.text = stats.totalForwarded.toString()

        val states = listOf(
            Permissions.hasNotificationAccess(activity),
            Permissions.canDrawOverlays(activity),
            Permissions.hasDndAccess(activity),
        )
        permissionLabels.forEachIndexed { index, label ->
            val granted = states.getOrElse(index) { false }
            label.text = if (granted) "已就绪" else "去开启"
            label.setTextColor(if (granted) Palette.SUCCESS else Palette.WARN)
        }
    }

    private fun build(): View {
        val scroll = ScrollView(activity).apply {
            isFillViewport = true
            isVerticalScrollBarEnabled = false
            clipToPadding = false
        }
        val root = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(activity.dp(20), activity.dp(18), activity.dp(20), activity.dp(28))
        }
        scroll.addView(
            root,
            FrameLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
            ),
        )

        root.addView(activity.eyebrow("控制台", Palette.ACCENT_2))
        root.addView(
            activity.textView("弹幕控制", 30f, Palette.TEXT, true).apply {
                setPadding(0, activity.dp(8), 0, 0)
                letterSpacing = -0.02f
            },
        )

        // 圆形启动按钮
        buttonHolder = FrameLayout(activity).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                activity.dp(300),
            ).apply { topMargin = activity.dp(10) }
        }
        button = LaunchButton(activity).apply {
            setOnClickListener { onToggle() }
        }
        buttonHolder.addView(
            button,
            FrameLayout.LayoutParams(activity.dp(232), activity.dp(232)).apply {
                gravity = Gravity.CENTER
            },
        )
        root.addView(buttonHolder)

        statusTitle = activity.textView("弹幕已关闭", 20f, Palette.TEXT_DIM, true).apply {
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
            )
        }
        root.addView(statusTitle)
        statusSub = activity.textView("轻点按钮开启，微信 / QQ 消息将化为弹幕", 12f, Palette.MUTED).apply {
            gravity = Gravity.CENTER
            setPadding(0, activity.dp(8), 0, 0)
            setLineSpacing(0f, 1.3f)
        }
        root.addView(statusSub)

        root.addView(statsStrip(), wrap(top = 22))
        root.addView(permissionCard(), wrap(top = 12))
        root.addView(hintCard(), wrap(top = 12))
        return scroll
    }

    private fun statsStrip(): View {
        val row = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            background = activity.glass(22f)
            setPadding(activity.dp(18), activity.dp(15), activity.dp(18), activity.dp(15))
        }

        val left = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
        }
        todayText = activity.textView("0", 22f, Palette.TEXT, true).apply { gravity = Gravity.CENTER }
        left.addView(todayText)
        left.addView(
            activity.textView("今日转发", 10f, Palette.MUTED).apply {
                gravity = Gravity.CENTER
                setPadding(0, activity.dp(5), 0, 0)
            },
        )
        row.addView(left, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))

        row.addView(
            View(activity).apply { setBackgroundColor(0x1AFFFFFF) },
            LinearLayout.LayoutParams(activity.dp(1), activity.dp(34)).apply {
                gravity = Gravity.CENTER_VERTICAL
            },
        )

        val right = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
        }
        totalText = activity.textView("0", 22f, Palette.TEXT, true).apply { gravity = Gravity.CENTER }
        right.addView(totalText)
        right.addView(
            activity.textView("累计转发", 10f, Palette.MUTED).apply {
                gravity = Gravity.CENTER
                setPadding(0, activity.dp(5), 0, 0)
            },
        )
        row.addView(right, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        return row
    }

    private fun permissionCard(): View {
        val card = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            background = activity.glass(24f)
            setPadding(activity.dp(18), activity.dp(8), activity.dp(18), activity.dp(8))
        }
        val titles = listOf("通知使用权", "悬浮窗权限", "免打扰权限")
        val subtitles = listOf(
            "读取新消息",
            "在应用上方绘制弹幕",
            "勿扰模式下仍可显示",
        )
        titles.forEachIndexed { index, title ->
            val status = activity.textView("去开启", 12f, Palette.WARN, true)
            permissionLabels.add(status)
            val chevron = ImageView(activity).apply {
                setImageResource(R.drawable.ic_chevron_right)
                tint(Palette.MUTED)
                alpha = 0.6f
            }
            val trailing = LinearLayout(activity).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
            }
            trailing.addView(status)
            trailing.addView(
                chevron,
                LinearLayout.LayoutParams(activity.dp(16), activity.dp(16)).apply {
                    marginStart = activity.dp(2)
                },
            )
            card.addView(activity.settingRow(title, subtitles[index], trailing) {
                onPermissionClick(index)
            })
            if (index < titles.size - 1) {
                card.addView(
                    activity.divider(),
                    LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 1),
                )
            }
        }
        return card
    }

    private fun hintCard(): View {
        val card = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            background = activity.glass(20f, 0x1417C4C4, 0x1F5FE3F3)
            setPadding(activity.dp(16), activity.dp(14), activity.dp(16), activity.dp(14))
        }
        card.addView(activity.textView("生效范围", 12f, Palette.ACCENT, true))
        card.addView(
            activity.textView(
                "仅「通知来源」中勾选的应用会生成弹幕；长消息以单行滚动展示，内容不会被省略。",
                11f,
                Palette.MUTED,
            ).apply {
                setPadding(0, activity.dp(6), 0, 0)
                setLineSpacing(0f, 1.35f)
            },
        )
        return card
    }

    private fun wrap(top: Int) = LinearLayout.LayoutParams(
        LinearLayout.LayoutParams.MATCH_PARENT,
        LinearLayout.LayoutParams.WRAP_CONTENT,
    ).apply { topMargin = activity.dp(top) }
}

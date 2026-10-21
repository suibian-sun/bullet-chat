package com.suibian.bulletchat.ui

import android.app.Activity
import android.graphics.Typeface
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import com.suibian.bulletchat.data.SettingsStore
import com.suibian.bulletchat.data.StatsStore
import com.suibian.bulletchat.data.UsageStats
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** 主页：应用运行时长 + 转发消息统计。 */
class HomePage(private val activity: Activity) {

    val view: View

    private lateinit var statusDot: TextView
    private lateinit var statusText: TextView
    private lateinit var sessionValue: TextView
    private lateinit var totalRunValue: TextView
    private lateinit var todayValue: TextView
    private lateinit var totalValue: TextView
    private lateinit var sourceValue: TextView
    private lateinit var recentApp: TextView
    private lateinit var recentTitle: TextView
    private lateinit var recentText: TextView
    private lateinit var recentTime: TextView

    private val handler = Handler(Looper.getMainLooper())
    private var ticking = false
    private var stats: UsageStats = UsageStats()

    private val ticker = object : Runnable {
        override fun run() {
            updateRuntime()
            handler.postDelayed(this, 1000L)
        }
    }

    init {
        view = build()
    }

    fun onVisible() {
        stats = StatsStore.snapshot()
        refresh()
        if (!ticking) {
            ticking = true
            handler.post(ticker)
        }
    }

    fun onHidden() {
        ticking = false
        handler.removeCallbacks(ticker)
    }

    fun refresh() {
        stats = StatsStore.snapshot()
        val settings = SettingsStore.settings.value
        val running = settings.enabled && stats.sessionStart > 0L

        statusDot.setTextColor(if (running) Palette.SUCCESS else Palette.MUTED)
        statusText.text = if (running) "弹幕服务运行中" else "弹幕服务已停止"
        statusText.setTextColor(if (running) Palette.TEXT_DIM else Palette.MUTED)

        todayValue.text = stats.todayForwarded.toString()
        totalValue.text = stats.totalForwarded.toString()
        totalRunValue.text = formatDuration(stats.totalRunMillis)
        sourceValue.text = settings.selectedPackages.size.toString()

        if (stats.lastTime > 0L) {
            recentApp.text = stats.lastApp.ifBlank { "未知应用" }
            recentTitle.text = stats.lastTitle.ifBlank { "新消息" }
            recentText.text = stats.lastText.ifBlank { "（无正文）" }
            recentTitle.visibility = View.VISIBLE
            recentText.visibility = View.VISIBLE
            recentTime.text = formatClock(stats.lastTime)
        } else {
            recentApp.text = "暂无消息"
            recentTitle.visibility = View.GONE
            recentText.visibility = View.GONE
            recentText.text = "收到第一条通知后，这里会显示最近一条弹幕。"
            recentText.visibility = View.VISIBLE
            recentTime.text = "--:--"
        }

        updateRuntime()
    }

    private fun updateRuntime() {
        if (!::sessionValue.isInitialized) return
        val running = stats.sessionStart > 0L && SettingsStore.settings.value.enabled
        sessionValue.text = formatDuration(StatsStore.sessionMillis(stats))
        sessionValue.setTextColor(if (running) Palette.TEXT else Palette.MUTED)
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
            android.widget.FrameLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
            ),
        )

        root.addView(activity.eyebrow("BulletChat · 概览", Palette.ACCENT))
        root.addView(
            activity.textView("运行概览", 30f, Palette.TEXT, true).apply {
                setPadding(0, activity.dp(8), 0, 0)
                letterSpacing = -0.02f
            },
        )
        root.addView(
            activity.textView(
                SimpleDateFormat("yyyy 年 M 月 d 日", Locale.getDefault()).format(Date()),
                12f,
                Palette.MUTED,
            ).apply { setPadding(0, activity.dp(7), 0, 0) },
        )

        root.addView(heroCard(), wrap(top = 20))
        root.addView(metricGrid(), wrap(top = 12))
        root.addView(recentCard(), wrap(top = 12))
        root.addView(tipCard(), wrap(top = 12))
        return scroll
    }

    private fun heroCard(): View {
        val card = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            background = activity.gradientFill(
                26f,
                intArrayOf(0xFF12303F.toInt(), 0xFF111C33.toInt(), 0xFF191A38.toInt()),
            )
            setPadding(activity.dp(20), activity.dp(18), activity.dp(20), activity.dp(20))
        }

        val statusRow = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        statusDot = activity.textView("●", 9f, Palette.MUTED)
        statusRow.addView(statusDot)
        statusText = activity.textView("弹幕服务已停止", 12f, Palette.MUTED).apply {
            setPadding(activity.dp(6), 0, 0, 0)
        }
        statusRow.addView(statusText)
        card.addView(statusRow)

        card.addView(
            activity.textView("00:00", 44f, Palette.TEXT, true).apply {
                letterSpacing = -0.03f
                setPadding(0, activity.dp(14), 0, 0)
                typeface = Typeface.create("sans-serif-light", Typeface.NORMAL)
            }.also { sessionValue = it },
        )
        card.addView(
            activity.textView("本次运行时长", 11f, Palette.MUTED).apply {
                setPadding(0, activity.dp(2), 0, 0)
                letterSpacing = 0.04f
            },
        )

        card.addView(
            activity.textView(
                "弹幕会以单行滚动的方式展示完整消息内容，不换行、不截断。",
                11f,
                Palette.TEXT_DIM,
            ).apply {
                setPadding(0, activity.dp(14), 0, 0)
                setLineSpacing(0f, 1.35f)
            },
        )
        return card
    }

    private fun metricGrid(): View {
        val column = LinearLayout(activity).apply { orientation = LinearLayout.VERTICAL }

        val row1 = LinearLayout(activity).apply { orientation = LinearLayout.HORIZONTAL }
        todayValue = metricCard(row1, "今日转发", "0", Palette.ACCENT)
        totalValue = metricCard(row1, "累计转发", "0", Palette.ACCENT_2)
        column.addView(row1)

        val row2 = LinearLayout(activity).apply { orientation = LinearLayout.HORIZONTAL }
        totalRunValue = metricCard(row2, "累计运行", "00:00", Palette.ACCENT_3)
        sourceValue = metricCard(row2, "通知来源", "0", Palette.SUCCESS)
        column.addView(row2, wrap(top = 10))

        return column
    }

    private fun metricCard(
        parent: LinearLayout,
        label: String,
        initial: String,
        accent: Int,
    ): TextView {
        val card = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            background = activity.glass(20f, 0xB30D1725.toInt(), 0x14FFFFFF)
            setPadding(activity.dp(16), activity.dp(14), activity.dp(16), activity.dp(15))
        }
        val head = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        head.addView(
            View(activity).apply {
                background = activity.circleFill(6, intArrayOf(accent, accent))
            },
            LinearLayout.LayoutParams(activity.dp(6), activity.dp(6)),
        )
        head.addView(
            activity.textView(label, 11f, Palette.MUTED).apply {
                setPadding(activity.dp(6), 0, 0, 0)
            },
        )
        card.addView(head)

        val value = activity.textView(initial, 26f, Palette.TEXT, true).apply {
            setPadding(0, activity.dp(9), 0, 0)
            letterSpacing = -0.02f
        }
        card.addView(value)

        parent.addView(
            card,
            LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                if (parent.childCount == 0) marginEnd = activity.dp(5) else marginStart = activity.dp(5)
            },
        )
        return value
    }

    private fun recentCard(): View {
        val card = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            background = activity.glass(24f)
            setPadding(activity.dp(18), activity.dp(16), activity.dp(18), activity.dp(16))
        }

        val header = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        header.addView(
            activity.eyebrow("最近一条", Palette.ACCENT_2),
            LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f),
        )
        recentTime = activity.textView("--:--", 10f, Palette.MUTED)
        header.addView(recentTime)
        card.addView(header)

        recentApp = activity.textView("暂无消息", 13f, Palette.TEXT, true).apply {
            setPadding(0, activity.dp(12), 0, 0)
        }
        card.addView(recentApp)

        recentTitle = activity.textView("", 11f, Palette.ACCENT).apply {
            setPadding(0, activity.dp(4), 0, 0)
            visibility = View.GONE
        }
        card.addView(recentTitle)

        recentText = activity.textView("", 12f, Palette.TEXT_DIM).apply {
            setPadding(0, activity.dp(7), 0, 0)
            maxLines = 1
            ellipsize = android.text.TextUtils.TruncateAt.END
        }
        card.addView(recentText)
        return card
    }

    private fun tipCard(): View {
        val card = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            background = activity.glass(20f, 0x14FFC470, 0x33FFC470)
            setPadding(activity.dp(16), activity.dp(14), activity.dp(16), activity.dp(14))
        }
        card.addView(
            activity.textView("提示", 11f, Palette.WARN, true),
        )
        card.addView(
            activity.textView(
                "主页数据仅保存在本机，卸载应用后会一并清除。",
                11f,
                Palette.MUTED,
            ).apply {
                setPadding(activity.dp(10), 0, 0, 0)
                setLineSpacing(0f, 1.3f)
            },
            LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f),
        )
        return card
    }

    private fun wrap(top: Int) = LinearLayout.LayoutParams(
        LinearLayout.LayoutParams.MATCH_PARENT,
        LinearLayout.LayoutParams.WRAP_CONTENT,
    ).apply { topMargin = activity.dp(top) }
}

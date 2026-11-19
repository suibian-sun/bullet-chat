package com.suibian.bulletchat

import android.app.Activity
import android.app.AlertDialog
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.suibian.bulletchat.data.HistoryEntry
import com.suibian.bulletchat.data.HistoryStore
import com.suibian.bulletchat.ui.AuroraDrawable
import com.suibian.bulletchat.ui.Palette
import com.suibian.bulletchat.ui.applyLightSystemBars
import com.suibian.bulletchat.ui.dp
import com.suibian.bulletchat.ui.eyebrow
import com.suibian.bulletchat.ui.glass
import com.suibian.bulletchat.ui.softShadow
import com.suibian.bulletchat.ui.staggerEnter
import com.suibian.bulletchat.ui.textView
import com.suibian.bulletchat.ui.tint
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 消息记录：最近 60 条已转发弹幕。
 * 弹幕本身是即时的、不缓存的，这里只是给用户留一份可回看的文字记录，全部存在本机。
 */
class HistoryActivity : Activity() {

    private lateinit var listContainer: LinearLayout
    private lateinit var emptyView: View

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        HistoryStore.init(this)
        window.setBackgroundDrawable(AuroraDrawable())
        window.applyLightSystemBars()
        setContentView(build())
    }

    private fun build(): View {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = AuroraDrawable()
        }

        val topBar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(12), dp(12), dp(16), dp(6))
        }
        val back = ImageView(this).apply {
            setImageResource(R.drawable.ic_back)
            tint(Palette.TEXT)
            setPadding(dp(10), dp(10), dp(10), dp(10))
            isClickable = true
            setOnClickListener { finish() }
        }
        topBar.addView(back, LinearLayout.LayoutParams(dp(40), dp(40)))
        topBar.addView(
            textView("消息记录", 17f, Palette.TEXT, true),
            LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                marginStart = dp(4)
            },
        )
        val clear = textView("清空", 13f, Palette.ACCENT, true).apply {
            setPadding(dp(10), dp(6), 0, dp(6))
            isClickable = true
            setOnClickListener { confirmClear() }
        }
        topBar.addView(clear)
        root.addView(topBar)

        ViewCompat.setOnApplyWindowInsetsListener(root) { view, insets ->
            val bars = insets.getInsets(
                WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout(),
            )
            view.setPadding(0, bars.top, 0, bars.bottom)
            insets
        }

        val scroll = ScrollView(this).apply {
            isFillViewport = true
            isVerticalScrollBarEnabled = false
            clipToPadding = false
        }
        val list = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(8), dp(20), dp(30))
        }
        scroll.addView(
            list,
            android.widget.FrameLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
            ),
        )

        list.addView(eyebrow("History", Palette.ACCENT_2))
        list.addView(
            textView("最近转发", 28f, Palette.TEXT, true).apply {
                setPadding(0, dp(8), 0, dp(4))
                letterSpacing = -0.02f
            },
        )
        list.addView(
            textView("只保留最近 60 条，点按任意一条即可复制完整内容。", 11f, Palette.MUTED).apply {
                setPadding(0, 0, 0, dp(16))
            },
        )

        listContainer = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        list.addView(listContainer)

        emptyView = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            background = glass(22f)
            softShadow(2f)
            setPadding(dp(20), dp(28), dp(20), dp(28))
            addView(textView("还没有记录", 15f, Palette.TEXT, true).apply { gravity = Gravity.CENTER })
            addView(
                textView("开启弹幕后，转发过的消息会按时间倒序出现在这里。", 11f, Palette.MUTED).apply {
                    gravity = Gravity.CENTER
                    setPadding(0, dp(8), 0, 0)
                    setLineSpacing(0f, 1.3f)
                },
            )
        }
        list.addView(emptyView)

        root.addView(
            scroll,
            LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f),
        )
        render()
        list.post { list.staggerEnter(startDelay = 60L, step = 40L, distanceDp = 14) }
        return root
    }

    private fun render() {
        val entries = HistoryStore.snapshot()
        listContainer.removeAllViews()
        emptyView.visibility = if (entries.isEmpty()) View.VISIBLE else View.GONE
        entries.forEach { listContainer.addView(entryCard(it)) }
    }

    private fun entryCard(entry: HistoryEntry): View {
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = glass(20f)
            softShadow(2f)
            setPadding(dp(16), dp(13), dp(16), dp(14))
            isClickable = true
            setOnClickListener { copy(entry) }
        }

        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        header.addView(
            textView(entry.appLabel.ifBlank { "未知应用" }, 11f, Palette.ACCENT, true),
        )
        header.addView(
            textView(formatTime(entry.time), 10f, Palette.MUTED),
            LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                gravity = Gravity.END
            },
        )
        card.addView(header)

        card.addView(
            textView(entry.displayText.ifBlank { "（无内容）" }, 13f, Palette.TEXT_DIM).apply {
                setPadding(0, dp(8), 0, 0)
                setLineSpacing(dp(3).toFloat(), 1.35f)
                maxLines = 4
                ellipsize = android.text.TextUtils.TruncateAt.END
            },
        )

        return card.apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
            ).apply { bottomMargin = dp(10) }
        }
    }

    private fun copy(entry: HistoryEntry) {
        val text = entry.displayText
        if (text.isBlank()) return
        try {
            val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            clipboard.setPrimaryClip(ClipData.newPlainText("弹幕内容", text))
            Toast.makeText(this, "已复制", Toast.LENGTH_SHORT).show()
        } catch (_: Throwable) {
            Toast.makeText(this, "复制失败", Toast.LENGTH_SHORT).show()
        }
    }

    private fun confirmClear() {
        if (HistoryStore.count() == 0) {
            Toast.makeText(this, "没有可清空的记录", Toast.LENGTH_SHORT).show()
            return
        }
        AlertDialog.Builder(this)
            .setTitle("清空消息记录")
            .setMessage("只会删除本机保存的记录，不影响转发统计中的累计数字。")
            .setPositiveButton("清空") { _, _ ->
                HistoryStore.clear()
                render()
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun formatTime(time: Long): String {
        if (time <= 0L) return "--:--"
        val pattern = if (isSameDay(time, System.currentTimeMillis())) "HH:mm" else "MM-dd HH:mm"
        return SimpleDateFormat(pattern, Locale.getDefault()).format(Date(time))
    }

    private fun isSameDay(a: Long, b: Long): Boolean {
        val format = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        return format.format(Date(a)) == format.format(Date(b))
    }
}

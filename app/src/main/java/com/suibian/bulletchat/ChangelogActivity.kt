package com.suibian.bulletchat

import android.app.Activity
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.suibian.bulletchat.data.Changelog
import com.suibian.bulletchat.ui.AuroraDrawable
import com.suibian.bulletchat.ui.applyLightSystemBars
import com.suibian.bulletchat.ui.softShadow
import com.suibian.bulletchat.ui.Palette
import com.suibian.bulletchat.ui.dp
import com.suibian.bulletchat.ui.eyebrow
import com.suibian.bulletchat.ui.glass
import com.suibian.bulletchat.ui.staggerEnter
import com.suibian.bulletchat.ui.textView
import com.suibian.bulletchat.ui.tint

/** 历史更新：按版本倒序的时间线。 */
class ChangelogActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
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
            setPadding(dp(12), dp(12), dp(20), dp(6))
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
            textView("历史更新", 17f, Palette.TEXT, true),
            LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                marginStart = dp(4)
            },
        )
        topBar.addView(textView("v${BuildConfig.VERSION_NAME}", 12f, Palette.ACCENT, true))
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

        list.addView(eyebrow("Release Notes", Palette.ACCENT_2))
        list.addView(
            textView("每一次变化", 28f, Palette.TEXT, true).apply {
                setPadding(0, dp(8), 0, dp(4))
                letterSpacing = -0.02f
            },
        )
        list.addView(
            textView("完整记录见 GitHub Releases", 11f, Palette.MUTED).apply {
                setPadding(0, 0, 0, dp(18))
            },
        )

        Changelog.entries.forEachIndexed { index, entry ->
            list.addView(entryCard(entry.version, entry.date, entry.title, entry.highlights, index == 0))
        }
        root.addView(scroll, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))
        list.post { list.staggerEnter(startDelay = 60L, step = 45L, distanceDp = 16) }
        return root
    }

    private fun entryCard(
        version: String,
        date: String,
        title: String,
        highlights: List<String>,
        latest: Boolean,
    ): View {
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = glass(
                22f,
                if (latest) 0xFFEFF5FF.toInt() else 0xFFFFFFFF.toInt(),
                if (latest) 0x332563EB else Palette.OUTLINE,
            )
            softShadow(if (latest) 3f else 1.5f)
            setPadding(dp(18), dp(16), dp(18), dp(16))
        }

        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val badge = textView("v$version", 12f, if (latest) 0xFFFFFFFF.toInt() else Palette.TEXT_DIM, true).apply {
            setPadding(dp(10), dp(5), dp(10), dp(5))
            background = if (latest) {
                gradientChip()
            } else {
                glass(10f, Palette.GHOST, 0x00000000)
            }
        }
        header.addView(badge)
        if (latest) {
            header.addView(
                textView("最新", 10f, Palette.ACCENT, true).apply {
                    setPadding(dp(8), 0, 0, 0)
                    letterSpacing = 0.08f
                },
            )
        }
        header.addView(
            textView(date, 10f, Palette.MUTED),
            LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                gravity = Gravity.END
            },
        )
        card.addView(header)

        card.addView(
            textView(title, 15f, Palette.TEXT, true).apply {
                setPadding(0, dp(12), 0, 0)
            },
        )

        highlights.forEach { item ->
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                setPadding(0, dp(9), 0, 0)
            }
            row.addView(
                View(this).apply {
                    background = glass(3f, Palette.ACCENT, Palette.ACCENT)
                },
                LinearLayout.LayoutParams(dp(5), dp(5)).apply { topMargin = dp(6) },
            )
            row.addView(
                textView(item, 12f, Palette.TEXT_DIM).apply {
                    setPadding(dp(9), 0, 0, 0)
                    setLineSpacing(dp(3).toFloat(), 1.3f)
                },
                LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f),
            )
            card.addView(row)
        }

        return card.apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
            ).apply { bottomMargin = dp(12) }
        }
    }

    private fun gradientChip() = android.graphics.drawable.GradientDrawable(
        android.graphics.drawable.GradientDrawable.Orientation.TL_BR,
        intArrayOf(0xFF4F8CFF.toInt(), 0xFF1D4ED8.toInt()),
    ).apply {
        shape = android.graphics.drawable.GradientDrawable.RECTANGLE
        cornerRadius = dp(10).toFloat()
    }
}

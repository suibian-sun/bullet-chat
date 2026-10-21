package com.suibian.bulletchat.ui

import android.app.Activity
import android.app.AlertDialog
import android.content.res.ColorStateList
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.TextView
import com.suibian.bulletchat.BuildConfig
import com.suibian.bulletchat.R
import com.suibian.bulletchat.data.AppSettings
import com.suibian.bulletchat.data.DisplayOrientation
import com.suibian.bulletchat.data.SettingsStore
import com.suibian.bulletchat.util.Permissions

/** 设置页：来源、显示方式、外观、过滤、权限与关于。 */
class SettingsPage(
    private val activity: Activity,
    private val onOpenAppPicker: () -> Unit,
    private val onOpenChangelog: () -> Unit,
    private val onCheckUpdate: () -> Unit,
    private val onOpenPermission: (Int) -> Unit,
    private val onSettingsChanged: () -> Unit,
) {

    val view: View

    private lateinit var sourceSummary: TextView
    private lateinit var orientationValue: TextView
    private lateinit var sourceCount: TextView
    private lateinit var updateValue: TextView
    private lateinit var versionValue: TextView

    private val toggles = LinkedHashMap<String, ToggleView>()
    private val sliders = ArrayList<SliderHolder>()
    private val permissionStatuses = ArrayList<TextView>()

    private var binding = false

    private class SliderHolder(
        val bar: SeekBar,
        val valueView: TextView,
        val min: Int,
        val max: Int,
        val formatter: (Int) -> String,
        val key: String,
    ) {
        fun valueOf(settings: AppSettings): Int = when (key) {
            "position" -> settings.positionPercent
            "lanes" -> settings.laneCount
            "alpha" -> settings.alphaPercent
            "font" -> settings.fontSp
            "speed" -> settings.speedPercent
            else -> min
        }
    }

    init {
        view = build()
    }

    fun refresh() {
        binding = true
        try {
            val settings = SettingsStore.settings.value
            sourceSummary.text = sourceSummaryText(settings)
            sourceCount.text = "${settings.selectedPackages.size} 个"
            orientationValue.text = settings.displayOrientation.label

            toggles["screenOn"]?.setChecked(settings.onlyWhenScreenOn)
            toggles["skipGroup"]?.setChecked(settings.skipGroupSummary)
            toggles["showIcon"]?.setChecked(settings.showAppIcon)
            toggles["clickOpen"]?.setChecked(settings.clickToOpen)
            toggles["floating"]?.apply {
                setChecked(settings.tryFloatingWindow)
                alpha = if (settings.clickToOpen) 1f else 0.4f
                isEnabled = settings.clickToOpen
            }

            sliders.forEach { holder ->
                val value = holder.valueOf(settings)
                holder.bar.progress = (value - holder.min).coerceIn(0, holder.max - holder.min)
                holder.valueView.text = holder.formatter(value)
            }

            versionValue.text = "v${BuildConfig.VERSION_NAME} · build ${BuildConfig.VERSION_CODE}"

            val granted = listOf(
                Permissions.hasNotificationAccess(activity),
                Permissions.canDrawOverlays(activity),
                Permissions.hasDndAccess(activity),
                Permissions.isIgnoringBattery(activity),
            )
            permissionStatuses.forEachIndexed { index, label ->
                val ok = granted.getOrElse(index) { false }
                label.text = if (ok) "已就绪" else "去开启"
                label.setTextColor(if (ok) Palette.SUCCESS else Palette.ACCENT)
            }
        } finally {
            binding = false
        }
    }

    fun setUpdateStatus(text: String) {
        if (::updateValue.isInitialized) updateValue.text = text
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

        root.addView(activity.eyebrow("Preferences", Palette.ACCENT))
        root.addView(
            activity.textView("设置", 30f, Palette.TEXT, true).apply {
                setPadding(0, activity.dp(8), 0, 0)
                letterSpacing = -0.02f
            },
        )

        root.addView(sectionLabel("通知来源"), wrap(top = 22))
        root.addView(sourceCard())

        root.addView(sectionLabel("显示方式"), wrap(top = 20))
        root.addView(displayCard())

        root.addView(sectionLabel("外观与位置"), wrap(top = 20))
        root.addView(appearanceCard())

        root.addView(sectionLabel("关键词过滤"), wrap(top = 20))
        root.addView(filterCard())

        root.addView(sectionLabel("权限"), wrap(top = 20))
        root.addView(permissionCard())

        root.addView(sectionLabel("关于"), wrap(top = 20))
        root.addView(aboutCard())
        return scroll
    }

    private fun sectionLabel(text: String): TextView =
        activity.eyebrow(text, Palette.MUTED).apply {
            setPadding(activity.dp(4), 0, 0, activity.dp(9))
        }

    private fun card(): LinearLayout = LinearLayout(activity).apply {
        orientation = LinearLayout.VERTICAL
        background = activity.glass(24f)
        setPadding(activity.dp(18), activity.dp(4), activity.dp(18), activity.dp(4))
    }

    private fun sourceCard(): View {
        val card = card()
        val trailing = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        sourceCount = activity.textView("2 个", 12f, Palette.ACCENT, true)
        trailing.addView(sourceCount)
        trailing.addView(chevron())

        card.addView(activity.settingRow("应用列表", "选择需要转成弹幕的应用", trailing) {
            onOpenAppPicker()
        })
        sourceSummary = activity.textView("", 11f, Palette.MUTED).apply {
            setPadding(0, activity.dp(2), 0, activity.dp(14))
            setLineSpacing(0f, 1.25f)
        }
        card.addView(sourceSummary)
        return card
    }

    private fun displayCard(): View {
        val card = card()

        val orientationTrailing = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        orientationValue = activity.textView("全部方向", 12f, Palette.ACCENT, true)
        orientationTrailing.addView(orientationValue)
        orientationTrailing.addView(chevron())
        card.addView(
            activity.settingRow("显示范围", "仅竖屏 / 仅横屏 / 全部方向", orientationTrailing) {
                showOrientationDialog()
            },
        )
        card.addView(activity.divider())

        card.addView(switchRow("screenOn", "仅亮屏时显示", "锁屏、息屏时不打扰") { value ->
            copy(onlyWhenScreenOn = value)
        })
        card.addView(activity.divider())
        card.addView(switchRow("skipGroup", "过滤群聊汇总", "减少「N 条新消息」这类重复弹幕") { value ->
            copy(skipGroupSummary = value)
        })
        card.addView(activity.divider())
        card.addView(switchRow("showIcon", "显示应用图标", "弹幕左侧显示来源图标") { value ->
            copy(showAppIcon = value)
        })
        card.addView(activity.divider())
        card.addView(switchRow("clickOpen", "点击弹幕打开应用", "点中弹幕直达会话，弹幕带内会拦截触摸") { value ->
            copy(clickToOpen = value)
        })
        card.addView(activity.divider())
        card.addView(switchRow("floating", "尝试小窗打开", "系统支持自由窗口时以小窗显示") { value ->
            copy(tryFloatingWindow = value)
        })
        return card
    }

    private fun switchRow(
        key: String,
        title: String,
        subtitle: String,
        onChange: AppSettings.(Boolean) -> AppSettings,
    ): View {
        val toggle = ToggleView(activity)
        toggles[key] = toggle
        toggle.onCheckedChange = { value ->
            if (!binding) update { it.onChange(value) }
        }
        return activity.settingRow(title, subtitle, toggle) {
            toggle.setChecked(!toggle.isChecked, true)
        }
    }

    private fun appearanceCard(): View {
        val card = card()
        sliderRow(card, "position", "垂直位置", { "$it% · 距顶部" }, 0, 70) {
            copy(positionPercent = it)
        }
        card.addView(activity.divider())
        sliderRow(card, "lanes", "弹幕行数", { "$it 行" }, 1, 8) {
            copy(laneCount = it)
        }
        card.addView(activity.divider())
        sliderRow(card, "alpha", "透明度", { "$it%" }, 20, 100) {
            copy(alphaPercent = it)
        }
        card.addView(activity.divider())
        sliderRow(card, "font", "字号", { "$it sp" }, 12, 28) {
            copy(fontSp = it)
        }
        card.addView(activity.divider())
        sliderRow(card, "speed", "速度", { "$it%" }, 50, 250) {
            copy(speedPercent = it)
        }

        val reset = activity.textView("恢复默认外观", 12f, Palette.ACCENT, true).apply {
            setPadding(0, activity.dp(16), 0, activity.dp(12))
            isClickable = true
            setOnClickListener {
                update {
                    it.copy(
                        positionPercent = 12,
                        laneCount = 5,
                        alphaPercent = 85,
                        fontSp = 16,
                        speedPercent = 100,
                    )
                }
            }
        }
        card.addView(reset)
        return card
    }

    private fun sliderRow(
        card: LinearLayout,
        key: String,
        title: String,
        formatter: (Int) -> String,
        min: Int,
        max: Int,
        onChange: AppSettings.(Int) -> AppSettings,
    ) {
        val header = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, activity.dp(15), 0, 0)
        }
        header.addView(
            activity.textView(title, 13f, Palette.TEXT_DIM),
            LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f),
        )
        val valueView = activity.textView(formatter(min), 12f, Palette.ACCENT, true)
        header.addView(valueView)
        card.addView(header)

        val bar = SeekBar(activity).apply {
            this.max = max - min
            progress = 0
            progressTintList = ColorStateList.valueOf(Palette.ACCENT)
            thumbTintList = ColorStateList.valueOf(Palette.ACCENT)
            progressBackgroundTintList = ColorStateList.valueOf(0xFF202C3B.toInt())
        }
        val holder = SliderHolder(bar, valueView, min, max, formatter, key)
        sliders.add(holder)

        bar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (!fromUser) return
                val value = progress + min
                valueView.text = formatter(value)
                SettingsStore.update({ it.onChange(value) }, persist = false)
                onSettingsChanged()
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) = Unit

            override fun onStopTrackingTouch(seekBar: SeekBar?) {
                SettingsStore.persist()
            }
        })
        card.addView(bar)
    }

    private fun filterCard(): View {
        val card = card()
        card.addView(
            activity.textView("包含关键词的消息不会显示，多个关键词用逗号分隔。", 11f, Palette.MUTED).apply {
                setPadding(0, activity.dp(11), 0, activity.dp(12))
            },
        )

        val edit = EditText(activity).apply {
            setText(SettingsStore.settings.value.blacklist)
            hint = "例如：广告, 砍价, 转账"
            setTextColor(Palette.TEXT)
            setHintTextColor(0xFF5A6A7E.toInt())
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            background = activity.glass(14f, 0xFF132030.toInt(), 0x14FFFFFF)
            setPadding(activity.dp(14), activity.dp(12), activity.dp(14), activity.dp(12))
            minLines = 2
            gravity = Gravity.TOP or Gravity.START
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE
        }
        edit.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
            override fun afterTextChanged(s: Editable?) {
                SettingsStore.update({ it.copy(blacklist = s?.toString().orEmpty()) }, persist = false)
            }
        })
        card.addView(
            edit,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
            ).apply { bottomMargin = activity.dp(12) },
        )
        return card
    }

    private fun permissionCard(): View {
        val card = card()
        val titles = listOf("通知使用权", "悬浮窗权限", "免打扰权限", "后台运行")
        val subtitles = listOf(
            "读取所选应用的新消息",
            "在其他应用上方显示弹幕",
            "勿扰模式下仍可显示",
            "忽略电池优化，避免服务被清理",
        )
        titles.forEachIndexed { index, title ->
            val status = activity.textView("去开启", 12f, Palette.ACCENT, true)
            permissionStatuses.add(status)
            val trailing = LinearLayout(activity).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
            }
            trailing.addView(status)
            trailing.addView(chevron())
            card.addView(activity.settingRow(title, subtitles[index], trailing) {
                onOpenPermission(index)
            })
            if (index < titles.size - 1) card.addView(activity.divider())
        }
        card.addView(
            activity.textView(
                "小米 / 华为 / OPPO / vivo 等机型还需在系统设置中允许「后台弹出界面」与自启动，否则悬浮层可能被拦截。",
                10f,
                Palette.MUTED,
            ).apply {
                setPadding(0, activity.dp(10), 0, activity.dp(10))
                setLineSpacing(activity.dp(2).toFloat(), 1.25f)
            },
        )
        return card
    }

    private fun aboutCard(): View {
        val card = card()

        versionValue = activity.textView("", 12f, Palette.MUTED)
        card.addView(activity.settingRow("当前版本", null, versionValue))
        card.addView(activity.divider())

        val updateTrailing = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        updateValue = activity.textView("检查", 12f, Palette.ACCENT, true)
        updateTrailing.addView(updateValue)
        updateTrailing.addView(chevron())
        card.addView(activity.settingRow("检查更新", "从 GitHub Releases 获取新版本", updateTrailing) {
            onCheckUpdate()
        })
        card.addView(activity.divider())

        val changelogTrailing = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        changelogTrailing.addView(activity.textView("查看", 12f, Palette.ACCENT, true))
        changelogTrailing.addView(chevron())
        card.addView(activity.settingRow("历史更新", "版本变更记录", changelogTrailing) {
            onOpenChangelog()
        })
        return card
    }

    private fun chevron(): ImageView = ImageView(activity).apply {
        setImageResource(R.drawable.ic_chevron_right)
        tint(Palette.MUTED)
        alpha = 0.55f
        layoutParams = LinearLayout.LayoutParams(activity.dp(16), activity.dp(16)).apply {
            marginStart = activity.dp(2)
        }
    }

    private fun showOrientationDialog() {
        val labels = DisplayOrientation.entries.map { it.label }.toTypedArray()
        val current = SettingsStore.settings.value.displayOrientation.ordinal
        AlertDialog.Builder(activity)
            .setTitle("显示范围")
            .setSingleChoiceItems(labels, current) { dialog, which ->
                update { it.copy(displayOrientation = DisplayOrientation.entries[which]) }
                dialog.dismiss()
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun sourceSummaryText(settings: AppSettings): String {
        val labels = settings.selectedPackages.mapNotNull { pkg ->
            try {
                val info = activity.packageManager.getApplicationInfo(pkg, 0)
                activity.packageManager.getApplicationLabel(info).toString()
            } catch (_: Throwable) {
                null
            }
        }.sorted()
        return when {
            labels.isEmpty() -> "未选择任何应用，不会显示弹幕"
            labels.size <= 4 -> labels.joinToString("、")
            else -> "${labels.take(4).joinToString("、")} 等 ${labels.size} 个"
        }
    }

    /** 持久化设置并通知外层刷新，避免本页重复刷新造成递归。 */
    private fun update(transform: (AppSettings) -> AppSettings) {
        SettingsStore.update(transform)
        onSettingsChanged()
    }

    private fun wrap(top: Int) = LinearLayout.LayoutParams(
        LinearLayout.LayoutParams.MATCH_PARENT,
        LinearLayout.LayoutParams.WRAP_CONTENT,
    ).apply { topMargin = activity.dp(top) }
}

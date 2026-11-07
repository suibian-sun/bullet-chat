package com.suibian.bulletchat.ui

import android.app.Activity
import android.app.AlertDialog
import android.app.TimePickerDialog
import android.content.res.ColorStateList
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.text.format.DateFormat
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
import com.suibian.bulletchat.data.CardStyle
import com.suibian.bulletchat.data.ContentMode
import com.suibian.bulletchat.data.DisplayOrientation
import com.suibian.bulletchat.data.FilterMode
import com.suibian.bulletchat.data.KeywordMode
import com.suibian.bulletchat.data.SettingsStore
import com.suibian.bulletchat.util.Permissions

/**
 * 设置页：来源、过滤、显示方式、弹幕外观、内容与关键词、安静时段、工具、权限与关于。
 */
class SettingsPage(
    private val activity: Activity,
    private val onOpenAppPicker: () -> Unit,
    private val onOpenFilterPicker: (FilterMode) -> Unit,
    private val onOpenChangelog: () -> Unit,
    private val onCheckUpdate: () -> Unit,
    private val onOpenPermission: (Int) -> Unit,
    private val onSendTest: () -> Unit,
    private val onSettingsChanged: () -> Unit,
) {

    val view: View

    private lateinit var sourceSummary: TextView
    private lateinit var sourceCount: TextView
    private lateinit var filterModeValue: TextView
    private lateinit var filterCount: TextView
    private lateinit var filterSummary: TextView
    private lateinit var filterRow: View
    private lateinit var orientationValue: TextView
    private lateinit var contentValue: TextView
    private lateinit var quietRange: TextView
    private lateinit var styleHint: TextView
    private lateinit var updateValue: TextView
    private lateinit var versionValue: TextView
    private lateinit var preview: DanmakuPreviewView

    private val toggles = LinkedHashMap<String, ToggleView>()
    private val toggleRows = LinkedHashMap<String, View>()
    private val sliders = ArrayList<SliderHolder>()
    private val permissionStatuses = ArrayList<TextView>()
    private val styleChips = LinkedHashMap<CardStyle, TextView>()
    private val keywordChips = LinkedHashMap<KeywordMode, TextView>()

    private var binding = false
    private var introPlayed = false
    private lateinit var rootContainer: LinearLayout

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
            "cardAlpha" -> settings.cardAlphaPercent
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

            filterModeValue.text = settings.filterMode.label
            filterCount.text = "${settings.filterPackages.size} 个"
            val filterActive = settings.filterMode != FilterMode.OFF
            filterRow.alpha = if (filterActive) 1f else 0.45f
            filterRow.isEnabled = filterActive
            filterSummary.text = filterHint(settings)

            toggles["screenOn"]?.setChecked(settings.onlyWhenScreenOn)
            toggles["skipGroup"]?.setChecked(settings.skipGroupSummary)
            toggles["showIcon"]?.setChecked(settings.showAppIcon)
            toggles["clickOpen"]?.setChecked(settings.clickToOpen)
            toggles["quiet"]?.setChecked(settings.quietEnabled)
            toggles["floating"]?.setChecked(settings.tryFloatingWindow)
            // 行容器与开关一起禁用，否则整行仍可点击、绕过依赖关系
            toggles["floating"]?.isEnabled = settings.clickToOpen
            toggleRows["floating"]?.apply {
                isEnabled = settings.clickToOpen
                alpha = if (settings.clickToOpen) 1f else 0.4f
            }

            styleChips.forEach { (style, chip) -> applyChipStyle(chip, style == settings.cardStyle) }
            styleHint.text = settings.cardStyle.desc
            keywordChips.forEach { (mode, chip) ->
                applyChipStyle(chip, mode == settings.keywordMode)
            }

            sliders.forEach { holder ->
                val value = holder.valueOf(settings)
                holder.bar.progress = (value - holder.min).coerceIn(0, holder.max - holder.min)
                holder.valueView.text = holder.formatter(value)
            }

            contentValue.text = settings.contentMode.label
            quietRange.text = "${formatMinuteOfDay(settings.quietStartMinute)} → " +
                formatMinuteOfDay(settings.quietEndMinute)
            quietRange.alpha = if (settings.quietEnabled) 1f else 0.45f

            preview.style = settings.cardStyle
            preview.cardAlphaPercent = settings.cardAlphaPercent
            preview.textAlphaPercent = settings.alphaPercent
            preview.showIcon = settings.showAppIcon
            preview.invalidate()

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

    /** 首次进入设置页：分组卡片自上而下错落出现。 */
    fun playIntro() {
        if (introPlayed) return
        introPlayed = true
        view.postDelayed({
            for (index in 0 until rootContainer.childCount) {
                rootContainer.getChildAt(index)
                    .enterFromBottom(delay = 28L * index, distanceDp = 16)
            }
        }, 40L)
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
        rootContainer = root
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

        root.addView(sectionLabel("应用过滤"), wrap(top = 20))
        root.addView(filterCard())

        root.addView(sectionLabel("显示方式"), wrap(top = 20))
        root.addView(displayCard())

        root.addView(sectionLabel("弹幕外观"), wrap(top = 20))
        root.addView(appearanceCard())

        root.addView(sectionLabel("内容与关键词"), wrap(top = 20))
        root.addView(contentCard())

        root.addView(sectionLabel("安静时段"), wrap(top = 20))
        root.addView(quietCard())

        root.addView(sectionLabel("工具"), wrap(top = 20))
        root.addView(toolsCard())

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
        background = activity.glass(22f)
        softShadow()
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

    private fun filterCard(): View {
        val card = card()

        val modeTrailing = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        filterModeValue = activity.textView(FilterMode.OFF.label, 12f, Palette.ACCENT, true)
        modeTrailing.addView(filterModeValue)
        modeTrailing.addView(chevron())
        card.addView(
            activity.settingRow("过滤模式", "不过滤 / 仅白名单 / 排除黑名单", modeTrailing) {
                showFilterModeDialog()
            },
        )
        card.addView(activity.divider())

        val listTrailing = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        filterCount = activity.textView("0 个", 12f, Palette.ACCENT, true)
        listTrailing.addView(filterCount)
        listTrailing.addView(chevron())
        filterRow = activity.settingRow("名单应用", "勾选白名单或黑名单中的应用", listTrailing) {
            val mode = SettingsStore.settings.value.filterMode
            if (mode != FilterMode.OFF) onOpenFilterPicker(mode)
        }
        card.addView(filterRow)

        filterSummary = activity.textView("", 11f, Palette.MUTED).apply {
            setPadding(0, activity.dp(2), 0, activity.dp(14))
            setLineSpacing(0f, 1.25f)
        }
        card.addView(filterSummary)
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
        card.addView(switchRow("skipGroup", "过滤群聊汇总", "自动识别「3 条新消息」这类汇总通知") { value ->
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
        val row = activity.settingRow(title, subtitle, toggle) {
            toggle.setChecked(!toggle.isChecked, true)
        }
        toggleRows[key] = row
        return row
    }

    private fun appearanceCard(): View {
        val card = card()

        preview = DanmakuPreviewView(activity).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                activity.dp(76),
            ).apply { topMargin = activity.dp(10) }
        }
        card.addView(preview)

        card.addView(label("卡片样式", top = 14))
        val chips = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        CardStyle.entries.forEach { style ->
            val chip = chip(style.label) {
                update { it.copy(cardStyle = style) }
            }
            styleChips[style] = chip
            chips.addView(
                chip,
                LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                    marginEnd = if (style == CardStyle.MINIMAL) 0 else activity.dp(8)
                },
            )
        }
        card.addView(chips, wrap(top = 8))

        styleHint = activity.textView("", 11f, Palette.MUTED).apply {
            setPadding(0, activity.dp(8), 0, activity.dp(4))
            setLineSpacing(0f, 1.25f)
        }
        card.addView(styleHint)

        sliderRow(card, "cardAlpha", "卡片透明度", { "$it%" }, 0, 100) {
            copy(cardAlphaPercent = it)
        }
        card.addView(activity.divider())
        sliderRow(card, "alpha", "文字透明度", { "$it%" }, 20, 100) {
            copy(alphaPercent = it)
        }
        card.addView(activity.divider())
        sliderRow(card, "position", "垂直位置", { "$it% · 距顶部" }, 0, 70) {
            copy(positionPercent = it)
        }
        card.addView(activity.divider())
        sliderRow(card, "lanes", "弹幕行数", { "$it 行" }, 1, 8) {
            copy(laneCount = it)
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
                        cardStyle = CardStyle.BUBBLE,
                        cardAlphaPercent = 92,
                        alphaPercent = 100,
                        positionPercent = 12,
                        laneCount = 5,
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
            progressBackgroundTintList = ColorStateList.valueOf(0xFFE1E9F4.toInt())
        }
        val holder = SliderHolder(bar, valueView, min, max, formatter, key)
        sliders.add(holder)

        bar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (!fromUser) return
                val value = progress + min
                valueView.text = formatter(value)
                // 只写数据，悬浮层自己观察设置；不做整页刷新，避免拖动时被回写打断
                SettingsStore.update({ it.onChange(value) }, persist = false)
                preview.also {
                    it.cardAlphaPercent = SettingsStore.settings.value.cardAlphaPercent
                    it.textAlphaPercent = SettingsStore.settings.value.alphaPercent
                    it.invalidate()
                }
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) = Unit

            override fun onStopTrackingTouch(seekBar: SeekBar?) {
                SettingsStore.persist()
            }
        })
        card.addView(bar)
    }

    private fun contentCard(): View {
        val card = card()

        val contentTrailing = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        contentValue = activity.textView(ContentMode.TITLE_AND_TEXT.label, 12f, Palette.ACCENT, true)
        contentTrailing.addView(contentValue)
        contentTrailing.addView(chevron())
        card.addView(
            activity.settingRow("弹幕内容", "标题 + 正文 / 仅正文 / 仅标题", contentTrailing) {
                showContentModeDialog()
            },
        )
        card.addView(activity.divider())

        card.addView(label("关键词策略", top = 14))
        val chips = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        KeywordMode.entries.forEachIndexed { index, mode ->
            val chip = chip(if (mode == KeywordMode.BLOCK) "过滤关键词" else "只看关键词") {
                update { it.copy(keywordMode = mode) }
            }
            keywordChips[mode] = chip
            chips.addView(
                chip,
                LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                    marginEnd = if (index == 0) activity.dp(8) else 0
                },
            )
        }
        card.addView(chips, wrap(top = 8))

        card.addView(
            activity.textView(
                "多个关键词用逗号、分号或顿号分隔；不区分大小写。",
                11f,
                Palette.MUTED,
            ).apply { setPadding(0, activity.dp(10), 0, activity.dp(6)) },
        )

        val edit = EditText(activity).apply {
            setText(SettingsStore.settings.value.blacklist)
            hint = "例如：广告, 砍价, 转账"
            setTextColor(Palette.TEXT)
            setHintTextColor(0xFF9AA9BC.toInt())
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            background = activity.glass(14f, Palette.GHOST, 0x00000000)
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

    private fun quietCard(): View {
        val card = card()
        card.addView(switchRow("quiet", "开启安静时段", "到点自动停发弹幕，结束后自动恢复") { value ->
            copy(quietEnabled = value)
        })
        card.addView(activity.divider())

        quietRange = activity.textView("23:00 → 07:00", 12f, Palette.ACCENT, true)
        card.addView(activity.settingRow("时间段", "点击分别调整开始与结束时间", quietRange) {
            showQuietDialog()
        })
        card.addView(
            activity.textView(
                "支持跨零点（如 23:00 → 07:00）。安静时段内消息不会显示，也不计入转发统计。",
                11f,
                Palette.MUTED,
            ).apply {
                setPadding(0, activity.dp(4), 0, activity.dp(14))
                setLineSpacing(0f, 1.25f)
            },
        )
        return card
    }

    private fun toolsCard(): View {
        val card = card()

        val testTrailing = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        testTrailing.addView(activity.textView("发送", 12f, Palette.ACCENT, true))
        testTrailing.addView(chevron())
        card.addView(
            activity.settingRow("发送测试弹幕", "立即在屏幕上发出 3 条示例消息", testTrailing) {
                onSendTest()
            },
        )
        card.addView(activity.divider())

        // 系统没有公开「添加磁贴」的入口，这里只做说明，右侧留空避免看起来像可点
        card.addView(
            activity.settingRow(
                "通知栏磁贴",
                "下拉快捷设置里添加「弹幕通知」即可一键开关",
                View(activity),
            ),
        )
        card.addView(
            activity.textView(
                "开启「点击弹幕打开应用」后，弹幕带会接收触摸：点按直达会话，长按复制完整内容。关闭时触摸全部穿透给下层应用。",
                11f,
                Palette.MUTED,
            ).apply {
                setPadding(0, activity.dp(2), 0, activity.dp(14))
                setLineSpacing(0f, 1.25f)
            },
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

    private fun label(text: String, top: Int): TextView =
        activity.textView(text, 13f, Palette.TEXT_DIM).apply {
            setPadding(0, activity.dp(top), 0, 0)
        }

    private fun chip(text: String, onClick: () -> Unit): TextView =
        activity.textView(text, 12f, Palette.TEXT_DIM, true).apply {
            gravity = Gravity.CENTER
            setPadding(0, activity.dp(9), 0, activity.dp(9))
            isClickable = true
            setOnClickListener { onClick() }
        }

    private fun applyChipStyle(chip: TextView, selected: Boolean) {
        chip.background = if (selected) {
            activity.gradientFill(12f)
        } else {
            activity.glass(12f, Palette.GHOST, 0x00000000)
        }
        chip.setTextColor(if (selected) 0xFFFFFFFF.toInt() else Palette.TEXT_DIM)
        chip.alpha = if (selected) 1f else 0.92f
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

    private fun showContentModeDialog() {
        val labels = ContentMode.entries.map { it.label }.toTypedArray()
        val current = SettingsStore.settings.value.contentMode.ordinal
        AlertDialog.Builder(activity)
            .setTitle("弹幕内容")
            .setSingleChoiceItems(labels, current) { dialog, which ->
                update { it.copy(contentMode = ContentMode.entries[which]) }
                dialog.dismiss()
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun showFilterModeDialog() {
        val labels = FilterMode.entries.map { it.label }.toTypedArray()
        val current = SettingsStore.settings.value.filterMode.ordinal
        AlertDialog.Builder(activity)
            .setTitle("应用过滤")
            .setSingleChoiceItems(labels, current) { dialog, which ->
                update { it.copy(filterMode = FilterMode.entries[which]) }
                dialog.dismiss()
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun showQuietDialog() {
        val settings = SettingsStore.settings.value
        val is24 = DateFormat.is24HourFormat(activity)
        val startPicker = TimePickerDialog(
            activity,
            { _, hour, minute -> update { it.copy(quietStartMinute = hour * 60 + minute) } },
            settings.quietStartMinute / 60,
            settings.quietStartMinute % 60,
            is24,
        ).apply { setTitle("安静时段开始") }
        val endPicker = TimePickerDialog(
            activity,
            { _, hour, minute -> update { it.copy(quietEndMinute = hour * 60 + minute) } },
            settings.quietEndMinute / 60,
            settings.quietEndMinute % 60,
            is24,
        ).apply { setTitle("安静时段结束") }

        AlertDialog.Builder(activity)
            .setTitle("安静时段")
            .setItems(arrayOf("调整开始时间", "调整结束时间")) { _, which ->
                if (which == 0) startPicker.show() else endPicker.show()
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun filterHint(settings: AppSettings): String = when (settings.filterMode) {
        FilterMode.OFF -> "当前不再额外筛选，来源应用的所有消息都会显示。"
        FilterMode.WHITELIST -> if (settings.filterPackages.isEmpty()) {
            "白名单为空，暂时不会显示任何弹幕。点击上方「名单应用」勾选要放行的应用。"
        } else {
            "白名单优先于「通知来源」：只显示名单内的应用，当前 ${settings.filterPackages.size} 个。"
        }
        FilterMode.BLACKLIST -> if (settings.filterPackages.isEmpty()) {
            "黑名单为空，来源应用的消息都会显示。"
        } else {
            "「通知来源」中，名单内的 ${settings.filterPackages.size} 个应用会被排除，其余正常显示。"
        }
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

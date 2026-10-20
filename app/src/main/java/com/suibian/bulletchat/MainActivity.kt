package com.suibian.bulletchat

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import androidx.core.app.NotificationManagerCompat
import com.suibian.bulletchat.data.AppSettings
import com.suibian.bulletchat.data.DisplayOrientation
import com.suibian.bulletchat.data.SettingsStore
import com.suibian.bulletchat.service.OverlayService
import com.suibian.bulletchat.ui.Palette
import com.suibian.bulletchat.ui.dp
import com.suibian.bulletchat.ui.roundedBackground
import com.suibian.bulletchat.ui.textView

class MainActivity : Activity() {

    private lateinit var settings: AppSettings
    private var binding = false

    private lateinit var serviceSwitch: Switch
    private lateinit var statusView: TextView
    private lateinit var notificationStatus: TextView
    private lateinit var overlayStatus: TextView
    private lateinit var dndStatus: TextView
    private lateinit var batteryStatus: TextView
    private lateinit var sourceSummary: TextView
    private lateinit var orientationValue: TextView
    private lateinit var floatingSwitch: Switch

    private val labelCache = HashMap<String, String>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        SettingsStore.init(this)
        settings = SettingsStore.settings.value

        window.statusBarColor = Palette.BG
        window.navigationBarColor = Palette.BG

        setContentView(createContentView())
        refreshUi()
    }

    override fun onResume() {
        super.onResume()
        settings = SettingsStore.settings.value
        refreshUi()
        if (settings.enabled) {
            OverlayService.refreshChannel(this)
        }
    }

    override fun onPause() {
        SettingsStore.persist()
        super.onPause()
    }

    private fun createContentView(): View {
        val scroll = ScrollView(this).apply {
            isFillViewport = true
            setBackgroundColor(Palette.BG)
            isVerticalScrollBarEnabled = false
        }
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(10), dp(16), dp(32))
        }
        scroll.addView(
            root,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.WRAP_CONTENT,
            ),
        )

        root.addView(textView("弹幕通知", 26f, Palette.TEXT, true))
        root.addView(
            textView("微信 / QQ 消息，弹幕化提醒", 12f, Palette.MUTED).apply {
                setPadding(0, dp(5), 0, dp(2))
            },
        )

        root.addView(heroCard(), matchWrap(top = 14))
        root.addView(permissionCard(), matchWrap(top = 12))
        root.addView(sourceCard(), matchWrap(top = 12))
        root.addView(displayCard(), matchWrap(top = 12))
        root.addView(appearanceCard(), matchWrap(top = 12))
        root.addView(filterCard(), matchWrap(top = 12))

        val testButton = Button(this).apply {
            text = "发送测试弹幕"
            isAllCaps = false
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
            setTextColor(Palette.ACCENT_DARK)
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            backgroundTintList = ColorStateList.valueOf(Palette.ACCENT)
            stateListAnimator = null
            setOnClickListener {
                when {
                    !settings.enabled -> toast("请先打开顶部总开关")
                    !Settings.canDrawOverlays(this@MainActivity) -> {
                        toast("请先授予悬浮窗权限")
                        openOverlaySettings()
                    }
                    else -> OverlayService.sendTest(this@MainActivity)
                }
            }
        }
        root.addView(testButton, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(50)).apply {
            topMargin = dp(16)
        })

        root.addView(guideCard(), matchWrap(top = 12))
        root.addView(
            textView(
                "BulletChat ${BuildConfig.VERSION_NAME} · 本地运行 · 无网络权限",
                11f,
                Palette.MUTED,
            ).apply {
                gravity = Gravity.CENTER
                setPadding(0, dp(20), 0, 0)
                alpha = 0.75f
            },
        )

        return scroll
    }

    private fun heroCard(): LinearLayout {
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                intArrayOf(Palette.HERO_START, Palette.HERO_END),
            ).apply { cornerRadius = dp(22).toFloat() }
            setPadding(dp(18), dp(18), dp(18), dp(18))
        }

        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        val icon = ImageView(this).apply {
            setImageResource(R.drawable.ic_launcher_foreground)
            background = roundedBackground(0x225FE3F3, 13f)
            setPadding(dp(6), dp(6), dp(6), dp(6))
        }
        row.addView(icon, LinearLayout.LayoutParams(dp(42), dp(42)))

        val titles = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(12), 0, 0, 0)
        }
        titles.addView(textView("弹幕通知", 19f, Palette.TEXT, true))
        statusView = textView("● 未开启", 12f, Palette.MUTED).apply {
            setPadding(0, dp(3), 0, 0)
        }
        titles.addView(statusView)
        row.addView(titles, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))

        serviceSwitch = styledSwitch()
        serviceSwitch.setOnCheckedChangeListener { _, checked ->
            if (binding) return@setOnCheckedChangeListener
            setServiceEnabled(checked)
        }
        row.addView(serviceSwitch)

        card.addView(row)
        card.addView(
            textView(
                "消息化作弹幕从屏幕上飘过，位置、透明度、字号、速度都能调。",
                13f,
                0xFFA9BCCD.toInt(),
            ).apply {
                setPadding(0, dp(13), 0, 0)
                setLineSpacing(0f, 1.15f)
            },
        )
        return card
    }

    private fun permissionCard(): LinearLayout {
        val card = sectionCard("运行权限")

        notificationStatus = permissionRow(card, "通知使用权", "读取所选应用的新消息，仅在本机解析") {
            openNotificationAccess()
        }
        overlayStatus = permissionRow(card, "悬浮窗权限", "在其他应用上方显示弹幕") {
            openOverlaySettings()
        }
        dndStatus = permissionRow(card, "免打扰权限", "授权后前台通知在勿扰模式下也不受限（可选）") {
            openDndAccess()
        }
        batteryStatus = permissionRow(card, "后台运行", "忽略电池优化，避免服务被清理（可选）") {
            requestBatteryExemption()
        }

        card.addView(
            textView(
                "小米 / 红米、OPPO、vivo 等机型还需允许「后台弹出界面」与自启动。",
                11f,
                Palette.MUTED,
            ).apply {
                setPadding(0, dp(12), 0, 0)
                setLineSpacing(0f, 1.2f)
            },
        )
        return card
    }

    private fun sourceCard(): LinearLayout {
        val card = sectionCard("通知来源")
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            isClickable = true
            background = selectableBackground()
            setOnClickListener { openAppPicker() }
            setPadding(0, dp(6), 0, dp(6))
        }
        val column = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        column.addView(textView("接收哪些应用的通知", 14f, Palette.TEXT))
        sourceSummary = textView("", 11f, Palette.MUTED).apply { setPadding(0, dp(3), 0, 0) }
        column.addView(sourceSummary)
        row.addView(column, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        row.addView(textView("选择  ›", 13f, Palette.ACCENT).apply { setPadding(dp(10), dp(8), 0, dp(8)) })
        card.addView(row)

        card.addView(
            textView("默认仅接收微信与 QQ；可自由勾选任意已安装应用。", 11f, Palette.MUTED).apply {
                setPadding(0, dp(6), 0, 0)
            },
        )
        return card
    }

    private fun displayCard(): LinearLayout {
        val card = sectionCard("显示方式")

        val orientationRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            isClickable = true
            background = selectableBackground()
            setPadding(0, dp(8), 0, dp(8))
            setOnClickListener { showOrientationDialog() }
        }
        orientationRow.addView(
            textView("显示范围", 14f, Palette.TEXT),
            LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f),
        )
        orientationValue = textView("全部方向  ›", 13f, Palette.ACCENT)
        orientationRow.addView(orientationValue)
        card.addView(orientationRow)

        addSwitchRow(card, "仅亮屏时显示", "锁屏、息屏时不打扰", settings.onlyWhenScreenOn) { value ->
            update { it.copy(onlyWhenScreenOn = value) }
        }
        addSwitchRow(card, "过滤群聊汇总", "减少「N 条新消息」这类重复弹幕", settings.skipGroupSummary) { value ->
            update { it.copy(skipGroupSummary = value) }
        }
        addSwitchRow(card, "显示应用图标", "在弹幕左侧显示来源应用图标", settings.showAppIcon) { value ->
            update { it.copy(showAppIcon = value) }
        }

        addSwitchRow(
            card,
            "点击弹幕打开应用",
            "点中弹幕直达对应聊天；开启后弹幕区域会拦截触摸",
            settings.clickToOpen,
        ) { value ->
            update { it.copy(clickToOpen = value) }
            refreshUi()
        }

        floatingSwitch = addSwitchRow(
            card,
            "尝试小窗打开",
            "系统支持自由窗口时以小窗显示，否则正常全屏打开",
            settings.tryFloatingWindow,
        ) { value ->
            update { it.copy(tryFloatingWindow = value) }
        }
        return card
    }

    private fun appearanceCard(): LinearLayout {
        val card = sectionCard("外观与位置")

        addSliderRow(card, "垂直位置", { "$it% · 距屏幕顶部" }, 0, 70, settings.positionPercent) { value ->
            applyTransient { it.copy(positionPercent = value) }
        }
        addSliderRow(card, "弹幕行数", { "$it 行" }, 1, 8, settings.laneCount) { value ->
            applyTransient { it.copy(laneCount = value) }
        }
        addSliderRow(card, "透明度", { "$it%" }, 20, 100, settings.alphaPercent) { value ->
            applyTransient { it.copy(alphaPercent = value) }
        }
        addSliderRow(card, "字号", { "$it sp" }, 12, 28, settings.fontSp) { value ->
            applyTransient { it.copy(fontSp = value) }
        }
        addSliderRow(card, "速度", { "$it%" }, 50, 250, settings.speedPercent) { value ->
            applyTransient { it.copy(speedPercent = value) }
        }

        val reset = textView("恢复默认外观", 12f, Palette.ACCENT).apply {
            setPadding(0, dp(12), 0, dp(2))
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
                recreate()
            }
        }
        card.addView(reset)
        return card
    }

    private fun filterCard(): LinearLayout {
        val card = sectionCard("关键词过滤")
        card.addView(
            textView("包含关键词的消息不会显示，多个关键词用逗号分隔。", 11f, Palette.MUTED).apply {
                setPadding(0, dp(2), 0, dp(8))
            },
        )

        val edit = EditText(this).apply {
            setText(settings.blacklist)
            hint = "例如：广告, 砍价, 转账"
            setTextColor(Palette.TEXT)
            setHintTextColor(0xFF64748B.toInt())
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            background = roundedBackground(Palette.SURFACE_2, 14f)
            setPadding(dp(14), dp(12), dp(14), dp(12))
            minLines = 2
            gravity = Gravity.TOP or Gravity.START
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE
        }
        edit.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
            override fun afterTextChanged(s: Editable?) {
                SettingsStore.update({ it.copy(blacklist = s?.toString().orEmpty()) }, persist = false)
                settings = SettingsStore.settings.value
            }
        })
        card.addView(edit, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))
        return card
    }

    private fun guideCard(): LinearLayout {
        val card = sectionCard("使用说明")
        val steps = listOf(
            "开启「通知使用权」，并在系统列表中勾选「弹幕通知」",
            "授予「悬浮窗权限」，部分机型还需允许「后台弹出界面」",
            "选择通知来源应用，打开总开关",
            "点击弹幕可直接跳转；小窗能力取决于系统是否支持",
        )
        steps.forEachIndexed { index, text ->
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.TOP
                setPadding(0, dp(4), 0, dp(4))
            }
            val badge = TextView(this).apply {
                this.text = (index + 1).toString()
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 10f)
                setTextColor(Palette.ACCENT)
                gravity = Gravity.CENTER
                background = roundedBackground(0x225FE3F3, 9f)
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            }
            row.addView(badge, LinearLayout.LayoutParams(dp(18), dp(18)))
            row.addView(
                textView(text, 12f, Palette.MUTED).apply {
                    setPadding(dp(9), 0, 0, 0)
                    setLineSpacing(0f, 1.2f)
                },
                LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f),
            )
            card.addView(row)
        }

        card.addView(
            textView(
                "隐私：通知只在本机读取与显示，应用未申请联网权限。",
                11f,
                Palette.MUTED,
            ).apply { setPadding(0, dp(10), 0, 0) },
        )
        return card
    }

    private fun sectionCard(title: String): LinearLayout {
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = roundedBackground(Palette.SURFACE, 20f)
            setPadding(dp(16), dp(15), dp(16), dp(15))
        }
        card.addView(textView(title, 15f, Palette.TEXT, true))
        return card
    }

    private fun permissionRow(
        card: LinearLayout,
        title: String,
        description: String,
        action: () -> Unit,
    ): TextView {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            isClickable = true
            setOnClickListener { action() }
            setPadding(0, dp(11), 0, dp(3))
        }
        val column = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        column.addView(textView(title, 14f, Palette.TEXT))
        column.addView(
            textView(description, 11f, Palette.MUTED).apply {
                setPadding(0, dp(3), 0, 0)
                setLineSpacing(0f, 1.15f)
            },
        )
        row.addView(column, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))

        val status = textView("去开启", 12f, Palette.ACCENT).apply {
            setPadding(dp(10), dp(6), 0, dp(6))
        }
        row.addView(status)
        card.addView(row)
        return status
    }

    private fun addSwitchRow(
        card: LinearLayout,
        title: String,
        description: String,
        checked: Boolean,
        onChange: (Boolean) -> Unit,
    ): Switch {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(9), 0, 0)
        }
        val column = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        column.addView(textView(title, 14f, Palette.TEXT))
        column.addView(
            textView(description, 11f, Palette.MUTED).apply {
                setPadding(0, dp(3), 0, 0)
                setLineSpacing(0f, 1.15f)
            },
        )
        row.addView(column, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))

        val toggle = styledSwitch().apply {
            isChecked = checked
            setOnCheckedChangeListener { _, value ->
                if (!binding) onChange(value)
            }
        }
        row.addView(toggle, LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
            marginStart = dp(10)
        })
        card.addView(row)
        return toggle
    }

    private fun addSliderRow(
        card: LinearLayout,
        title: String,
        formatter: (Int) -> String,
        min: Int,
        max: Int,
        current: Int,
        onChange: (Int) -> Unit,
    ) {
        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(14), 0, 0)
        }
        header.addView(
            textView(title, 13f, Palette.MUTED),
            LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f),
        )
        val valueView = textView(formatter(current), 12f, Palette.ACCENT, true)
        header.addView(valueView)
        card.addView(header)

        val bar = SeekBar(this).apply {
            this.max = max - min
            progress = (current - min).coerceIn(0, max - min)
            progressTintList = ColorStateList.valueOf(Palette.ACCENT)
            thumbTintList = ColorStateList.valueOf(Palette.ACCENT)
            progressBackgroundTintList = ColorStateList.valueOf(Palette.SURFACE_2)
            setPadding(0, dp(2), 0, dp(2))
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                    if (!fromUser) return
                    val value = progress + min
                    valueView.text = formatter(value)
                    onChange(value)
                }

                override fun onStartTrackingTouch(seekBar: SeekBar?) = Unit

                override fun onStopTrackingTouch(seekBar: SeekBar?) {
                    SettingsStore.persist()
                }
            })
        }
        card.addView(bar, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))
    }

    private fun styledSwitch(): Switch = Switch(this).apply {
        val checkedStates = intArrayOf(android.R.attr.state_checked)
        val defaultStates = intArrayOf()
        thumbTintList = ColorStateList(
            arrayOf(checkedStates, defaultStates),
            intArrayOf(Palette.ACCENT, 0xFF8A97A6.toInt()),
        )
        trackTintList = ColorStateList(
            arrayOf(checkedStates, defaultStates),
            intArrayOf(0x665FE3F3, 0xFF2B3949.toInt()),
        )
    }

    private fun matchWrap(top: Int = 0) = LinearLayout.LayoutParams(
        LinearLayout.LayoutParams.MATCH_PARENT,
        LinearLayout.LayoutParams.WRAP_CONTENT,
    ).apply { topMargin = dp(top) }

    private fun selectableBackground(): Drawable? {
        val out = TypedValue()
        theme.resolveAttribute(android.R.attr.selectableItemBackground, out, true)
        return if (out.resourceId != 0) getDrawable(out.resourceId) else null
    }

    private fun refreshUi() {
        binding = true
        try {
            serviceSwitch.isChecked = settings.enabled
            statusView.text = if (settings.enabled) "● 运行中 · 弹幕已就绪" else "● 未开启"
            statusView.setTextColor(if (settings.enabled) Palette.SUCCESS else Palette.MUTED)

            setPermissionStatus(notificationStatus, hasNotificationAccess(), "已开启", "去开启")
            setPermissionStatus(overlayStatus, Settings.canDrawOverlays(this), "已开启", "去授予")
            setPermissionStatus(dndStatus, hasDndAccess(), "已授权", "去授权")
            setPermissionStatus(batteryStatus, isIgnoringBattery(), "已忽略", "去设置")

            sourceSummary.text = sourceSummaryText()
            orientationValue.text = settings.displayOrientation.label + "  ›"
            floatingSwitch.isChecked = settings.tryFloatingWindow
            floatingSwitch.isEnabled = settings.clickToOpen
            floatingSwitch.alpha = if (settings.clickToOpen) 1f else 0.4f
        } finally {
            binding = false
        }
    }

    private fun setPermissionStatus(view: TextView, granted: Boolean, grantedText: String, actionText: String) {
        view.text = if (granted) grantedText else actionText
        view.setTextColor(if (granted) Palette.SUCCESS else Palette.ACCENT)
    }

    private fun sourceSummaryText(): String {
        val labels = settings.selectedPackages.mapNotNull { labelFor(it) }.sorted()
        return when {
            labels.isEmpty() -> "未选择任何应用 · 不会显示弹幕"
            labels.size <= 2 -> labels.joinToString("、")
            else -> "${labels.take(2).joinToString("、")} 等 ${labels.size} 个应用"
        }
    }

    private fun labelFor(packageName: String): String? {
        labelCache[packageName]?.let { return it }
        return try {
            val info = packageManager.getApplicationInfo(packageName, 0)
            packageManager.getApplicationLabel(info).toString().also { labelCache[packageName] = it }
        } catch (_: Throwable) {
            null
        }
    }

    private fun setServiceEnabled(enable: Boolean) {
        if (!enable) {
            update { it.copy(enabled = false) }
            OverlayService.stop(this)
            refreshUi()
            return
        }
        if (!Settings.canDrawOverlays(this)) {
            toast("请先授予悬浮窗权限")
            openOverlaySettings()
            refreshUi()
            return
        }
        if (!hasNotificationAccess()) {
            toast("请先开启通知使用权，否则读不到消息")
            openNotificationAccess()
            refreshUi()
            return
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1)
        }
        update { it.copy(enabled = true) }
        OverlayService.start(this)
        refreshUi()
    }

    private fun update(transform: (AppSettings) -> AppSettings) {
        SettingsStore.update(transform)
        settings = SettingsStore.settings.value
    }

    private fun applyTransient(transform: (AppSettings) -> AppSettings) {
        SettingsStore.update(transform, persist = false)
        settings = SettingsStore.settings.value
    }

    private fun showOrientationDialog() {
        val labels = DisplayOrientation.entries.map { it.label }.toTypedArray()
        AlertDialog.Builder(this)
            .setTitle("显示范围")
            .setSingleChoiceItems(labels, settings.displayOrientation.ordinal) { dialog, which ->
                update { it.copy(displayOrientation = DisplayOrientation.entries[which]) }
                dialog.dismiss()
                refreshUi()
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun openAppPicker() {
        startActivity(Intent(this, AppPickerActivity::class.java))
    }

    private fun hasNotificationAccess(): Boolean =
        NotificationManagerCompat.getEnabledListenerPackages(this).contains(packageName)

    private fun hasDndAccess(): Boolean =
        getSystemService(NotificationManager::class.java)?.isNotificationPolicyAccessGranted == true

    private fun isIgnoringBattery(): Boolean {
        val powerManager = getSystemService(Context.POWER_SERVICE) as? PowerManager ?: return false
        return powerManager.isIgnoringBatteryOptimizations(packageName)
    }

    private fun openNotificationAccess() {
        try {
            startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
        } catch (_: Throwable) {
            try {
                startActivity(Intent(Settings.ACTION_SETTINGS))
            } catch (_: Throwable) {
                // ignore
            }
        }
    }

    private fun openOverlaySettings() {
        try {
            startActivity(
                Intent(
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:$packageName"),
                ),
            )
        } catch (_: Throwable) {
            try {
                startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION))
            } catch (_: Throwable) {
                // ignore
            }
        }
    }

    private fun openDndAccess() {
        try {
            startActivity(Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS))
        } catch (_: Throwable) {
            toast("当前系统没有免打扰授权页面")
        }
    }

    private fun requestBatteryExemption() {
        try {
            startActivity(
                Intent(
                    Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                    Uri.parse("package:$packageName"),
                ),
            )
        } catch (_: Throwable) {
            try {
                startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
            } catch (_: Throwable) {
                // ignore
            }
        }
    }

    private fun toast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }
}

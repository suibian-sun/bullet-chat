package com.suibian.bulletchat

import android.app.Activity
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.res.ColorStateList
import android.os.Bundle
import android.text.Editable
import android.text.TextUtils
import android.text.TextWatcher
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.Switch
import android.widget.TextView
import com.suibian.bulletchat.data.ChatPackages
import com.suibian.bulletchat.data.SettingsStore
import com.suibian.bulletchat.ui.Palette
import com.suibian.bulletchat.ui.dp
import com.suibian.bulletchat.ui.roundedBackground
import com.suibian.bulletchat.ui.textView
import com.suibian.bulletchat.util.AppIconLoader
import java.text.Collator
import java.util.Locale

/**
 * 通知来源选择器：列出可启动的已安装应用，支持搜索、全选、清空与恢复默认。
 * 勾选结果写回 SettingsStore.selectedPackages。
 */
class AppPickerActivity : Activity() {

    private data class AppEntry(
        val packageName: String,
        val label: String,
        val system: Boolean,
    )

    private lateinit var listView: ListView
    private lateinit var countView: TextView
    private lateinit var emptyView: TextView
    private lateinit var adapter: AppAdapter

    private val allApps = ArrayList<AppEntry>()
    private val filtered = ArrayList<AppEntry>()
    private val selected = LinkedHashSet<String>()

    private var loaded = false
    private var query: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        SettingsStore.init(this)
        selected.addAll(SettingsStore.settings.value.selectedPackages)

        window.statusBarColor = Palette.BG
        window.navigationBarColor = Palette.BG

        setContentView(createContentView())
        reloadFilter("")
        loadInstalledApps()
    }

    override fun onPause() {
        SettingsStore.persist()
        super.onPause()
    }

    private fun createContentView(): View {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Palette.BG)
        }

        // 顶部导航
        val topBar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(16), dp(12), dp(16), dp(10))
        }
        val back = textView("‹ 返回", 14f, Palette.ACCENT).apply {
            setPadding(0, dp(6), dp(10), dp(6))
            isClickable = true
            setOnClickListener { finish() }
        }
        topBar.addView(back)
        topBar.addView(
            textView("通知来源", 17f, Palette.TEXT, true),
            LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                marginStart = dp(6)
            },
        )
        val done = textView("完成", 14f, Palette.ACCENT, true).apply {
            setPadding(dp(12), dp(6), 0, dp(6))
            isClickable = true
            setOnClickListener { finish() }
        }
        topBar.addView(done)
        root.addView(topBar, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))

        // 搜索框
        val search = EditText(this).apply {
            hint = "搜索应用名称或包名"
            setSingleLine()
            ellipsize = TextUtils.TruncateAt.END
            setTextColor(Palette.TEXT)
            setHintTextColor(0xFF5D6B7C.toInt())
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            background = roundedBackground(Palette.SURFACE_2, 14f)
            setPadding(dp(14), dp(11), dp(14), dp(11))
            addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
                override fun afterTextChanged(s: Editable?) = reloadFilter(s?.toString().orEmpty())
            })
        }
        root.addView(
            search,
            LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                leftMargin = dp(16)
                rightMargin = dp(16)
            },
        )

        // 统计与快捷操作
        val actions = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(16), dp(10), dp(16), dp(8))
        }
        countView = textView("", 12f, Palette.MUTED)
        actions.addView(countView, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        actions.addView(chip("全选") { selectAll(true) })
        actions.addView(chip("清空") { selectAll(false) })
        actions.addView(chip("默认") { restoreDefault() })
        root.addView(actions, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))

        // 列表
        listView = ListView(this).apply {
            divider = roundedBackground(0x1A2B3949, 0f)
            dividerHeight = dp(1)
            setPadding(dp(8), 0, dp(8), dp(8))
            clipToPadding = false
            isVerticalScrollBarEnabled = false
            setBackgroundColor(Palette.BG)
        }
        adapter = AppAdapter()
        listView.adapter = adapter
        listView.setOnItemClickListener { _, _, position, _ -> toggle(filtered[position].packageName) }

        emptyView = textView("正在读取应用列表…", 13f, Palette.MUTED).apply {
            gravity = Gravity.CENTER
            setPadding(0, dp(60), 0, 0)
        }
        listView.emptyView = emptyView
        root.addView(emptyView, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))
        root.addView(
            listView,
            LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f),
        )

        // 底部提示
        root.addView(
            textView("勾选后仅这些应用的通知会变成弹幕；建议只保留聊天类应用。", 11f, Palette.MUTED).apply {
                setPadding(dp(18), dp(8), dp(18), dp(14))
                setLineSpacing(0f, 1.2f)
            },
        )

        updateCount()
        return root
    }

    private fun chip(label: String, onClick: () -> Unit): TextView = textView(label, 12f, Palette.ACCENT).apply {
        setPadding(dp(12), dp(6), dp(12), dp(6))
        background = roundedBackground(0x1F5FE3F3, 11f)
        isClickable = true
        setOnClickListener { onClick() }
        val lp = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT,
            LinearLayout.LayoutParams.WRAP_CONTENT,
        )
        lp.marginStart = dp(6)
        layoutParams = lp
    }

    private fun loadInstalledApps() {
        Thread {
            val pm = packageManager
            val map = LinkedHashMap<String, AppEntry>()

            runCatching {
                val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
                pm.queryIntentActivities(intent, 0).forEach { resolved ->
                    val info = resolved.activityInfo?.applicationInfo ?: return@forEach
                    val pkg = info.packageName
                    if (pkg == packageName || pkg in map) return@forEach
                    val label = runCatching { info.loadLabel(pm).toString() }.getOrNull()
                        ?.takeIf { it.isNotBlank() } ?: pkg
                    val system = (info.flags and (ApplicationInfo.FLAG_SYSTEM or ApplicationInfo.FLAG_UPDATED_SYSTEM_APP)) != 0
                    map[pkg] = AppEntry(pkg, label, system)
                }
            }

            // 已选中但不出现在启动器列表里的应用也要能显示出来
            selected.forEach { pkg ->
                if (pkg !in map && pkg != packageName) {
                    val label = runCatching {
                        pm.getApplicationInfo(pkg, 0).loadLabel(pm).toString()
                    }.getOrNull()
                    if (label != null) map[pkg] = AppEntry(pkg, label, false)
                }
            }

            val collator = Collator.getInstance(Locale.CHINA)
            val sorted = map.values.sortedWith { a, b -> collator.compare(a.label, b.label) }

            runOnUiThread {
                allApps.clear()
                allApps.addAll(sorted)
                loaded = true
                reloadFilter(query)
            }
        }.start()
    }

    private fun reloadFilter(query: String) {
        this.query = query.trim()
        val q = this.query
        filtered.clear()
        if (q.isEmpty()) {
            filtered.addAll(allApps)
        } else {
            allApps.forEach {
                if (it.label.contains(q, true) || it.packageName.contains(q, true)) filtered.add(it)
            }
        }
        adapter.notifyDataSetChanged()
        emptyView.text = when {
            !loaded -> "正在读取应用列表…"
            q.isNotEmpty() -> "没有匹配的应用"
            else -> "没有可选择的已安装应用"
        }
    }

    private fun toggle(packageName: String) {
        if (!selected.remove(packageName)) selected.add(packageName)
        adapter.notifyDataSetChanged()
        updateCount()
        applyToStore()
    }

    private fun selectAll(value: Boolean) {
        if (value) {
            // 只对当前筛选结果生效，避免误选全部
            val pool = if (filtered.isEmpty()) allApps else filtered
            pool.forEach { selected.add(it.packageName) }
        } else {
            val pool = if (filtered.isEmpty()) allApps else filtered
            pool.forEach { selected.remove(it.packageName) }
        }
        adapter.notifyDataSetChanged()
        updateCount()
        applyToStore()
    }

    private fun restoreDefault() {
        selected.clear()
        selected.addAll(ChatPackages.DEFAULT_SELECTED)
        adapter.notifyDataSetChanged()
        updateCount()
        applyToStore()
        SettingsStore.persist()
    }

    private fun applyToStore() {
        val snapshot = LinkedHashSet(selected)
        SettingsStore.update({ it.copy(selectedPackages = snapshot) }, persist = false)
    }

    private fun updateCount() {
        countView.text = if (selected.isEmpty()) {
            "未选择应用 · 不会显示弹幕"
        } else {
            "已选 ${selected.size} 个应用"
        }
        countView.setTextColor(if (selected.isEmpty()) Palette.WARN else Palette.MUTED)
    }

    private inner class AppAdapter : BaseAdapter() {
        override fun getCount(): Int = filtered.size
        override fun getItem(position: Int): AppEntry = filtered[position]
        override fun getItemId(position: Int): Long = filtered[position].packageName.hashCode().toLong()

        override fun getView(position: Int, convertView: View?, parent: ViewGroup?): View {
            val row: LinearLayout
            val holder: Holder
            if (convertView == null) {
                row = LinearLayout(this@AppPickerActivity).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                    setPadding(dp(10), dp(9), dp(10), dp(9))
                }
                val icon = ImageView(this@AppPickerActivity).apply {
                    background = roundedBackground(0x14FFFFFF, 10f)
                    setPadding(dp(3), dp(3), dp(3), dp(3))
                }
                row.addView(icon, LinearLayout.LayoutParams(dp(34), dp(34)))

                val column = LinearLayout(this@AppPickerActivity).apply {
                    orientation = LinearLayout.VERTICAL
                }
                val label = textView("", 14f, Palette.TEXT).apply {
                    setSingleLine()
                    ellipsize = TextUtils.TruncateAt.END
                }
                val pkg = textView("", 10f, Palette.MUTED).apply {
                    setSingleLine()
                    ellipsize = TextUtils.TruncateAt.END
                    setPadding(0, dp(3), 0, 0)
                }
                column.addView(label)
                column.addView(pkg)
                row.addView(
                    column,
                    LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                        marginStart = dp(11)
                        marginEnd = dp(8)
                    },
                )

                val toggle = Switch(this@AppPickerActivity).apply {
                    isClickable = false
                    isFocusable = false
                    val on = intArrayOf(android.R.attr.state_checked)
                    val off = intArrayOf()
                    thumbTintList = ColorStateList(arrayOf(on, off), intArrayOf(Palette.ACCENT, 0xFF8A97A6.toInt()))
                    trackTintList = ColorStateList(arrayOf(on, off), intArrayOf(0x665FE3F3, 0xFF2B3949.toInt()))
                }
                row.addView(toggle)

                holder = Holder(icon, label, pkg, toggle)
                row.tag = holder
            } else {
                row = convertView as LinearLayout
                holder = row.tag as Holder
            }

            val entry = filtered[position]
            holder.label.text = entry.label
            holder.pkg.text = entry.packageName
            holder.toggle.isChecked = entry.packageName in selected
            holder.label.alpha = 1f
            holder.icon.setImageBitmap(AppIconLoader.load(this@AppPickerActivity, entry.packageName, 34))
            return row
        }
    }

    private class Holder(
        val icon: ImageView,
        val label: TextView,
        val pkg: TextView,
        val toggle: Switch,
    )
}

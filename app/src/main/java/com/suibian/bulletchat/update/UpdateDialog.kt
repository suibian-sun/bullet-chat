package com.suibian.bulletchat.update

import android.app.Activity
import android.app.Dialog
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.view.WindowManager
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import com.suibian.bulletchat.BuildConfig
import com.suibian.bulletchat.R
import com.suibian.bulletchat.ui.ACCENT_GRADIENT
import com.suibian.bulletchat.ui.Palette
import com.suibian.bulletchat.ui.dp
import com.suibian.bulletchat.ui.glass
import com.suibian.bulletchat.ui.pressScale
import com.suibian.bulletchat.ui.textView
import com.suibian.bulletchat.util.Permissions

/**
 * 新版本弹窗：展示更新内容，提供「关闭」与「立即更新」。
 * 点击立即更新后在同一位置显示下载进度条，完成后调起系统安装器。
 */
class UpdateDialog(
    private val activity: Activity,
    private val info: ReleaseInfo,
) : Dialog(activity) {

    private lateinit var actionRow: LinearLayout
    private lateinit var progressArea: LinearLayout
    private lateinit var progressBar: ProgressBar
    private lateinit var progressText: TextView
    private lateinit var progressHint: TextView
    private var downloading = false

    init {
        requestWindowFeature(Window.FEATURE_NO_TITLE)
        setContentView(buildContent())
        window?.apply {
            setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            setGravity(Gravity.CENTER)
            addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
            setDimAmount(0.72f)
        }
        setCanceledOnTouchOutside(true)
    }

    override fun show() {
        super.show()
        window?.decorView?.let { decor ->
            decor.alpha = 0f
            decor.scaleX = 0.9f
            decor.scaleY = 0.9f
            decor.animate()
                .alpha(1f)
                .scaleX(1f)
                .scaleY(1f)
                .setDuration(240L)
                .setInterpolator(android.view.animation.DecelerateInterpolator())
                .start()
        }
    }

    private fun buildContent(): View {
        val container = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(activity.dp(18), activity.dp(18), activity.dp(18), activity.dp(18))
        }

        val card = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            background = activity.glass(28f, 0xFA0A1422.toInt(), 0x26FFFFFF)
            setPadding(activity.dp(22), activity.dp(22), activity.dp(22), activity.dp(20))
            elevation = activity.dp(18).toFloat()
        }

        val badge = ImageView(activity).apply {
            setImageResource(R.drawable.ic_download)
            setPadding(activity.dp(11), activity.dp(11), activity.dp(11), activity.dp(11))
            background = GradientDrawable(
                GradientDrawable.Orientation.TL_BR, ACCENT_GRADIENT,
            ).apply { shape = GradientDrawable.OVAL }
            colorFilter = android.graphics.PorterDuffColorFilter(Color.WHITE, android.graphics.PorterDuff.Mode.SRC_IN)
        }
        card.addView(badge, LinearLayout.LayoutParams(activity.dp(46), activity.dp(46)))

        card.addView(
            activity.textView("发现新版本 ${info.version}", 20f, Palette.TEXT, true).apply {
                setPadding(0, activity.dp(16), 0, 0)
            },
        )
        card.addView(
            activity.textView(
                "当前 v${BuildConfig.VERSION_NAME} · ${UpdateInstaller.formatSize(info.apkSize)}",
                11f,
                Palette.MUTED,
            ).apply { setPadding(0, activity.dp(7), 0, 0) },
        )

        card.addView(
            activity.textView("更新内容", 11f, Palette.ACCENT, true).apply {
                letterSpacing = 0.1f
                setPadding(0, activity.dp(18), 0, 0)
            },
        )

        val notesView = activity.textView(info.notes.trim(), 12f, Palette.TEXT_DIM).apply {
            setLineSpacing(activity.dp(4).toFloat(), 1.35f)
        }
        val notesScroll = ScrollView(activity).apply {
            isVerticalScrollBarEnabled = false
            addView(notesView, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
            background = activity.glass(16f, 0xFF0F1B29.toInt(), 0x12FFFFFF)
            setPadding(activity.dp(14), activity.dp(12), activity.dp(14), activity.dp(12))
        }
        card.addView(
            notesScroll,
            LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, activity.dp(190)).apply {
                topMargin = activity.dp(10)
            },
        )

        // 进度区（点击更新后显示）
        progressArea = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            visibility = View.GONE
            setPadding(0, activity.dp(16), 0, 0)
        }
        progressBar = ProgressBar(activity, null, android.R.attr.progressBarStyleHorizontal).apply {
            max = 100
            progress = 0
            progressTintList = ColorStateList.valueOf(Palette.ACCENT)
            progressBackgroundTintList = ColorStateList.valueOf(0xFF1B2836.toInt())
        }
        progressArea.addView(
            progressBar,
            LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, activity.dp(6)),
        )
        progressText = activity.textView("正在下载 0%", 12f, Palette.ACCENT, true).apply {
            setPadding(0, activity.dp(10), 0, 0)
        }
        progressArea.addView(progressText)
        progressHint = activity.textView("", 11f, Palette.MUTED).apply {
            setPadding(0, activity.dp(4), 0, 0)
        }
        progressArea.addView(progressHint)
        card.addView(progressArea)

        // 按钮行
        actionRow = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, activity.dp(20), 0, 0)
        }

        val close = activity.textView("关闭", 14f, Palette.TEXT_DIM, true).apply {
            gravity = Gravity.CENTER
            background = activity.glass(16f, 0xFF152232.toInt(), 0x1FFFFFFF)
            isClickable = true
            pressScale()
            setOnClickListener { dismiss() }
        }
        actionRow.addView(
            close,
            LinearLayout.LayoutParams(0, activity.dp(46), 1f).apply { marginEnd = activity.dp(5) },
        )

        val update = activity.textView("立即更新", 14f, 0xFF06202A.toInt(), true).apply {
            gravity = Gravity.CENTER
            background = GradientDrawable(
                GradientDrawable.Orientation.TL_BR, ACCENT_GRADIENT,
            ).apply { cornerRadius = activity.dp(16).toFloat() }
            isClickable = true
            pressScale()
            setOnClickListener { startUpdate() }
        }
        actionRow.addView(
            update,
            LinearLayout.LayoutParams(0, activity.dp(46), 1f).apply { marginStart = activity.dp(5) },
        )
        card.addView(actionRow)

        container.addView(card)
        return container
    }

    private fun startUpdate() {
        if (downloading) return
        if (info.apkUrl.isBlank()) {
            Toast.makeText(activity, "该版本没有可下载的 APK 资源", Toast.LENGTH_SHORT).show()
            return
        }
        if (!Permissions.canInstallPackages(activity)) {
            Toast.makeText(activity, "请先允许「安装未知应用」", Toast.LENGTH_LONG).show()
            Permissions.openInstallPermission(activity)
            return
        }

        downloading = true
        actionRow.visibility = View.GONE
        progressArea.visibility = View.VISIBLE
        progressBar.progress = 0
        progressText.text = "正在下载 0%"
        progressHint.text = "下载完成后会自动调起系统安装器"

        Thread {
            val result = runCatching {
                UpdateInstaller.download(activity, info.apkUrl, info.version) { percent, done, total ->
                    activity.runOnUiThread {
                        if (!isShowing) return@runOnUiThread
                        if (percent >= 0) {
                            progressBar.progress = percent
                            progressText.text = "正在下载 $percent%"
                        } else {
                            progressText.text = "正在下载…"
                        }
                        progressHint.text = if (total > 0) {
                            "${UpdateInstaller.formatSize(done)} / ${UpdateInstaller.formatSize(total)}"
                        } else {
                            UpdateInstaller.formatSize(done)
                        }
                    }
                }
            }
            activity.runOnUiThread {
                downloading = false
                result.onSuccess { file ->
                    progressBar.progress = 100
                    progressText.text = "下载完成"
                    progressHint.text = "正在调起系统安装器…"
                    runCatching { UpdateInstaller.install(activity, file) }
                        .onFailure {
                            progressText.text = "无法启动安装器"
                            progressHint.text = "请确认已允许「安装未知应用」后重试"
                            actionRow.visibility = View.VISIBLE
                        }
                    dismiss()
                }.onFailure { error ->
                    progressText.text = "下载失败"
                    progressHint.text = "请检查网络后重试（${error.message ?: "未知错误"}）"
                    actionRow.visibility = View.VISIBLE
                }
            }
        }.start()
    }
}

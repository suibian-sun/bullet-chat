package com.suibian.bulletchat

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.animation.DecelerateInterpolator
import android.widget.FrameLayout
import android.widget.Toast
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.suibian.bulletchat.data.SettingsStore
import com.suibian.bulletchat.data.StatsStore
import com.suibian.bulletchat.service.OverlayService
import com.suibian.bulletchat.ui.AuroraDrawable
import com.suibian.bulletchat.ui.HomePage
import com.suibian.bulletchat.ui.LaunchPage
import com.suibian.bulletchat.ui.NavBar
import com.suibian.bulletchat.ui.SettingsPage
import com.suibian.bulletchat.ui.dp
import com.suibian.bulletchat.update.ReleaseInfo
import com.suibian.bulletchat.update.UpdateChecker
import com.suibian.bulletchat.update.UpdateDialog
import com.suibian.bulletchat.util.Permissions

/**
 * 单 Activity + 三页容器：主页 / 启动 / 设置，底部导航中间为圆形启动按钮。
 */
class MainActivity : Activity() {

    private lateinit var content: FrameLayout
    private lateinit var nav: NavBar
    private lateinit var home: HomePage
    private lateinit var launch: LaunchPage
    private lateinit var settings: SettingsPage

    private var current = -1

    private val pageViews: List<View> by lazy { listOf(home.view, launch.view, settings.view) }
    private var checkingUpdate = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        SettingsStore.init(this)
        StatsStore.init(this)
        UpdateChecker.init(this)
        applyEdgeToEdge()

        val root = FrameLayout(this).apply { background = AuroraDrawable() }
        content = FrameLayout(this)
        root.addView(
            content,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT,
            ),
        )

        home = HomePage(this)
        launch = LaunchPage(
            activity = this,
            onToggle = { toggleService() },
            onPermissionClick = { openPermission(it) },
            onTest = { sendTestDanmaku() },
        )
        settings = SettingsPage(
            activity = this,
            onOpenAppPicker = { startActivity(Intent(this, AppPickerActivity::class.java)) },
            onOpenChangelog = { startActivity(Intent(this, ChangelogActivity::class.java)) },
            onCheckUpdate = { checkUpdate(manual = true) },
            onOpenPermission = { openPermission(it) },
            onSettingsChanged = { refreshAll() },
        )

        listOf(home.view, launch.view, settings.view).forEach { page ->
            content.addView(
                page,
                FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.MATCH_PARENT,
                ),
            )
        }

        nav = NavBar(this) { select(it) }
        // 中间圆形按钮：进入启动页并直接启停弹幕，符合「启动按钮」的直觉
        nav.fab.setOnClickListener {
            select(1)
            toggleService()
        }
        root.addView(
            nav,
            FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, dp(88)).apply {
                gravity = Gravity.BOTTOM
            },
        )

        setContentView(root)

        ViewCompat.setOnApplyWindowInsetsListener(root) { _, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            content.setPadding(0, bars.top, 0, dp(96) + bars.bottom)
            (nav.layoutParams as FrameLayout.LayoutParams).bottomMargin = bars.bottom + dp(6)
            nav.requestLayout()
            insets
        }

        select(0)
    }

    override fun onResume() {
        super.onResume()
        refreshAll()
        if (SettingsStore.settings.value.enabled) OverlayService.refreshChannel(this)
        if (current == 0) home.onVisible()
        if (UpdateChecker.shouldAutoCheck()) {
            checkUpdate(manual = false)
        }
    }

    override fun onPause() {
        SettingsStore.persist()
        home.onHidden()
        super.onPause()
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray,
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        refreshAll()
    }

    private fun applyEdgeToEdge() {
        window.statusBarColor = Color.TRANSPARENT
        window.navigationBarColor = Color.TRANSPARENT
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            window.isNavigationBarContrastEnforced = false
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            window.setDecorFitsSystemWindows(false)
        } else {
            @Suppress("DEPRECATION")
            window.decorView.systemUiVisibility =
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
        }
    }

    private fun select(target: Int) {
        if (target == current && pageViews[target].visibility == View.VISIBLE) {
            nav.setSelected(target)
            return
        }
        val previous = current
        current = target
        nav.setSelected(target)

        pageViews.forEachIndexed { index, page ->
            page.animate().cancel()
            if (index == target) {
                if (page.visibility != View.VISIBLE) {
                    page.visibility = View.VISIBLE
                    page.alpha = 0f
                    page.translationY = dp(22).toFloat()
                    page.scaleX = 0.985f
                    page.scaleY = 0.985f
                }
                page.bringToFront()
                page.animate()
                    .alpha(1f)
                    .translationY(0f)
                    .scaleX(1f)
                    .scaleY(1f)
                    .setDuration(320L)
                    .setInterpolator(DecelerateInterpolator())
                    .start()
            } else if (page.visibility == View.VISIBLE) {
                val goingLeft = index < target
                page.animate()
                    .alpha(0f)
                    .translationY((if (goingLeft) -dp(12) else dp(12)).toFloat())
                    .scaleX(0.99f)
                    .scaleY(0.99f)
                    .setDuration(160L)
                    .setInterpolator(DecelerateInterpolator())
                    .withEndAction {
                        if (current != index) {
                            page.visibility = View.GONE
                            page.alpha = 1f
                            page.translationY = 0f
                            page.scaleX = 1f
                            page.scaleY = 1f
                        }
                    }
                    .start()
            }
        }

        if (target == 0) home.onVisible() else home.onHidden()
        when (target) {
            0 -> {
                home.refresh()
                home.playIntro()
            }
            1 -> {
                launch.refresh()
                launch.playIntro()
            }
            2 -> {
                settings.refresh()
                settings.playIntro()
            }
        }
        nav.setFabRunning(SettingsStore.settings.value.enabled)
        if (previous < 0) {
            // 首帧：整体轻微放大，避免冷启动生硬
            nav.alpha = 0f
            nav.translationY = dp(16).toFloat()
            nav.animate().alpha(1f).translationY(0f).setDuration(380L)
                .setInterpolator(DecelerateInterpolator()).start()
        }
    }

    private fun refreshAll() {
        home.refresh()
        launch.refresh()
        settings.refresh()
        nav.setFabRunning(SettingsStore.settings.value.enabled)
    }

    private fun toggleService() {
        val enabled = SettingsStore.settings.value.enabled
        if (enabled) {
            SettingsStore.update({ it.copy(enabled = false) })
            OverlayService.stop(this)
            refreshAll()
            toast("弹幕已停止")
            return
        }
        if (!Permissions.canDrawOverlays(this)) {
            toast("请先授予悬浮窗权限")
            Permissions.openOverlaySettings(this)
            return
        }
        if (!Permissions.hasNotificationAccess(this)) {
            toast("请先开启通知使用权，否则读不到消息")
            Permissions.openNotificationAccess(this)
            return
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1)
        }
        SettingsStore.update({ it.copy(enabled = true) })
        OverlayService.start(this)
        refreshAll()
        toast("弹幕已开启")
    }

    private fun sendTestDanmaku() {
        if (!SettingsStore.settings.value.enabled) {
            toast("请先开启弹幕，再发送测试")
            return
        }
        if (!Permissions.canDrawOverlays(this)) {
            toast("请先授予悬浮窗权限")
            Permissions.openOverlaySettings(this)
            return
        }
        OverlayService.sendTest(this)
        toast("正在发送测试弹幕")
    }

    private fun openPermission(index: Int) {
        when (index) {
            0 -> Permissions.openNotificationAccess(this)
            1 -> Permissions.openOverlaySettings(this)
            2 -> Permissions.openDndAccess(this)
            3 -> Permissions.requestBatteryExemption(this)
        }
    }

    private fun checkUpdate(manual: Boolean) {
        if (checkingUpdate) return
        checkingUpdate = true
        if (manual) settings.setUpdateStatus("检查中…")

        Thread {
            val result = UpdateChecker.fetchLatest()
            runOnUiThread {
                checkingUpdate = false
                UpdateChecker.markChecked()
                result.onSuccess { info: ReleaseInfo ->
                    val newer = UpdateChecker.isNewer(info.version, BuildConfig.VERSION_NAME)
                    if (newer) {
                        settings.setUpdateStatus("发现 v${info.version}")
                        if (!isFinishing) UpdateDialog(this, info).show()
                    } else {
                        settings.setUpdateStatus("已是最新")
                        if (manual) toast("当前已是最新版本")
                    }
                }.onFailure {
                    settings.setUpdateStatus("检查失败")
                    if (manual) toast("检查更新失败，请检查网络")
                }
            }
        }.start()
    }

    private fun toast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }
}

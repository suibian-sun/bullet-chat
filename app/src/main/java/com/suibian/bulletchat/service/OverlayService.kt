package com.suibian.bulletchat.service

import android.annotation.SuppressLint
import android.app.ActivityOptions
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.ComponentCallbacks2
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.content.res.Configuration
import android.graphics.PixelFormat
import android.graphics.Rect
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.provider.Settings
import android.util.Log
import android.util.TypedValue
import android.view.Gravity
import android.view.WindowManager
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.suibian.bulletchat.MainActivity
import com.suibian.bulletchat.R
import com.suibian.bulletchat.data.AppSettings
import com.suibian.bulletchat.data.ChatPackages
import com.suibian.bulletchat.data.SettingsStore
import com.suibian.bulletchat.data.StatsStore
import com.suibian.bulletchat.model.DanmakuMessage
import com.suibian.bulletchat.ui.DanmakuView
import com.suibian.bulletchat.util.AppIconLoader
import com.suibian.bulletchat.util.Permissions
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.max

/**
 * 前台服务：维护一条与弹幕带等高的悬浮窗。
 * 窗口更小 -> Surface 显存更低；点击仅在弹幕带内生效，带外触摸仍可穿透。
 */
class OverlayService : Service() {

    companion object {
        private const val TAG = "OverlayService"
        private const val PREFS = "bullet_chat_service"
        private const val KEY_CHANNEL = "active_channel_id"
        private const val KEY_BYPASS = "channel_bypass_dnd"
        private const val FALLBACK_CHANNEL = "bullet_chat_service_fallback"
        private const val NOTIFICATION_ID = 1001

        @Volatile
        var isRunning: Boolean = false
            private set

        const val ACTION_START = "com.suibian.bulletchat.action.START"
        const val ACTION_STOP = "com.suibian.bulletchat.action.STOP"
        const val ACTION_TEST = "com.suibian.bulletchat.action.TEST"
        const val ACTION_REFRESH = "com.suibian.bulletchat.action.REFRESH"

        fun start(context: Context) {
            val intent = Intent(context, OverlayService::class.java).setAction(ACTION_START)
            try {
                ContextCompat.startForegroundService(context, intent)
            } catch (t: Throwable) {
                Log.w(TAG, "启动弹幕服务失败", t)
            }
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, OverlayService::class.java))
        }

        fun sendTest(context: Context) {
            val intent = Intent(context, OverlayService::class.java).setAction(ACTION_TEST)
            try {
                ContextCompat.startForegroundService(context, intent)
            } catch (t: Throwable) {
                Log.w(TAG, "发送测试弹幕失败", t)
            }
        }

        /** 权限变化后重建渠道；服务未运行时不做任何事，避免把服务拉起来。 */
        fun refreshChannel(context: Context) {
            if (!isRunning) return
            val intent = Intent(context, OverlayService::class.java).setAction(ACTION_REFRESH)
            try {
                context.startService(intent)
            } catch (_: Throwable) {
                // ignore
            }
        }
    }

    private lateinit var windowManager: WindowManager
    private var danmakuView: DanmakuView? = null
    private val handler = Handler(Looper.getMainLooper())
    private val applyLayout = Runnable { updateOverlay() }
    private var settings: AppSettings = AppSettings()
    private var receiverRegistered = false
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val density: Float by lazy { resources.displayMetrics.density }
    private val spToPx: Float by lazy {
        TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, 1f, resources.displayMetrics)
    }

    private val screenReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                Intent.ACTION_SCREEN_OFF -> danmakuView?.pauseRendering()
                Intent.ACTION_SCREEN_ON -> danmakuView?.resumeRendering()
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        SettingsStore.init(this)
        StatsStore.init(this)
        StatsStore.sessionStart()
        isRunning = true
        settings = SettingsStore.settings.value
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager

        createNotificationChannel()
        startAsForeground()
        registerScreenReceiver()
        observeSettings()

        if (isOrientationAllowed(settings)) {
            attachOverlay()
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                SettingsStore.update({ it.copy(enabled = false) })
                stopSelf()
                return START_NOT_STICKY
            }
            ACTION_START -> {
                // 服务在跑但悬浮层没挂上（例如权限刚恢复）：补挂一次
                if (danmakuView == null && settings.enabled) attachOverlay()
            }
            ACTION_TEST -> sendTestDanmaku()
            ACTION_REFRESH -> {
                createNotificationChannel()
                startAsForeground()
            }
        }
        return START_STICKY
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        settings = SettingsStore.settings.value
        if (!settings.enabled) return

        // 注意：这里必须用 newConfig 的方向，resources.configuration 可能仍是旧值
        if (!settings.displayOrientation.matches(newConfig.orientation)) {
            detachOverlay()
            return
        }

        if (danmakuView == null) {
            attachOverlay(newConfig.orientation)
        } else {
            // 保留视图，只更新窗口尺寸/位置；DanmakuView 会按新尺寸重排已有弹幕
            updateOverlay()
            // 个别 ROM 在回调时刻仍返回旧度量，稍后再校正一次
            handler.postDelayed({ updateOverlay() }, 150L)
        }
    }

    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        if (level >= ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW) {
            AppIconLoader.clear()
            danmakuView?.clearAll()
            Log.d(TAG, "内存偏低，清理图标与弹幕缓存")
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun isOrientationAllowed(value: AppSettings): Boolean =
        value.displayOrientation.matches(resources.configuration.orientation)

    private fun screenHeight(): Int =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            windowManager.currentWindowMetrics.bounds.height()
        } else {
            resources.displayMetrics.heightPixels
        }

    /** 弹幕带最多占屏幕 62%；屏幕矮（横屏）时自动减少有效轨道，避免行高被压到文字重叠。 */
    private fun effectiveLanes(): Int {
        val lanePx = (settings.fontSp * spToPx * 1.55f).coerceAtLeast(1f)
        val maxBand = (screenHeight() * 0.62f).coerceAtLeast(dp(40).toFloat())
        val maxLanes = (maxBand / lanePx).toInt().coerceIn(1, 8)
        return settings.laneCount.coerceIn(1, 8).coerceAtMost(maxLanes)
    }

    private fun bandHeight(): Int {
        val lanePx = settings.fontSp * spToPx * 1.55f
        return (lanePx * effectiveLanes()).toInt().coerceAtLeast(dp(32))
    }

    private fun dp(value: Int): Int = (value * density).toInt()

    /** Android 8.0 起使用 TYPE_APPLICATION_OVERLAY，更低版本回落到 TYPE_PHONE。 */
    @Suppress("DEPRECATION")
    private fun overlayType(): Int =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            WindowManager.LayoutParams.TYPE_PHONE
        }

    private fun pendingFlags(): Int =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        } else {
            PendingIntent.FLAG_UPDATE_CURRENT
        }

    private fun buildLayoutParams(): WindowManager.LayoutParams {
        val screen = screenHeight()
        val height = bandHeight().coerceAtMost(screen)
        val y = (screen * settings.positionPercent.coerceIn(0, 70) / 100f)
            .toInt()
            .coerceIn(0, max(0, screen - height))

        var flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
            WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED
        flags = flags or if (settings.clickToOpen) {
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
        } else {
            WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
        }

        return WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            height,
            overlayType(),
            flags,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            this.y = y
        }
    }

    private fun attachOverlay(orientationOverride: Int? = null) {
        if (danmakuView != null) return
        if (!Permissions.canDrawOverlays(this)) {
            Log.w(TAG, "没有悬浮窗权限")
            return
        }
        val orientation = orientationOverride ?: resources.configuration.orientation
        if (!settings.displayOrientation.matches(orientation)) return

        val view = DanmakuView(this).apply {
            config = settings.toConfig()
            onDanmakuClick = { message -> openMessage(message) }
        }
        try {
            windowManager.addView(view, buildLayoutParams())
            danmakuView = view
            DanmakuBus.attach(view)
        } catch (t: Throwable) {
            Log.e(TAG, "添加悬浮层失败", t)
        }
    }

    private fun detachOverlay() {
        val view = danmakuView ?: return
        DanmakuBus.detach(view)
        view.pauseRendering()
        try {
            windowManager.removeView(view)
        } catch (_: Throwable) {
            // ignore
        }
        danmakuView = null
    }

    private fun updateOverlay() {
        val view = danmakuView ?: return
        view.config = settings.toConfig()
        try {
            windowManager.updateViewLayout(view, buildLayoutParams())
        } catch (t: Throwable) {
            Log.w(TAG, "更新悬浮层失败", t)
        }
    }

    private fun observeSettings() {
        scope.launch {
            SettingsStore.settings.collect { value ->
                settings = value
                if (!value.enabled) {
                    detachOverlay()
                    return@collect
                }
                if (!isOrientationAllowed(value)) {
                    detachOverlay()
                    return@collect
                }
                if (danmakuView == null) {
                    attachOverlay()
                } else {
                    handler.removeCallbacks(applyLayout)
                    handler.postDelayed(applyLayout, 60L)
                }
            }
        }
    }

    private fun startAsForeground() {
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
        } else {
            0
        }
        try {
            ServiceCompat.startForeground(this, NOTIFICATION_ID, buildNotification(), type)
        } catch (t: Throwable) {
            Log.e(TAG, "startForeground 失败，尝试无类型启动", t)
            try {
                ServiceCompat.startForeground(this, NOTIFICATION_ID, buildNotification(), 0)
            } catch (t2: Throwable) {
                Log.e(TAG, "startForeground 仍然失败", t2)
            }
        }
    }

    private fun buildNotification() = NotificationCompat.Builder(this, currentChannelId())
        .setSmallIcon(R.drawable.ic_notification)
        .setContentTitle("弹幕通知运行中")
        .setContentText("微信 / QQ 等应用的新消息将以弹幕显示")
        .setOngoing(true)
        .setShowWhen(false)
        .setCategory(NotificationCompat.CATEGORY_SERVICE)
        .setPriority(NotificationCompat.PRIORITY_LOW)
        .setContentIntent(
            PendingIntent.getActivity(
                this,
                11,
                Intent(this, MainActivity::class.java),
                pendingFlags(),
            ),
        )
        .addAction(
            0,
            "停止",
            PendingIntent.getService(
                this,
                12,
                Intent(this, OverlayService::class.java).setAction(ACTION_STOP),
                pendingFlags(),
            ),
        )
        .build()

    private fun currentChannelId(): String =
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            FALLBACK_CHANNEL
        } else {
            getSharedPreferences(PREFS, MODE_PRIVATE).getString(KEY_CHANNEL, FALLBACK_CHANNEL)
                ?: FALLBACK_CHANNEL
        }

    /**
     * 免打扰状态下，前台通知必须能绕过勿扰，否则部分 ROM 会把服务当作不可见后台服务限制。
     *
     * 注意：Android 的渠道重要性 / bypassDnd 一旦创建就不可修改，且「删除后用同名 ID 重建」
     * 会继承旧渠道的设置。因此这里按「是否已授权免打扰」生成一个全新的渠道 ID，
     * 授权状态变化时创建新渠道并删除旧的，确保 bypassDnd 真正生效。
     */
    private fun createNotificationChannel(): String {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return FALLBACK_CHANNEL

        val manager = getSystemService(NotificationManager::class.java)
            ?: return FALLBACK_CHANNEL
        val bypassDnd = Permissions.hasDndAccess(this)
        val prefs = getSharedPreferences(PREFS, MODE_PRIVATE)
        val savedId = prefs.getString(KEY_CHANNEL, null)

        // 以「上次创建渠道时的授权状态」为准。
        // 不能用 canBypassDnd() 判断：部分 ROM 即使 setBypassDnd(true) 成功也返回 false，
        // 那样每次刷新都会重建渠道。
        val savedBypass = prefs.getBoolean(KEY_BYPASS, false)
        if (savedId != null && savedBypass == bypassDnd) {
            if (manager.getNotificationChannel(savedId) != null) return savedId
        }

        val newId = (if (bypassDnd) "bullet_chat_dnd_" else "bullet_chat_std_") +
            System.currentTimeMillis()
        val channel = NotificationChannel(
            newId,
            getString(R.string.channel_name),
            // DEFAULT：勿扰下更容易被系统放行；无声音、无振动、不弹横幅
            NotificationManager.IMPORTANCE_DEFAULT,
        ).apply {
            description = getString(R.string.channel_desc)
            setShowBadge(false)
            enableVibration(false)
            setSound(null, null)
            if (bypassDnd) {
                runCatching { setBypassDnd(true) }
            }
        }
        manager.createNotificationChannel(channel)
        prefs.edit()
            .putString(KEY_CHANNEL, newId)
            .putBoolean(KEY_BYPASS, bypassDnd)
            .apply()

        if (savedId != null && savedId != newId) {
            runCatching { manager.deleteNotificationChannel(savedId) }
        }
        Log.d(TAG, "通知渠道已就绪：$newId（bypassDnd=$bypassDnd）")
        return newId
    }

    private fun registerScreenReceiver() {
        if (receiverRegistered) return
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_OFF)
            addAction(Intent.ACTION_SCREEN_ON)
        }
        ContextCompat.registerReceiver(
            this,
            screenReceiver,
            filter,
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
        receiverRegistered = true
    }

    private fun sendTestDanmaku() {
        if (danmakuView == null) {
            attachOverlay()
        }
        val view = danmakuView ?: return
        val samples = listOf(
            Triple(ChatPackages.WECHAT, "小林", "今晚一起吃饭吗？"),
            Triple(ChatPackages.QQ, "产品群", "新版本已经发到群里了，大家看下"),
            Triple(
                ChatPackages.WECHAT,
                "妈妈",
                "这是一条很长的测试消息，用来确认弹幕不会截断内容，而是自动换行完整显示出来，位置、透明度、字号和速度都可以在设置里调整。",
            ),
        )
        view.whenReady {
            scope.launch {
                samples.forEachIndexed { index, (pkg, title, text) ->
                    if (index > 0) delay(420)
                    val label = when (pkg) {
                        ChatPackages.WECHAT -> "微信"
                        ChatPackages.QQ -> "QQ"
                        else -> pkg
                    }
                    view.push(
                        DanmakuMessage(
                            packageName = pkg,
                            appLabel = label,
                            title = title,
                            text = text,
                            icon = AppIconLoader.load(this@OverlayService, pkg, 36),
                        ),
                    )
                }
            }
        }
    }

    /**
     * 优先使用通知原始 contentIntent，可直接跳到对应会话；
     * 再尝试以自由窗口（小窗）方式启动，失败则普通打开。
     */
    private fun openMessage(message: DanmakuMessage) {
        val options = if (settings.tryFloatingWindow) floatingOptions() else null
        val pending = message.contentIntent

        if (pending != null) {
            try {
                if (options != null) {
                    pending.send(this, 0, null, null, null, null, options.toBundle())
                } else {
                    pending.send()
                }
                return
            } catch (t: Throwable) {
                Log.w(TAG, "contentIntent 打开失败", t)
            }
        }

        val launch = packageManager.getLaunchIntentForPackage(message.packageName) ?: return
        launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        try {
            if (options != null) startActivity(launch, options.toBundle()) else startActivity(launch)
        } catch (_: Throwable) {
            try {
                startActivity(launch)
            } catch (_: Throwable) {
                // ignore
            }
        }
    }

    @SuppressLint("PrivateApi", "DiscouragedPrivateApi")
    private fun floatingOptions(): ActivityOptions? = try {
        val options = ActivityOptions.makeBasic()
        var applied = false
        try {
            val method = ActivityOptions::class.java
                .getMethod("setLaunchWindowingMode", Int::class.javaPrimitiveType)
            method.invoke(options, 5) // WINDOWING_MODE_FREEFORM
            applied = true
        } catch (_: Throwable) {
            // 隐藏 API 不可用
        }
        if (!applied && Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            val dm = resources.displayMetrics
            options.setLaunchBounds(
                Rect(
                    dp(8),
                    dp(64),
                    (dm.widthPixels * 0.78f).toInt(),
                    (dm.heightPixels * 0.56f).toInt(),
                ),
            )
        }
        options
    } catch (_: Throwable) {
        null
    }

    override fun onDestroy() {
        // 用户主动停止时会先把 enabled 置 false；仍为 true 说明是系统回收，需要自动拉起
        val shouldRestart = SettingsStore.settings.value.enabled
        isRunning = false
        handler.removeCallbacksAndMessages(null)
        scope.cancel()
        if (receiverRegistered) {
            try {
                unregisterReceiver(screenReceiver)
            } catch (_: Throwable) {
                // ignore
            }
            receiverRegistered = false
        }
        detachOverlay()
        AppIconLoader.clear()
        StatsStore.sessionEnd()
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        super.onDestroy()

        if (shouldRestart) {
            Handler(Looper.getMainLooper()).postDelayed({
                if (SettingsStore.settings.value.enabled && !isRunning) {
                    Log.w(TAG, "服务被系统回收，尝试自动恢复")
                    start(this)
                }
            }, 1500L)
        }
    }

    private fun AppSettings.toConfig() = DanmakuView.Config(
        laneCount = effectiveLanes(),
        alphaPercent = alphaPercent,
        fontSp = fontSp,
        speedPercent = speedPercent,
        showAppIcon = showAppIcon,
        clickable = clickToOpen,
    )
}

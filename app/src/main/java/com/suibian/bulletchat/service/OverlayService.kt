package com.suibian.bulletchat.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import android.util.Log
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
import com.suibian.bulletchat.model.DanmakuMessage
import com.suibian.bulletchat.ui.DanmakuView
import com.suibian.bulletchat.util.AppIconLoader
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * 前台服务：维护一个全屏、可穿透触摸的悬浮层，把消息画成弹幕。
 */
class OverlayService : Service() {

    companion object {
        private const val TAG = "OverlayService"
        private const val CHANNEL_ID = "bullet_chat_overlay"
        private const val NOTIFICATION_ID = 1001

        const val ACTION_START = "com.suibian.bulletchat.action.START"
        const val ACTION_STOP = "com.suibian.bulletchat.action.STOP"
        const val ACTION_TEST = "com.suibian.bulletchat.action.TEST"

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
    }

    private lateinit var windowManager: WindowManager
    private var danmakuView: DanmakuView? = null
    private var layoutParams: WindowManager.LayoutParams? = null
    private var receiverRegistered = false
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

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
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager

        createNotificationChannel()
        startAsForeground()
        attachOverlay()
        observeSettings()
        observeDanmaku()
        registerScreenReceiver()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                SettingsStore.update { it.copy(enabled = false) }
                stopSelf()
                return START_NOT_STICKY
            }
            ACTION_TEST -> sendTestDanmaku()
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun startAsForeground() {
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
        } else {
            0
        }
        try {
            ServiceCompat.startForeground(this, NOTIFICATION_ID, buildNotification(), type)
        } catch (t: Throwable) {
            Log.e(TAG, "startForeground 失败", t)
        }
    }

    private fun buildNotification() = NotificationCompat.Builder(this, CHANNEL_ID)
        .setSmallIcon(R.drawable.ic_notification)
        .setContentTitle("弹幕通知运行中")
        .setContentText("微信 / QQ 新消息将以弹幕显示")
        .setOngoing(true)
        .setShowWhen(false)
        .setCategory(NotificationCompat.CATEGORY_SERVICE)
        .setPriority(NotificationCompat.PRIORITY_LOW)
        .setContentIntent(
            PendingIntent.getActivity(
                this,
                11,
                Intent(this, MainActivity::class.java),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            ),
        )
        .addAction(
            0,
            "停止",
            PendingIntent.getService(
                this,
                12,
                Intent(this, OverlayService::class.java).setAction(ACTION_STOP),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            ),
        )
        .build()

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = getSystemService(NotificationManager::class.java) ?: return
        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.channel_name),
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = getString(R.string.channel_desc)
            setShowBadge(false)
            enableVibration(false)
        }
        manager.createNotificationChannel(channel)
    }

    private fun attachOverlay() {
        if (danmakuView != null) return
        if (!Settings.canDrawOverlays(this)) {
            Log.w(TAG, "没有悬浮窗权限，无法创建弹幕层")
            return
        }

        val view = DanmakuView(this).apply {
            config = SettingsStore.settings.value.toDanmakuConfig()
        }
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.START
        }

        try {
            windowManager.addView(view, params)
            danmakuView = view
            layoutParams = params
        } catch (t: Throwable) {
            Log.e(TAG, "添加悬浮层失败", t)
        }
    }

    private fun observeSettings() {
        scope.launch {
            SettingsStore.settings.collect { settings ->
                danmakuView?.config = settings.toDanmakuConfig()
                if (settings.enabled && danmakuView == null) attachOverlay()
            }
        }
    }

    private fun observeDanmaku() {
        scope.launch {
            DanmakuBus.events.collect { message ->
                if (danmakuView == null) attachOverlay()
                danmakuView?.push(message)
            }
        }
    }

    private fun sendTestDanmaku() {
        attachOverlay()
        val view = danmakuView ?: return
        val samples = listOf(
            Triple(ChatPackages.WECHAT, "小林", "今晚一起吃饭吗？"),
            Triple("com.tencent.mobileqq", "产品群", "新版本已经发到群里了，大家看下"),
            Triple(ChatPackages.WECHAT, "妈妈", "降温了，记得穿外套"),
        )
        scope.launch {
            samples.forEachIndexed { index, (pkg, title, text) ->
                if (index > 0) delay(420)
                val label = when {
                    ChatPackages.isWeChat(pkg) -> "微信"
                    ChatPackages.isQq(pkg) -> "QQ"
                    else -> pkg
                }
                view.push(
                    DanmakuMessage(
                        packageName = pkg,
                        appLabel = label,
                        title = title,
                        text = text,
                        icon = AppIconLoader.load(this@OverlayService, pkg),
                    ),
                )
            }
        }
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

    override fun onDestroy() {
        scope.cancel()
        if (receiverRegistered) {
            try {
                unregisterReceiver(screenReceiver)
            } catch (_: Throwable) {
                // ignore
            }
            receiverRegistered = false
        }
        danmakuView?.let { view ->
            try {
                windowManager.removeView(view)
            } catch (_: Throwable) {
                // ignore
            }
        }
        danmakuView = null
        layoutParams = null
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        super.onDestroy()
    }

    private fun AppSettings.toDanmakuConfig() = DanmakuView.Config(
        positionPercent = positionPercent,
        laneCount = laneCount,
        alphaPercent = alphaPercent,
        fontSp = fontSp,
        speedPercent = speedPercent,
        showAppIcon = showAppIcon,
    )
}

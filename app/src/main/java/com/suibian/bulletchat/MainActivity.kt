package com.suibian.bulletchat

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.suibian.bulletchat.data.AppSettings
import com.suibian.bulletchat.data.SettingsStore
import com.suibian.bulletchat.service.OverlayService
import com.suibian.bulletchat.ui.theme.AccentCyan
import com.suibian.bulletchat.ui.theme.BulletChatTheme
import com.suibian.bulletchat.ui.theme.SuccessGreen
import com.suibian.bulletchat.ui.theme.WarnAmber
import kotlin.math.roundToInt

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        SettingsStore.init(this)
        setContent {
            BulletChatTheme {
                val settings by SettingsStore.settings.collectAsStateWithLifecycle()
                BulletChatScreen(
                    settings = settings,
                    onUpdate = { updated -> SettingsStore.update { updated } },
                )
            }
        }

        val current = SettingsStore.settings.value
        if (current.enabled && Settings.canDrawOverlays(this)) {
            OverlayService.start(this)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BulletChatScreen(
    settings: AppSettings,
    onUpdate: (AppSettings) -> Unit,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    var hasListener by remember { mutableStateOf(hasNotificationAccess(context)) }
    var hasOverlay by remember { mutableStateOf(Settings.canDrawOverlays(context)) }
    var batteryFree by remember { mutableStateOf(isIgnoringBatteryOptimizations(context)) }

    val notificationPermission = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) { }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                hasListener = hasNotificationAccess(context)
                hasOverlay = Settings.canDrawOverlays(context)
                batteryFree = isIgnoringBatteryOptimizations(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    fun toggleService(enable: Boolean) {
        if (!enable) {
            onUpdate(settings.copy(enabled = false))
            OverlayService.stop(context)
            return
        }
        if (!hasOverlay) {
            Toast.makeText(context, "请先授予悬浮窗权限", Toast.LENGTH_SHORT).show()
            openOverlaySettings(context)
            return
        }
        if (!hasListener) {
            Toast.makeText(context, "请先开启通知使用权，否则读不到微信 / QQ 消息", Toast.LENGTH_LONG).show()
            openNotificationAccess(context)
            return
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        onUpdate(settings.copy(enabled = true))
        OverlayService.start(context)
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text("弹幕通知", fontWeight = FontWeight.SemiBold) },
                actions = {
                    Text(
                        "v${BuildConfig.VERSION_NAME}",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(end = 16.dp),
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = MaterialTheme.colorScheme.onBackground,
                ),
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            HeroCard(settings = settings, onToggle = ::toggleService)

            PermissionCard(
                hasListener = hasListener,
                hasOverlay = hasOverlay,
                batteryFree = batteryFree,
                onListener = { openNotificationAccess(context) },
                onOverlay = { openOverlaySettings(context) },
                onBattery = { requestBatteryExemption(context) },
            )

            SectionCard(title = "消息来源") {
                SettingSwitchRow(
                    title = "微信",
                    description = "私聊、群聊的全部通知",
                    checked = settings.wechatEnabled,
                    onCheckedChange = { onUpdate(settings.copy(wechatEnabled = it)) },
                )
                SettingSwitchRow(
                    title = "QQ / TIM",
                    description = "私聊、群聊的全部通知",
                    checked = settings.qqEnabled,
                    onCheckedChange = { onUpdate(settings.copy(qqEnabled = it)) },
                )
                SettingSwitchRow(
                    title = "其他应用",
                    description = "默认关闭，开启后接收所有应用的通知",
                    checked = settings.otherAppsEnabled,
                    onCheckedChange = { onUpdate(settings.copy(otherAppsEnabled = it)) },
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f))
                SettingSwitchRow(
                    title = "仅亮屏时显示",
                    description = "锁屏、息屏时不打扰",
                    checked = settings.onlyWhenScreenOn,
                    onCheckedChange = { onUpdate(settings.copy(onlyWhenScreenOn = it)) },
                )
                SettingSwitchRow(
                    title = "过滤群聊汇总",
                    description = "减少「N 条新消息」这类重复弹幕",
                    checked = settings.skipGroupSummary,
                    onCheckedChange = { onUpdate(settings.copy(skipGroupSummary = it)) },
                )
                SettingSwitchRow(
                    title = "显示应用图标",
                    description = "在弹幕左侧显示来源 App 图标",
                    checked = settings.showAppIcon,
                    onCheckedChange = { onUpdate(settings.copy(showAppIcon = it)) },
                )
            }

            SectionCard(
                title = "外观与位置",
                trailing = {
                    TextButton(
                        onClick = {
                            onUpdate(
                                settings.copy(
                                    positionPercent = 16,
                                    laneCount = 4,
                                    alphaPercent = 85,
                                    fontSp = 16,
                                    speedPercent = 100,
                                ),
                            )
                        },
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                    ) {
                        Text("重置", fontSize = 12.sp)
                    }
                },
            ) {
                SettingSliderRow(
                    title = "垂直位置",
                    valueLabel = "${settings.positionPercent}% · 距屏幕顶部",
                    value = settings.positionPercent.toFloat(),
                    range = 0f..70f,
                    onValueChange = { onUpdate(settings.copy(positionPercent = it.roundToInt())) },
                )
                SettingSliderRow(
                    title = "弹幕行数",
                    valueLabel = "${settings.laneCount} 行",
                    value = settings.laneCount.toFloat(),
                    range = 1f..8f,
                    onValueChange = { onUpdate(settings.copy(laneCount = it.roundToInt())) },
                )
                SettingSliderRow(
                    title = "透明度",
                    valueLabel = "${settings.alphaPercent}%",
                    value = settings.alphaPercent.toFloat(),
                    range = 20f..100f,
                    onValueChange = { onUpdate(settings.copy(alphaPercent = it.roundToInt())) },
                )
                SettingSliderRow(
                    title = "字号",
                    valueLabel = "${settings.fontSp} sp",
                    value = settings.fontSp.toFloat(),
                    range = 12f..28f,
                    onValueChange = { onUpdate(settings.copy(fontSp = it.roundToInt())) },
                )
                SettingSliderRow(
                    title = "速度",
                    valueLabel = "${settings.speedPercent}%",
                    value = settings.speedPercent.toFloat(),
                    range = 50f..250f,
                    onValueChange = { onUpdate(settings.copy(speedPercent = it.roundToInt())) },
                )
                Text(
                    "调节后立即生效，无需重启服务。",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            SectionCard(title = "关键词过滤") {
                Text(
                    "包含关键词的消息不会显示，多个关键词用逗号分隔。",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedTextField(
                    value = settings.blacklist,
                    onValueChange = { onUpdate(settings.copy(blacklist = it)) },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("例如：广告, 砍价, 转账", fontSize = 13.sp) },
                    textStyle = TextStyle(fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurface),
                    minLines = 2,
                    shape = RoundedCornerShape(14.dp),
                )
            }

            Button(
                onClick = {
                    if (!settings.enabled) {
                        Toast.makeText(context, "请先打开顶部的总开关", Toast.LENGTH_SHORT).show()
                    } else {
                        OverlayService.sendTest(context)
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                shape = RoundedCornerShape(16.dp),
            ) {
                Icon(Icons.Rounded.PlayArrow, contentDescription = null, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Text("发送测试弹幕", fontWeight = FontWeight.SemiBold)
            }

            SectionCard(title = "使用说明") {
                GuideStep("1", "开启「通知使用权」，并在系统列表中勾选「弹幕通知」")
                GuideStep("2", "授予「悬浮窗权限」，部分机型还需允许「后台弹出界面」")
                GuideStep("3", "打开总开关，收到微信 / QQ 消息即可看到弹幕")
                GuideStep("4", "回到本页可随时调整位置、透明度、字号和速度")
                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f))
                Text(
                    "隐私：所有通知仅在本机读取与显示，应用未申请联网权限，不会上传任何内容。",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    lineHeight = 16.sp,
                )
            }

            Text(
                "BulletChat ${BuildConfig.VERSION_NAME} · 本地运行 · 无网络权限",
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center,
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
            )
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun HeroCard(settings: AppSettings, onToggle: (Boolean) -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(Brush.linearGradient(listOf(Color(0xFF123244), Color(0xFF0B292B))))
                .padding(20.dp),
        ) {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(Color(0x2E5FE3F3)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(Icons.Rounded.Notifications, contentDescription = null, tint = AccentCyan)
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text("弹幕通知", fontSize = 20.sp, fontWeight = FontWeight.Bold)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                Modifier
                                    .size(7.dp)
                                    .clip(CircleShape)
                                    .background(if (settings.enabled) SuccessGreen else Color(0xFF64748B)),
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(
                                if (settings.enabled) "运行中 · 弹幕已就绪" else "未开启",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    Switch(checked = settings.enabled, onCheckedChange = onToggle)
                }
                Spacer(Modifier.height(14.dp))
                Text(
                    "微信、QQ 消息化作弹幕从屏幕上飘过，不打断你正在做的事。",
                    fontSize = 13.sp,
                    color = Color(0xFFA9BCCD),
                    lineHeight = 19.sp,
                )
            }
        }
    }
}

@Composable
private fun PermissionCard(
    hasListener: Boolean,
    hasOverlay: Boolean,
    batteryFree: Boolean,
    onListener: () -> Unit,
    onOverlay: () -> Unit,
    onBattery: () -> Unit,
) {
    SectionCard(title = "运行权限") {
        PermissionRow(
            title = "通知使用权",
            description = "读取微信 / QQ 新消息，仅在本机解析",
            granted = hasListener,
            actionLabel = "去开启",
            onAction = onListener,
        )
        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f))
        PermissionRow(
            title = "悬浮窗权限",
            description = "在其他应用上方显示弹幕",
            granted = hasOverlay,
            actionLabel = "去授予",
            onAction = onOverlay,
        )
        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f))
        PermissionRow(
            title = "后台运行（可选）",
            description = "避免系统清理导致弹幕中断",
            granted = batteryFree,
            actionLabel = "去设置",
            onAction = onBattery,
        )
        Text(
            "小米 / 红米、OPPO、vivo 等机型还需在系统设置中允许「后台弹出界面」与自启动。",
            fontSize = 11.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            lineHeight = 16.sp,
        )
    }
}

@Composable
private fun PermissionRow(
    title: String,
    description: String,
    granted: Boolean,
    actionLabel: String,
    onAction: () -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            imageVector = if (granted) Icons.Rounded.CheckCircle else Icons.Rounded.Warning,
            contentDescription = null,
            tint = if (granted) SuccessGreen else WarnAmber,
            modifier = Modifier.size(19.dp),
        )
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(title, fontSize = 14.sp)
            Text(
                description,
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                lineHeight = 15.sp,
            )
        }
        if (granted) {
            Text("已开启", fontSize = 11.sp, color = SuccessGreen)
        } else {
            TextButton(
                onClick = onAction,
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp),
            ) {
                Text(actionLabel, fontSize = 12.sp)
            }
        }
    }
}

@Composable
private fun SettingSwitchRow(
    title: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, fontSize = 14.sp)
            Text(
                description,
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                lineHeight = 15.sp,
            )
        }
        Spacer(Modifier.width(12.dp))
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun SettingSliderRow(
    title: String,
    valueLabel: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    onValueChange: (Float) -> Unit,
) {
    Column(Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(title, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(
                valueLabel,
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Medium,
            )
        }
        Slider(
            value = value.coerceIn(range.start, range.endInclusive),
            onValueChange = onValueChange,
            valueRange = range,
            modifier = Modifier.fillMaxWidth(),
            colors = SliderDefaults.colors(
                thumbColor = MaterialTheme.colorScheme.primary,
                activeTrackColor = MaterialTheme.colorScheme.primary,
                inactiveTrackColor = MaterialTheme.colorScheme.surfaceVariant,
            ),
        )
    }
}

@Composable
private fun SectionCard(
    title: String,
    trailing: (@Composable () -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 18.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    title,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f),
                )
                trailing?.invoke()
            }
            content()
        }
    }
}

@Composable
private fun GuideStep(index: String, text: String) {
    Row(verticalAlignment = Alignment.Top) {
        Box(
            modifier = Modifier
                .size(20.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                index,
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold,
            )
        }
        Spacer(Modifier.width(10.dp))
        Text(
            text,
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            lineHeight = 18.sp,
        )
    }
}

private fun hasNotificationAccess(context: Context): Boolean =
    NotificationManagerCompat.getEnabledListenerPackages(context).contains(context.packageName)

private fun isIgnoringBatteryOptimizations(context: Context): Boolean {
    val powerManager = context.getSystemService(Context.POWER_SERVICE) as? PowerManager ?: return false
    return powerManager.isIgnoringBatteryOptimizations(context.packageName)
}

private fun openNotificationAccess(context: Context) {
    try {
        context.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
    } catch (_: Throwable) {
        try {
            context.startActivity(Intent(Settings.ACTION_SETTINGS))
        } catch (_: Throwable) {
            // ignore
        }
    }
}

private fun openOverlaySettings(context: Context) {
    val intent = Intent(
        Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
        Uri.parse("package:${context.packageName}"),
    )
    try {
        context.startActivity(intent)
    } catch (_: Throwable) {
        try {
            context.startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION))
        } catch (_: Throwable) {
            // ignore
        }
    }
}

private fun requestBatteryExemption(context: Context) {
    try {
        context.startActivity(
            Intent(
                Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                Uri.parse("package:${context.packageName}"),
            ),
        )
    } catch (_: Throwable) {
        try {
            context.startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
        } catch (_: Throwable) {
            // ignore
        }
    }
}

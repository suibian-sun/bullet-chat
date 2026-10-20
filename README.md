# 弹幕通知 · BulletChat

把微信、QQ 的新消息变成一条条从屏幕上飘过的弹幕。基于 **Kotlin + Jetpack Compose** 编写，通过系统的 **NotificationListenerService** 读取通知，再用 **悬浮窗（TYPE_APPLICATION_OVERLAY）** 绘制弹幕，全程本地处理，不联网、不上传。

![Android CI](https://github.com/suibian-sun/bullet-chat/actions/workflows/android.yml/badge.svg)

## 功能

- **微信 / QQ 全量通知**：私聊、群聊消息都会转成弹幕；可选接收其他应用通知
- **位置调节**：弹幕区域距离屏幕顶部 0%–70% 可调，行数 1–8 行
- **透明度调节**：20%–100% 实时生效
- **字号 / 速度调节**：12–28sp，50%–250%
- **来源过滤**：微信、QQ/TIM、其他应用分别开关
- **关键词过滤**：包含指定关键词的消息不显示
- **智能去重**：过滤群聊汇总与重复通知，避免刷屏
- **仅亮屏显示**：锁屏、息屏时不打扰
- **应用图标**：弹幕左侧显示来源 App 图标
- **开机自启**：重启后自动恢复（需保留权限）
- **前台服务**：常驻通知栏，随时可停止
- **隐私安全**：无 `INTERNET` 权限，通知内容只在本机解析

## 安装

### 方式一：GitHub Actions 构建产物（推荐）

1. 打开仓库的 **Actions** 页面，进入最近一次 `Android CI` 运行
2. 在 **Artifacts** 中下载 `BulletChat-debug-apk`
3. 解压得到 `BulletChat-*.apk`，传到手机安装（需允许「安装未知来源应用」）

也可以推送 `v*` 标签（如 `v1.0.0`），工作流会自动创建 Release 并附上 APK：

```bash
git tag v1.0.0
git push origin v1.0.0
```

### 方式二：本地构建

需要 JDK 17 与 Android SDK 35：

```bash
export ANDROID_HOME=/path/to/android-sdk
./gradlew assembleDebug
# 产物：app/build/outputs/apk/debug/app-debug.apk
```

## 首次使用

1. 安装并打开「弹幕通知」
2. **通知使用权** → 去开启 → 在系统列表中找到「弹幕通知」并勾选
3. **悬浮窗权限** → 去授予 → 允许「显示在其他应用上层」
4. 建议开启 **后台运行**（忽略电池优化）
5. 打开顶部总开关
6. 点「发送测试弹幕」确认效果，再去微信 / QQ 收一条消息试试

> 小米 / 红米、OPPO、vivo、realme 等机型还需要在系统设置里允许「后台弹出界面」「自启动」，否则悬浮层可能被系统拦截。

## 调节外观

| 设置项 | 范围 | 说明 |
| --- | --- | --- |
| 垂直位置 | 0%–70% | 弹幕区域顶部距屏幕顶部的比例 |
| 弹幕行数 | 1–8 行 | 同时并排的弹幕轨道数量 |
| 透明度 | 20%–100% | 整体透明度（背景与文字） |
| 字号 | 12–28sp | 弹幕文字大小 |
| 速度 | 50%–250% | 滚动速度，100% 为默认 |

所有调节**即时生效**，不需要重启服务。

## 技术架构

```
app/src/main/java/com/suibian/bulletchat/
├── MainActivity.kt                  # Compose 设置界面、权限引导
├── BulletChatApp.kt                 # Application，初始化本地配置
├── data/AppSettings.kt              # SharedPreferences + StateFlow 配置中心
├── model/DanmakuMessage.kt          # 弹幕数据模型
├── service/
│   ├── BulletNotificationListener.kt # 通知监听、正文提取、过滤、去重
│   ├── OverlayService.kt             # 前台服务 + 全屏悬浮窗
│   ├── DanmakuBus.kt                 # 进程内事件通道（SharedFlow）
│   └── BootReceiver.kt               # 开机 / 更新后恢复
├── ui/DanmakuView.kt                 # Canvas 逐帧弹幕引擎（分轨、避让、透明度）
├── ui/theme/Theme.kt                 # Material 3 深色主题
└── util/AppIconLoader.kt             # 应用图标加载与缓存
```

关键实现：

- **通知读取**：`NotificationListenerService` → `onNotificationPosted`，从 `extras` 读取 `EXTRA_TITLE` / `EXTRA_TEXT` / `EXTRA_BIG_TEXT`，并兼容 `MessagingStyle`
- **弹幕渲染**：自定义 `View` + `Choreographer` 逐帧绘制，按轨道分配并做碰撞避让，空闲时自动停止刷新以省电
- **悬浮层**：`TYPE_APPLICATION_OVERLAY` + `FLAG_NOT_TOUCHABLE`，全屏穿透，不影响正常操作
- **状态同步**：`StateFlow` 把设置变化实时推给悬浮层，滑动滑块立即看到效果
- **前台服务**：`foregroundServiceType="specialUse"`，适配 Android 14/15

## 云端构建

`.github/workflows/android.yml` 会在 push 到 `main`、提交 PR 或手动触发时：

1. 准备 JDK 17 与 Android SDK 35
2. 执行 `./gradlew assembleDebug`
3. 上传 APK 到 Actions Artifacts
4. 若推送了 `v*` 标签，自动发布 Release

## 常见问题

**收不到弹幕？**

- 确认「通知使用权」已勾选（系统设置 → 通知 → 通知使用权）
- 确认总开关已打开，且对应来源（微信 / QQ）开关未关闭
- 检查微信 / QQ 是否开启了「消息通知」，被折叠的群消息不会产生通知
- MIUI / ColorOS 需允许「后台弹出界面」，并把应用加入自启动白名单

**弹幕挡住操作？**

悬浮层整体不可触摸，不会拦截点击；如觉得视觉干扰，可提高透明度或只保留 1–2 行。

**会被系统杀掉吗？**

已使用前台服务并申请忽略电池优化；部分 ROM 仍需手动锁定后台任务。

**为什么没有联网权限？**

应用完全离线运行，通知内容不会离开设备。

## License

[MIT](LICENSE)

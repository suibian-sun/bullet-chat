# 弹幕通知 · BulletChat

把微信、QQ 的新消息变成一条条从屏幕上飘过的弹幕。基于 **Kotlin + 原生 View** 编写，通过系统的 **NotificationListenerService** 读取通知，再用 **悬浮窗（TYPE_APPLICATION_OVERLAY）** 绘制弹幕，全程本地处理，不联网、不上传。

![Android CI](https://github.com/suibian-sun/bullet-chat/actions/workflows/android.yml/badge.svg)

## 功能

- **通知来源可自选**：默认微信 + QQ，点开「通知来源」可搜索并勾选任意已安装应用
- **位置调节**：弹幕区域距离屏幕顶部 0%–70% 可调，行数 1–8 行
- **透明度调节**：20%–100% 实时生效
- **字号 / 速度调节**：12–28sp，50%–250%
- **显示范围**：全部方向 / 仅竖屏 / 仅横屏，旋转屏幕自动挂载或收起悬浮层
- **免打扰可用**：申请通知策略权限后，前台通知渠道设置 `bypassDnd`，勿扰模式下仍能显示
- **点击弹幕打开应用**：可选开启；点击弹幕直接跳到对应会话，并尽量以系统小窗（自由窗口）打开
- **长内容不截断**：多行自动换行 + 字号自适应 + 整宽重排，长消息完整显示
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
2. 在 **Artifacts** 中下载 `BulletChat-release-apk`
3. 解压得到 `BulletChat-*.apk`，传到手机安装（需允许「安装未知来源应用」）

也可以推送 `v*` 标签（如 `v1.0.1`），工作流会自动创建 Release 并附上 APK：

```bash
git tag v1.0.1
git push origin v1.0.1
```

### 方式二：本地构建

需要 JDK 17 与 Android SDK 35：

```bash
export ANDROID_HOME=/path/to/android-sdk
./gradlew assembleRelease
# 产物：app/build/outputs/apk/release/app-release.apk
```

> Release 默认复用 debug 签名，方便直接安装；正式分发请替换为自己的 keystore。

## 首次使用

1. 安装并打开「弹幕通知」
2. **通知使用权** → 去开启 → 在系统列表中找到「弹幕通知」并勾选
3. **悬浮窗权限** → 去授予 → 允许「显示在其他应用上层」
4. 建议开启 **后台运行**（忽略电池优化）；若常开勿扰，再授权 **免打扰权限**
5. 进入「通知来源」，勾选需要弹幕的应用
6. 打开顶部总开关
7. 点「发送测试弹幕」确认效果，再去微信 / QQ 收一条消息试试

> 小米 / 红米、OPPO、vivo、realme 等机型还需要在系统设置里允许「后台弹出界面」「自启动」，否则悬浮层可能被系统拦截。

## 调节外观

| 设置项 | 范围 | 说明 |
| --- | --- | --- |
| 垂直位置 | 0%–70% | 弹幕区域顶部距屏幕顶部的比例 |
| 弹幕行数 | 1–8 行 | 同时并排的弹幕轨道数量，行数越多长文本越宽松 |
| 透明度 | 20%–100% | 整体透明度（背景与文字） |
| 字号 | 12–28sp | 弹幕文字大小 |
| 速度 | 50%–250% | 滚动速度，100% 为默认 |

所有调节**即时生效**，不需要重启服务。

## 技术架构

```
app/src/main/java/com/suibian/bulletchat/
├── MainActivity.kt                   # 原生 View 设置界面、权限引导
├── AppPickerActivity.kt              # 通知来源选择器（搜索 / 多选）
├── BulletChatApp.kt                  # Application，初始化本地配置
├── data/AppSettings.kt               # SharedPreferences + StateFlow 配置中心
├── model/DanmakuMessage.kt           # 弹幕数据模型（含 contentIntent）
├── service/
│   ├── BulletNotificationListener.kt # 通知监听、正文提取、过滤、去重
│   ├── OverlayService.kt             # 前台服务 + 弹幕带悬浮窗
│   ├── DanmakuBus.kt                 # 进程内事件通道（SharedFlow）
│   └── BootReceiver.kt               # 开机 / 更新后恢复
├── ui/DanmakuView.kt                 # Canvas 逐帧弹幕引擎（分轨、避让、点击命中）
├── ui/UiKit.kt                       # 颜色、dp、圆角背景等轻量工具
└── util/AppIconLoader.kt             # 应用图标加载与 LRU 缓存
```

关键实现：

- **通知读取**：`NotificationListenerService` → `onNotificationPosted`；正文在 `EXTRA_BIG_TEXT` 与 `EXTRA_TEXT` 之间取更长的一份，并兼容 `MessagingStyle`，避免微信长消息只拿到截断文本
- **弹幕渲染**：自定义 `View` + `Choreographer` 逐帧绘制；`StaticLayout` 多行换行、字号自适应、整宽兜底重排；按轨道分配并做碰撞避让，空闲时自动停止刷新
- **悬浮层**：窗口高度等于弹幕带高度（而非整屏），`FLAG_NOT_TOUCHABLE` 全屏穿透；开启点击跳转时改用 `FLAG_NOT_TOUCH_MODAL` 只拦截弹幕带内的触摸
- **点击跳转**：优先透传通知原始 `contentIntent`，直达对应会话；再尝试 `ActivityOptions` 的自由窗口模式，不可用时退回普通启动
- **显示范围**：监听 `onConfigurationChanged`，按设置挂载或收起悬浮层
- **免打扰**：`NotificationChannel.setBypassDnd(true)`，需先授予通知策略访问权限
- **低内存**：不引入 Compose；图标 `RGB_565` + LRU 上限；`onTrimMemory` 时清理图标与弹幕缓存
- **状态同步**：`StateFlow` 把设置变化实时推给悬浮层，滑动滑块立即看到效果
- **前台服务**：`foregroundServiceType="specialUse"`，适配 Android 14/15

## 云端构建

`.github/workflows/android.yml` 会在 push 到 `main`、提交 PR 或手动触发时：

1. 准备 JDK 17 与 Android SDK 35
2. 执行 `./gradlew assembleRelease`
3. 上传 APK 到 Actions Artifacts
4. 若推送了 `v*` 标签，自动发布 Release

## 常见问题

**收不到弹幕？**

- 确认「通知使用权」已勾选（系统设置 → 通知 → 通知使用权）
- 确认总开关已打开，且「通知来源」里包含该应用
- 检查微信 / QQ 是否开启了「消息通知」，被折叠的群消息不会产生通知
- MIUI / ColorOS 需允许「后台弹出界面」，并把应用加入自启动白名单

**勿扰模式下没有弹幕？**

免打扰只影响通知提示音与横幅，弹幕是悬浮窗，理论上不受影响。若系统连悬浮窗一起限制，请授权「免打扰权限」，让前台通知渠道绕过勿扰，并确认机型没有「勿扰时禁止悬浮窗」的额外开关。

**点击弹幕没进小窗？**

小窗取决于系统是否开放自由窗口能力（部分国产 ROM 已关闭）。不可用时会普通全屏打开对应应用，功能不受影响。

**弹幕挡住操作？**

默认整层不可触摸，不会拦截点击；开启「点击弹幕打开应用」后，仅弹幕带区域会接收触摸，其余区域仍可正常操作。

**会被系统杀掉吗？**

已使用前台服务并申请忽略电池优化；部分 ROM 仍需手动锁定后台任务。

**为什么没有联网权限？**

应用完全离线运行，通知内容不会离开设备。

## License

[MIT](LICENSE)

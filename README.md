# 弹幕通知 · BulletChat

把微信、QQ 的新消息变成一条条从屏幕上飘过的弹幕。基于 **Kotlin + 原生 View** 编写，通过系统的 **NotificationListenerService** 读取通知，再用 **悬浮窗** 绘制弹幕。除了检查更新，全程本地处理，不上传任何通知内容。

![Android CI](https://github.com/suibian-sun/bullet-chat/actions/workflows/android.yml/badge.svg)

## v1.1 新界面

底部三页导航，中间是一个圆形启动按钮：

| 页面 | 内容 |
| --- | --- |
| **主页** | 本次运行时长、今日转发、累计转发、累计运行、最近一条弹幕 |
| **启动** | 中央大号圆形开关，一键启停弹幕；权限状态一览 |
| **设置** | 通知来源、显示方式、外观与位置、关键词、权限、版本与更新 |

整体采用深色极光背景 + 玻璃拟态卡片 + 青紫渐变的现代美术风格。

## 功能

- **通知来源可自选**：默认微信 + QQ，可搜索并勾选任意已安装应用
- **长消息单行完整显示**：不换行、不截断，文本超出屏幕时靠滚动逐字露出
- **位置调节**：弹幕带距屏幕顶部 0%–70%，行数 1–8 行
- **透明度 / 字号 / 速度**：20%–100%、12–28sp、50%–250%，滑动即时生效
- **显示范围**：全部方向 / 仅竖屏 / 仅横屏，旋转屏幕自动挂载或收起
- **免打扰可用**：授予通知策略权限后，前台渠道 `bypassDnd`，勿扰下仍显示
- **点击弹幕打开应用**：直达对应会话，并尽量以系统小窗（自由窗口）打开
- **关键词过滤、群聊汇总过滤、重复通知去重、仅亮屏显示**
- **运行统计**：转发条数按天累计，运行时长跨重启保留
- **内置更新**：读取 GitHub Releases，有新版本时弹窗提示，可直接下载并调起系统安装器
- **历史更新**：设置页可查看每个版本的变更记录
- **开机自启、前台服务保活**
- **隐私**：通知内容只在本机解析；仅「检查更新」会访问 GitHub API

## 安装

### 方式一：GitHub Release（推荐）

打开 [Releases](https://github.com/suibian-sun/bullet-chat/releases) 下载最新 `BulletChat-v*.apk` 安装。若提示签名冲突，先卸载旧版本。

### 方式二：Actions 构建产物

Actions → 最近一次 `Android CI` → Artifacts → `BulletChat-release-apk`。

### 方式三：本地构建

需要 JDK 17 与 Android SDK 35：

```bash
export ANDROID_HOME=/path/to/android-sdk
./gradlew assembleRelease
# 产物：app/build/outputs/apk/release/app-release.apk
```

> Release 复用 debug 签名，方便直接覆盖安装；正式分发请替换为自己的 keystore。

## 系统兼容

- **最低 Android 5.0（API 21）**，覆盖 Android 5.0 → 16，并已在 targetSdk 35 下为后续版本保留兼容路径
- 各版本差异均做了分支处理：

| 版本区间 | 处理方式 |
| --- | --- |
| Android 5.0–7.1 | 悬浮窗使用 `TYPE_PHONE`；无通知渠道概念 |
| Android 6.0+ | 运行时申请悬浮窗权限；免打扰、电池优化检查启用 |
| Android 8.0+ | 改用 `TYPE_APPLICATION_OVERLAY`；创建通知渠道；安装 APK 需「未知来源」授权 |
| Android 10+ | 分区存储、深色系统栏适配 |
| Android 11+ | 窗口度量改用 `WindowMetrics` |
| Android 13+ | 运行时申请 `POST_NOTIFICATIONS` |
| Android 14/15+ | 前台服务声明 `specialUse` 类型；边到边布局 + Insets 适配 |

> 关于「Android 1 到 17」：Android 4.4 及更早系统没有 `NotificationListenerService`（API 18 才引入），无法读取任何应用的通知，因此不存在可用的实现路径；Android 17 尚未正式发布。当前实现以 **API 21 为技术下限**，并在 Android 16/17 上以兼容模式运行。

## 首次使用

1. 打开应用，进入「设置 → 权限」或「启动」页
2. **通知使用权** → 在系统列表中勾选「弹幕通知」
3. **悬浮窗权限** → 允许「显示在其他应用上层」
4. 建议开启 **后台运行**（忽略电池优化）；常开勿扰再授权 **免打扰权限**
5. 进入「设置 → 通知来源」，勾选需要弹幕的应用
6. 点底部中间的圆形按钮，或「启动」页的中央按钮
7. 收一条微信消息验证效果

> 小米 / 红米、华为、OPPO、vivo 等机型还需允许「后台弹出界面」与自启动，否则悬浮层可能被拦截。

## 调节外观

| 设置项 | 范围 | 说明 |
| --- | --- | --- |
| 垂直位置 | 0%–70% | 弹幕带顶部距屏幕顶部的比例 |
| 弹幕行数 | 1–8 行 | 并行轨道数量 |
| 透明度 | 20%–100% | 背景与文字整体透明度 |
| 字号 | 12–28sp | 弹幕文字大小 |
| 速度 | 50%–250% | 滚动速度，100% 为默认 |

## 更新机制

1. 启动或回到前台时（6 小时内最多一次）请求
   `https://api.github.com/repos/suibian-sun/bullet-chat/releases/latest`
2. 用语义化版本比较本地 `versionName`
3. 有新版则弹出对话框：更新内容 + **关闭** / **立即更新**
4. 立即更新后显示下载进度条（百分比与已下载大小）
5. 下载到 `cacheDir/updates/`，经 `FileProvider` 交给系统安装器

设置页「检查更新」可手动触发；「历史更新」是独立页面，展示每个版本的变化。

## 技术架构

```
app/src/main/java/com/suibian/bulletchat/
├── MainActivity.kt                   # 单 Activity 容器 + 底部导航 + 更新检查
├── AppPickerActivity.kt              # 通知来源选择器
├── ChangelogActivity.kt              # 历史更新时间线
├── data/
│   ├── AppSettings.kt                # 设置项（SharedPreferences + StateFlow）
│   ├── StatsStore.kt                 # 运行时长与转发统计
│   └── Changelog.kt                  # 内置版本历史
├── model/DanmakuMessage.kt           # 弹幕数据模型（含 contentIntent）
├── service/
│   ├── BulletNotificationListener.kt # 通知监听、正文提取、过滤、去重、计数
│   ├── OverlayService.kt             # 前台服务 + 弹幕带悬浮窗
│   ├── DanmakuBus.kt                 # 进程内事件通道
│   └── BootReceiver.kt               # 开机 / 更新后恢复
├── ui/
│   ├── HomePage.kt / LaunchPage.kt / SettingsPage.kt
│   ├── NavBar.kt                     # 底部导航与圆形启动按钮
│   ├── DanmakuView.kt                # 单行弹幕引擎（分轨、避让、点击）
│   └── UiKit.kt                      # 配色、极光背景、玻璃卡片、开关
├── update/
│   ├── UpdateChecker.kt              # GitHub Releases 检查与版本比较
│   ├── UpdateInstaller.kt            # 下载 + FileProvider 安装
│   └── UpdateDialog.kt               # 更新弹窗与进度条
└── util/
    ├── AppIconLoader.kt              # 图标 RGB_565 + LRU
    └── Permissions.kt                # 各版本权限与系统页跳转
```

关键实现：

- **单行弹幕**：`Paint.measureText` 测量整行宽度，`canvas.drawText` 一次绘制；条目宽度可超过屏幕，靠滚动完整呈现，不做换行与省略
- **小窗口悬浮层**：窗口高度只等于弹幕带高度，Surface 更小；关闭点击时 `FLAG_NOT_TOUCHABLE` 全透传，开启后改为 `FLAG_NOT_TOUCH_MODAL`
- **点击跳转**：透传通知原始 `contentIntent`，再尝试 `ActivityOptions` 自由窗口，失败退回普通启动
- **统计**：`StatsStore` 独立 SharedPreferences，服务启停时累加运行时长，监听服务每转发一条自增
- **低内存**：无 Compose；图标 `RGB_565` + LRU 上限；`onTrimMemory` 清理缓存

## 云端构建

`.github/workflows/android.yml` 在 push `main`、`v*` 标签、PR 或手动触发时：安装 JDK 17 与 Android SDK 35 → `./gradlew assembleRelease` → 上传 Artifact；若为标签则自动创建 Release 并附上 APK。

## 常见问题

**收不到弹幕？** 检查通知使用权、悬浮窗权限、总开关状态，以及「通知来源」是否包含该应用。

**勿扰模式下没有弹幕？** v1.1.1 起会按授权状态重建独立通知渠道，确保 `bypassDnd` 真正生效；若仍无效，请在系统「勿扰 → 允许例外」中把「弹幕通知」加入白名单。

**横屏下弹幕不显示或位置异常？** v1.1.1 已修复：旋转时按新方向重算窗口尺寸与位置，并保留正在显示的消息；若「显示范围」设为「仅竖屏」，横屏本就不会显示，改成「全部方向」即可。

**点击弹幕没进小窗？** 小窗取决于系统是否开放自由窗口，不可用时普通全屏打开。

**更新下载后无法安装？** 需允许「安装未知应用」；已下载文件在 `cache/updates`，可在提示后重试。

## License

[MIT](LICENSE)

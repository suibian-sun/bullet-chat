package com.suibian.bulletchat.data

data class ChangelogEntry(
    val version: String,
    val date: String,
    val title: String,
    val highlights: List<String>,
)

/** 内置版本历史；发版时在首位追加即可。 */
object Changelog {
    val entries: List<ChangelogEntry> = listOf(
        ChangelogEntry(
            version = "1.1.0",
            date = "2026-10-21",
            title = "全新界面与更新能力",
            highlights = listOf(
                "底部三页导航：主页 / 启动 / 设置，中间为圆形启动按钮",
                "主页展示本次运行时长、今日与累计转发、累计运行时长",
                "现代美术风格重排：极光背景、玻璃卡片、渐变强调色",
                "长消息改为单行滚动，完整展示、不再换行截断",
                "内置更新系统：对接 GitHub Releases，支持关闭与立即更新",
                "新增历史更新页面，设置页展示当前版本号",
                "最低兼容下探至 Android 5.0，覆盖更多机型",
            ),
        ),
        ChangelogEntry(
            version = "1.0.1",
            date = "2026-10-20",
            title = "显示控制与内存优化",
            highlights = listOf(
                "新增显示范围：全部方向 / 仅竖屏 / 仅横屏",
                "免打扰模式下仍可显示弹幕",
                "通知来源改为可搜索的多选列表",
                "点击弹幕可直达会话，并尝试小窗打开",
                "移除 Compose，安装包体积从 9.09 MB 降至 149 KB",
                "长文本支持自动换行与字号自适应",
            ),
        ),
        ChangelogEntry(
            version = "1.0.0",
            date = "2026-10-20",
            title = "首个版本",
            highlights = listOf(
                "微信 / QQ 通知转弹幕",
                "位置、透明度、字号、速度可调",
                "关键词过滤与重复通知去重",
                "前台服务保活与开机自启",
                "无网络权限，通知内容只在本机解析",
            ),
        ),
    )
}

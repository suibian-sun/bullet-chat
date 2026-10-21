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
            version = "1.1.2",
            date = "2026-10-21",
            title = "导航层级与过渡动画",
            highlights = listOf(
                "修复底部导航图标与圆形按钮被底座遮住的问题（elevation 导致绘制层级错乱）",
                "中间圆形启动按钮改为浮起式，带圆形投影与按压缩放",
                "页面切换新增淡入上浮 + 轻微缩放过渡，底部导航图标选中时弹性放大",
                "主页、启动页、设置页首次进入时卡片错落入场",
                "主页转发数字刷新时带弹性脉冲，更新弹窗缩放浮现",
                "冷启动时导航栏自下而上浮现，历史更新页卡片依次出现",
            ),
        ),
        ChangelogEntry(
            version = "1.1.1",
            date = "2026-10-21",
            title = "横屏与免打扰修复",
            highlights = listOf(
                "修复横屏下弹幕带位置与尺寸未重算，导致部分消息不可见",
                "横竖屏切换时保留正在显示的消息，重挂载后重新播放",
                "弹幕速度改为恒定时长模型，横屏不再长期占用轨道挤掉后续消息",
                "修复免打扰下不显示：按授权状态重建独立渠道，确保 bypassDnd 真正生效",
                "服务被免打扰 / 省电策略回收后自动恢复，收到新消息时自愈",
                "前台服务启动失败时自动回退兼容模式",
                "弹幕带高度在横屏下限制为屏幕 62%，避免挤压顶部内容",
            ),
        ),
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

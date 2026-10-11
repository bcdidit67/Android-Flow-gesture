package com.flowgesture.app.core

/** 动作分组（用于选择弹层分组展示） */
enum class ActionGroup(val label: String) {
    SYSTEM("系统"),
    MEDIA("媒体"),
    MISC("其他"),
}

/**
 * 可映射的动作类型。
 *
 * [id] 会写入配置文件并由 Root 守护进程解析执行，两端必须保持一致（不要修改既有 id）。
 */
enum class ActionType(val id: String, val label: String, val group: ActionGroup) {
    NONE("none", "无", ActionGroup.MISC),
    RECENTS("recents", "任务视图", ActionGroup.SYSTEM),
    NOTIFICATIONS("notifications", "通知中心", ActionGroup.SYSTEM),
    HOME("home", "主屏幕 / 显示桌面", ActionGroup.SYSTEM),
    BACK("back", "返回", ActionGroup.SYSTEM),
    QUICK_SETTINGS("quick_settings", "快捷设置", ActionGroup.SYSTEM),
    SCREENSHOT("screenshot", "截屏", ActionGroup.MISC),
    PLAY_PAUSE("play_pause", "播放 / 暂停", ActionGroup.MEDIA),
    PREV_TRACK("prev", "上一曲", ActionGroup.MEDIA),
    NEXT_TRACK("next", "下一曲", ActionGroup.MEDIA),
    VOLUME_UP("vol_up", "调高音量", ActionGroup.MEDIA),
    VOLUME_DOWN("vol_down", "调低音量", ActionGroup.MEDIA),
    MUTE("mute", "静音", ActionGroup.MEDIA),
    CUSTOM("custom", "自定义快捷方式（Shell）", ActionGroup.MISC);

    companion object {
        fun fromId(id: String?): ActionType = entries.firstOrNull { it.id == id } ?: NONE
    }
}
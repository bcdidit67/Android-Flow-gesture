package com.flowgesture.app.core

/**
 * 可映射的动作类型。
 *
 * [id] 会写入配置文件并由 Root 守护进程解析执行，两端必须保持一致。
 */
enum class ActionType(val id: String, val label: String) {
    NONE("none", "无"),
    BACK("back", "返回"),
    HOME("home", "主屏幕 / 显示桌面"),
    RECENTS("recents", "任务视图"),
    NOTIFICATIONS("notifications", "通知中心"),
    QUICK_SETTINGS("quick_settings", "快捷设置"),
    PLAY_PAUSE("play_pause", "播放 / 暂停"),
    NEXT_TRACK("next", "下一曲目"),
    PREV_TRACK("prev", "上一曲目"),
    VOLUME_UP("vol_up", "调高音量"),
    VOLUME_DOWN("vol_down", "调低音量"),
    MUTE("mute", "静音"),
    SCREENSHOT("screenshot", "截屏"),
    CUSTOM("custom", "自定义快捷方式（Shell）");

    companion object {
        fun fromId(id: String?): ActionType = entries.firstOrNull { it.id == id } ?: NONE
    }
}
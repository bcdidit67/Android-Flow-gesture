package com.flowgesture.app.data

/** 手势方向（对标 Windows 触摸板：点击 + 四向轻扫） */
enum class GestureDirection(val id: String, val label: String) {
    TAP("tap", "点击"),
    UP("up", "向上轻扫"),
    DOWN("down", "向下轻扫"),
    LEFT("left", "向左轻扫"),
    RIGHT("right", "向右轻扫");

    companion object {
        fun fromId(id: String): GestureDirection? = entries.firstOrNull { it.id == id }
    }
}
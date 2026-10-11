#pragma once

#include <cstdint>
#include <string>

namespace flowgesture {

/** 手势类型 */
enum class GestureType {
    kNone,
    kTap,
    kSwipeUp,
    kSwipeDown,
    kSwipeLeft,
    kSwipeRight,
};

/** 一次完整的多指手势 */
struct Gesture {
    int fingers = 0;
    GestureType type = GestureType::kNone;
    std::string deviceName;
    int64_t durationMs = 0;
    int32_t deltaX = 0;
    int32_t deltaY = 0;
    // 诊断信息：实际使用的旋转档位与原始（面板坐标系）位移
    int rotationUsed = 0;
    int32_t rawDeltaX = 0;
    int32_t rawDeltaY = 0;
};

/** 用于日志展示，如 "SWIPE_UP" */
const char* GestureTypeName(GestureType type);

/** 用于配置文件键名，如 "up" */
const char* GestureTypeKey(GestureType type);

}  // namespace flowgesture

#pragma once

#include <linux/input.h>

#include <cstdint>
#include <functional>
#include <map>
#include <string>
#include <utility>
#include <vector>

#include "common.h"

namespace flowgesture {

/** 单根手指的状态 */
struct FingerState {
    int64_t downTimeMs = 0;
    int32_t startX = 0;
    int32_t startY = 0;
    int32_t lastX = 0;
    int32_t lastY = 0;
    int32_t maxMoveX = 0;
    int32_t maxMoveY = 0;
};

/**
 * 手势识别器（单设备）。
 *
 * 输入：多点触控协议 B 的槽位事件（按下 / 移动 / 抬起）。
 * 输出：满足条件时产出一枚 Gesture（3/4/5 指 的 点击与四向轻扫）。
 *
 * 识别规则（MVP）：
 * - 手势 = 从第一根手指按下到最后一根手指抬起的完整周期；
 * - 手指数取周期内同时按下的最大手指数（3 ~ 5）；
 * - 点击：总时长 <= 350ms 且任意手指位移 <= 40px；
 * - 轻扫：平均净位移超过阈值（屏幕尺寸约 1/12）且主方向明确。
 */
class Recognizer {
public:
    void Reset();
    void SetRanges(int32_t rangeX, int32_t rangeY);

    void OnSlotDown(int slot, int32_t x, int32_t y, int64_t timeMs);
    void OnSlotMove(int slot, int32_t x, int32_t y);
    void OnSlotUp(int slot, int64_t timeMs);
    /** 每帧（SYN_REPORT）结束时调用，用于判定手势结束 */
    void OnFrameEnd(int64_t timeMs);

    /** 是否产生了新的手势结果（取出即清除） */
    bool ConsumeGesture(Gesture* out);

    int ActiveFingerCount() const { return (int)fingers_.size(); }
    std::string DescribeTouches() const;

private:
    bool Classify();

    std::map<int, FingerState> fingers_;
    int maxFingers_ = 0;
    int64_t gestureStartMs_ = 0;
    int64_t gestureEndMs_ = 0;
    bool active_ = false;
    bool windowViolated_ = false;

    int64_t sumDx_ = 0;
    int64_t sumDy_ = 0;
    int completedFingers_ = 0;
    int32_t globalMaxMove_ = 0;

    int32_t rangeX_ = 1080;
    int32_t rangeY_ = 2340;

    bool pending_ = false;
    Gesture pendingGesture_;
};

/**
 * 输入设备监听器。
 *
 * - 扫描 /dev/input/event*，筛选多点触控设备（需要 Root 权限打开）；
 * - epoll 监听所有设备，解析协议 B，投喂给 Recognizer；
 * - 选择"主设备"（INPUT_PROP_DIRECT 优先、面积最大者）产生动作事件，
 *   其余设备仅打印调试日志；
 * - 周期性检查配置文件变更并热重载。
 */
class InputReader {
public:
    using GestureCallback = std::function<void(const Gesture&)>;

    void SetGestureCallback(GestureCallback callback) { callback_ = std::move(callback); }

    /** 主循环（阻塞，直到进程被杀死） */
    void Loop();

private:
    struct Device {
        int fd = -1;
        std::string path;
        std::string name;
        bool primary = false;
        int32_t rangeX = 0;
        int32_t rangeY = 0;
        int64_t lastCoordLogMs = 0;
        int lastLoggedFingers = -1;
        int currentSlot = 0;
        std::map<int, std::pair<int32_t, int32_t>> slotPositions;
        std::vector<struct input_event> frameEvents;
        Recognizer recognizer;
    };

    bool ScanDevices();
    void HandleDeviceReadable(Device& device);
    void ProcessFrame(Device& device);
    void LogFrameCoordinates(Device& device);

    std::vector<Device> devices_;
    GestureCallback callback_;
    bool needsRescan_ = true;
    bool emptyScanLogged_ = false;
    int64_t lastScanMs_ = 0;
};

/** 诊断用：扫描 /dev/input 并返回报告（供 App JNI 调用） */
std::string ProbeInputDevicesForDiagnostics();

}  // namespace flowgesture
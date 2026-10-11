#include "input_reader.h"

#include <dirent.h>
#include <fcntl.h>
#include <sys/epoll.h>
#include <sys/ioctl.h>
#include <unistd.h>

#include <algorithm>
#include <cerrno>
#include <cstring>

#include "config.h"
#include "log.h"

namespace flowgesture {

namespace {

constexpr int32_t kTapSlopPx = 40;               // 点击允许的最大位移
constexpr int64_t kTapMaxDurationMs = 350;       // 点击最大时长
constexpr int64_t kMultiFingerDownWindowMs = 180;  // 多指按下的时间窗
constexpr int64_t kRescanIntervalMs = 5000;      // 无设备时的重扫间隔
constexpr int64_t kCoordLogIntervalMs = 100;     // 坐标日志限频
constexpr size_t kMaxFrameEvents = 256;

bool TestBit(const unsigned char* bits, int index) {
    return ((bits[index >> 3] >> (index & 7)) & 1) != 0;
}

int64_t NowMs() {
    struct timespec ts;
    clock_gettime(CLOCK_MONOTONIC, &ts);
    return (int64_t)ts.tv_sec * 1000 + ts.tv_nsec / 1000000;
}

int64_t EventTimeMs(const struct input_event& ev) {
    return (int64_t)ev.time.tv_sec * 1000 + ev.time.tv_usec / 1000;
}

int32_t Abs32(int32_t v) { return v >= 0 ? v : -v; }

}  // namespace

// ---------------------------------------------------------------------------
// Recognizer
// ---------------------------------------------------------------------------

void Recognizer::Reset() {
    fingers_.clear();
    maxFingers_ = 0;
    gestureStartMs_ = 0;
    gestureEndMs_ = 0;
    active_ = false;
    windowViolated_ = false;
    sumDx_ = 0;
    sumDy_ = 0;
    completedFingers_ = 0;
    globalMaxMove_ = 0;
    // 注意：pending_ / pendingGesture_ 不做清理，等待 ConsumeGesture 取走
}

void Recognizer::SetRanges(int32_t rangeX, int32_t rangeY) {
    if (rangeX > 0) {
        rangeX_ = rangeX;
    }
    if (rangeY > 0) {
        rangeY_ = rangeY;
    }
}

void Recognizer::OnSlotDown(int slot, int32_t x, int32_t y, int64_t timeMs) {
    if (!active_) {
        Reset();
        active_ = true;
        gestureStartMs_ = timeMs;
    }
    // 多指必须在大致同一时间按下，避免"滚动中误加手指"被识别成手势
    if (!fingers_.empty() && timeMs - gestureStartMs_ > kMultiFingerDownWindowMs) {
        windowViolated_ = true;
    }

    FingerState finger;
    finger.downTimeMs = timeMs;
    finger.startX = x;
    finger.startY = y;
    finger.lastX = x;
    finger.lastY = y;
    fingers_[slot] = finger;

    if ((int)fingers_.size() > maxFingers_) {
        maxFingers_ = (int)fingers_.size();
    }
}

void Recognizer::OnSlotMove(int slot, int32_t x, int32_t y) {
    auto it = fingers_.find(slot);
    if (it == fingers_.end()) {
        return;
    }
    FingerState& finger = it->second;
    finger.lastX = x;
    finger.lastY = y;

    int32_t dx = Abs32(x - finger.startX);
    int32_t dy = Abs32(y - finger.startY);
    if (dx > finger.maxMoveX) {
        finger.maxMoveX = dx;
    }
    if (dy > finger.maxMoveY) {
        finger.maxMoveY = dy;
    }
}

void Recognizer::OnSlotUp(int slot, int64_t timeMs) {
    auto it = fingers_.find(slot);
    if (it == fingers_.end()) {
        return;
    }
    const FingerState& finger = it->second;

    sumDx_ += (int64_t)finger.lastX - finger.startX;
    sumDy_ += (int64_t)finger.lastY - finger.startY;
    completedFingers_++;

    int32_t maxMove = finger.maxMoveX > finger.maxMoveY ? finger.maxMoveX : finger.maxMoveY;
    if (maxMove > globalMaxMove_) {
        globalMaxMove_ = maxMove;
    }

    fingers_.erase(it);
    gestureEndMs_ = timeMs;
}

void Recognizer::OnFrameEnd(int64_t /*timeMs*/) {
    if (active_ && fingers_.empty()) {
        if (Classify()) {
            pending_ = true;
        }
        Reset();
    }
}

bool Recognizer::Classify() {
    if (maxFingers_ < 3 || maxFingers_ > 5) {
        return false;
    }
    if (windowViolated_) {
        return false;
    }
    if (completedFingers_ <= 0 || gestureEndMs_ <= gestureStartMs_) {
        return false;
    }

    const int64_t durationMs = gestureEndMs_ - gestureStartMs_;
    const int32_t avgDx = (int32_t)(sumDx_ / completedFingers_);
    const int32_t avgDy = (int32_t)(sumDy_ / completedFingers_);
    const int32_t absDx = Abs32(avgDx);
    const int32_t absDy = Abs32(avgDy);

    int32_t swipeMinX = rangeX_ / 12;  // 约 8.3% 屏宽
    if (swipeMinX < 80) {
        swipeMinX = 80;
    }
    int32_t swipeMinY = rangeY_ / 12;
    if (swipeMinY < 80) {
        swipeMinY = 80;
    }

    GestureType type = GestureType::kNone;
    if (durationMs <= kTapMaxDurationMs && globalMaxMove_ <= kTapSlopPx) {
        type = GestureType::kTap;
    } else if (absDy >= swipeMinY && absDy > absDx) {
        type = avgDy < 0 ? GestureType::kSwipeUp : GestureType::kSwipeDown;
    } else if (absDx >= swipeMinX && absDx >= absDy) {
        type = avgDx < 0 ? GestureType::kSwipeLeft : GestureType::kSwipeRight;
    } else {
        return false;
    }

    pendingGesture_ = Gesture();
    pendingGesture_.fingers = maxFingers_;
    pendingGesture_.type = type;
    pendingGesture_.durationMs = durationMs;
    pendingGesture_.deltaX = avgDx;
    pendingGesture_.deltaY = avgDy;
    return true;
}

bool Recognizer::ConsumeGesture(Gesture* out) {
    if (!pending_) {
        return false;
    }
    pending_ = false;
    if (out != nullptr) {
        *out = pendingGesture_;
    }
    return true;
}

std::string Recognizer::DescribeTouches() const {
    std::string text = "fingers=" + std::to_string(fingers_.size()) + " [";
    bool first = true;
    for (const auto& kv : fingers_) {
        if (!first) {
            text += ", ";
        }
        first = false;
        text += "slot" + std::to_string(kv.first) + "(" + std::to_string(kv.second.lastX) +
                "," + std::to_string(kv.second.lastY) + ")";
    }
    text += "]";
    return text;
}

// ---------------------------------------------------------------------------
// InputReader
// ---------------------------------------------------------------------------

bool InputReader::ScanDevices() {
    for (auto& device : devices_) {
        if (device.fd >= 0) {
            close(device.fd);
        }
    }
    devices_.clear();

    DIR* dir = opendir("/dev/input");
    if (dir == nullptr) {
        FG_LOGI("无法打开 /dev/input: %s", strerror(errno));
        return false;
    }
    std::vector<std::string> paths;
    struct dirent* entry = nullptr;
    while ((entry = readdir(dir)) != nullptr) {
        std::string name = entry->d_name;
        if (name.rfind("event", 0) == 0) {
            paths.push_back("/dev/input/" + name);
        }
    }
    closedir(dir);
    std::sort(paths.begin(), paths.end());

    struct Candidate {
        Device device;
        bool hasDirect = false;
        int64_t area = 0;
    };
    std::vector<Candidate> candidates;
    int denied = 0;

    for (const auto& path : paths) {
        int fd = open(path.c_str(), O_RDONLY | O_NONBLOCK);
        if (fd < 0) {
            if (errno == EACCES || errno == EPERM) {
                denied++;
            }
            continue;
        }

        unsigned char absBits[64] = {0};
        if (ioctl(fd, EVIOCGBIT(EV_ABS, sizeof(absBits)), absBits) < 0 ||
            !TestBit(absBits, ABS_MT_POSITION_X) || !TestBit(absBits, ABS_MT_POSITION_Y)) {
            close(fd);
            continue;
        }

        Candidate candidate;
        candidate.device.fd = fd;
        candidate.device.path = path;

        char nameBuf[128] = {0};
        if (ioctl(fd, EVIOCGNAME(sizeof(nameBuf) - 1), nameBuf) >= 0) {
            candidate.device.name = nameBuf;
        } else {
            candidate.device.name = path;
        }

        struct input_absinfo xInfo = {};
        struct input_absinfo yInfo = {};
        if (ioctl(fd, EVIOCGABS(ABS_MT_POSITION_X), &xInfo) < 0 ||
            ioctl(fd, EVIOCGABS(ABS_MT_POSITION_Y), &yInfo) < 0) {
            close(fd);
            continue;
        }
        int32_t rangeX = xInfo.maximum - xInfo.minimum;
        int32_t rangeY = yInfo.maximum - yInfo.minimum;
        candidate.device.rangeX = rangeX > 0 ? rangeX : 1080;
        candidate.device.rangeY = rangeY > 0 ? rangeY : 2340;
        candidate.device.recognizer.SetRanges(candidate.device.rangeX, candidate.device.rangeY);

        unsigned char propBits[8] = {0};
        if (ioctl(fd, EVIOCGPROP(sizeof(propBits)), propBits) >= 0) {
            candidate.hasDirect = TestBit(propBits, INPUT_PROP_DIRECT);
        }
        candidate.area = (int64_t)candidate.device.rangeX * candidate.device.rangeY;

        candidates.push_back(std::move(candidate));
    }

    if (candidates.empty()) {
        if (denied > 0 && !emptyScanLogged_) {
            FG_LOGI("/dev/input 中有 %d 个设备被拒绝访问：守护进程需要以 Root 运行。", denied);
            emptyScanLogged_ = true;
        }
        return false;
    }

    // 主设备：优先 INPUT_PROP_DIRECT，其次触控面积最大
    size_t primaryIndex = 0;
    int64_t bestScore = -1;
    for (size_t i = 0; i < candidates.size(); ++i) {
        int64_t score = candidates[i].area;
        if (candidates[i].hasDirect) {
            score += (int64_t)1 << 50;
        }
        if (score > bestScore) {
            bestScore = score;
            primaryIndex = i;
        }
    }

    for (size_t i = 0; i < candidates.size(); ++i) {
        Candidate& candidate = candidates[i];
        candidate.device.primary = (i == primaryIndex);
        FG_LOGI("输入设备: %s (%s) range=%dx%d direct=%d%s", candidate.device.name.c_str(),
                candidate.device.path.c_str(), candidate.device.rangeX, candidate.device.rangeY,
                candidate.hasDirect ? 1 : 0, candidate.device.primary ? " [主设备]" : "");
        devices_.push_back(std::move(candidate.device));
    }
    emptyScanLogged_ = false;
    return true;
}

void InputReader::HandleDeviceReadable(Device& device) {
    struct input_event events[64];
    while (true) {
        ssize_t bytes = read(device.fd, events, sizeof(events));
        if (bytes < 0) {
            if (errno == EAGAIN || errno == EWOULDBLOCK) {
                return;  // 本轮读空
            }
            FG_LOGI("读取 %s 失败: %s，稍后重新扫描设备", device.path.c_str(), strerror(errno));
            close(device.fd);
            device.fd = -1;
            return;
        }
        if (bytes == 0) {
            close(device.fd);
            device.fd = -1;
            return;
        }

        size_t count = (size_t)bytes / sizeof(struct input_event);
        for (size_t i = 0; i < count; ++i) {
            const struct input_event& ev = events[i];
            if (ev.type == EV_SYN && ev.code == SYN_REPORT) {
                ProcessFrame(device);
            } else if (ev.type == EV_ABS || ev.type == EV_KEY) {
                if (device.frameEvents.size() < kMaxFrameEvents) {
                    device.frameEvents.push_back(ev);
                }
            }
        }
    }
}

void InputReader::ProcessFrame(Device& device) {
    if (device.frameEvents.empty()) {
        return;
    }

    // 单遍解析：始终缓存各槽位最新坐标，保证"坐标先于 TRACKING_ID 到达"也能正确处理
    for (const auto& ev : device.frameEvents) {
        if (ev.type != EV_ABS) {
            continue;
        }
        switch (ev.code) {
            case ABS_MT_SLOT:
                device.currentSlot = ev.value;
                break;
            case ABS_MT_TRACKING_ID:
                if (ev.value >= 0) {
                    auto posIt = device.slotPositions.find(device.currentSlot);
                    int32_t x = posIt != device.slotPositions.end() ? posIt->second.first : 0;
                    int32_t y = posIt != device.slotPositions.end() ? posIt->second.second : 0;
                    device.recognizer.OnSlotDown(device.currentSlot, x, y, EventTimeMs(ev));
                } else {
                    device.recognizer.OnSlotUp(device.currentSlot, EventTimeMs(ev));
                }
                break;
            case ABS_MT_POSITION_X: {
                auto& pos = device.slotPositions[device.currentSlot];
                pos.first = ev.value;
                device.recognizer.OnSlotMove(device.currentSlot, pos.first, pos.second);
                break;
            }
            case ABS_MT_POSITION_Y: {
                auto& pos = device.slotPositions[device.currentSlot];
                pos.second = ev.value;
                device.recognizer.OnSlotMove(device.currentSlot, pos.first, pos.second);
                break;
            }
            default:
                break;
        }
    }

    device.recognizer.OnFrameEnd(NowMs());

    Gesture gesture;
    if (device.recognizer.ConsumeGesture(&gesture)) {
        gesture.deviceName = device.name;
        if (device.primary) {
            if (callback_) {
                callback_(gesture);
            }
        } else {
            FG_LOGI("检测到手势（非主设备，仅记录）: %d 指 %s", gesture.fingers,
                    GestureTypeName(gesture.type));
        }
    }

    LogFrameCoordinates(device);
    device.frameEvents.clear();
}

void InputReader::LogFrameCoordinates(Device& device) {
    int count = device.recognizer.ActiveFingerCount();
    if (count <= 0) {
        device.lastLoggedFingers = count;
        return;
    }

    int64_t now = NowMs();
    bool countChanged = count != device.lastLoggedFingers;
    if (!countChanged && now - device.lastCoordLogMs < kCoordLogIntervalMs) {
        return;
    }
    device.lastCoordLogMs = now;
    device.lastLoggedFingers = count;
    FG_LOGI("[%s] %s", device.name.c_str(), device.recognizer.DescribeTouches().c_str());
}

void InputReader::Loop() {
    int epollFd = epoll_create1(EPOLL_CLOEXEC);
    if (epollFd < 0) {
        FG_LOGI("epoll_create1 失败: %s", strerror(errno));
        return;
    }
    std::vector<epoll_event> events(32);

    while (true) {
        Config::Get().ReloadIfChanged();

        int64_t now = NowMs();
        if (needsRescan_) {
            needsRescan_ = false;
            lastScanMs_ = now;
            ScanDevices();
            for (size_t i = 0; i < devices_.size(); ++i) {
                Device& device = devices_[i];
                if (device.fd < 0) {
                    continue;
                }
                struct epoll_event event = {};
                event.events = EPOLLIN;
                event.data.u64 = (uint64_t)i;
                epoll_ctl(epollFd, EPOLL_CTL_ADD, device.fd, &event);
            }
        } else if (devices_.empty() && now - lastScanMs_ >= kRescanIntervalMs) {
            needsRescan_ = true;
        }

        int count = epoll_wait(epollFd, events.data(), (int)events.size(), 1000);
        if (count < 0) {
            if (errno == EINTR) {
                continue;
            }
            FG_LOGI("epoll_wait 错误: %s", strerror(errno));
            continue;
        }

        for (int i = 0; i < count; ++i) {
            size_t index = (size_t)events[i].data.u64;
            if (index >= devices_.size()) {
                continue;
            }
            Device& device = devices_[index];
            if (device.fd < 0) {
                continue;
            }
            HandleDeviceReadable(device);
            if (device.fd < 0) {
                needsRescan_ = true;
            }
        }
    }
}

std::string ProbeInputDevicesForDiagnostics() {
    std::string report;
    DIR* dir = opendir("/dev/input");
    if (dir == nullptr) {
        return "无法打开 /dev/input (errno=" + std::to_string(errno) + ")";
    }

    struct dirent* entry = nullptr;
    int total = 0;
    int opened = 0;
    int denied = 0;
    int multitouch = 0;

    while ((entry = readdir(dir)) != nullptr) {
        std::string name = entry->d_name;
        if (name.rfind("event", 0) != 0) {
            continue;
        }
        total++;
        std::string path = "/dev/input/" + name;
        int fd = open(path.c_str(), O_RDONLY | O_NONBLOCK);
        if (fd < 0) {
            denied++;
            continue;
        }
        opened++;

        char nameBuf[128] = {0};
        ioctl(fd, EVIOCGNAME(sizeof(nameBuf) - 1), nameBuf);
        unsigned char absBits[64] = {0};
        bool isMultitouch = false;
        if (ioctl(fd, EVIOCGBIT(EV_ABS, sizeof(absBits)), absBits) >= 0) {
            isMultitouch =
                TestBit(absBits, ABS_MT_POSITION_X) && TestBit(absBits, ABS_MT_POSITION_Y);
        }
        if (isMultitouch) {
            multitouch++;
        }
        report += path + " -> " + (strlen(nameBuf) > 0 ? nameBuf : std::string("unknown")) +
                  (isMultitouch ? " [多点触控]" : "") + "\n";
        close(fd);
    }
    closedir(dir);

    if (report.empty()) {
        report = "共 " + std::to_string(total) + " 个输入设备，成功打开 " + std::to_string(opened) +
                 "，被拒绝 " + std::to_string(denied) +
                 "\n（以 App 身份无法读取 /dev/input 属预期行为，真正的监听在 Root 守护进程内完成）";
    } else {
        report += "多点触控设备: " + std::to_string(multitouch) + " 个\n（App 身份读取，仅供参考）";
    }
    return report;
}

}  // namespace flowgesture
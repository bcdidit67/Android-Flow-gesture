#include "config.h"

#include <sys/stat.h>

#include <fstream>

#include "actions.h"
#include "log.h"

namespace flowgesture {

namespace {

std::string Trim(const std::string& s) {
    size_t begin = s.find_first_not_of(" \t\r\n");
    if (begin == std::string::npos) {
        return "";
    }
    size_t end = s.find_last_not_of(" \t\r\n");
    return s.substr(begin, end - begin + 1);
}

}  // namespace

Config& Config::Get() {
    static Config instance;
    return instance;
}

void Config::Load(const std::string& path) {
    path_ = path;
    lastMtime_ = 0;
    lastSize_ = -1;
    ReloadIfChanged();
    FG_LOGI("配置已加载: %s (%zu 条)", path_.c_str(), map_.size());
}

void Config::ReloadIfChanged() {
    if (path_.empty()) {
        return;
    }

    struct stat st;
    if (stat(path_.c_str(), &st) != 0) {
        return;
    }
    if (st.st_mtime == lastMtime_ && (long long)st.st_size == lastSize_) {
        return;
    }
    lastMtime_ = st.st_mtime;
    lastSize_ = st.st_size;

    std::unordered_map<std::string, std::string> newMap;
    std::ifstream in(path_);
    std::string line;
    while (std::getline(in, line)) {
        line = Trim(line);
        if (line.empty() || line[0] == '#') {
            continue;
        }
        auto pos = line.find('=');
        if (pos == std::string::npos) {
            continue;
        }
        std::string key = Trim(line.substr(0, pos));
        std::string value = Trim(line.substr(pos + 1));
        if (!key.empty()) {
            newMap[key] = value;
        }
    }
    map_.swap(newMap);
    FG_LOGI("配置已重载: %zu 条", map_.size());
}

std::string Config::Lookup(const Gesture& gesture) const {
    std::string key = std::to_string(gesture.fingers) + "." + GestureTypeKey(gesture.type);
    auto it = map_.find(key);
    if (it == map_.end()) {
        return "none";
    }
    return it->second;
}

void Config::HandleGesture(const Gesture& gesture) {
    std::string action = Lookup(gesture);
    FG_LOGI("手势识别: %d 指 %s (dur=%lldms, dx=%d, dy=%d, rot=%d, raw=(%d,%d), dev=%s) -> 动作 '%s'",
            gesture.fingers, GestureTypeName(gesture.type),
            (long long)gesture.durationMs, gesture.deltaX, gesture.deltaY,
            gesture.rotationUsed, gesture.rawDeltaX, gesture.rawDeltaY,
            gesture.deviceName.c_str(), action.c_str());
    ExecuteAction(action);
}

}  // namespace flowgesture
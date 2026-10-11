#pragma once

#include <string>
#include <unordered_map>

#include "common.h"

namespace flowgesture {

/**
 * 手势映射配置。
 *
 * 配置文件由 App 生成（gesture_config.ini），格式：
 *   3.tap=notifications
 *   3.up=recents
 *   3.down=custom:input keyevent 26
 *
 * 守护进程每秒检查一次文件 mtime/size，变更后自动重载。
 */
class Config {
public:
    static Config& Get();

    void Load(const std::string& path);
    void ReloadIfChanged();

    /** 手势到达时执行映射的动作 */
    void HandleGesture(const Gesture& gesture);

private:
    Config() = default;

    std::string Lookup(const Gesture& gesture) const;

    std::string path_;
    std::unordered_map<std::string, std::string> map_;
    long long lastMtime_ = 0;
    long long lastSize_ = -1;
};

}  // namespace flowgesture
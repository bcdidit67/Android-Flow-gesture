#include "actions.h"

#include <unistd.h>

#include <string>

#include "log.h"

namespace flowgesture {

namespace {

/**
 * 后台执行命令：
 * 双重 fork + setsid 完全脱离守护进程，避免僵尸进程、避免依赖父进程存活。
 */
void SpawnCommand(const std::string& command) {
    pid_t pid = fork();
    if (pid < 0) {
        FG_LOGI("fork 失败，无法执行命令");
        return;
    }
    if (pid == 0) {
        pid_t pid2 = fork();
        if (pid2 < 0) {
            _exit(1);
        }
        if (pid2 > 0) {
            _exit(0);
        }
        setsid();
        execl("/system/bin/sh", "sh", "-c", command.c_str(), (char*)nullptr);
        _exit(127);
    }
    // 父进程不等待（守护进程已忽略 SIGCHLD）
}

}  // namespace

void RunShellCommand(const std::string& command) {
    SpawnCommand(command);
}

void ExecuteAction(const std::string& actionSpec) {
    if (actionSpec.empty() || actionSpec == "none") {
        return;
    }

    // 自定义 Shell 命令
    const std::string customPrefix = "custom:";
    if (actionSpec.rfind(customPrefix, 0) == 0) {
        std::string command = actionSpec.substr(customPrefix.size());
        if (!command.empty()) {
            FG_LOGI("执行自定义命令: %s", command.c_str());
            RunShellCommand(command);
        }
        return;
    }

    std::string command;
    if (actionSpec == "back") {
        command = "input keyevent 4";
    } else if (actionSpec == "home") {
        command = "input keyevent 3";
    } else if (actionSpec == "recents") {
        command = "input keyevent 187";
    } else if (actionSpec == "notifications") {
        command = "cmd statusbar expand-notifications";
    } else if (actionSpec == "quick_settings") {
        command = "cmd statusbar expand-settings";
    } else if (actionSpec == "play_pause") {
        command = "input keyevent 85";
    } else if (actionSpec == "next") {
        command = "input keyevent 87";
    } else if (actionSpec == "prev") {
        command = "input keyevent 88";
    } else if (actionSpec == "vol_up") {
        command = "input keyevent 24";
    } else if (actionSpec == "vol_down") {
        command = "input keyevent 25";
    } else if (actionSpec == "mute") {
        command = "input keyevent 164";
    } else if (actionSpec == "screenshot") {
        command =
            "screencap -p /sdcard/Pictures/FlowGesture_$(date +%Y%m%d_%H%M%S).png";
    } else {
        FG_LOGI("未知动作: %s", actionSpec.c_str());
        return;
    }

    FG_LOGI("执行动作命令: %s", command.c_str());
    SpawnCommand(command);
}

}  // namespace flowgesture
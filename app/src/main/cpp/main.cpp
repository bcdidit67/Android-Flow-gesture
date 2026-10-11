#include <fcntl.h>
#include <signal.h>
#include <sys/stat.h>
#include <unistd.h>

#include <string>

#include "config.h"
#include "input_reader.h"
#include "log.h"

using namespace flowgesture;

namespace {

/** 双重 fork + setsid，完全脱离终端与父进程（由 Root shell 启动） */
void Daemonize() {
    pid_t pid = fork();
    if (pid < 0) {
        _exit(1);
    }
    if (pid > 0) {
        _exit(0);
    }
    setsid();
    pid = fork();
    if (pid < 0) {
        _exit(1);
    }
    if (pid > 0) {
        _exit(0);
    }
    umask(0);

    int devNull = open("/dev/null", O_RDWR);
    if (devNull >= 0) {
        dup2(devNull, STDIN_FILENO);
        dup2(devNull, STDOUT_FILENO);
        dup2(devNull, STDERR_FILENO);
        if (devNull > 2) {
            close(devNull);
        }
    }
}

}  // namespace

/**
 * Flow Gesture 守护进程。
 *
 * 用法：
 *   flowgesture_daemon --config=<gesture_config.ini路径> --log=<日志文件路径> [--foreground]
 *
 * 以 Root 运行，直接读取 /dev/input/eventX，
 * 识别 3/4/5 指的 点击 / 上滑 / 下滑 / 左滑 / 右滑 并执行映射动作。
 */
int main(int argc, char** argv) {
    std::string configPath;
    std::string logPath;
    std::string rotationPath;
    bool foreground = false;

    for (int i = 1; i < argc; ++i) {
        std::string arg = argv[i];
        const std::string configPrefix = "--config=";
        const std::string logPrefix = "--log=";
        const std::string rotationPrefix = "--rotation=";
        if (arg.rfind(configPrefix, 0) == 0) {
            configPath = arg.substr(configPrefix.size());
        } else if (arg.rfind(logPrefix, 0) == 0) {
            logPath = arg.substr(logPrefix.size());
        } else if (arg.rfind(rotationPrefix, 0) == 0) {
            rotationPath = arg.substr(rotationPrefix.size());
        } else if (arg == "--foreground") {
            foreground = true;
        }
    }

    if (!foreground) {
        Daemonize();
    }

    log::Init(logPath);
    FG_LOGI("FlowGesture 守护进程启动: pid=%d uid=%d config=%s", getpid(), getuid(),
            configPath.c_str());

    if (getuid() != 0) {
        FG_LOGI("警告: 未以 Root 运行，可能无法读取 /dev/input");
    }

    signal(SIGCHLD, SIG_IGN);
    signal(SIGPIPE, SIG_IGN);

    Config::Get().Load(configPath);

    InputReader reader;
    reader.SetRotationFile(rotationPath);
    reader.SetGestureCallback([](const Gesture& gesture) {
        Config::Get().HandleGesture(gesture);
    });
    reader.Loop();  // 阻塞

    return 0;
}
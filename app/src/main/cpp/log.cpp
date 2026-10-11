#include "log.h"

#include <android/log.h>
#include <unistd.h>

#include <cstdarg>
#include <cstdio>
#include <ctime>
#include <mutex>

namespace flowgesture {
namespace log {

namespace {

std::mutex g_mutex;
FILE* g_file = nullptr;

constexpr const char* kTag = "FlowGesture";
constexpr long kMaxFileSize = 2 * 1024 * 1024;  // 2MB 自动截断

void FormatTime(char* buf, size_t len) {
    struct timespec ts;
    clock_gettime(CLOCK_REALTIME, &ts);
    struct tm tmv;
    localtime_r(&ts.tv_sec, &tmv);
    snprintf(buf, len, "%02d:%02d:%02d.%03ld", tmv.tm_hour, tmv.tm_min, tmv.tm_sec,
             ts.tv_nsec / 1000000);
}

}  // namespace

void Init(const std::string& logFilePath) {
    std::lock_guard<std::mutex> lock(g_mutex);
    if (logFilePath.empty()) {
        return;
    }

    // 防止日志文件无限增长
    FILE* probe = fopen(logFilePath.c_str(), "rb");
    if (probe != nullptr) {
        fseek(probe, 0, SEEK_END);
        long size = ftell(probe);
        fclose(probe);
        if (size > kMaxFileSize) {
            truncate(logFilePath.c_str(), 0);
        }
    }

    g_file = fopen(logFilePath.c_str(), "a");
}

void Write(const char* fmt, ...) {
    va_list args;
    va_start(args, fmt);
    va_list argsCopy;
    va_copy(argsCopy, args);

    __android_log_vprint(ANDROID_LOG_INFO, kTag, fmt, args);
    va_end(args);

    std::lock_guard<std::mutex> lock(g_mutex);
    if (g_file != nullptr) {
        char timeBuf[32];
        FormatTime(timeBuf, sizeof(timeBuf));
        fprintf(g_file, "[%s] ", timeBuf);
        vfprintf(g_file, fmt, argsCopy);
        fputc('\n', g_file);
        fflush(g_file);
    }
    va_end(argsCopy);
}

}  // namespace log
}  // namespace flowgesture

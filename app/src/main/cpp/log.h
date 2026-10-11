#pragma once

#include <string>

namespace flowgesture {
namespace log {

/** 初始化日志文件（同时仍输出到 logcat）。logFilePath 为空则只输出 logcat */
void Init(const std::string& logFilePath);

/** printf 风格日志：同时写入 logcat 与日志文件 */
void Write(const char* fmt, ...) __attribute__((format(printf, 1, 2)));

}  // namespace log
}  // namespace flowgesture

#define FG_LOGI(...) ::flowgesture::log::Write(__VA_ARGS__)

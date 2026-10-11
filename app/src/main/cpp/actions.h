#pragma once

#include <string>

namespace flowgesture {

/** 执行一条动作描述（对应配置文件 value），如 "recents"、"custom:input keyevent 26" */
void ExecuteAction(const std::string& actionSpec);

/** 以 Root 身份在后台执行 Shell 命令 */
void RunShellCommand(const std::string& command);

}  // namespace flowgesture
# Flow Gesture — Android 全局多指手势增强（Root 方案）

对标 Windows 触摸板的高级手势设置，为 Android 带来丰富的全局触摸手势：
**三指 / 四指 / 五指** 的 **点击、上滑、下滑、左滑、右滑**（共 15 种组合），
可映射到系统动作或自定义 Shell 命令。

> ⚠️ **需要 Root**（Magisk / KernelSU）。本应用**不使用无障碍服务（Accessibility Service），
> 也不使用悬浮窗（Overlay）**——所有触摸监听由 Root 守护进程直接读取 `/dev/input` 完成，
> 不丢事件、不掉帧、不易被杀。

---

## 功能特性

- **原始触摸监听**：Native (C++) 守护进程直接读取 `/dev/input/eventX`，
  解析 Linux 多点触控协议 B（MT Protocol B），epoll 驱动，零轮询开销；
- **15 种手势**：3/4/5 指 ×（点击 / 上滑 / 下滑 / 左滑 / 右滑）；
- **丰富的动作映射**：
  - 返回、主屏幕 / 显示桌面、任务视图、通知中心、快捷设置
  - 播放 / 暂停、上一曲、下一曲、音量 +/-、静音
  - 截屏
  - 自定义 Shell 命令（以 Root 身份执行）
- **实时配置热更新**：App 修改映射后无需重启守护进程（配置文件自动重载）；
- **数据持久化**：Jetpack Compose 界面 + DataStore；
- **常驻服务**：前台服务保活 + 开机自启（已处理 Android 12+ 后台启动前台服务限制，
  并提供 Root `am` 兜底启动方案）；
- **可观测**：手势守护进程日志写入 App 私有目录，App 内直接查看（同时输出 logcat，
  TAG=`FlowGesture`）。

## 架构

```
┌───────────────────────────┐
│  Compose 配置界面 │
│  (三/四/五指映射编辑) │
└────────────┬──────────────┘
             │ DataStore 持久化
             ▼
   filesDir/gesture_config.ini  ◄─── 热重载（每秒检查 mtime）
             ▲                     │
             │ (libsu / root)      ▼
┌────────────┴──────────────┐   ┌───────────────────────────────┐
│  前台服务 GestureService  │──►│  守护进程 flowgesture_daemon  │
│  · 启动/监控守护进程      │   │  · open /dev/input/eventX     │
│  · 30s 周期健康检查       │   │  · epoll + 协议B 解析         │
│  · 开机自启               │   │  · 3/4/5 指手势识别           │
└───────────────────────────┘   │  · input keyevent / 自定义命令 │
                                └───────────────────────────────┘
```

**为什么不用无障碍 / 悬浮窗？**
它们在全局连续手势监听场景下存在丢事件、掉帧、被系统回收等问题；
而 Root 守护进程直接消费输入子系统原始事件，识别与响应都在事件源头完成，
再由 Root 注入动作，稳定性和延迟都远优于前者。

## 使用方法

1. 从 [Releases](../../releases) 下载 `FlowGesture-vX.X.X-release.apk` 并安装；
2. 打开 App，按提示授予 **Root 权限**（Magisk / KernelSU 弹窗）；
3. 点击「启动服务」，状态卡片应显示三项全部为运行状态：
   - Root 权限：已获取
   - 前台服务：运行中
   - 手势守护进程：运行中
4. 在「手势映射」中为 3/4/5 指配置动作（首次安装已内置一套默认映射）；
5. 手势立即生效。「运行日志」面板可实时查看触摸坐标与识别记录。

## 从源码构建

**环境要求**：JDK 17+、Android SDK（compileSdk 37、build-tools 37.0.0）、网络可访问
Google Maven / Maven Central / JitPack（国内网络已内置启动构建可用说明）。

**依赖版本**：AGP 9.4.0 · Kotlin 2.4.20 · Compose BOM 2026.09.00 · libsu 5.2.2

```bash
# 使用仓库自带 wrapper（distributionUrl 已指向腾讯镜像，国内可直接使用）
./gradlew assembleDebug     # 调试包
./gradlew assembleRelease   # 发布包（当前版本使用 debug 签名，可直接安装）
```

> 提示：AGP 9.x 默认启用「内置 Kotlin」，本仓库通过
> `android.newDsl=false` + `android.builtInKotlin=false`（见 `gradle.properties`）
> 暂时保持经典 `kotlin-android` 插件工作流，待上游生态完全迁移后再切换。

### 原生部分（C++）

仓库的 `app/src/main/jniLibs/arm64-v8a/` 内**已包含预编译好的原生库**，直接构建即可：

- `libflowgesture_daemon.so` — Root 手势守护进程（本质是可执行文件，以 lib 前缀命名以便打包）
- `libflowgesture_jni.so` — JNI 诊断库

拥有完整官方 NDK 的开发者，可以从源码重建原生部分：

```bash
./gradlew assembleDebug -PbuildNativeFromSource=true   # 需要 SDK 中已安装 NDK + CMake
```

或在无官方 aarch64 NDK 的特殊环境（如 Termux / proot Ubuntu）中使用脚本方案
（主机 clang + NDK sysroot）：

```bash
export FG_NDK_SYSROOT=/path/to/ndk/sysroot
export FG_NDK_CLANG_RES=/path/to/clang-resource-dir   # 含 builtins / libunwind
./scripts/build_native.sh
```

## 目录结构

```
app/src/main/
├── cpp/                        # C++ 原生源码
│   ├── main.cpp                # 守护进程入口（daemonize、参数解析）
│   ├── input_reader.{h,cpp}    # 设备扫描 / epoll / 协议B解析 / 手势识别
│   ├── config.{h,cpp}          # 配置加载与热重载
│   ├── actions.{h,cpp}         # 动作执行（input keyevent / 自定义命令）
│   ├── log.{h,cpp}             # logcat + 文件双写日志
│   ├── common.{h,cpp}          # 公共定义
│   ├── jni_bridge.cpp          # JNI 桥（诊断接口）
│   └── CMakeLists.txt          # 标准 NDK 构建入口
├── java/com/flowgesture/app/
│   ├── MainActivity.kt
│   ├── App.kt
│   ├── core/                   # RootManager / DaemonController / 动作定义 / 状态桥
│   ├── data/                   # DataStore 仓库与手势方向定义
│   ├── service/                # 前台服务 + 开机自启
│   └── ui/                     # Compose 设置界面
└── jniLibs/arm64-v8a/          # 预编译原生库
scripts/build_native.sh         # 特殊环境的原生构建脚本
```

## 已知限制（v0.1.0 MVP）

- 仅提供 `arm64-v8a` 预编译原生库；
- 手势判定阈值（位移 / 时长）暂为内置常量，后续版本将开放「灵敏度」设置；
- 不拦截单指 / 双指手势（保持系统与第三方应用原有交互不受影响）；
- 部分厂商 ROM 的省电策略可能影响前台服务存活，可在系统设置中为本应用关闭省电限制。

## Roadmap

- [ ] armeabi-v7a 支持
- [ ] 灵敏度与阈值图形化配置
- [ ] 长按 / 按住滑动（Hold & Swipe）等更多手势
- [ ] 按前台应用区分映射规则
- [ ] 手势振动反馈

## 免责声明

本项目需要 Root 权限并直接读写系统输入设备，请自行评估风险；使用本软件即表示您了解
并接受可能的风险。请勿用于任何违反所在地区法律法规的用途。

## License

[MIT](LICENSE)

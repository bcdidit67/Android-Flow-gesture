#!/usr/bin/env bash
#
# Flow Gesture - 原生库构建脚本
#
# 适用场景：aarch64 主机（如 proot Ubuntu / Termux 环境）上没有官方 aarch64 版 NDK 时，
# 使用「主机 clang + NDK sysroot」方案交叉编译 Android arm64 原生库。
#
# 前置要求：
#   1. 主机安装 clang / lld（Ubuntu: apt install clang lld）
#   2. 从官方 NDK（如 android-ndk-r27c-linux.zip）中提取出 sysroot 目录，
#      并用环境变量 FG_NDK_SYSROOT 指向它，例如：
#        export FG_NDK_SYSROOT=/path/to/android-ndk-r27c/toolchains/llvm/prebuilt/linux-x86_64/sysroot
#   3. 准备 clang 资源目录（builtins + libunwind），用 FG_NDK_CLANG_RES 指向它：
#        RES=/path/to/res
#        mkdir -p $RES/lib/linux/aarch64 $RES/include
#        cp -r <ndk>/lib/clang/18/include $RES/include
#        cp <ndk>/lib/clang/18/lib/linux/libclang_rt.builtins-aarch64-android.a $RES/lib/linux/
#        cp <ndk>/lib/clang/18/lib/linux/aarch64/libunwind.a $RES/lib/linux/aarch64/
#
# 产物：
#   app/src/main/jniLibs/arm64-v8a/libflowgesture_daemon.so  （Root 手势守护进程）
#   app/src/main/jniLibs/arm64-v8a/libflowgesture_jni.so     （JNI 诊断库）

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
PROJECT_DIR="$(dirname "$SCRIPT_DIR")"
CPP_DIR="$PROJECT_DIR/app/src/main/cpp"
JNI_LIBS="$PROJECT_DIR/app/src/main/jniLibs/arm64-v8a"

: "${FG_NDK_SYSROOT:?请设置 FG_NDK_SYSROOT 指向 NDK sysroot 目录}"
: "${FG_NDK_CLANG_RES:?请设置 FG_NDK_CLANG_RES 指向 clang 资源目录（含 builtins）}"

CXX="${CXX:-clang++}"
OUT_DIR="${FG_OUT_DIR:-$PROJECT_DIR/build/native-obj}"

TARGET="aarch64-linux-android26"
LIBS26="$FG_NDK_SYSROOT/usr/lib/aarch64-linux-android/26"
LIBS="$FG_NDK_SYSROOT/usr/lib/aarch64-linux-android"

CXX_FLAGS=(
    "--target=$TARGET"
    "--sysroot=$FG_NDK_SYSROOT"
    "-resource-dir=$FG_NDK_CLANG_RES"
    "-stdlib=libc++"
    "-fPIC" "-O2" "-Wall"
    "-fno-exceptions" "-fno-rtti"
    "--rtlib=compiler-rt"
)

LINK_FLAGS=(
    "-fuse-ld=lld"
    "-static-libstdc++"
    "-Wl,-z,max-page-size=16384"
    "-Wl,--gc-sections"
    "-Wl,-s"
    "-L$LIBS26" "-L$LIBS" "-L$FG_NDK_CLANG_RES/lib/linux/aarch64"
    "-llog" "-lunwind"
)

mkdir -p "$OUT_DIR" "$JNI_LIBS"

echo "==> 编译核心对象文件"
CORE_OBJS=()
for f in common log config actions input_reader main; do
    echo "    - $f.cpp"
    "$CXX" "${CXX_FLAGS[@]}" -c "$CPP_DIR/$f.cpp" -o "$OUT_DIR/$f.o"
    CORE_OBJS+=("$OUT_DIR/$f.o")
done

echo "    - jni_bridge.cpp"
"$CXX" "${CXX_FLAGS[@]}" -c "$CPP_DIR/jni_bridge.cpp" -o "$OUT_DIR/jni_bridge.o"

echo "==> 链接守护进程 flowgesture_daemon"
"$CXX" "${CXX_FLAGS[@]}" "${LINK_FLAGS[@]}" \
    -o "$OUT_DIR/flowgesture_daemon" "${CORE_OBJS[@]}"

echo "==> 链接 JNI 库 libflowgesture_jni.so"
"$CXX" "${CXX_FLAGS[@]}" "${LINK_FLAGS[@]}" -shared \
    -o "$OUT_DIR/libflowgesture_jni.so" \
    "$OUT_DIR/jni_bridge.o" \
    "$OUT_DIR/common.o" "$OUT_DIR/log.o" "$OUT_DIR/config.o" \
    "$OUT_DIR/actions.o" "$OUT_DIR/input_reader.o"

echo "==> 复制到 jniLibs"
cp "$OUT_DIR/flowgesture_daemon" "$JNI_LIBS/libflowgesture_daemon.so"
cp "$OUT_DIR/libflowgesture_jni.so" "$JNI_LIBS/libflowgesture_jni.so"

echo "==> 完成"
ls -la "$JNI_LIBS"
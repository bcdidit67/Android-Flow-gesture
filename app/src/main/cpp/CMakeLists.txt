cmake_minimum_required(VERSION 3.22.1)
project(flowgesture LANGUAGES CXX)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

set(FLOWGESTURE_CORE_SOURCES
    main.cpp
    common.cpp
    log.cpp
    config.cpp
    actions.cpp
    input_reader.cpp
)

# ---------------------------------------------------------------------------
# 手势守护进程：独立可执行文件，由 App 通过 Root 复制到 /data/local/tmp 后启动。
# 以 lib*.so 命名以便 AGP 将其打包进 APK（useLegacyPackaging=true 时会被解压）。
# ---------------------------------------------------------------------------
add_executable(flowgesture_daemon ${FLOWGESTURE_CORE_SOURCES})
target_link_libraries(flowgesture_daemon log)
set_target_properties(flowgesture_daemon PROPERTIES
    PREFIX "lib"
    SUFFIX ".so"
    OUTPUT_NAME "flowgesture_daemon"
)

# ---------------------------------------------------------------------------
# JNI 库：供 App 调用的诊断接口与版本信息。
# ---------------------------------------------------------------------------
add_library(flowgesture_jni SHARED
    jni_bridge.cpp
    common.cpp
    log.cpp
    config.cpp
    actions.cpp
    input_reader.cpp
)
target_link_libraries(flowgesture_jni log)
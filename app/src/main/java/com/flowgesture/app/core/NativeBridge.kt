package com.flowgesture.app.core

/**
 * JNI 桥接：加载原生库，提供版本与输入设备探测等诊断能力。
 *
 * 注意：App 进程自身无法读取 /dev/input（SELinux 限制），
 * 真正的手势监听在 Root 守护进程（flowgesture_daemon）中完成。
 */
object NativeBridge {

    init {
        try {
            System.loadLibrary("flowgesture_jni")
        } catch (_: Throwable) {
            // 原生库缺失时降级为纯诊断提示，不影响主流程
        }
    }

    private external fun nativeVersion(): String
    private external fun nativeProbeInputDevices(): String

    fun version(): String = runCatching { nativeVersion() }.getOrDefault("native 未加载")

    /** 探测 /dev/input 设备（用于诊断：以 App 身份通常无权限，属预期行为） */
    fun probeInputDevices(): String =
        runCatching { nativeProbeInputDevices() }.getOrDefault("native 未加载")
}
#include <jni.h>

#include <string>

#include "input_reader.h"

/**
 * JNI 桥接（libflowgesture_jni.so）。
 *
 * 注意：App 进程受 SELinux 限制无法读取 /dev/input，
 * 这里提供的接口用于诊断与版本展示。
 */
extern "C" JNIEXPORT jstring JNICALL
Java_com_flowgesture_app_core_NativeBridge_nativeVersion(JNIEnv* env, jobject /*thiz*/) {
    return env->NewStringUTF("flowgesture-core 0.1.0");
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_flowgesture_app_core_NativeBridge_nativeProbeInputDevices(JNIEnv* env,
                                                                   jobject /*thiz*/) {
    std::string report = flowgesture::ProbeInputDevicesForDiagnostics();
    return env->NewStringUTF(report.c_str());
}
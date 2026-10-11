package com.flowgesture.app.core

import android.content.Context
import android.hardware.display.DisplayManager
import android.view.Display
import java.io.File

/**
 * 屏幕旋转状态上报。
 *
 * 背景：触摸面板的原始事件坐标固定在「设备自然方向」（通常是竖屏）的坐标系中，
 * 而显示旋转由系统在框架层完成。守护进程直接读取 /dev/input 拿到的是原始坐标，
 * 因此横屏时必须按当前显示旋转做坐标变换，否则上/下/左/右会错乱。
 *
 * 本类负责把当前显示旋转（0/1/2/3，对应 Surface.ROTATION_*）写入文件，
 * 由守护进程读取（--rotation=<file>）。
 */
object RotationReporter {

    const val ROTATION_FILE = "display_rotation.txt"

    /** 读取当前显示旋转（无显示信息时回退为 0 = 竖屏） */
    fun currentRotation(context: Context): Int {
        return try {
            val manager = context.getSystemService(DisplayManager::class.java) ?: return 0
            val display = manager.getDisplay(Display.DEFAULT_DISPLAY) ?: return 0
            display.rotation
        } catch (_: Throwable) {
            0
        }
    }

    fun writeRotation(context: Context, rotation: Int, mirror: Boolean, override: Int) {
        try {
            // 文件内容： "<rotation> <mirror> <override>"，例如 "1 0 -1"
            File(context.filesDir, ROTATION_FILE)
                .writeText("$rotation ${if (mirror) 1 else 0} $override")
        } catch (_: Throwable) {
            // 写入失败不影响主流程
        }
    }

    fun writeCurrent(context: Context, mirror: Boolean = false, override: Int = -1) {
        writeRotation(context, currentRotation(context), mirror, override)
    }
}
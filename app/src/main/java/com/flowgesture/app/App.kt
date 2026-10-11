package com.flowgesture.app

import android.app.Application

/**
 * Flow Gesture Application。
 *
 * 说明：本应用刻意不使用无障碍服务（AccessibilityService）与悬浮窗（Overlay），
 * 所有触摸监听与动作注入均由 Root 守护进程完成。
 */
class App : Application()
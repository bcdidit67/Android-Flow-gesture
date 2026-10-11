package com.flowgesture.app.core

import kotlinx.coroutines.flow.MutableStateFlow

/** 跨组件（Service / UI）共享的运行时状态 */
object ServiceBridge {
    val serviceRunning = MutableStateFlow(false)
    val daemonRunning = MutableStateFlow(false)
    val lastError = MutableStateFlow<String?>(null)
}
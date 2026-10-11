package com.flowgesture.app.core

import com.topjohnwu.superuser.Shell
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** libsu 封装：获取 Root 并执行 Root 命令 */
object RootManager {

    @Volatile
    private var initialized = false

    private fun ensureInit() {
        if (!initialized) {
            synchronized(this) {
                if (!initialized) {
                    Shell.enableVerboseLogging = false
                    initialized = true
                }
            }
        }
    }

    /** 是否已获得 Root 权限（会触发 su 授权弹窗） */
    suspend fun isRootAvailable(): Boolean = withContext(Dispatchers.IO) {
        try {
            ensureInit()
            Shell.getShell().isRoot
        } catch (t: Throwable) {
            false
        }
    }

    /** 以 Root 身份执行一条或多条命令 */
    suspend fun run(vararg commands: String): Shell.Result = withContext(Dispatchers.IO) {
        ensureInit()
        Shell.cmd(*commands).exec()
    }
}
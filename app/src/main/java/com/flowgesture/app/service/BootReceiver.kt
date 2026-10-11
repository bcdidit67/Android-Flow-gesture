package com.flowgesture.app.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.flowgesture.app.core.RootManager
import com.flowgesture.app.data.SettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * 开机自启。
 *
 * 行为约束（避免打扰用户）：
 * 1. 仅当用户主动开启过服务（service_enabled = true）时，设备重启后才自动拉起；
 * 2. 未获取 Root 时不启动（无实际作用，只会徒增前台服务通知）；
 * 3. Android 12+ 对后台启动前台服务有限制，BOOT_COMPLETED 属于官方豁免场景；
 *    针对厂商额外限制，另有 Root（am 命令）兜底方案。
 */
class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) {
            return
        }

        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val repository = SettingsRepository.get(context)
                // 用户从未主动开启过服务 → 不自动启动
                if (!repository.serviceEnabledFlow.first()) {
                    return@launch
                }
                // 未获取 Root → 不启动
                if (!RootManager.isRootAvailable()) {
                    return@launch
                }
                startServiceSafely(context)
            } catch (_: Throwable) {
                // 静默失败：用户仍可手动打开 App 启动
            } finally {
                pending.finish()
            }
        }
    }

    private suspend fun startServiceSafely(context: Context) {
        // 方案 1：常规前台服务启动
        try {
            GestureService.start(context)
            return
        } catch (_: Throwable) {
            // 继续尝试兜底方案
        }

        // 方案 2：通过 Root 执行 am 命令启动（绕过厂商后台限制）
        try {
            RootManager.run(
                "am start-foreground-service -n ${context.packageName}/${GestureService::class.java.name}"
            )
        } catch (_: Throwable) {
            // 两种方案都失败时静默
        }
    }
}
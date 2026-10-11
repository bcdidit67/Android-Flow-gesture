package com.flowgesture.app.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import com.flowgesture.app.MainActivity
import com.flowgesture.app.R
import com.flowgesture.app.core.DaemonController
import com.flowgesture.app.core.ServiceBridge
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull

/**
 * 常驻前台服务：保持 Root 守护进程存活。
 *
 * - 服务启动时拉起守护进程；
 * - 每隔一段时间检查一次守护进程状态，意外退出时自动重启；
 * - 服务被停止时同步停掉守护进程。
 */
class GestureService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var monitorJob: Job? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        ServiceBridge.serviceRunning.value = true
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                stopForegroundAndSelf()
                return START_NOT_STICKY
            }

            ACTION_RESTART_DAEMON -> {
                startInForeground()
                scope.launch { doStartDaemon(forceRestart = true) }
                return START_STICKY
            }

            else -> {
                startInForeground()
                scope.launch { doStartDaemon(forceRestart = false) }
            }
        }
        return START_STICKY
    }

    private fun startInForeground() {
        try {
            val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            } else {
                0
            }
            ServiceCompat.startForeground(this, NOTIFICATION_ID, buildNotification(), type)
        } catch (t: Throwable) {
            ServiceBridge.lastError.value = "前台服务启动失败: ${t.message}"
        }
    }

    private fun stopForegroundAndSelf() {
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private suspend fun doStartDaemon(forceRestart: Boolean) {
        try {
            if (forceRestart || !DaemonController.isDaemonRunning()) {
                DaemonController.startDaemon(applicationContext)
                    .onFailure { ServiceBridge.lastError.value = "启动守护进程失败: ${it.message}" }
            }
            delay(400)
            val alive = DaemonController.isDaemonRunning()
            ServiceBridge.daemonRunning.value = alive
            if (!alive) {
                ServiceBridge.lastError.value = "守护进程未运行，请检查 Root 授权与运行日志"
            }
        } catch (t: Throwable) {
            ServiceBridge.lastError.value = t.message
        }
        startMonitor()
    }

    private fun startMonitor() {
        if (monitorJob?.isActive == true) return
        monitorJob = scope.launch {
            while (isActive) {
                delay(MONITOR_INTERVAL_MS)
                try {
                    var alive = DaemonController.isDaemonRunning()
                    if (!alive) {
                        DaemonController.startDaemon(applicationContext)
                        delay(400)
                        alive = DaemonController.isDaemonRunning()
                    }
                    ServiceBridge.daemonRunning.value = alive
                } catch (_: Throwable) {
                    // 忽略单次检查异常
                }
            }
        }
    }

    override fun onDestroy() {
        ServiceBridge.serviceRunning.value = false
        ServiceBridge.daemonRunning.value = false
        monitorJob?.cancel()
        // 停止守护进程（阻塞一小段，确保发得出去）
        runBlocking {
            withTimeoutOrNull(3000) { DaemonController.stopDaemon() }
        }
        scope.cancel()
        super.onDestroy()
    }

    private fun createNotificationChannel() {
        val manager = getSystemService(NotificationManager::class.java) ?: return
        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.fg_service_channel_name),
            NotificationManager.IMPORTANCE_LOW,
        )
        manager.createNotificationChannel(channel)
    }

    private fun buildNotification(): Notification {
        val intent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_gesture)
            .setContentTitle(getString(R.string.fg_service_title))
            .setContentText(getString(R.string.fg_service_text))
            .setOngoing(true)
            .setContentIntent(pendingIntent)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .build()
    }

    companion object {
        const val ACTION_STOP = "com.flowgesture.app.action.STOP"
        const val ACTION_RESTART_DAEMON = "com.flowgesture.app.action.RESTART_DAEMON"

        private const val CHANNEL_ID = "gesture_service"
        private const val NOTIFICATION_ID = 1001
        private const val MONITOR_INTERVAL_MS = 30_000L

        fun start(context: Context) {
            try {
                val intent = Intent(context, GestureService::class.java)
                context.startForegroundService(intent)
            } catch (t: Throwable) {
                ServiceBridge.lastError.value = "启动服务失败: ${t.message}"
            }
        }

        fun stop(context: Context) {
            try {
                val intent = Intent(context, GestureService::class.java).setAction(ACTION_STOP)
                context.startService(intent)
            } catch (_: Throwable) {
                // 服务不存在时忽略
            }
        }

        fun restartDaemon(context: Context) {
            try {
                val intent = Intent(context, GestureService::class.java).setAction(ACTION_RESTART_DAEMON)
                context.startService(intent)
            } catch (_: Throwable) {
                // 服务不存在时忽略
            }
        }
    }
}
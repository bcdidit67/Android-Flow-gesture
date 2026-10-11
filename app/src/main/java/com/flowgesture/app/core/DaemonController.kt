package com.flowgesture.app.core

import android.content.Context
import com.flowgesture.app.data.SettingsRepository
import com.topjohnwu.superuser.Shell
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * 守护进程控制器。
 *
 * 工作方式：
 * 1. 通过 libsu 获取 Root shell；
 * 2. 将 APK 中打包的原生守护进程（libflowgesture_daemon.so）复制到 /data/local/tmp 并赋可执行权限；
 * 3. 以 Root 身份启动守护进程（自主 daemonize，独立于 App 进程存活）；
 * 4. 守护进程直接读取 /dev/input/eventX 并执行手势动作。
 */
object DaemonController {

    const val DAEMON_LOG_FILE = "daemon.log"

    private const val DAEMON_NAME = "flowgesture_daemon"
    private const val TMP_DIR = "/data/local/tmp/flowgesture"

    /** 启动（或重启）守护进程 */
    suspend fun startDaemon(context: Context): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val shell = Shell.getShell()
            if (!shell.isRoot) {
                return@withContext Result.failure(IllegalStateException("未获得 Root 权限"))
            }

            // 先确保配置文件存在且为最新
            SettingsRepository.get(context).writeConfigFile()

            val libDir = context.applicationInfo.nativeLibraryDir
            val configPath = File(context.filesDir, SettingsRepository.CONFIG_FILE_NAME).absolutePath
            val logPath = File(context.filesDir, DAEMON_LOG_FILE).absolutePath

            val commands = arrayOf(
                "mkdir -p $TMP_DIR",
                "cp -f \"$libDir/lib$DAEMON_NAME.so\" \"$TMP_DIR/$DAEMON_NAME\"",
                "chmod 755 \"$TMP_DIR/$DAEMON_NAME\"",
                "pkill -f \"$TMP_DIR/$DAEMON_NAME\" >/dev/null 2>&1 || true",
                "\"$TMP_DIR/$DAEMON_NAME\" --config=\"$configPath\" --log=\"$logPath\" </dev/null >/dev/null 2>&1 &",
            )
            shell.newJob().add(*commands).exec()

            Result.success(Unit)
        } catch (t: Throwable) {
            Result.failure(t)
        }
    }

    /** 守护进程是否存活 */
    suspend fun isDaemonRunning(): Boolean = withContext(Dispatchers.IO) {
        try {
            val shell = Shell.getShell()
            if (!shell.isRoot) return@withContext false
            shell.newJob().add("pgrep -f \"$TMP_DIR/$DAEMON_NAME\"").exec().isSuccess
        } catch (t: Throwable) {
            false
        }
    }

    /** 停止守护进程 */
    suspend fun stopDaemon(): Boolean = withContext(Dispatchers.IO) {
        try {
            val shell = Shell.getShell()
            if (!shell.isRoot) return@withContext false
            shell.newJob().add("pkill -f \"$TMP_DIR/$DAEMON_NAME\" >/dev/null 2>&1").exec()
            true
        } catch (t: Throwable) {
            false
        }
    }

    /** 读取守护进程日志尾部，供 UI 展示 */
    suspend fun readLogTail(context: Context, maxLines: Int = 200): String =
        withContext(Dispatchers.IO) {
            try {
                val file = File(context.filesDir, DAEMON_LOG_FILE)
                if (!file.exists()) return@withContext "(日志文件尚未生成)"
                val lines = file.readLines()
                lines.takeLast(maxLines).joinToString("\n")
            } catch (t: Throwable) {
                "读取日志失败: ${t.message}"
            }
        }
}
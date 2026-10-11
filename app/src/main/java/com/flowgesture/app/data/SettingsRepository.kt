package com.flowgesture.app.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.flowgesture.app.core.ActionType
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.io.File

/** 单个手势的配置项 */
data class GestureSettings(
    val action: ActionType = ActionType.NONE,
    val customCommand: String = "",
)

private val Context.dataStore by preferencesDataStore(name = "gesture_settings")

/**
 * 手势映射与偏好设置仓库。
 *
 * 职责：
 * 1. DataStore 持久化用户配置（手势映射 + 外观 / 方向 / 行为偏好）；
 * 2. 将手势映射生成为守护进程可读的 gesture_config.ini（守护进程监视变更并热重载）。
 */
class SettingsRepository private constructor(private val context: Context) {

    val settingsFlow: Flow<Map<String, GestureSettings>> = context.dataStore.data.map { prefs ->
        val result = LinkedHashMap<String, GestureSettings>()
        for (fingers in MIN_FINGERS..MAX_FINGERS) {
            for (direction in GestureDirection.entries) {
                val key = keyOf(fingers, direction)
                val action = ActionType.fromId(
                    prefs[actionKey(key)] ?: DEFAULT_ACTIONS[key]?.id
                )
                val custom = prefs[customKey(key)] ?: ""
                result[key] = GestureSettings(action, custom)
            }
        }
        result
    }

    /** 是否启用动态取色（Material You / Monet） */
    val dynamicColorFlow: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[DYNAMIC_COLOR_KEY] ?: true
    }

    suspend fun setDynamicColor(enabled: Boolean) {
        context.dataStore.edit { prefs -> prefs[DYNAMIC_COLOR_KEY] = enabled }
    }

    /**
     * 用户是否主动开启过手势服务。
     * 仅当为 true 时，开机自启才会拉起服务，避免安装 / 更新后自行打扰用户。
     */
    val serviceEnabledFlow: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[SERVICE_ENABLED_KEY] ?: false
    }

    suspend fun setServiceEnabled(enabled: Boolean) {
        context.dataStore.edit { prefs -> prefs[SERVICE_ENABLED_KEY] = enabled }
    }

    /** 开机自启（仅在用户开启过服务之后生效） */
    val bootAutoStartFlow: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[BOOT_AUTOSTART_KEY] ?: true
    }

    suspend fun setBootAutoStart(enabled: Boolean) {
        context.dataStore.edit { prefs -> prefs[BOOT_AUTOSTART_KEY] = enabled }
    }

    /**
     * 横屏左右反向：部分 ROM / 面板的横屏约定与另一侧相反时开启。
     * 仅影响横屏（旋转 1 / 3），竖屏不受影响。
     */
    val landscapeFlipFlow: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[LANDSCAPE_FLIP_KEY] ?: false
    }

    suspend fun setLandscapeFlip(enabled: Boolean) {
        context.dataStore.edit { prefs -> prefs[LANDSCAPE_FLIP_KEY] = enabled }
    }

    /**
     * 横屏旋转档位：-1 = 自动（跟随系统旋转），0..3 = 横屏时强制使用该档位。
     * **只作用于横屏**，竖屏恒按 0° 处理。
     * 旧版本键名（rotation_override）会作为回退值读取一次。
     */
    val landscapeRotationFlow: Flow<Int> = context.dataStore.data.map { prefs ->
        prefs[LANDSCAPE_ROTATION_KEY] ?: prefs[LEGACY_ROTATION_OVERRIDE_KEY] ?: -1
    }

    suspend fun setLandscapeRotation(value: Int) {
        context.dataStore.edit { prefs -> prefs[LANDSCAPE_ROTATION_KEY] = value }
    }

    suspend fun setAction(fingers: Int, direction: GestureDirection, action: ActionType) {
        context.dataStore.edit { prefs ->
            prefs[actionKey(keyOf(fingers, direction))] = action.id
        }
        writeConfigFile()
    }

    suspend fun setCustomCommand(fingers: Int, direction: GestureDirection, command: String) {
        context.dataStore.edit { prefs ->
            prefs[customKey(keyOf(fingers, direction))] = command
        }
        writeConfigFile()
    }

    /** 恢复默认映射：清空所有自定义映射（默认仅保留「三指上滑 / 三指下滑」两条） */
    suspend fun resetMappings() {
        context.dataStore.edit { prefs ->
            for (fingers in MIN_FINGERS..MAX_FINGERS) {
                for (direction in GestureDirection.entries) {
                    val key = keyOf(fingers, direction)
                    prefs.remove(actionKey(key))
                    prefs.remove(customKey(key))
                }
            }
        }
        writeConfigFile()
    }

    /** 将当前映射写入文件，供 Root 守护进程读取 */
    suspend fun writeConfigFile() {
        val prefs = context.dataStore.data.first()
        val sb = StringBuilder()
        sb.appendLine("# Flow Gesture config (auto generated, do not edit)")
        sb.appendLine("# format: <fingers>.<gesture>=<action>, e.g. 3.up=recents")
        for (fingers in MIN_FINGERS..MAX_FINGERS) {
            for (direction in GestureDirection.entries) {
                val key = keyOf(fingers, direction)
                val action = ActionType.fromId(
                    prefs[actionKey(key)] ?: DEFAULT_ACTIONS[key]?.id
                )
                val custom = prefs[customKey(key)] ?: ""
                val value = when {
                    action == ActionType.CUSTOM && custom.isNotBlank() ->
                        "custom:" + custom.replace("\n", " ")
                    action == ActionType.CUSTOM -> "none"
                    else -> action.id
                }
                sb.appendLine("$fingers.${direction.id}=$value")
            }
        }
        File(context.filesDir, CONFIG_FILE_NAME).writeText(sb.toString())
    }

    companion object {
        const val CONFIG_FILE_NAME = "gesture_config.ini"
        const val MIN_FINGERS = 3
        const val MAX_FINGERS = 5

        @Volatile
        private var instance: SettingsRepository? = null

        fun get(context: Context): SettingsRepository =
            instance ?: synchronized(this) {
                instance ?: SettingsRepository(context.applicationContext).also { instance = it }
            }

        private val DYNAMIC_COLOR_KEY = booleanPreferencesKey("dynamic_color")
        private val SERVICE_ENABLED_KEY = booleanPreferencesKey("service_enabled")
        private val BOOT_AUTOSTART_KEY = booleanPreferencesKey("boot_autostart")
        private val LANDSCAPE_FLIP_KEY = booleanPreferencesKey("landscape_mirror")
        private val LANDSCAPE_ROTATION_KEY = intPreferencesKey("landscape_rotation")
        private val LEGACY_ROTATION_OVERRIDE_KEY = intPreferencesKey("rotation_override")

        private fun keyOf(fingers: Int, direction: GestureDirection) = "g${fingers}_${direction.id}"
        private fun actionKey(key: String) = stringPreferencesKey("${key}_action")
        private fun customKey(key: String) = stringPreferencesKey("${key}_custom")

        /**
         * 默认映射：**只保留两条**，其余一律为「无」。
         * 三指上滑 = 任务视图；三指下滑 = 通知中心。
         */
        val DEFAULT_ACTIONS: Map<String, ActionType> = mapOf(
            "g3_up" to ActionType.RECENTS,
            "g3_down" to ActionType.NOTIFICATIONS,
        )
    }
}

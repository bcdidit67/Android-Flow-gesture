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
 * 3/4/5 指手势 -> 动作 映射仓库。
 *
 * 职责：
 * 1. DataStore 持久化用户配置（手势映射 + 外观设置）；
 * 2. 将手势配置生成为守护进程可读的配置文件（gesture_config.ini）。
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

    suspend fun setDynamicColor(enabled: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[DYNAMIC_COLOR_KEY] = enabled
        }
    }

    /**
     * 用户是否主动开启过手势服务。
     * 仅当为 true 时，开机自启才会拉起服务，避免安装 / 更新后自行打扰用户。
     */
    val serviceEnabledFlow: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[SERVICE_ENABLED_KEY] ?: false
    }

    suspend fun setServiceEnabled(enabled: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[SERVICE_ENABLED_KEY] = enabled
        }
    }

    /**
     * 横屏方向镜像：部分 ROM / 触摸面板的旋转方向约定相反，
     * 开启后会把横屏的坐标变换做镜像（左右/上下互换），用于快速纠偏。
     */
    val landscapeMirrorFlow: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[LANDSCAPE_MIRROR_KEY] ?: false
    }

    suspend fun setLandscapeMirror(enabled: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[LANDSCAPE_MIRROR_KEY] = enabled
        }
    }

    /**
     * 手动旋转档位覆盖：-1 = 自动（跟随系统旋转），0..3 = 强制使用该档位。
     * 极少数机型的旋转约定不同，可用它在现场逐个尝试直至方向正确。
     */
    val rotationOverrideFlow: Flow<Int> = context.dataStore.data.map { prefs ->
        prefs[ROTATION_OVERRIDE_KEY] ?: -1
    }

    suspend fun setRotationOverride(value: Int) {
        context.dataStore.edit { prefs ->
            prefs[ROTATION_OVERRIDE_KEY] = value
        }
    }

    /** 将当前配置写入文件，供 Root 守护进程读取（守护进程监视文件变更并自动重载） */
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
        private val LANDSCAPE_MIRROR_KEY = booleanPreferencesKey("landscape_mirror")
        private val ROTATION_OVERRIDE_KEY = intPreferencesKey("rotation_override")

        private fun keyOf(fingers: Int, direction: GestureDirection) = "g${fingers}_${direction.id}"
        private fun actionKey(key: String) = stringPreferencesKey("${key}_action")
        private fun customKey(key: String) = stringPreferencesKey("${key}_custom")

        /** 默认映射（首次安装时生效，可由用户修改） */
        val DEFAULT_ACTIONS: Map<String, ActionType> = mapOf(
            "g3_tap" to ActionType.NOTIFICATIONS,
            "g3_up" to ActionType.RECENTS,
            "g3_down" to ActionType.HOME,
            "g3_left" to ActionType.BACK,
            "g3_right" to ActionType.BACK,
            "g4_tap" to ActionType.NOTIFICATIONS,
            "g4_up" to ActionType.RECENTS,
            "g4_down" to ActionType.HOME,
            "g4_left" to ActionType.NONE,
            "g4_right" to ActionType.NONE,
            "g5_tap" to ActionType.NONE,
            "g5_up" to ActionType.QUICK_SETTINGS,
            "g5_down" to ActionType.HOME,
            "g5_left" to ActionType.NONE,
            "g5_right" to ActionType.NONE,
        )
    }
}
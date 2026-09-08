package com.qqt.music.player

import android.content.Context
import android.content.SharedPreferences
import android.os.SystemClock
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 播放设置单例：播放模式 / 定时关闭（模式对齐 DownloadManager / PrefsManager）。
 *
 * - 播放模式按队列来源记忆保持（ADR 0015）：五键各记一份，无记忆回退默认记忆（play_mode 键即默认记忆），
 *   由 MusicPlayerService 收集 [playMode] 落到 ExoPlayer（repeatMode + shuffleModeEnabled，ADR 0008）
 * - 定时关闭是会话级，进程被杀即失效
 */
object PlayerSettingsManager {

    private const val PREF_NAME = "player_settings"
    private const val KEY_PLAY_MODE = "play_mode"
    private const val KEY_SOURCE_MODES = "source_play_modes"

    /**
     * 播放模式（ADR 0008）。「顺序播放」=按队列顺序循环整个队列（引擎 REPEAT_MODE_ALL，即历史默认行为），
     * 不是行业里“顺序播完即止”；随机播放 = 乱序 + 整队列循环（一轮随机完重新随机）；单曲循环 = REPEAT_MODE_ONE。
     * 声明顺序即菜单展示顺序与图标轮转顺序（随机 → 顺序 → 单曲循环）。
     */
    enum class PlayMode(val label: String) {
        SHUFFLE("随机播放"),
        SEQUENTIAL("顺序播放"),
        REPEAT_ONE("单曲循环"),
    }

    /** 定时关闭状态；null = 未启用。分钟模式记到点时刻，播完本曲只打标志（切曲事件驱动） */
    data class SleepTimerState(
        val minutes: Int = 0,
        val endAtElapsedRealtime: Long = 0L,
        val endOfTrack: Boolean = false,
    ) {
        val isActive: Boolean get() = endOfTrack || endAtElapsedRealtime > 0L
    }

    private var prefs: SharedPreferences? = null

    private val gson = Gson()

    /** 各来源的播放模式记忆（来源描述符 → 模式名），JSON 落盘；读时回退默认记忆、切模式时才落键（ADR 0015） */
    private var sourceModes: Map<String, String> = emptyMap()

    /** 当前队列来源：点播打标（playSong）与冷启动恢复（队列快照）落位，决定切模式写哪份记忆 */
    var currentSource: String = QueueSource.DEFAULT
        private set

    private val _playMode = MutableStateFlow(PlayMode.SEQUENTIAL)
    val playMode: StateFlow<PlayMode> = _playMode.asStateFlow()

    private val _sleepTimer = MutableStateFlow<SleepTimerState?>(null)
    val sleepTimer: StateFlow<SleepTimerState?> = _sleepTimer.asStateFlow()

    /** 分钟模式剩余秒数（PlayerViewModel 定时刷新）；null = 非分钟模式（未启用或播完本曲） */
    private val _sleepRemainingSeconds = MutableStateFlow<Long?>(null)
    val sleepRemainingSeconds: StateFlow<Long?> = _sleepRemainingSeconds.asStateFlow()

    /** MainActivity 启动时调用；Service 可能先于 Activity 启动（开机自启），未 init 时按默认值工作 */
    fun init(context: Context) {
        prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        sourceModes = try {
            prefs?.getString(KEY_SOURCE_MODES, null)
                ?.let { json -> gson.fromJson<Map<String, String>>(json, object : TypeToken<Map<String, String>>() {}.type) }
                ?: emptyMap()
        } catch (e: Exception) {
            emptyMap()
        }
        _playMode.value = defaultMode()
    }

    // --- 播放模式 ---

    /**
     * 切换当前队列来源（点播打标 / 冷启动恢复落位，ADR 0015）：装载该来源的记忆，
     * 无记忆回退默认记忆；同源重入（如同专辑内换歌）不重复装载。
     */
    fun setSource(descriptor: String) {
        if (descriptor == currentSource) return
        currentSource = descriptor
        _playMode.value = sourceModes[descriptor]
            ?.let { saved -> PlayMode.values().firstOrNull { it.name == saved } }
            ?: defaultMode()
    }

    /** UI 切换播放模式：写当前来源的记忆（default 来源写 play_mode 键即默认记忆，其余写来源 map，ADR 0015）；
     *  MusicPlayerService 收集 [playMode] 写入 ExoPlayer（ADR 0008） */
    fun setPlayMode(mode: PlayMode) {
        _playMode.value = mode
        val editor = prefs?.edit() ?: return
        if (currentSource == QueueSource.DEFAULT) {
            editor.putString(KEY_PLAY_MODE, mode.name).apply()
        } else {
            sourceModes = sourceModes + (currentSource to mode.name)
            editor.putString(KEY_SOURCE_MODES, gson.toJson(sourceModes)).apply()
        }
    }

    /** 删除本地歌单时清除其来源记忆（UUID 不复用，残留即死数据，ADR 0015）；
     *  被删来源正持有时归位默认来源，当前模式保持不动（队列仍在播，模式不应跳变） */
    fun removeSourceMemory(descriptor: String) {
        if (descriptor in sourceModes) {
            sourceModes = sourceModes - descriptor
            prefs?.edit()?.putString(KEY_SOURCE_MODES, gson.toJson(sourceModes))?.apply()
        }
        if (currentSource == descriptor) currentSource = QueueSource.DEFAULT
    }

    /** 默认记忆（play_mode 键，ADR 0008）：临时列表共享，兼作无记忆来源的初值（ADR 0015） */
    private fun defaultMode(): PlayMode =
        prefs?.getString(KEY_PLAY_MODE, null)
            ?.let { saved -> PlayMode.values().firstOrNull { it.name == saved } }
            ?: PlayMode.SEQUENTIAL

    // --- 定时关闭 ---

    fun startSleepTimer(minutes: Int) {
        _sleepTimer.value = SleepTimerState(
            minutes = minutes,
            endAtElapsedRealtime = SystemClock.elapsedRealtime() + minutes * 60_000L,
        )
    }

    fun startEndOfTrackTimer() {
        _sleepTimer.value = SleepTimerState(endOfTrack = true)
        _sleepRemainingSeconds.value = null
    }

    fun updateSleepRemaining(seconds: Long?) {
        _sleepRemainingSeconds.value = seconds
    }

    fun clearSleepTimer() {
        _sleepTimer.value = null
        _sleepRemainingSeconds.value = null
    }
}

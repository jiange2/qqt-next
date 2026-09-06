package com.qqt.music.player

import android.content.Context
import android.content.SharedPreferences
import android.os.SystemClock
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 播放设置单例：倍速 / 播放模式 / 均衡器预设 / 定时关闭（模式对齐 DownloadManager / PrefsManager）。
 *
 * - 倍速、播放模式与均衡器预设持久化到 SharedPreferences，跨启动保持；定时关闭是会话级，进程被杀即失效
 * - 播放模式由 MusicPlayerService 收集后落到 ExoPlayer（repeatMode + shuffleModeEnabled，ADR 0008）
 * - 均衡器可用性由 MusicPlayerService 探测后写入（AudioFX 效果是否存在取决于设备 ROM）
 * - EQ 效果实例由 Service 侧持有：AudioFX 挂在播放会话（audioSessionId）上，实例生命周期随 Service
 */
object PlayerSettingsManager {

    private const val PREF_NAME = "player_settings"
    private const val KEY_SPEED = "playback_speed"
    private const val KEY_PLAY_MODE = "play_mode"
    private const val KEY_EQ_PRESET = "eq_preset_key"

    /** 倍速档位（变速不变调） */
    val SPEED_STEPS = listOf(0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 2.0f)

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

    /**
     * 均衡器预设。[platformPreset] 为平台 Equalizer 内置预设序号
     * （AOSP 顺序：0 正常 / 1 古典 / 2 舞曲 / 8 流行 / 9 摇滚）；
     * 重低音无对应平台预设，用 [bandLevelsDb] 手工频段（运行时按实际频段数自适应，见 MusicPlayerService）。
     */
    data class EqPreset(
        val key: String,
        val label: String,
        val platformPreset: Short? = null,
        val bandLevelsDb: List<Float>? = null,
        val bypass: Boolean = false,
    )

    val EQ_PRESETS = listOf(
        EqPreset("normal", "正常", bypass = true),
        EqPreset("pop", "流行", platformPreset = 8),
        EqPreset("rock", "摇滚", platformPreset = 9),
        EqPreset("classical", "古典", platformPreset = 1),
        EqPreset("dance", "舞曲", platformPreset = 2),
        EqPreset("bass", "重低音", bandLevelsDb = listOf(4f, 4f)),
    )

    /** 定时关闭状态；null = 未启用。分钟模式记到点时刻，播完本曲只打标志（切曲事件驱动） */
    data class SleepTimerState(
        val minutes: Int = 0,
        val endAtElapsedRealtime: Long = 0L,
        val endOfTrack: Boolean = false,
    ) {
        val isActive: Boolean get() = endOfTrack || endAtElapsedRealtime > 0L
    }

    private var prefs: SharedPreferences? = null

    private val _playMode = MutableStateFlow(PlayMode.SEQUENTIAL)
    val playMode: StateFlow<PlayMode> = _playMode.asStateFlow()

    private val _speed = MutableStateFlow(1.0f)
    val speed: StateFlow<Float> = _speed.asStateFlow()

    private val _eqPreset = MutableStateFlow(EQ_PRESETS.first())
    val eqPreset: StateFlow<EqPreset> = _eqPreset.asStateFlow()

    private val _eqAvailable = MutableStateFlow(true)
    val eqAvailable: StateFlow<Boolean> = _eqAvailable.asStateFlow()

    private val _sleepTimer = MutableStateFlow<SleepTimerState?>(null)
    val sleepTimer: StateFlow<SleepTimerState?> = _sleepTimer.asStateFlow()

    /** 分钟模式剩余秒数（PlayerViewModel 定时刷新）；null = 非分钟模式（未启用或播完本曲） */
    private val _sleepRemainingSeconds = MutableStateFlow<Long?>(null)
    val sleepRemainingSeconds: StateFlow<Long?> = _sleepRemainingSeconds.asStateFlow()

    /** MainActivity 启动时调用；Service 可能先于 Activity 启动（开机自启），未 init 时按默认值工作 */
    fun init(context: Context) {
        prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        val savedSpeed = prefs?.getFloat(KEY_SPEED, 1.0f) ?: 1.0f
        _speed.value = if (savedSpeed in SPEED_STEPS) savedSpeed else 1.0f
        val savedKey = prefs?.getString(KEY_EQ_PRESET, null) ?: EQ_PRESETS.first().key
        _eqPreset.value = EQ_PRESETS.firstOrNull { it.key == savedKey } ?: EQ_PRESETS.first()
        val savedMode = prefs?.getString(KEY_PLAY_MODE, null)
            ?.let { saved -> PlayMode.values().firstOrNull { it.name == saved } }
        _playMode.value = savedMode ?: PlayMode.SEQUENTIAL
    }

    // --- 播放模式 ---

    /** UI 切换播放模式；MusicPlayerService 收集 [playMode] 写入 ExoPlayer（ADR 0008） */
    fun setPlayMode(mode: PlayMode) {
        _playMode.value = mode
        prefs?.edit()?.putString(KEY_PLAY_MODE, mode.name)?.apply()
    }

    // --- 倍速 ---

    fun setSpeed(value: Float) {
        _speed.value = value
        prefs?.edit()?.putFloat(KEY_SPEED, value)?.apply()
    }

    // --- 均衡器 ---

    /** UI 切换预设；MusicPlayerService 收集 [eqPreset] 写入 Equalizer 实例 */
    fun setEqPreset(preset: EqPreset) {
        _eqPreset.value = preset
        prefs?.edit()?.putString(KEY_EQ_PRESET, preset.key)?.apply()
    }

    /** Service 探测 AudioFX 后写入；不可用时 UI 将均衡器区替换为提示文案 */
    fun setEqAvailable(available: Boolean) {
        _eqAvailable.value = available
    }

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

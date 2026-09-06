package com.qqt.music.data.local

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 本机已评记录（评分每人每首仅一次、不可改分，设计见 android/docs/adr/0007）。
 *
 * - 歌曲 ID → 星级的映射，进程内唯一事实来源，UI（评分对话框的已评态）一律读这里
 * - 每次变更即时落盘 [PrefsManager]；卸载重装即失，后端 rate_already 为兜底
 */
object RatingStore {

    private val _ratedSongs = MutableStateFlow<Map<String, Int>>(emptyMap())
    val ratedSongs: StateFlow<Map<String, Int>> = _ratedSongs.asStateFlow()

    /** 进程启动时从 [PrefsManager] 装载（MainActivity.onCreate，须在 PrefsManager.init 之后） */
    fun init() {
        _ratedSongs.value = PrefsManager.getRatedSongs()
    }

    fun isRated(songId: String?): Boolean = songId != null && songId in _ratedSongs.value

    /** 提交成功或撞后端「已评分」后记入本地（后者星级以本次所选为准，ADR 0007） */
    fun record(songId: String, rate: Int) {
        _ratedSongs.value = _ratedSongs.value + (songId to rate)
        PrefsManager.setRatedSongs(_ratedSongs.value)
    }
}

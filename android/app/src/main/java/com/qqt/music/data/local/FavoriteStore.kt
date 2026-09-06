package com.qqt.music.data.local

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 本地收藏存储（未登录形态，设计见 android/docs/adr/0005）。
 *
 * - 收藏 = 用户标记喜欢的歌曲 ID 序列，最新收藏在最前，不设上限、不自动驱逐
 * - 进程内唯一事实来源，UI（播放器爱心、歌曲收藏页）一律读这里
 * - 每次变更即时落盘 [PrefsManager]
 */
object FavoriteStore {

    private val _favouriteIds = MutableStateFlow<List<String>>(emptyList())
    val favouriteIds: StateFlow<List<String>> = _favouriteIds.asStateFlow()

    /** 进程启动时从 [PrefsManager] 装载（MainActivity.onCreate，须在 PrefsManager.init 之后） */
    fun init() {
        _favouriteIds.value = PrefsManager.getFavouriteIds()
    }

    fun isFavourite(songId: String?): Boolean = songId != null && songId in _favouriteIds.value

    /** 收藏/取消收藏：原本不存在则插入头部（最新收藏在前），存在则移除 */
    fun toggle(songId: String) {
        val ids = _favouriteIds.value.toMutableList()
        if (!ids.remove(songId)) ids.add(0, songId)
        _favouriteIds.value = ids
        PrefsManager.setFavouriteIds(ids)
    }
}

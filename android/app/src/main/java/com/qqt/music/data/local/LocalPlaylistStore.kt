package com.qqt.music.data.local

import java.util.UUID
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** 本地歌单：仅存本机的用户自建歌曲集合，songIds 按添加顺序排列（设计见 android/docs/adr/0006） */
data class LocalPlaylist(
    val id: String,
    val name: String,
    val songIds: List<String> = emptyList(),
    val createdAt: Long,
)

/**
 * 本地歌单存储（未登录形态，设计见 android/docs/adr/0006）。
 *
 * - 歌单 = 用户自建的歌曲 ID 序列，按添加顺序收歌，不设上限、不自动驱逐
 * - 进程内唯一事实来源，UI（播放器加入歌单浮层、我的歌单页、歌单详情页）一律读这里
 * - 每次变更即时落盘 [PrefsManager]
 */
object LocalPlaylistStore {

    private val _playlists = MutableStateFlow<List<LocalPlaylist>>(emptyList())
    val playlists: StateFlow<List<LocalPlaylist>> = _playlists.asStateFlow()

    /** 进程启动时从 [PrefsManager] 装载（MainActivity.onCreate，须在 PrefsManager.init 之后） */
    fun init() {
        _playlists.value = PrefsManager.getLocalPlaylists()
    }

    /** 新建歌单，置于最前（最新创建在前） */
    fun create(name: String) {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return
        val playlist = LocalPlaylist(
            id = UUID.randomUUID().toString(),
            name = trimmed,
            createdAt = System.currentTimeMillis(),
        )
        _playlists.value = listOf(playlist) + _playlists.value
        persist()
    }

    fun rename(id: String, name: String) {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return
        update(id) { it.copy(name = trimmed) }
    }

    fun remove(id: String) {
        _playlists.value = _playlists.value.filterNot { it.id == id }
        persist()
    }

    /** 勾选式加歌/移歌：不在歌单则追加到尾（按添加序收歌），已在则移出 */
    fun toggleSong(playlistId: String, songId: String) {
        update(playlistId) { playlist ->
            if (songId in playlist.songIds) playlist.copy(songIds = playlist.songIds - songId)
            else playlist.copy(songIds = playlist.songIds + songId)
        }
    }

    /** 从歌单移出歌曲 */
    fun removeSong(playlistId: String, songId: String) {
        update(playlistId) { playlist -> playlist.copy(songIds = playlist.songIds - songId) }
    }

    fun byId(id: String?): LocalPlaylist? = id?.let { pid -> _playlists.value.find { it.id == pid } }

    private fun update(id: String, transform: (LocalPlaylist) -> LocalPlaylist) {
        _playlists.value = _playlists.value.map { if (it.id == id) transform(it) else it }
        persist()
    }

    private fun persist() {
        PrefsManager.setLocalPlaylists(_playlists.value)
    }
}

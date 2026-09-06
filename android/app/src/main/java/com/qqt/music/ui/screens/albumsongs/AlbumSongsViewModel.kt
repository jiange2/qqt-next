package com.qqt.music.ui.screens.albumsongs

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.qqt.music.data.api.model.Song
import com.qqt.music.data.repository.MusicRepository
import com.qqt.music.ui.navigation.AlbumNav
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * 专辑歌曲页数据源：按 [AlbumNav] 暂存的专辑一次性拉取全部歌曲。
 * 后端 album_songs 接口整单返回（上限 2000 首，仓库级 ADR 0007），不再分页。
 */
class AlbumSongsViewModel : ViewModel() {
    private val albumId = AlbumNav.album?.id.orEmpty()

    private val _songs = MutableStateFlow<List<Song>>(emptyList())
    val songs: StateFlow<List<Song>> = _songs.asStateFlow()
    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    init { load() }

    private fun load() {
        if (albumId.isBlank()) return
        viewModelScope.launch {
            _isLoading.value = true
            _songs.value = MusicRepository.getAlbumSongs(albumId)
            _isLoading.value = false
        }
    }
}

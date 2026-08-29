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
 * 专辑歌曲页数据源：按 [AlbumNav] 暂存的专辑分页拉取歌曲。
 * 后端 album_songs 接口每页固定 10 首，不可调整。
 */
class AlbumSongsViewModel : ViewModel() {
    private val albumId = AlbumNav.album?.id.orEmpty()

    private val _songs = MutableStateFlow<List<Song>>(emptyList())
    val songs: StateFlow<List<Song>> = _songs.asStateFlow()
    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()
    private var page = 1
    private var hasMore = true

    init { loadMore() }

    fun loadMore() {
        if (albumId.isBlank() || _isLoading.value || !hasMore) return
        viewModelScope.launch {
            _isLoading.value = true
            val result = MusicRepository.getAlbumSongs(albumId, page)
            if (result.isEmpty()) hasMore = false
            else { _songs.value = _songs.value + result; page++ }
            _isLoading.value = false
        }
    }
}

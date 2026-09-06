package com.qqt.music.ui.screens.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.qqt.music.data.api.model.Song
import com.qqt.music.data.repository.MusicRepository
import com.qqt.music.ui.navigation.ArtistNav
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** 艺术家歌曲页状态：按艺术家名对 artist_name_songs 接口滚动自动分页（每页 10 条，id 倒序） */
class ArtistSongsViewModel : ViewModel() {
    private val _songs = MutableStateFlow<List<Song>>(emptyList())
    val songs: StateFlow<List<Song>> = _songs.asStateFlow()
    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()
    private var page = 1
    private var hasMore = true

    val artistName: String get() = ArtistNav.artist?.name.orEmpty()

    init { loadMore() }

    fun loadMore() {
        val name = ArtistNav.artist?.name.orEmpty()
        if (name.isEmpty() || _isLoading.value || !hasMore) return
        viewModelScope.launch {
            _isLoading.value = true
            val result = MusicRepository.getArtistSongs(name, page)
            if (result.isEmpty()) hasMore = false
            else { _songs.value = _songs.value + result; page++ }
            _isLoading.value = false
        }
    }
}

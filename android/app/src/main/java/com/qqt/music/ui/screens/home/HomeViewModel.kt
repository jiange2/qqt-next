package com.qqt.music.ui.screens.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.qqt.music.data.api.model.*
import com.qqt.music.data.repository.MusicRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class HomeUiState(
    val isLoading: Boolean = true,
    val banners: List<Banner> = emptyList(),
    val trendingSongs: List<Song> = emptyList(),
    val latestAlbums: List<Album> = emptyList(),
    val latestArtists: List<Artist> = emptyList(),
    val recentSongs: List<Song> = emptyList(),
    val error: String? = null,
)

class HomeViewModel : ViewModel() {
    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    init {
        loadHome()
    }

    fun loadHome() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            val home = MusicRepository.getHome()
            if (home != null) {
                _uiState.value = HomeUiState(
                    isLoading = false,
                    // 无歌曲且无外链的横幅点击后无处可去，不在轮播中展示
                    banners = home.banners.filter { it.songs.isNotEmpty() || it.link.isNotBlank() },
                    trendingSongs = home.trendingSongs,
                    latestAlbums = home.latestAlbums,
                    latestArtists = home.latestArtists,
                )
            } else {
                _uiState.value = HomeUiState(isLoading = false, error = "加载失败，请重试")
            }
        }
    }
}

package com.qqt.music.ui.screens.recent

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.qqt.music.data.api.model.Song
import com.qqt.music.data.local.PrefsManager
import com.qqt.music.data.repository.MusicRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class RecentViewModel : ViewModel() {
    private val _songs = MutableStateFlow<List<Song>>(emptyList())
    val songs: StateFlow<List<Song>> = _songs.asStateFlow()
    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    init { load() }

    fun load() {
        val ids = PrefsManager.getRecentIds()
        if (ids.isEmpty()) return
        viewModelScope.launch {
            _isLoading.value = true
            // 后端按歌曲 ID 降序返回，此处重排为点播顺序（最新点播在前）
            val fetched = MusicRepository.getRecentSongs(ids.joinToString(","), 1) ?: emptyList()
            _songs.value = fetched.filter { it.id in ids }.sortedBy { ids.indexOf(it.id) }
            _isLoading.value = false
        }
    }
}

package com.qqt.music.ui.screens.favorites

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.qqt.music.data.api.model.Song
import com.qqt.music.data.repository.MusicRepository
import com.qqt.music.ui.components.EmptyState
import com.qqt.music.ui.components.SongListItem
import com.qqt.music.ui.theme.BrandOrange
import com.qqt.music.viewmodel.PlayerViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class FavoritesViewModel : ViewModel() {
    private val _songs = MutableStateFlow<List<Song>>(emptyList())
    val songs: StateFlow<List<Song>> = _songs.asStateFlow()
    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    // In a real app, user ID would come from auth state
    private val userId = 0
    private var page = 1
    private var hasMore = true

    init { load() }

    fun load() {
        if (_isLoading.value || !hasMore) return
        viewModelScope.launch {
            _isLoading.value = true
            val result = MusicRepository.getFavorites(userId, page)
            if (result.isEmpty()) hasMore = false
            else { _songs.value = _songs.value + result; page++ }
            _isLoading.value = false
        }
    }
}

@Composable
fun FavoritesScreen(
    playerViewModel: PlayerViewModel,
    viewModel: FavoritesViewModel = viewModel(),
) {
    val songs by viewModel.songs.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()

    if (!isLoading && songs.isEmpty()) {
        EmptyState(
            icon = Icons.Outlined.FavoriteBorder,
            title = "没有发现歌曲",
            actionText = "刷新",
            onAction = { viewModel.load() },
        )
        return
    }

    val listState = rememberLazyListState()
    val reachedEnd by remember {
        derivedStateOf {
            val info = listState.layoutInfo
            val total = info.totalItemsCount
            val last = info.visibleItemsInfo.lastOrNull()?.index ?: 0
            total > 0 && last >= total - 3
        }
    }
    LaunchedEffect(reachedEnd) { if (reachedEnd) viewModel.load() }

    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize().background(Color.White),
        contentPadding = PaddingValues(vertical = 6.dp),
    ) {
        items(songs) { song ->
            SongListItem(song = song, playerViewModel = playerViewModel, onClick = { playerViewModel.playSong(song, songs) })
        }
        if (isLoading) {
            item {
                Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = BrandOrange, modifier = Modifier.size(32.dp))
                }
            }
        }
    }
}

package com.qqt.music.ui.screens.favorites

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.qqt.music.data.api.model.Song
import com.qqt.music.data.repository.MusicRepository
import com.qqt.music.ui.components.SongListItem
import com.qqt.music.ui.theme.OrangePrimary
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
        // Empty state
        Column(
            modifier = Modifier.fillMaxSize().background(Color(0xFFF5F5F5)),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Box(
                modifier = Modifier
                    .size(80.dp)
                    .clip(CircleShape)
                    .background(Color(0xFFEEEEEE)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Default.Search, null, tint = Color(0xFFCCAAAA), modifier = Modifier.size(44.dp))
            }
            Spacer(Modifier.height(16.dp))
            Text("没有发现歌曲", color = Color.Gray, fontSize = 15.sp)
            Spacer(Modifier.height(12.dp))
            Button(
                onClick = { viewModel.load() },
                colors = ButtonDefaults.buttonColors(containerColor = OrangePrimary),
                modifier = Modifier.width(160.dp),
            ) { Text("刷新") }
        }
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
    ) {
        items(songs) { song ->
            SongListItem(song = song, playerViewModel = playerViewModel, onClick = { playerViewModel.playSong(song, songs) })
            HorizontalDivider(thickness = 0.5.dp, color = Color(0xFFF0F0F0), modifier = Modifier.padding(start = 80.dp))
        }
        if (isLoading) {
            item {
                Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = OrangePrimary, modifier = Modifier.size(32.dp))
                }
            }
        }
    }
}

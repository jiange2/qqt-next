package com.qqt.music.ui.screens.favorites

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
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
import com.qqt.music.data.local.PrefsManager
import com.qqt.music.data.repository.MusicRepository
import com.qqt.music.player.QueueSource
import com.qqt.music.ui.components.EmptyState
import com.qqt.music.ui.components.SongListItem
import com.qqt.music.ui.theme.BrandOrange
import com.qqt.music.viewmodel.PlayerViewModel
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class FavoritesViewModel : ViewModel() {
    private val _songs = MutableStateFlow<List<Song>>(emptyList())
    val songs: StateFlow<List<Song>> = _songs.asStateFlow()
    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()
    private val _loadFailed = MutableStateFlow(false)
    val loadFailed: StateFlow<Boolean> = _loadFailed.asStateFlow()

    init { load() }

    fun load() {
        if (_isLoading.value) return
        val ids = PrefsManager.getFavouriteIds()
        if (ids.isEmpty()) { _songs.value = emptyList(); _loadFailed.value = false; return }
        viewModelScope.launch {
            _isLoading.value = true
            // 后端 get_recent_songs 每页固定 10 条且按歌曲 ID 降序分页，与传入顺序无关：
            // 按收藏数并行取全部页，合并后按本地收藏顺序（最新收藏在前）重排
            val idsStr = ids.joinToString(",")
            val pageCount = (ids.size + MusicRepository.RECENT_PAGE_SIZE - 1) / MusicRepository.RECENT_PAGE_SIZE
            val fetched = (1..pageCount).map { page -> async { MusicRepository.getRecentSongs(idsStr, page) } }.awaitAll()
            _isLoading.value = false
            if (fetched.any { it == null }) {
                _loadFailed.value = true
                return@launch
            }
            _loadFailed.value = false
            _songs.value = fetched.filterNotNull().flatten().filter { it.id in ids }.sortedBy { ids.indexOf(it.id) }
        }
    }

    /** 页面复用 ViewModel，回到本页时调用：本地收藏集合与已加载结果不一致（或上次失败）时重新加载 */
    fun refreshIfStale() {
        val ids = PrefsManager.getFavouriteIds()
        when {
            _loadFailed.value -> load()
            ids.isEmpty() -> _songs.value = emptyList()
            ids != _songs.value.map { it.id } -> load()
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
    val loadFailed by viewModel.loadFailed.collectAsState()

    // 从播放器返回时重新进入组合，比对本地收藏集合，变更（如在播放器取消收藏）则重载
    LaunchedEffect(Unit) { viewModel.refreshIfStale() }

    when {
        isLoading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = BrandOrange)
        }
        loadFailed -> EmptyState(
            icon = Icons.Outlined.FavoriteBorder,
            title = "加载失败",
            subtitle = "网络开小差了，稍后再试试",
            actionText = "重试",
            onAction = { viewModel.load() },
        )
        songs.isEmpty() -> EmptyState(
            icon = Icons.Outlined.FavoriteBorder,
            title = "还没有收藏的歌曲",
            subtitle = "在播放器点喜欢图标，喜欢的歌都在这里",
        )
        else -> LazyColumn(
            modifier = Modifier.fillMaxSize().background(Color.White),
            contentPadding = PaddingValues(vertical = 6.dp),
        ) {
            itemsIndexed(songs) { index, song ->
                SongListItem(index = index + 1, song = song, playerViewModel = playerViewModel, onClick = { playerViewModel.playSong(song, songs, QueueSource.FAVOURITES) })
            }
        }
    }
}

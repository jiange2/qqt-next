package com.qqt.music.ui.screens.mylist

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.QueueMusic
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.qqt.music.data.api.model.Song
import com.qqt.music.data.local.LocalPlaylist
import com.qqt.music.data.local.LocalPlaylistStore
import com.qqt.music.data.repository.MusicRepository
import com.qqt.music.ui.components.EmptyState
import com.qqt.music.ui.components.SongListItem
import com.qqt.music.ui.navigation.MyListNav
import com.qqt.music.ui.theme.BrandOrange
import com.qqt.music.ui.theme.InkFaint
import com.qqt.music.ui.theme.WarmBackground
import com.qqt.music.viewmodel.PlayerViewModel
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class PlaylistDetailViewModel : ViewModel() {
    private val _playlist = MutableStateFlow<LocalPlaylist?>(null)
    val playlist: StateFlow<LocalPlaylist?> = _playlist.asStateFlow()
    private val _songs = MutableStateFlow<List<Song>>(emptyList())
    val songs: StateFlow<List<Song>> = _songs.asStateFlow()
    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()
    private val _loadFailed = MutableStateFlow(false)
    val loadFailed: StateFlow<Boolean> = _loadFailed.asStateFlow()

    init { load() }

    fun load() {
        val current = MyListNav.playlistId?.let { LocalPlaylistStore.byId(it) }
        _playlist.value = current
        val ids = current?.songIds ?: emptyList()
        if (ids.isEmpty()) { _songs.value = emptyList(); _loadFailed.value = false; return }
        viewModelScope.launch {
            _isLoading.value = true
            // 后端 get_recent_songs 每页固定 10 条且与传入顺序无关：
            // 按歌单歌曲数并行取全部页，合并后按歌单添加顺序重排（同收藏页）
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

    /** 移出歌单：Store 即时落盘，已加载列表与歌单计数同步剔除 */
    fun removeSong(song: Song) {
        val id = MyListNav.playlistId ?: return
        LocalPlaylistStore.removeSong(id, song.id)
        _songs.value = _songs.value.filterNot { it.id == song.id }
        _playlist.value = LocalPlaylistStore.byId(id)
    }
}

@Composable
fun PlaylistDetailScreen(
    playerViewModel: PlayerViewModel,
    viewModel: PlaylistDetailViewModel = viewModel(),
) {
    val playlist by viewModel.playlist.collectAsState()
    val songs by viewModel.songs.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val loadFailed by viewModel.loadFailed.collectAsState()

    when {
        isLoading -> Box(
            modifier = Modifier.fillMaxSize().background(WarmBackground),
            contentAlignment = Alignment.Center,
        ) {
            CircularProgressIndicator(color = BrandOrange)
        }
        loadFailed -> EmptyState(
            icon = Icons.Outlined.QueueMusic,
            title = "加载失败",
            subtitle = "网络开小差了，稍后再试试",
            actionText = "重试",
            onAction = { viewModel.load() },
        )
        songs.isEmpty() -> EmptyState(
            icon = Icons.Outlined.QueueMusic,
            // songIds 已清空 = 歌单本身为空；songIds 还在但详情换不到 = 歌曲全部下架
            title = if (playlist?.songIds?.isEmpty() != false) "歌单还是空的" else "歌单里的歌曲暂不可用",
            subtitle = "在播放器点「加入歌单」把喜欢的歌放进来",
        )
        else -> LazyColumn(
            modifier = Modifier.fillMaxSize().background(Color.White),
            contentPadding = PaddingValues(vertical = 6.dp),
        ) {
            items(songs, key = { it.id }) { song ->
                SongListItem(
                    song = song,
                    playerViewModel = playerViewModel,
                    // 点歌以整单替换播放队列（点播心智）
                    onClick = { playerViewModel.playSong(song, songs) },
                    trailing = {
                        IconButton(onClick = { viewModel.removeSong(song) }, modifier = Modifier.size(32.dp)) {
                            Icon(Icons.Outlined.Close, contentDescription = "移出歌单", tint = InkFaint, modifier = Modifier.size(20.dp))
                        }
                    },
                )
            }
        }
    }
}

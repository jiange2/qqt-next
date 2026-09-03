package com.qqt.music.ui.screens.albumsongs

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Album
import androidx.compose.material.icons.outlined.BrokenImage
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.qqt.music.ui.components.EmptyState
import com.qqt.music.ui.components.SongListItem
import com.qqt.music.ui.navigation.AlbumNav
import com.qqt.music.ui.theme.BrandOrange
import com.qqt.music.viewmodel.PlayerViewModel

/**
 * 专辑歌曲页：展示某张专辑下的歌曲，页面标题由顶栏取自专辑名。
 *
 * 后端分页返回（每页 10 首），滚动到底自动加载下一页；
 * 点击歌曲卡片直接播放，播放队列为当前已加载的全部歌曲。
 */
@Composable
fun AlbumSongsScreen(
    playerViewModel: PlayerViewModel,
    viewModel: AlbumSongsViewModel = viewModel(),
) {
    val album = AlbumNav.album
    if (album == null) {
        EmptyState(icon = Icons.Outlined.BrokenImage, title = "内容已失效")
        return
    }

    val songs by viewModel.songs.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val listState = rememberLazyListState()

    val reachedEnd by remember {
        derivedStateOf {
            val info = listState.layoutInfo
            val total = info.totalItemsCount
            val last = info.visibleItemsInfo.lastOrNull()?.index ?: 0
            total > 0 && last >= total - 3
        }
    }
    LaunchedEffect(reachedEnd) { if (reachedEnd) viewModel.loadMore() }

    if (isLoading && songs.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = BrandOrange)
        }
        return
    }

    if (!isLoading && songs.isEmpty()) {
        EmptyState(icon = Icons.Outlined.Album, title = "该专辑暂无歌曲")
        return
    }

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

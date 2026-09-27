package com.qqt.music.ui.screens.search

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MusicNote
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
import com.qqt.music.player.QueueSource
import com.qqt.music.ui.components.EmptyState
import com.qqt.music.ui.components.SongListItem
import com.qqt.music.ui.navigation.ArtistNav
import com.qqt.music.ui.theme.BrandOrange
import com.qqt.music.viewmodel.PlayerViewModel

/**
 * 艺术家歌曲页：展示某位艺术家的歌曲，页面标题由顶栏取自艺术家名。
 *
 * 从搜索结果艺术家行进入，数据源 [ArtistSongsViewModel] 按艺术家名分页加载
 * （每页 10 条，id 倒序，重名艺术家会进同一结果页），滚动接近底部时追加下一页；
 * 点击歌曲卡片直接播放，播放队列为当前已加载的列表。
 */
@Composable
fun ArtistSongsScreen(
    playerViewModel: PlayerViewModel,
    viewModel: ArtistSongsViewModel = viewModel(),
) {
    val artist = ArtistNav.artist
    if (artist == null) {
        EmptyState(icon = Icons.Outlined.BrokenImage, title = "内容已失效")
        return
    }

    val songs by viewModel.songs.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val listState = rememberLazyListState()

    // 滚动接近底部时追加下一页（每页 10 条）
    val reachedEnd by remember {
        derivedStateOf {
            val info = listState.layoutInfo
            val last = info.visibleItemsInfo.lastOrNull()?.index ?: 0
            info.totalItemsCount > 0 && last >= info.totalItemsCount - 3
        }
    }
    LaunchedEffect(reachedEnd) { if (reachedEnd) viewModel.loadMore() }

    when {
        isLoading && songs.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = BrandOrange)
        }
        songs.isEmpty() -> EmptyState(
            icon = Icons.Default.MusicNote,
            title = "该艺术家暂无歌曲",
        )
        else -> LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize().background(Color.White),
            contentPadding = PaddingValues(vertical = 6.dp),
        ) {
            itemsIndexed(songs) { index, song ->
                SongListItem(index = index + 1, song = song, playerViewModel = playerViewModel, onClick = { playerViewModel.playSong(song, songs, QueueSource.DEFAULT) })
            }
            if (isLoading) {
                item {
                    Box(Modifier.fillMaxWidth().padding(vertical = 14.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = BrandOrange, modifier = Modifier.size(26.dp))
                    }
                }
            }
        }
    }
}

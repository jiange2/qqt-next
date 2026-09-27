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
import androidx.compose.material.icons.filled.SearchOff
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
import com.qqt.music.ui.navigation.SearchNav
import com.qqt.music.ui.theme.BrandOrange
import com.qqt.music.viewmodel.PlayerViewModel

/**
 * 搜索结果页：展示关键词命中的全部歌曲，页面标题由顶栏取自关键词。
 *
 * 从搜索页「查看全部歌曲」进入，数据源 [SearchSongsViewModel] 走纯歌曲搜索
 * 接口分页加载（每页 10 条），滚动接近底部时追加下一页；点击歌曲卡片直接播放，
 * 播放队列为当前已加载的列表。
 */
@Composable
fun SearchSongsScreen(
    playerViewModel: PlayerViewModel,
    viewModel: SearchSongsViewModel = viewModel(),
) {
    val query = SearchNav.query
    if (query.isNullOrBlank()) {
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
            icon = Icons.Default.SearchOff,
            title = "未找到相关歌曲",
            subtitle = "换个关键词试试",
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

package com.qqt.music.ui.screens.categorysongs

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.qqt.music.ui.components.SongListItem
import com.qqt.music.ui.navigation.CategoryNav
import com.qqt.music.ui.theme.OrangePrimary
import com.qqt.music.viewmodel.PlayerViewModel

/**
 * 分类歌曲页：展示某个分类下的歌曲，页面标题由顶栏取自分类名。
 *
 * 后端分页返回（每页 10 首），滚动到底自动加载下一页；
 * 点击歌曲卡片直接播放，播放队列为当前已加载的全部歌曲。
 */
@Composable
fun CategorySongsScreen(
    playerViewModel: PlayerViewModel,
    viewModel: CategorySongsViewModel = viewModel(),
) {
    val category = CategoryNav.category
    if (category == null) {
        Box(Modifier.fillMaxSize().background(Color.White), contentAlignment = Alignment.Center) {
            Text("内容已失效", color = Color.Gray)
        }
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
            CircularProgressIndicator(color = OrangePrimary)
        }
        return
    }

    if (!isLoading && songs.isEmpty()) {
        Box(
            Modifier.fillMaxSize().background(Color(0xFFF5F5F5)),
            contentAlignment = Alignment.Center
        ) {
            Text("该分类暂无歌曲", color = Color.Gray, fontSize = 15.sp, textAlign = TextAlign.Center)
        }
        return
    }

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

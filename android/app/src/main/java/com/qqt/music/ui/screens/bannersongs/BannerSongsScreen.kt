package com.qqt.music.ui.screens.bannersongs

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.qqt.music.ui.components.SongListItem
import com.qqt.music.ui.navigation.BannerNav
import com.qqt.music.viewmodel.PlayerViewModel

/**
 * 横幅歌曲页：展示轮播图挂接的歌曲组，页面标题由顶栏取自横幅标题。
 *
 * 点击歌曲卡片直接播放（播放队列为该横幅的全部歌曲），不跳转播放页。
 */
@Composable
fun BannerSongsScreen(playerViewModel: PlayerViewModel) {
    val banner = BannerNav.banner

    if (banner == null || banner.songs.isEmpty()) {
        Box(Modifier.fillMaxSize().background(Color.White), contentAlignment = Alignment.Center) {
            Text("内容已失效", color = Color.Gray)
        }
        return
    }

    val songs = banner.songs

    LazyColumn(
        modifier = Modifier.fillMaxSize().background(Color.White),
    ) {
        items(songs) { song ->
            SongListItem(
                song = song,
                playerViewModel = playerViewModel,
                onClick = { playerViewModel.playSong(song, songs) },
            )
            HorizontalDivider(thickness = 0.5.dp, color = Color(0xFFF0F0F0), modifier = Modifier.padding(start = 80.dp))
        }
    }
}

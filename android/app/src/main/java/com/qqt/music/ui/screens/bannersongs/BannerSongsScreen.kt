package com.qqt.music.ui.screens.bannersongs

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.BrokenImage
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.qqt.music.player.QueueSource
import com.qqt.music.ui.components.EmptyState
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
        EmptyState(icon = Icons.Outlined.BrokenImage, title = "内容已失效")
        return
    }

    val songs = banner.songs

    LazyColumn(
        modifier = Modifier.fillMaxSize().background(Color.White),
        contentPadding = PaddingValues(vertical = 6.dp),
    ) {
        itemsIndexed(songs) { index, song ->
            SongListItem(
                index = index + 1,
                song = song,
                playerViewModel = playerViewModel,
                onClick = { playerViewModel.playSong(song, songs, QueueSource.DEFAULT) },
            )
        }
    }
}

package com.qqt.music.ui.screens.recent

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.History
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.qqt.music.player.QueueSource
import com.qqt.music.ui.components.EmptyState
import com.qqt.music.ui.components.SongListItem
import com.qqt.music.ui.theme.BrandOrange
import com.qqt.music.viewmodel.PlayerViewModel

@Composable
fun RecentScreen(
    playerViewModel: PlayerViewModel,
    viewModel: RecentViewModel = viewModel(),
) {
    val songs by viewModel.songs.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()

    when {
        isLoading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = BrandOrange)
        }
        songs.isEmpty() -> EmptyState(
            icon = Icons.Outlined.History,
            title = "暂无最近播放记录",
            subtitle = "去首页找些好听的歌吧",
        )
        else -> LazyColumn(
            modifier = Modifier.fillMaxSize().background(Color.White),
            contentPadding = PaddingValues(vertical = 6.dp),
        ) {
            itemsIndexed(songs) { index, song ->
                SongListItem(index = index + 1, song = song, playerViewModel = playerViewModel, onClick = { playerViewModel.playSong(song, songs, QueueSource.DEFAULT) })
            }
        }
    }
}

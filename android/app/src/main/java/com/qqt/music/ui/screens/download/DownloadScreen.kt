package com.qqt.music.ui.screens.download

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CloudDownload
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.qqt.music.data.local.PrefsManager
import com.qqt.music.ui.components.EmptyState
import com.qqt.music.ui.components.SongListItem
import com.qqt.music.viewmodel.PlayerViewModel

@Composable
fun DownloadScreen(playerViewModel: PlayerViewModel) {
    val songs = remember { PrefsManager.getDownloadedSongs() }

    if (songs.isEmpty()) {
        EmptyState(
            icon = Icons.Outlined.CloudDownload,
            title = "暂无下载内容",
            subtitle = "在歌曲列表中点击下载按钮即可",
        )
    } else {
        LazyColumn(
            modifier = Modifier.fillMaxSize().background(Color.White),
            contentPadding = PaddingValues(vertical = 6.dp),
        ) {
            items(songs) { song ->
                SongListItem(song = song, playerViewModel = playerViewModel, onClick = { playerViewModel.playSong(song, songs) })
            }
        }
    }
}

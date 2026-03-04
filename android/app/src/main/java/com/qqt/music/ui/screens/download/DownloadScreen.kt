package com.qqt.music.ui.screens.download

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.qqt.music.data.local.PrefsManager
import com.qqt.music.ui.components.SongListItem
import com.qqt.music.ui.theme.OrangePrimary
import com.qqt.music.viewmodel.PlayerViewModel

@Composable
fun DownloadScreen(playerViewModel: PlayerViewModel) {
    val songs = remember { PrefsManager.getDownloadedSongs() }

    if (songs.isEmpty()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFFF5F5F5)),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text("暂无下载内容", color = Color.Gray, fontSize = 15.sp)
            Spacer(Modifier.height(8.dp))
            Text("在歌曲列表中点击下载按钮即可", color = Color.LightGray, fontSize = 13.sp, textAlign = TextAlign.Center)
        }
    } else {
        LazyColumn(
            modifier = Modifier.fillMaxSize().background(Color.White),
        ) {
            items(songs) { song ->
                SongListItem(song = song, onClick = { playerViewModel.playSong(song, songs) })
                HorizontalDivider(thickness = 0.5.dp, color = Color(0xFFF0F0F0), modifier = Modifier.padding(start = 80.dp))
            }
        }
    }
}

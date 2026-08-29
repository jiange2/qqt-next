package com.qqt.music.ui.screens.recent

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
import androidx.lifecycle.viewmodel.compose.viewModel
import com.qqt.music.ui.components.SongListItem
import com.qqt.music.ui.theme.OrangePrimary
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
            CircularProgressIndicator(color = OrangePrimary)
        }
        songs.isEmpty() -> Box(
            Modifier.fillMaxSize().background(Color(0xFFF5F5F5)),
            contentAlignment = Alignment.Center
        ) {
            Text("暂无最近播放记录", color = Color.Gray, fontSize = 15.sp, textAlign = TextAlign.Center)
        }
        else -> LazyColumn(
            modifier = Modifier.fillMaxSize().background(Color.White),
        ) {
            items(songs) { song ->
                SongListItem(song = song, playerViewModel = playerViewModel, onClick = { playerViewModel.playSong(song, songs) })
                HorizontalDivider(thickness = 0.5.dp, color = Color(0xFFF0F0F0), modifier = Modifier.padding(start = 80.dp))
            }
        }
    }
}

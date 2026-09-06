package com.qqt.music.ui.screens.download

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.outlined.CloudDownload
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.qqt.music.data.api.model.Song
import com.qqt.music.download.DownloadManager
import com.qqt.music.ui.components.EmptyState
import com.qqt.music.ui.components.SongListItem
import com.qqt.music.ui.theme.BrandOrange
import com.qqt.music.ui.theme.InkFaint
import com.qqt.music.ui.theme.InkPrimary
import com.qqt.music.ui.theme.InkSecondary
import com.qqt.music.ui.theme.PlaceholderBg
import com.qqt.music.viewmodel.PlayerViewModel

/** 我的下载 = 下载列表：主动缓存完成（含本次会话进行中）的歌曲（ADR 0003） */
@Composable
fun DownloadScreen(playerViewModel: PlayerViewModel) {
    val downloadedSongs by DownloadManager.downloadedSongs.collectAsState()
    val activeDownloads by DownloadManager.activeDownloads.collectAsState()
    var pendingDelete by remember { mutableStateOf<Song?>(null) }

    val activeList = activeDownloads.values.toList()
    if (downloadedSongs.isEmpty() && activeList.isEmpty()) {
        EmptyState(
            icon = Icons.Outlined.CloudDownload,
            title = "暂无下载内容",
            subtitle = "在播放器中点击下载按钮即可",
        )
    } else {
        LazyColumn(
            modifier = Modifier.fillMaxSize().background(Color.White),
            contentPadding = PaddingValues(vertical = 6.dp),
        ) {
            items(activeList, key = { "active_" + it.song.id }) { active ->
                DownloadingItem(active = active, onMoreClick = { pendingDelete = active.song })
            }
            items(downloadedSongs, key = { it.id }) { song ->
                SongListItem(
                    song = song,
                    playerViewModel = playerViewModel,
                    onClick = { playerViewModel.playSong(song, downloadedSongs) },
                    onMoreClick = { pendingDelete = song },
                )
            }
        }
    }

    pendingDelete?.let { song ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("删除下载") },
            text = { Text("「${song.title}」将从下载列表移除，本地文件同时删除。") },
            confirmButton = {
                TextButton(onClick = {
                    DownloadManager.deleteDownload(song.id)
                    pendingDelete = null
                }) { Text("删除") }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) { Text("取消") }
            },
        )
    }
}

/** 下载中的条目：缩略图 + 标题 + 进度条（排队中显示等待文案） */
@Composable
private fun DownloadingItem(active: DownloadManager.ActiveDownload, onMoreClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(54.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(PlaceholderBg),
        ) {
            AsyncImage(
                model = active.song.thumbnailSmall.ifBlank { active.song.thumbnailBig },
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = active.song.title,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                color = InkPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(6.dp))
            when (val state = active.state) {
                DownloadManager.ActiveState.Queued ->
                    Text(text = "排队中…", fontSize = 12.sp, color = InkSecondary)
                is DownloadManager.ActiveState.Downloading -> {
                    if (state.progress >= 0f) {
                        LinearProgressIndicator(
                            progress = { state.progress },
                            modifier = Modifier.fillMaxWidth().height(3.dp).clip(RoundedCornerShape(2.dp)),
                            color = BrandOrange,
                            trackColor = PlaceholderBg,
                        )
                        Spacer(Modifier.height(3.dp))
                        Text(text = "${(state.progress * 100).toInt()}%", fontSize = 11.sp, color = InkFaint)
                    } else {
                        LinearProgressIndicator(
                            modifier = Modifier.fillMaxWidth().height(3.dp).clip(RoundedCornerShape(2.dp)),
                            color = BrandOrange,
                            trackColor = PlaceholderBg,
                        )
                    }
                }
            }
        }
        IconButton(onClick = onMoreClick, modifier = Modifier.size(32.dp)) {
            Icon(Icons.Default.MoreVert, contentDescription = "More", tint = InkFaint, modifier = Modifier.size(20.dp))
        }
    }
}

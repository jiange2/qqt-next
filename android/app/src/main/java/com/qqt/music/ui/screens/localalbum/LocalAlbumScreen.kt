package com.qqt.music.ui.screens.localalbum

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Album
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.qqt.music.player.QueueSource
import com.qqt.music.ui.components.EmptyState
import com.qqt.music.ui.screens.localmusic.LocalAlbumCover
import com.qqt.music.ui.screens.localmusic.LocalMusicViewModel
import com.qqt.music.ui.screens.localmusic.LocalSongRow
import com.qqt.music.ui.theme.BrandOrange
import com.qqt.music.ui.theme.InkFaint
import com.qqt.music.ui.theme.InkPrimary
import com.qqt.music.ui.theme.InkSecondary
import com.qqt.music.viewmodel.PlayerViewModel
import java.io.File

/**
 * 本地专辑页：某张本地专辑的歌曲列表，页面标题由顶栏取自专辑名。
 *
 * 歌曲按音轨号升序（音轨号为空按标题）——顺序在分组时就算好了（[com.qqt.music.data.local.groupIntoAlbums]）；
 * 点歌以整张专辑为播放队列，来源仍是本地音乐（与本地音乐页共用同一份来源记忆）。
 * 数据全部来自扫描索引，页面不发起任何网络请求。
 */
@Composable
fun LocalAlbumScreen(
    albumKey: String?,
    viewModel: LocalMusicViewModel,
    playerViewModel: PlayerViewModel,
) {
    val albums by viewModel.albums.collectAsState()
    val loading by viewModel.loading.collectAsState()
    val album = albums.firstOrNull { it.key == albumKey }

    when {
        // 索引还没读回来：先别把「还没读出来」当成「专辑没有了」
        album == null && loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = BrandOrange, modifier = Modifier.size(28.dp))
        }

        // 专辑可能因为重扫、移除目录或改专辑名而不再存在
        album == null -> EmptyState(
            icon = Icons.Outlined.Album,
            title = "专辑已不存在",
            subtitle = "重新扫描音乐文件夹后再试",
        )

        else -> Column(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.White),
        ) {
            LocalAlbumHeader(
                albumKey = album.key,
                albumName = album.name,
                artist = album.artist,
                songCount = album.songs.size,
                viewModel = viewModel,
            )
            val queue = album.songs.map { it.toSong() }
            LazyColumn(
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(bottom = 6.dp),
            ) {
                itemsIndexed(album.songs, key = { _, song -> song.id }) { index, song ->
                    LocalSongRow(
                        index = index + 1,
                        song = song,
                        playerViewModel = playerViewModel,
                        // 点歌以整张专辑为队列（spec：点击本地专辑以整张专辑顺序聆听）
                        onClick = {
                            playerViewModel.playSong(
                                song = song.toSong(),
                                queue = queue,
                                sourceDescriptor = QueueSource.LOCAL,
                            )
                        },
                    )
                }
            }
        }
    }
}

/** 专辑头：封面 + 专辑名 + 艺术家 + 歌曲数 */
@Composable
private fun LocalAlbumHeader(
    albumKey: String,
    albumName: String,
    artist: String,
    songCount: Int,
    viewModel: LocalMusicViewModel,
) {
    val generation by viewModel.coverGeneration.collectAsState()
    val cover by produceState<File?>(null, albumKey, generation) {
        value = viewModel.albumCover(albumKey)
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        LocalAlbumCover(cover = cover, albumName = albumName, modifier = Modifier.size(88.dp))
        Spacer(Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = albumName,
                fontSize = 17.sp,
                fontWeight = FontWeight.SemiBold,
                color = InkPrimary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = artist.ifBlank { "未知艺术家" },
                fontSize = 13.sp,
                color = InkSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(2.dp))
            Text(text = "$songCount 首", fontSize = 12.sp, color = InkFaint)
        }
    }
}
package com.qqt.music.ui.screens.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.PlayArrow
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
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.qqt.music.data.api.model.Album
import com.qqt.music.data.api.model.Banner
import com.qqt.music.data.api.model.Song
import com.qqt.music.ui.components.BannerCarousel
import com.qqt.music.ui.components.SongListItem
import com.qqt.music.ui.theme.OrangePrimary
import com.qqt.music.viewmodel.PlayerViewModel

@Composable
fun HomeScreen(
    playerViewModel: PlayerViewModel,
    onSeeAllSongs: () -> Unit = {},
    onAlbumClick: (Album) -> Unit = {},
    onBannerClick: (Banner) -> Unit = {},
    viewModel: HomeViewModel = viewModel(),
) {
    val state by viewModel.uiState.collectAsState()

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFF5F5F5)),
        contentPadding = PaddingValues(bottom = 8.dp),
    ) {
        // Banner carousel
        if (state.banners.isNotEmpty()) {
            item {
                BannerCarousel(
                    banners = state.banners,
                    onBannerClick = onBannerClick,
                    modifier = Modifier.padding(8.dp),
                )
            }
        }

        // Loading
        if (state.isLoading) {
            item {
                Box(Modifier.fillMaxWidth().height(200.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = OrangePrimary)
                }
            }
        }

        // Error
        state.error?.let { err ->
            item {
                Column(
                    Modifier.fillMaxWidth().padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(err, color = Color.Gray)
                    Spacer(Modifier.height(12.dp))
                    Button(
                        onClick = { viewModel.loadHome() },
                        colors = ButtonDefaults.buttonColors(containerColor = OrangePrimary),
                    ) { Text("重试") }
                }
            }
        }

        // Trending songs
        if (state.trendingSongs.isNotEmpty()) {
            item {
                SectionHeader("热门歌曲", onSeeAll = onSeeAllSongs)
            }
            item {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    items(state.trendingSongs) { song ->
                        SongCard(song) { playerViewModel.playSong(song, state.trendingSongs) }
                    }
                }
            }
            item { Spacer(Modifier.height(16.dp)) }
        }

        // Recent songs
        if (state.recentSongs.isNotEmpty()) {
            item {
                SectionHeader("最近播放", onSeeAll = onSeeAllSongs)
            }
            item {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    items(state.recentSongs) { song ->
                        SongCard(song) { playerViewModel.playSong(song, state.recentSongs) }
                    }
                }
            }
            item { Spacer(Modifier.height(16.dp)) }
        }

        // Latest albums
        if (state.latestAlbums.isNotEmpty()) {
            item { SectionHeader("最新专辑") }
            item {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    items(state.latestAlbums) { album ->
                        AlbumCard(album) { onAlbumClick(album) }
                    }
                }
            }
            item { Spacer(Modifier.height(16.dp)) }
        }
    }
}

@Composable
private fun SectionHeader(title: String, onSeeAll: (() -> Unit)? = null) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .width(4.dp)
                .height(18.dp)
                .background(OrangePrimary, RoundedCornerShape(2.dp))
        )
        Spacer(Modifier.width(8.dp))
        Text(title, fontWeight = FontWeight.Bold, fontSize = 16.sp)
        Spacer(Modifier.weight(1f))
        if (onSeeAll != null) {
            Row(
                modifier = Modifier.clickable(onClick = onSeeAll),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("查看所有", color = OrangePrimary, fontSize = 13.sp)
                Icon(Icons.Default.ChevronRight, null, tint = OrangePrimary, modifier = Modifier.size(16.dp))
            }
        }
    }
}

@Composable
private fun SongCard(song: Song, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .width(130.dp)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(8.dp),
        elevation = CardDefaults.cardElevation(2.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
    ) {
        Column {
            Box {
                AsyncImage(
                    model = song.thumbnailBig.ifBlank { song.thumbnailSmall },
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(90.dp)
                        .background(Color(0xFFEEEEEE)),
                )
            }
            Column(Modifier.padding(8.dp)) {
                Text(song.title, fontSize = 12.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    song.artist.ifBlank { song.categoryName.ifBlank { "佚名" } },
                    fontSize = 11.sp, color = Color.Gray, maxLines = 1, overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun AlbumCard(album: Album, onClick: () -> Unit) {
    Card(
        modifier = Modifier.width(130.dp).clickable(onClick = onClick),
        shape = RoundedCornerShape(8.dp),
        elevation = CardDefaults.cardElevation(2.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
    ) {
        Column {
            Box {
                AsyncImage(
                    model = album.image.ifBlank { album.imageThumb },
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(90.dp)
                        .background(Color(0xFFEEEEEE)),
                )
                Box(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(OrangePrimary),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Default.PlayArrow, null, tint = Color.White, modifier = Modifier.size(20.dp))
                }
            }
            Text(album.name, fontSize = 12.sp, fontWeight = FontWeight.Medium, maxLines = 2, modifier = Modifier.padding(8.dp))
        }
    }
}

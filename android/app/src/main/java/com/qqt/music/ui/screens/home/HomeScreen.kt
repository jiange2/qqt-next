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
import com.qqt.music.ui.theme.BrandOrange
import com.qqt.music.ui.theme.InkPrimary
import com.qqt.music.ui.theme.InkSecondary
import com.qqt.music.ui.theme.PlaceholderBg
import com.qqt.music.ui.theme.WarmBackground
import com.qqt.music.ui.theme.brandBrush
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
            .background(WarmBackground),
        contentPadding = PaddingValues(top = 8.dp, bottom = 12.dp),
    ) {
        // Banner carousel
        if (state.banners.isNotEmpty()) {
            item {
                BannerCarousel(
                    banners = state.banners,
                    onBannerClick = onBannerClick,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                )
            }
        }

        // Loading
        if (state.isLoading) {
            item {
                Box(Modifier.fillMaxWidth().height(200.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = BrandOrange)
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
                    Text(err, color = InkSecondary)
                    Spacer(Modifier.height(12.dp))
                    Button(
                        onClick = { viewModel.loadHome() },
                        colors = ButtonDefaults.buttonColors(containerColor = BrandOrange),
                        shape = RoundedCornerShape(20.dp),
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
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    items(state.trendingSongs) { song ->
                        SongCard(song) { playerViewModel.playSong(song, state.trendingSongs) }
                    }
                }
            }
            item { Spacer(Modifier.height(20.dp)) }
        }

        // Recent songs
        if (state.recentSongs.isNotEmpty()) {
            item {
                SectionHeader("最近播放", onSeeAll = onSeeAllSongs)
            }
            item {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    items(state.recentSongs) { song ->
                        SongCard(song) { playerViewModel.playSong(song, state.recentSongs) }
                    }
                }
            }
            item { Spacer(Modifier.height(20.dp)) }
        }

        // Latest albums
        if (state.latestAlbums.isNotEmpty()) {
            item { SectionHeader("最新专辑") }
            item {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    items(state.latestAlbums) { album ->
                        AlbumCard(album) { onAlbumClick(album) }
                    }
                }
            }
        }
    }
}

@Composable
private fun SectionHeader(title: String, onSeeAll: (() -> Unit)? = null) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 8.dp, top = 6.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .width(4.dp)
                .height(16.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(brandBrush())
        )
        Spacer(Modifier.width(8.dp))
        Text(title, fontWeight = FontWeight.Bold, fontSize = 17.sp, color = InkPrimary)
        Spacer(Modifier.weight(1f))
        if (onSeeAll != null) {
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .clickable(onClick = onSeeAll)
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("查看所有", color = BrandOrange, fontSize = 12.sp)
                Icon(Icons.Default.ChevronRight, null, tint = BrandOrange, modifier = Modifier.size(15.dp))
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
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
    ) {
        Column {
            AsyncImage(
                model = song.thumbnailBig.ifBlank { song.thumbnailSmall },
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(100.dp)
                    .clip(RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp))
                    .background(PlaceholderBg),
            )
            Column(Modifier.padding(horizontal = 10.dp, vertical = 8.dp)) {
                Text(
                    song.title,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = InkPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(1.dp))
                Text(
                    song.artist.ifBlank { song.categoryName.ifBlank { "佚名" } },
                    fontSize = 11.sp,
                    color = InkSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun AlbumCard(album: Album, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .width(130.dp)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
    ) {
        Column {
            Box {
                AsyncImage(
                    model = album.imageThumb.ifBlank { album.image },
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(100.dp)
                        .clip(RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp))
                        .background(PlaceholderBg),
                )
                Box(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .size(34.dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.92f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Default.PlayArrow, null, tint = BrandOrange, modifier = Modifier.size(20.dp))
                }
            }
            Text(
                album.name,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                color = InkPrimary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
            )
        }
    }
}

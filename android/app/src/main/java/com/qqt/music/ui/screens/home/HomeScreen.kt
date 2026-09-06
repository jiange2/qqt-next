package com.qqt.music.ui.screens.home

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridItemSpan
import androidx.compose.foundation.lazy.staggeredgrid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.outlined.Headphones
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

/** 瀑布流参差档位：按歌曲 id 哈希确定性取档（约六成高卡），刷新不跳动 */
private fun isTallCard(song: Song): Boolean = song.id.hashCode().mod(5) >= 2

/** 播放量万化展示：12345 -> 1.2w，123456789 -> 1.2亿，不足一万原样 */
private fun formatPlayCount(raw: String): String {
    val n = raw.toLongOrNull() ?: return ""
    val compact = when {
        n >= 100_000_000 -> String.format("%.1f", n / 100_000_000.0) + "亿"
        n >= 10_000 -> String.format("%.1f", n / 10_000.0) + "w"
        else -> n.toString()
    }
    return compact.removeSuffix(".0")
}

@Composable
private fun ChipLabel(text: String) {
    Text(
        text,
        fontSize = 10.sp,
        color = InkSecondary,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(PlaceholderBg)
            .padding(horizontal = 6.dp, vertical = 2.dp),
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun HomeScreen(
    playerViewModel: PlayerViewModel,
    onBannerClick: (Banner) -> Unit = {},
    viewModel: HomeViewModel = viewModel(),
) {
    val state by viewModel.uiState.collectAsState()

    // 整页两列瀑布流：Banner/加载/错误以 FullLine 横贯，热门歌曲卡片分列排布
    LazyVerticalStaggeredGrid(
        columns = StaggeredGridCells.Fixed(2),
        modifier = Modifier
            .fillMaxSize()
            .background(WarmBackground),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 12.dp),
        verticalItemSpacing = 12.dp,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // Banner carousel
        if (state.banners.isNotEmpty()) {
            item(span = StaggeredGridItemSpan.FullLine) {
                BannerCarousel(
                    banners = state.banners,
                    onBannerClick = onBannerClick,
                )
            }
        }

        // Loading
        if (state.isLoading) {
            item(span = StaggeredGridItemSpan.FullLine) {
                Box(Modifier.fillMaxWidth().height(200.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = BrandOrange)
                }
            }
        }

        // Error
        state.error?.let { err ->
            item(span = StaggeredGridItemSpan.FullLine) {
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

        // Trending songs 瀑布流
        if (state.trendingSongs.isNotEmpty()) {
            item(span = StaggeredGridItemSpan.FullLine) {
                SectionHeader("热门歌曲")
            }
            items(state.trendingSongs) { song ->
                WaterfallCard(song) { playerViewModel.playSong(song, state.trendingSongs) }
            }
        }
    }
}

@Composable
private fun SectionHeader(title: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 6.dp, bottom = 4.dp),
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
    }
}

@Composable
private fun WaterfallCard(song: Song, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
    ) {
        Column {
            // 封面：高卡 1:1、矮卡 4:3 制造参差；右下播放按钮浮层
            Box {
                AsyncImage(
                    model = song.thumbnailBig.ifBlank { song.thumbnailSmall },
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(if (isTallCard(song)) 1f else 4f / 3f)
                        .clip(RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp))
                        .background(PlaceholderBg),
                )
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(8.dp)
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(Color.Black.copy(alpha = 0.35f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.Filled.PlayArrow,
                        contentDescription = "播放",
                        tint = Color.White,
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
            Column(Modifier.padding(horizontal = 10.dp, vertical = 10.dp)) {
                Text(
                    song.title,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = InkPrimary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                // 歌手副标题/曲风 chip/热度行：各行沿用旧卡行式样式，仅曲风为 chip
                if (song.artist.isNotBlank()) {
                    Spacer(Modifier.height(2.dp))
                    Text(
                        song.artist,
                        fontSize = 11.sp,
                        color = InkSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                if (song.categoryName.isNotBlank()) {
                    Spacer(Modifier.height(6.dp))
                    ChipLabel(song.categoryName)
                }
                if ((song.totalViews.toLongOrNull() ?: 0L) > 0) {
                    // 热度行用耳机图标：心形已被「喜欢/收藏」语义占用
                    Spacer(Modifier.height(8.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Outlined.Headphones,
                            contentDescription = null,
                            tint = InkSecondary,
                            modifier = Modifier.size(12.dp),
                        )
                        Spacer(Modifier.width(3.dp))
                        Text(formatPlayCount(song.totalViews), fontSize = 11.sp, color = InkSecondary)
                    }
                }
            }
        }
    }
}

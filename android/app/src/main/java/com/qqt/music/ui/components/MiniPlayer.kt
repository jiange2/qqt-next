package com.qqt.music.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.qqt.music.ui.theme.Hairline
import com.qqt.music.ui.theme.InkFaint
import com.qqt.music.ui.theme.InkPrimary
import com.qqt.music.ui.theme.InkSecondary
import com.qqt.music.ui.theme.PlaceholderBg
import com.qqt.music.ui.theme.brandBrush
import com.qqt.music.viewmodel.PlayerViewModel

/**
 * 迷你播放器：与底部导航栏连成一体的扁平长条（无圆角，带轻微投影拉开层次），
 * 顶部嵌入实时进度细条（4 物理像素），播放键为品牌渐变圆钮；
 * 白色底延伸至屏幕底部，内容自动抬升到系统手势区之上。
 */
@Composable
fun MiniPlayer(
    playerViewModel: PlayerViewModel,
    modifier: Modifier = Modifier,
    onPlayerClick: () -> Unit = {},
    showTopDivider: Boolean = false,
) {
    val currentSong by playerViewModel.currentSong.collectAsState()
    val isPlaying by playerViewModel.isPlaying.collectAsState()
    val currentPosition by playerViewModel.currentPosition.collectAsState()
    val duration by playerViewModel.duration.collectAsState()

    // 细线宽 = 4 物理像素：由 1px 逐轮加粗而来，任何密度下清晰可见
    val hairlineThickness = with(LocalDensity.current) { (4f / density).dp }
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .clickable(enabled = currentSong != null) { onPlayerClick() },
        color = Color.White,
        shadowElevation = 8.dp,
    ) {
        Column {
            // 无底部导航的页面（设置/详情页）：加一条上边线与内容分隔
            if (showTopDivider) {
                HorizontalDivider(thickness = hairlineThickness, color = Hairline)
            }

            // 顶部实时进度细条
            val progress = if (currentSong != null && duration > 0) {
                (currentPosition.toFloat() / duration.toFloat()).coerceIn(0f, 1f)
            } else 0f
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(hairlineThickness)
                    .background(Hairline),
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(progress)
                        .fillMaxHeight()
                        .background(brandBrush()),
                )
            }

            if (currentSong != null) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(60.dp)
                        .padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    // Album art：圆角方形封面
                    AsyncImage(
                        model = currentSong!!.thumbnailSmall.ifBlank { currentSong!!.thumbnailBig },
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .size(42.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(PlaceholderBg),
                    )
                    Spacer(Modifier.width(10.dp))
                    // Song info
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = currentSong!!.title,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = InkPrimary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            text = currentSong!!.artist.ifBlank { "佚名" },
                            fontSize = 12.sp,
                            color = InkSecondary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    // Controls
                    IconButton(onClick = { playerViewModel.skipPrev() }) {
                        Icon(Icons.Default.SkipPrevious, contentDescription = "Prev", tint = InkPrimary)
                    }
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(CircleShape)
                            .background(brandBrush())
                            .clickable { playerViewModel.togglePlayPause() },
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                            contentDescription = "Play/Pause",
                            tint = Color.White,
                            modifier = Modifier.size(22.dp),
                        )
                    }
                    IconButton(onClick = { playerViewModel.skipNext() }) {
                        Icon(Icons.Default.SkipNext, contentDescription = "Next", tint = InkPrimary)
                    }
                }
            } else {
                // Placeholder when no song is selected
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(60.dp)
                        .padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    // Empty placeholder art
                    Box(
                        modifier = Modifier
                            .size(42.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(PlaceholderBg),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(Icons.Default.MusicNote, contentDescription = null, tint = InkFaint, modifier = Modifier.size(20.dp))
                    }
                    Spacer(Modifier.width(10.dp))
                    // Empty state text
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "暂无播放歌曲",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = InkPrimary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            text = "选择歌曲开始播放",
                            fontSize = 12.sp,
                            color = InkSecondary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    // Disabled controls
                    IconButton(onClick = { }, enabled = false) {
                        Icon(Icons.Default.SkipPrevious, contentDescription = "Prev", tint = InkFaint)
                    }
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(CircleShape)
                            .background(PlaceholderBg),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = Icons.Default.PlayArrow,
                            contentDescription = "Play/Pause",
                            tint = InkFaint,
                            modifier = Modifier.size(22.dp),
                        )
                    }
                    IconButton(onClick = { }, enabled = false) {
                        Icon(Icons.Default.SkipNext, contentDescription = "Next", tint = InkFaint)
                    }
                }
            }

            // 系统导航栏/手势区：白底延伸至屏幕底部，内容抬升到其上方
            Spacer(Modifier.windowInsetsBottomHeight(WindowInsets.navigationBars))
        }
    }
}

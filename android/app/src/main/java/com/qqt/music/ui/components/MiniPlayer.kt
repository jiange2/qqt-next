package com.qqt.music.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
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
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.qqt.music.ui.theme.BrandOrange
import com.qqt.music.ui.theme.Hairline
import com.qqt.music.ui.theme.InkFaint
import com.qqt.music.ui.theme.InkPrimary
import com.qqt.music.ui.theme.InkSecondary
import com.qqt.music.ui.theme.PlaceholderBg
import com.qqt.music.ui.theme.brandBrush
import com.qqt.music.viewmodel.PlayerViewModel

/**
 * 迷你播放器：悬浮胶囊卡片，顶部嵌入实时进度细条，
 * 封面随播放旋转，播放键为品牌渐变圆钮。
 */
@Composable
fun MiniPlayer(
    playerViewModel: PlayerViewModel,
    modifier: Modifier = Modifier,
    onPlayerClick: () -> Unit = {},
) {
    val currentSong by playerViewModel.currentSong.collectAsState()
    val isPlaying by playerViewModel.isPlaying.collectAsState()
    val currentPosition by playerViewModel.currentPosition.collectAsState()
    val duration by playerViewModel.duration.collectAsState()

    // 封面旋转（仅播放时）
    val rotationTransition = rememberInfiniteTransition(label = "miniDisc")
    val discRotation by rotationTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 16000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "miniDiscRotation",
    )

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp)
            .shadow(6.dp, RoundedCornerShape(18.dp), ambientColor = Color(0x33211D19), spotColor = Color(0x33211D19))
            .clickable(enabled = currentSong != null) { onPlayerClick() },
        shape = RoundedCornerShape(18.dp),
        color = Color.White,
    ) {
        Column {
            // 顶部实时进度细条
            val progress = if (currentSong != null && duration > 0) {
                (currentPosition.toFloat() / duration.toFloat()).coerceIn(0f, 1f)
            } else 0f
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(2.dp)
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
                        .padding(horizontal = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    // Album art：旋转唱片
                    AsyncImage(
                        model = currentSong!!.thumbnailSmall.ifBlank { currentSong!!.thumbnailBig },
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .size(42.dp)
                            .rotate(if (isPlaying) discRotation else 0f)
                            .clip(CircleShape)
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
                        .padding(horizontal = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    // Empty placeholder art
                    Box(
                        modifier = Modifier
                            .size(42.dp)
                            .clip(CircleShape)
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
        }
    }
}

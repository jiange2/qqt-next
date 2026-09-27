package com.qqt.music.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.qqt.music.data.api.model.Song
import com.qqt.music.ui.theme.BrandOrange
import com.qqt.music.ui.theme.PlaceholderBg
import kotlin.math.PI
import kotlin.math.sin

/** 歌曲封面缩略图：歌曲列表行与播放队列浮层共用；当前曲叠加蒙层与播放态标记（频谱条 / 播放箭头） */
@Composable
fun SongThumbnail(
    song: Song,
    isCurrent: Boolean,
    isPlaying: Boolean,
    modifier: Modifier = Modifier,
    size: Dp = 54.dp,
) {
    Box(
        modifier = modifier
            .size(size)
            .clip(RoundedCornerShape(12.dp))
            .background(PlaceholderBg),
    ) {
        AsyncImage(
            model = song.thumbnailSmall.ifBlank { song.thumbnailBig },
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
        )
        if (isCurrent) {
            Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.35f)))
            Box(
                modifier = Modifier
                    .align(Alignment.Center)
                    .size(24.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(BrandOrange),
                contentAlignment = Alignment.Center,
            ) {
                if (isPlaying) {
                    PlayingBars()
                } else {
                    Icon(Icons.Default.PlayArrow, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                }
            }
        }
    }
}

/** 正在播放的动效：三根高低起伏的频谱条 */
@Composable
private fun PlayingBars() {
    val transition = rememberInfiniteTransition(label = "playingBars")
    val phase by transition.animateFloat(
        initialValue = 0f,
        targetValue = (2 * PI).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 900, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "playingBarsPhase",
    )
    Row(
        modifier = Modifier.height(12.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalAlignment = Alignment.Bottom,
    ) {
        listOf(0f, 1f, 2f).forEach { offset ->
            val fraction = 0.35f + 0.65f * ((sin(phase + offset * 2f) + 1f) / 2f)
            Box(
                modifier = Modifier
                    .width(2.5.dp)
                    .height(12.dp * fraction)
                    .background(Color.White, RoundedCornerShape(1.dp))
            )
        }
    }
}

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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
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
import com.qqt.music.ui.theme.OrangePrimary
import com.qqt.music.viewmodel.PlayerViewModel
import kotlin.math.PI
import kotlin.math.sin

@Composable
fun SongListItem(
    song: Song,
    playerViewModel: PlayerViewModel,
    onClick: () -> Unit,
    onMoreClick: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val currentSong by playerViewModel.currentSong.collectAsState()
    val isPlaying by playerViewModel.isPlaying.collectAsState()
    val isCurrent = currentSong?.id == song.id
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Thumbnail (with playing overlay for the current song)
        Box(
            modifier = Modifier
                .size(56.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(Color(0xFFEEEEEE)),
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
                        .size(22.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(OrangePrimary),
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
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = song.title,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = song.artist.ifBlank { song.categoryName.ifBlank { "佚名" } },
                fontSize = 12.sp,
                color = Color.Gray,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(4.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                // Star rating
                val avg = song.rateAvg.toFloatOrNull() ?: 0f
                repeat(5) { i ->
                    Icon(
                        imageVector = if (i < avg) Icons.Default.Star else Icons.Default.StarBorder,
                        contentDescription = null,
                        tint = if (i < avg) Color(0xFFFFB300) else Color(0xFFCCCCCC),
                        modifier = Modifier.size(12.dp),
                    )
                }
                Spacer(Modifier.width(6.dp))
                // Rating count badge
                Surface(
                    color = Color(0xFF4CAF50),
                    shape = RoundedCornerShape(3.dp),
                ) {
                    Text(
                        text = formatCount(song.totalRate),
                        fontSize = 10.sp,
                        color = Color.White,
                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp),
                    )
                }
                Spacer(Modifier.width(6.dp))
                Text(
                    text = "👁 ${formatCount(song.totalViews)}",
                    fontSize = 11.sp,
                    color = Color.Gray,
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    text = "⬇ ${formatCount(song.totalDownload)}",
                    fontSize = 11.sp,
                    color = OrangePrimary.copy(alpha = 0.8f),
                )
            }
        }
        IconButton(onClick = onMoreClick, modifier = Modifier.size(32.dp)) {
            Icon(Icons.Default.MoreVert, contentDescription = "More", tint = Color.Gray, modifier = Modifier.size(20.dp))
        }
    }
}

private fun formatCount(raw: String): String {
    val n = raw.toLongOrNull() ?: return raw
    return when {
        n >= 1_000_000 -> "${n / 1_000_000}M"
        n >= 1_000 -> "${n / 1_000}k"
        else -> "$n"
    }
}

/**
 * 正在播放的动效：三根高低起伏的频谱条。
 */
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

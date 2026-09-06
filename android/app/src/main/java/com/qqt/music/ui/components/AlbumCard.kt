package com.qqt.music.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.qqt.music.data.api.model.Album
import com.qqt.music.ui.theme.BrandOrange
import com.qqt.music.ui.theme.InkPrimary
import com.qqt.music.ui.theme.PlaceholderBg

/**
 * 专辑卡片（音乐专辑页与分类专辑页共用）：
 * 专辑图在上（1:1、四角 16dp 圆角、中央播放钮浮层），专辑名在图外下方（左对齐，最多 2 行），
 * 名称不与图片同框（无卡片容器）。
 * 按压反馈：整卡变淡（α 0.5），无波纹无阴影，与分类网格规范一致。
 */
@Composable
fun AlbumCard(
    album: Album,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    Column(
        modifier = modifier
            .fillMaxWidth()
            .alpha(if (pressed) 0.5f else 1f)
            .clickable(interactionSource = interactionSource, indication = null, onClick = onClick),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .clip(RoundedCornerShape(16.dp))
                .background(PlaceholderBg),
            contentAlignment = Alignment.Center,
        ) {
            AsyncImage(
                // thumb(300x300) 优先省流量，原图兜底
                model = album.imageThumb.ifBlank { album.image },
                contentDescription = album.name,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.92f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Default.PlayArrow, null, tint = BrandOrange, modifier = Modifier.size(24.dp))
            }
        }
        Text(
            text = album.name,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
            color = InkPrimary,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 8.dp),
        )
    }
}

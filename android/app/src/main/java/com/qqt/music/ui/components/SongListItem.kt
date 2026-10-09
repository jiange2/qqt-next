package com.qqt.music.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.FileDownload
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.qqt.music.data.api.model.Song
import com.qqt.music.ui.theme.BrandOrange
import com.qqt.music.ui.theme.CachedGold
import com.qqt.music.ui.theme.InkFaint
import com.qqt.music.ui.theme.InkPrimary
import com.qqt.music.ui.theme.InkSecondary
import com.qqt.music.viewmodel.PlayerViewModel

/**
 * 歌曲列表行：[index] 列表序号（1 计）/ 标题 / 歌手与播放、下载计数 / 行尾封面。
 * [swipe] 非空时该行可左滑露出操作按钮（见 [SwipeRevealRow]），无移除语义的列表不传。
 */
@Composable
fun SongListItem(
    index: Int,
    song: Song,
    playerViewModel: PlayerViewModel,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    swipe: RowSwipeAction? = null,
) {
    val currentSong by playerViewModel.currentSong.collectAsState()
    val isPlaying by playerViewModel.isPlaying.collectAsState()
    val isCurrent = currentSong?.id == song.id
    val fullyCached = rememberFullyCached(song)
    SwipeRevealRow(rowKey = song.id, swipe = swipe, modifier = modifier) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                // 左留白 8dp = 序号格与标题之间的间距，使序号在「行左缘 ↔ 标题」之间左右居中
                .padding(start = 8.dp, end = 16.dp, top = 9.dp, bottom = 9.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // 行首列表序号：固定 26dp 格内水平居中，1~4 位数中心对齐同一竖线；字号按位数算（见 songIndexFontSize），
            // 位数多或系统字体放大时自动缩小以免换行/挤出；当前曲橙色，整曲本地可得金色
            Text(
                text = "$index",
                fontSize = songIndexFontSize("$index".length),
                fontWeight = if (isCurrent) FontWeight.SemiBold else FontWeight.Normal,
                textAlign = TextAlign.Center,
                color = when {
                    isCurrent -> BrandOrange
                    fullyCached -> CachedGold
                    else -> InkSecondary
                },
                maxLines = 1,
                softWrap = false,
                modifier = Modifier.width(26.dp),
            )
            Spacer(Modifier.width(8.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = song.title,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = if (isCurrent) BrandOrange else InkPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(1.dp))
                // 副行一条：歌手（无歌手回退分类名）+ 播放次数 + 下载次数；歌手过长先截断，计数始终可见
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = song.artist.ifBlank { song.categoryName.ifBlank { "佚名" } },
                        fontSize = 12.sp,
                        color = InkSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    Spacer(Modifier.width(10.dp))
                    Icon(
                        Icons.Outlined.Visibility, contentDescription = null,
                        tint = InkFaint, modifier = Modifier.size(12.dp),
                    )
                    Spacer(Modifier.width(3.dp))
                    Text(
                        text = formatPlayCount(song.totalViews),
                        fontSize = 11.sp,
                        color = InkSecondary,
                    )
                    Spacer(Modifier.width(10.dp))
                    Icon(
                        Icons.Outlined.FileDownload, contentDescription = null,
                        tint = InkFaint, modifier = Modifier.size(12.dp),
                    )
                    Spacer(Modifier.width(3.dp))
                    Text(
                        text = formatCount(song.totalDownload),
                        fontSize = 11.sp,
                        color = InkSecondary,
                    )
                }
            }
            Spacer(Modifier.width(12.dp))
            SongThumbnail(song = song, isCurrent = isCurrent, isPlaying = isPlaying)
        }
    }
}

/**
 * 行首序号字号：设计 18sp。按序号位数与固定 26dp 格宽算出视觉尺寸上限（1~2 位为 18dp、3 位约 14dp、
 * 更多位继续缩小），再除以系统 fontScale 抵消字体放大，使数字视觉尺寸恒定、永不换行。
 * 供歌曲列表行 / 播放队列浮层行 / 下载中条目共用。
 */
@Composable
fun songIndexFontSize(digits: Int): TextUnit {
    val fontScale = LocalDensity.current.fontScale
    val designDp = minOf(18f, 26f / (digits * 0.62f))
    return (designDp / fontScale).sp
}

private fun formatCount(raw: String): String {
    val n = raw.toLongOrNull() ?: return raw
    return when {
        n >= 1_000_000 -> "${n / 1_000_000}M"
        n >= 1_000 -> "${n / 1_000}k"
        else -> "$n"
    }
}

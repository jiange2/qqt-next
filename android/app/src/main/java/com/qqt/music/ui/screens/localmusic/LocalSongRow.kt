package com.qqt.music.ui.screens.localmusic

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.qqt.music.data.local.LocalSong
import com.qqt.music.ui.components.songIndexFontSize
import com.qqt.music.ui.theme.BrandOrange
import com.qqt.music.ui.theme.InkFaint
import com.qqt.music.ui.theme.InkPrimary
import com.qqt.music.ui.theme.InkSecondary
import com.qqt.music.viewmodel.PlayerViewModel

/**
 * 本地歌曲行：序号 / 标题 / 艺术家 / 时长；当前曲目序号与标题转橙。
 * 歌曲视图与本地专辑页共用（在线列表行走 [com.qqt.music.ui.components.SongListItem]，
 * 那行的播放、下载计数对本地歌曲没有意义，所以本地另写一行）。
 * 长按打开元数据编辑面板（与全屏播放器「更多菜单 → 编辑信息」同一个面板）。
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun LocalSongRow(
    index: Int,
    song: LocalSong,
    playerViewModel: PlayerViewModel,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null,
) {
    val currentSong by playerViewModel.currentSong.collectAsState()
    val isCurrent = currentSong?.id == song.id
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            // 左留白 8dp = 序号格与标题之间的间距，使序号在「行左缘 ↔ 标题」之间左右居中
            .padding(start = 8.dp, end = 16.dp, top = 9.dp, bottom = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "$index",
            fontSize = songIndexFontSize("$index".length),
            fontWeight = if (isCurrent) FontWeight.SemiBold else FontWeight.Normal,
            textAlign = TextAlign.Center,
            color = if (isCurrent) BrandOrange else InkSecondary,
            maxLines = 1,
            softWrap = false,
            modifier = Modifier.width(26.dp),
        )
        Spacer(Modifier.width(8.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = song.displayTitle(),
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                color = if (isCurrent) BrandOrange else InkPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(1.dp))
            Text(
                text = song.displayArtist().ifBlank { "未知艺术家" },
                fontSize = 12.sp,
                color = InkSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.width(12.dp))
        Text(
            text = formatDuration(song.durationMs),
            fontSize = 12.sp,
            color = InkFaint,
            maxLines = 1,
        )
    }
}

/** mm:ss；时长未知（标签读不到）显示 --:-- */
internal fun formatDuration(millis: Long): String {
    if (millis <= 0L) return "--:--"
    val totalSeconds = millis / 1000
    return "%d:%02d".format(totalSeconds / 60, totalSeconds % 60)
}
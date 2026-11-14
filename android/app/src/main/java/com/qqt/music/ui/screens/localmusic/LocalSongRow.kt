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
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.qqt.music.data.local.LocalSong
import com.qqt.music.ui.components.RowSwipeAction
import com.qqt.music.ui.components.SwipeRevealRow
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
 * 文件不可用的行整体灰显、不可点，右侧以「不可用」代替时长（spec「权限与失效」）。
 * [swipe] 非空时该行可左滑露出操作按钮（见 [SwipeRevealRow]，本地两页传「删除」）；
 * 灰显只关行内点击，不影响外层左滑——文件不可用的行照样可删。
 * [editing] 为 true（编辑模式，仅歌曲视图）时序号位换成前置勾选框：点行 / 长按切换勾选
 * （由调用方接线），灰显行不可勾选；勾选框本身不接事件，避免与整行点击双重切换。
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun LocalSongRow(
    index: Int,
    song: LocalSong,
    playerViewModel: PlayerViewModel,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
    swipe: RowSwipeAction? = null,
    editing: Boolean = false,
    selected: Boolean = false,
) {
    val currentSong by playerViewModel.currentSong.collectAsState()
    val isCurrent = currentSong?.id == song.id
    // 不可用：文件被删 / 读不到（播放失败标记），或所在目录授权被系统回收
    val available = song.available
    SwipeRevealRow(rowKey = song.id, swipe = swipe, modifier = modifier) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .combinedClickable(enabled = available, onClick = onClick, onLongClick = onLongClick)
                // 左留白 8dp = 序号格与标题之间的间距，使序号在「行左缘 ↔ 标题」之间左右居中
                .padding(start = 8.dp, end = 16.dp, top = 9.dp, bottom = 9.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (editing) {
                // 编辑模式：序号位换成前置勾选框。只展示不接事件（onCheckedChange = null），
                // 勾选切换统一走整行点击 / 长按，避免点框与点行双重切换；灰显行禁用不可勾选
                Checkbox(
                    checked = selected,
                    onCheckedChange = null,
                    enabled = available,
                    colors = CheckboxDefaults.colors(checkedColor = BrandOrange, checkmarkColor = Color.White),
                )
            } else {
                Text(
                    text = "$index",
                    fontSize = songIndexFontSize("$index".length),
                    fontWeight = if (isCurrent && available) FontWeight.SemiBold else FontWeight.Normal,
                    textAlign = TextAlign.Center,
                    color = if (isCurrent && available) BrandOrange else InkSecondary,
                    maxLines = 1,
                    softWrap = false,
                    modifier = Modifier.width(26.dp),
                )
            }
            Spacer(Modifier.width(8.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = song.displayTitle(),
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = if (!available) InkFaint else if (isCurrent) BrandOrange else InkPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(1.dp))
                Text(
                    text = song.displayArtist().ifBlank { "未知艺术家" },
                    fontSize = 12.sp,
                    color = if (available) InkSecondary else InkFaint,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.width(12.dp))
            Text(
                text = if (available) formatDuration(song.durationMs) else "不可用",
                fontSize = 12.sp,
                color = InkFaint,
                maxLines = 1,
            )
        }
    }
}

/** mm:ss；时长未知（标签读不到）显示 --:-- */
internal fun formatDuration(millis: Long): String {
    if (millis <= 0L) return "--:--"
    val totalSeconds = millis / 1000
    return "%d:%02d".format(totalSeconds / 60, totalSeconds % 60)
}
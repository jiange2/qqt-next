package com.qqt.music.ui.screens.player

import android.os.SystemClock
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.DragInteraction
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.lerp
import androidx.compose.ui.unit.sp
import com.qqt.music.data.api.ApiClient
import com.qqt.music.data.api.model.Song
import com.qqt.music.ui.theme.BrandOrange
import com.qqt.music.ui.theme.PlayerIconGray
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

/** 歌词页加载状态：加载中 / 就绪（解析后的行）/ 空（无歌词或加载失败，展示「暂无歌词」占位） */
internal sealed interface LyricsState {
    object Loading : LyricsState
    data class Ready(val lines: List<LrcLine>) : LyricsState
    object Empty : LyricsState
}

/** 歌词会话缓存：歌曲 ID → 解析后的行。进程内存级，不落盘、不进缓存预算（缓存预算是音频的）；顶栏刷新按 ID 清除 */
internal object LyricsCache {
    private val cache = HashMap<String, List<LrcLine>>()

    fun get(songId: String): List<LrcLine>? = synchronized(cache) { cache[songId] }

    fun put(songId: String, lines: List<LrcLine>) {
        synchronized(cache) { cache[songId] = lines }
    }

    fun clear(songId: String) {
        synchronized(cache) { cache.remove(songId) }
    }
}

/** 获取歌词：内嵌 lrcText 优先，为空时下载 lrcUrl 解析；均无或解析为空返回 null（展示「暂无歌词」） */
internal suspend fun loadLyrics(song: Song): List<LrcLine>? = withContext(Dispatchers.IO) {
    LrcParser.parse(song.lrcText).takeIf { it.isNotEmpty() }
        ?: song.lrcUrl.takeIf { it.isNotBlank() }?.let { url ->
            ApiClient.fetchText(url)?.let { text -> LrcParser.parse(text).takeIf { lines -> lines.isNotEmpty() } }
        }
}

/**
 * 歌词区（歌词页中替代歌曲封面的槽位）：居中歌词行，当前播放行品牌橙加粗并随进度自动滚动居中；
 * 点击任意行跳转播放进度到该行起始时间；用户手动滚动后 3 秒内暂停自动跟随。
 */
@Composable
internal fun LyricsPanel(
    state: LyricsState,
    positionMs: Long,
    onSeekTo: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    when (state) {
        LyricsState.Loading -> LyricsPlaceholder("歌词加载中…", modifier)
        LyricsState.Empty -> LyricsPlaceholder("暂无歌词", modifier)
        is LyricsState.Ready -> LyricsList(state.lines, positionMs, onSeekTo, modifier)
    }
}

/** 加载中 / 暂无歌词的居中占位文案 */
@Composable
private fun LyricsPlaceholder(text: String, modifier: Modifier = Modifier) {
    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(
            text = text,
            fontSize = 14.sp,
            color = PlayerIconGray,
        )
    }
}

@Composable
private fun LyricsList(
    lines: List<LrcLine>,
    positionMs: Long,
    onSeekTo: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    val listState = rememberLazyListState()
    val activeIndex = remember(lines, positionMs) { activeLineIndex(lines, positionMs) }
    // 用户手动滚动后 3 秒内不自动跟随（自 DragInteraction.Start 起计时）
    var manualScrollUntil by remember { mutableStateOf(0L) }
    LaunchedEffect(listState) {
        listState.interactionSource.interactions.collect { interaction ->
            if (interaction is DragInteraction.Start) {
                manualScrollUntil = SystemClock.uptimeMillis() + ManualFollowDelayMs
            }
        }
    }
    // 当前行变化时滚动居中；切歌 / 首次就绪为瞬时定位，其后平滑动画；
    // 手动滚动后 3 秒内不跟随（等待期满后恢复），期满时若仍在拖动则等下次行变化再恢复
    var settled by remember(lines) { mutableStateOf(false) }
    val rowHalfPx = with(LocalDensity.current) { 23.dp.roundToPx() }
    LaunchedEffect(lines, activeIndex, manualScrollUntil) {
        val wait = manualScrollUntil - SystemClock.uptimeMillis()
        if (wait > 0) delay(wait)
        if (activeIndex < 0 || listState.isScrollInProgress) return@LaunchedEffect
        val viewport = listState.layoutInfo.viewportEndOffset - listState.layoutInfo.viewportStartOffset
        if (viewport <= 0) return@LaunchedEffect
        // 负偏移把目标行顶滚到视口顶上方半个视口处（行高按 46dp 估），使行落在视口中部
        val centeringOffset = -(viewport / 2) + rowHalfPx
        if (settled) {
            listState.animateScrollToItem(activeIndex, centeringOffset)
        } else {
            listState.scrollToItem(activeIndex, centeringOffset)
            settled = true
        }
    }
    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        // 首尾留白：让首尾行也能滚动到视口中部
        val edgePad = (maxHeight * 0.38f).coerceAtLeast(24.dp)
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            contentPadding = PaddingValues(vertical = edgePad),
        ) {
            itemsIndexed(lines) { index, line ->
                LyricsRow(
                    line = line,
                    active = index == activeIndex,
                    onClick = { onSeekTo(line.timeMs) },
                )
            }
        }
    }
}

/** 单行歌词：当前行品牌橙加粗放大（200ms 过渡），其余灰；按压反馈为线性变淡（与播放器一致，无波纹） */
@Composable
private fun LyricsRow(
    line: LrcLine,
    active: Boolean,
    onClick: () -> Unit,
) {
    val progress by animateFloatAsState(
        targetValue = if (active) 1f else 0f,
        animationSpec = tween(200),
        label = "lyricRow",
    )
    val interactionSource = remember { MutableInteractionSource() }
    val pressAlpha = rememberPressDimAlpha(interactionSource)
    Text(
        text = line.text,
        fontSize = lerp(14.sp, 17.sp, progress),
        fontWeight = if (progress > 0.5f) FontWeight.SemiBold else FontWeight.Normal,
        color = lerp(PlayerIconGray, BrandOrange, progress),
        textAlign = TextAlign.Center,
        maxLines = 3,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier
            .fillMaxWidth()
            .graphicsLayer { alpha = pressAlpha.value }
            .clickable(interactionSource = interactionSource, indication = null, onClick = onClick)
            .padding(horizontal = 28.dp, vertical = 10.dp),
    )
}

/** 当前播放行：最后一个起始时间不晚于播放位置的行；播放未达首行返回 -1 */
private fun activeLineIndex(lines: List<LrcLine>, positionMs: Long): Int {
    var index = -1
    for (i in lines.indices) {
        if (lines[i].timeMs <= positionMs) index = i else break
    }
    return index
}

// 手动滚动歌词后自动跟随的暂停时长
private const val ManualFollowDelayMs = 3000L

package com.qqt.music.ui.screens.player

import android.os.SystemClock
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.interaction.DragInteraction
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import com.qqt.music.data.repository.MusicRepository
import com.qqt.music.ui.theme.BrandOrange
import com.qqt.music.ui.theme.PlayerIconGray
import com.qqt.music.ui.theme.PlayerNavy
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlin.math.abs

/** 歌词页加载状态：加载中 / 就绪（解析后的行）/ 空（歌曲本无歌词数据，终态）/ 失败（有数据但取回或解析失败，可重试） */
internal sealed interface LyricsState {
    object Loading : LyricsState
    data class Ready(val lines: List<LrcLine>) : LyricsState
    object Empty : LyricsState
    object Error : LyricsState
}

/** 歌词加载结果：就绪 / 歌曲本无歌词数据（终态「暂无歌词」）/ 有数据但取回或解析失败（可重试） */
internal sealed interface LyricsOutcome {
    data class Ready(val lines: List<LrcLine>) : LyricsOutcome
    object NoData : LyricsOutcome
    object Failed : LyricsOutcome
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

/** 获取歌词（ADR 0014）：歌词字段只在 song_info 详情接口下发，按歌曲 ID 请求后只取歌词两字段使用；
 *  内嵌 lrcText 优先，为空或解析不出时下载 lrcUrl 兜底；两字段全空为 NoData，任一数据源失败为 Failed。
 *  文本入解析器前统一经 LrcCipher 按前缀判定解密（docs/adr/0010 歌词密文）：无前缀明文直通，解密失败归
 *  Failed、不回退明文。后端对无歌词的歌输出 JSON null（旧库空列），Song 字段可空、此处 orEmpty 归一；
 *  非预期异常一律归 Failed（可重试），歌词不把播放器崩掉 */
internal suspend fun loadLyrics(songId: String): LyricsOutcome = withContext(Dispatchers.IO) {
    try {
        val song = MusicRepository.getSongInfo(songId) ?: return@withContext LyricsOutcome.Failed
        val embedded = LrcCipher.decrypt(song.lrcText.orEmpty())
            ?: return@withContext LyricsOutcome.Failed
        LrcParser.parse(embedded).takeIf { it.isNotEmpty() }
            ?.let { return@withContext LyricsOutcome.Ready(it) }
        if (song.lrcUrl.orEmpty().isBlank()) {
            // 内嵌文本非空但解析为空 = 数据坏（如无时间轴的纯文本），与字段全空（真无歌词）区分
            return@withContext if (song.lrcText.orEmpty().isBlank()) LyricsOutcome.NoData else LyricsOutcome.Failed
        }
        val text = ApiClient.fetchText(song.lrcUrl.orEmpty()) ?: return@withContext LyricsOutcome.Failed
        val plain = LrcCipher.decrypt(text) ?: return@withContext LyricsOutcome.Failed
        LrcParser.parse(plain).takeIf { it.isNotEmpty() }?.let { LyricsOutcome.Ready(it) } ?: LyricsOutcome.Failed
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        LyricsOutcome.Failed
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
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    when (state) {
        LyricsState.Loading -> LyricsPlaceholder("歌词加载中…", modifier)
        LyricsState.Empty -> LyricsPlaceholder("暂无歌词", modifier)
        is LyricsState.Ready -> LyricsList(state.lines, positionMs, onSeekTo, modifier)
        LyricsState.Error -> LyricsError(onRetry, modifier)
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

/** 歌词加载失败占位：文案加重试入口（复用顶栏刷新的 lyricsReload 通道强制重取当前歌）；按压反馈为线性变淡 */
@Composable
private fun LyricsError(onRetry: () -> Unit, modifier: Modifier = Modifier) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressAlpha = rememberPressDimAlpha(interactionSource)
    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(text = "歌词加载失败", fontSize = 14.sp, color = PlayerIconGray)
            Text(
                text = "重试",
                fontSize = 14.sp,
                color = BrandOrange,
                modifier = Modifier
                    .padding(top = 14.dp)
                    .graphicsLayer { alpha = pressAlpha.value }
                    .clickable(interactionSource = interactionSource, indication = null, onClick = onRetry)
                    .padding(horizontal = 12.dp, vertical = 6.dp),
            )
        }
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
    // 高亮判定比播放位置提前 HighlightLeadMs：进度轮询粒度 100ms + 行内 200ms 放大动画，
    // 提前启动后放大完成时刻 ≈ 行首开唱；滚动（ScrollLeadMs）比高亮再早 100ms，时序为滚→亮→唱
    val activeIndex = remember(lines, positionMs) { activeLineIndex(lines, positionMs + HighlightLeadMs) }
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
    // 手动滚动后 3 秒内不跟随（等待期满后恢复），期满时若仍在拖动则等下次行变化再恢复；
    // 跟随为目标行实测 offset 的差值 animateScrollBy 单段 tween 插值直达——不用 animateScrollToItem：
    // 其慢速 spring 在行变化密集时与 isScrollInProgress 丢弃叠加，会出现过冲回落与顿挫；
    // 滚动触发比高亮提前 ScrollLeadMs（覆盖 tween 时长与进度更新粒度），到位时行首正好开唱；高亮提前 HighlightLeadMs
    var settled by remember(lines) { mutableStateOf(false) }
    val rowHalfPx = with(LocalDensity.current) { 28.dp.roundToPx() }
    val scrollIndex = remember(lines, positionMs) { activeLineIndex(lines, positionMs + ScrollLeadMs) }
    LaunchedEffect(lines, scrollIndex, manualScrollUntil) {
        val wait = manualScrollUntil - SystemClock.uptimeMillis()
        if (wait > 0) delay(wait)
        if (scrollIndex < 0 || listState.isScrollInProgress) return@LaunchedEffect
        val layoutInfo = listState.layoutInfo
        val viewport = layoutInfo.viewportEndOffset - layoutInfo.viewportStartOffset
        if (viewport <= 0) return@LaunchedEffect
        val item = layoutInfo.visibleItemsInfo.firstOrNull { it.index == scrollIndex + 1 }
        // 目标位：行顶位于视口顶下方半视口处再压半行高，行体落在视口正中（+1 跳过首部 Spacer）
        val targetTop = viewport / 2 - rowHalfPx
        if (item == null) {
            // 目标行不在可见区（切歌大跳）：瞬时定位
            listState.scrollToItem(scrollIndex + 1, -targetTop)
            settled = true
            return@LaunchedEffect
        }
        // 滚动量：animateScrollBy 正向 = 向列表尾 = 内容上移，行 offset 随之减小，故 delta = 当前 offset - 目标位
        val delta = item.offset - targetTop
        if (abs(delta) <= 1) return@LaunchedEffect
        if (settled) {
            listState.animateScrollBy(delta.toFloat(), tween(260, easing = FastOutSlowInEasing))
        } else {
            listState.scrollBy(delta.toFloat())
            settled = true
        }
    }
    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        // 首尾半视口透明 Spacer 替代 contentPadding：首尾行也能滚到视口正中，scrollToItem 偏移无 padding 坐标歧义
        val halfViewport = maxHeight / 2
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            item { Spacer(Modifier.height(halfViewport)) }
            itemsIndexed(lines) { index, line ->
                LyricsRow(
                    line = line,
                    active = index == activeIndex,
                    onClick = { onSeekTo(line.timeMs) },
                )
            }
            item { Spacer(Modifier.height(halfViewport)) }
        }
    }
}

/** 单行歌词：当前行藏青大号加粗（200ms 过渡），其余灰；按压反馈为线性变淡（与播放器一致，无波纹） */
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
        fontSize = lerp(18.sp, 26.sp, progress),
        fontWeight = if (progress > 0.5f) FontWeight.SemiBold else FontWeight.Normal,
        color = lerp(PlayerIconGray, PlayerNavy, progress),
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

// 自动跟随相对高亮的提前量（ms）：覆盖滚动 tween 时长与播放进度更新粒度，到位时行首正好开唱；高亮提前 HighlightLeadMs
private const val ScrollLeadMs = 300L

// 当前高亮相对播放位置的提前量（ms）：与行内 200ms 放大动画对齐，放大完成时行首正好开唱
private const val HighlightLeadMs = 200L

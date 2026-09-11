package com.qqt.music.player

import android.util.Log
import androidx.media3.common.Player
import com.qqt.music.ui.screens.player.LrcLine
import com.qqt.music.ui.screens.player.LyricsOutcome
import com.qqt.music.ui.screens.player.loadLyrics
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * 车机歌词同步（ADR 0016）：蓝牙 AVRCP 无歌词字段（文本属性为固定枚举，subtitle/extras
 * 不过蓝牙），借专辑行承载当前句、作者行承载下一句（车机行序实测为 标题→专辑→歌手），
 * 标题永远真实。qqt 不设置专辑、车机专辑行原本为空，借用不挤掉现有信息；Media3 默认
 * 通知只渲染标题+作者，手机端代价是通知作者位变为下一句歌词（2026-09-09 红线修订：
 * 标题永远真实，作者/专辑行承载歌词）。
 *
 * 车机若不刷新某行，仅需调整 [writeLines] 中的两个 setter 映射，逻辑不变。
 */
class CarLyricsSync(private val player: Player, private val scope: CoroutineScope) {

    companion object {
        private const val TAG = "CarLyricsSync"

        /** 歌曲 ID 经元数据 extras 从 ViewModel 传递到 Service 侧 */
        const val EXTRA_SONG_ID = "qqt.lyrics.song_id"

        private const val POLL_INTERVAL_MS = 200L

        /** 行判定前置量：蓝牙元数据推送+车机渲染有秒级滞后，提前写出补偿（实车校准值） */
        private const val LEAD_MS = 1000L
    }

    private var loadedSongId: String? = null
    private var lines: List<LrcLine>? = null
    private var lastIndex: Int = Int.MIN_VALUE
    private var loadJob: Job? = null

    init {
        scope.launch {
            while (isActive) {
                tick()
                delay(POLL_INTERVAL_MS)
            }
        }
        Log.d(TAG, "🎙️ car lyrics sync started (album=当前句, artist=下一句)")
    }

    private suspend fun tick() {
        val songId = player.currentMediaItem?.mediaMetadata?.extras?.getString(EXTRA_SONG_ID)
        if (songId != loadedSongId) {
            loadedSongId = songId
            lines = null
            // 切歌后新条目元数据是构建时的干净值（作者/专辑未占用），无需写清空；
            // lastIndex 归位 MIN，歌词就绪即写首两句
            lastIndex = Int.MIN_VALUE
            loadJob?.cancel()
            loadJob = if (songId.isNullOrEmpty()) null else scope.launch {
                lines = (loadLyrics(songId) as? LyricsOutcome.Ready)?.lines
            }
        }
        val currentLines = lines ?: return
        // 位置加前置量后判定当前行（补偿蓝牙+车机渲染滞后）；首行前视作第 0 行：
        // 歌词就绪立即显示第一句/第二句，不等开唱
        val index = activeLineIndex(currentLines, player.currentPosition.coerceAtLeast(0L) + LEAD_MS)
            .coerceAtLeast(0)
        if (index == lastIndex) return
        lastIndex = index
        writeLines(currentLines[index].text, currentLines.getOrNull(index + 1)?.text)
    }

    /** 当前播放行：最后一个起始时间不晚于播放位置的行；未达首行返回 -1（与歌词页同判定） */
    private fun activeLineIndex(lines: List<LrcLine>, positionMs: Long): Int {
        var index = -1
        for (i in lines.indices) {
            if (lines[i].timeMs <= positionMs) index = i else break
        }
        return index
    }

    private fun writeLines(current: String?, next: String?) {
        val item = player.currentMediaItem ?: return
        val metadata = item.mediaMetadata.buildUpon()
            // 行位映射（ADR 0016，车机行序 标题→专辑→歌手）：车机若不刷新某行，换 setter 即可，逻辑不变
            .setAlbumTitle(current.orEmpty())
            .setArtist(next.orEmpty())
            .build()
        // 仅改元数据（localConfiguration 不变），ExoPlayer 原地更新不打断播放、不重开数据源；
        // 1.4.0 Controller 层会把它误判为切曲事件，由 PlayerViewModel 去重（ADR 0016）
        player.replaceMediaItem(
            player.currentMediaItemIndex,
            item.buildUpon().setMediaMetadata(metadata).build()
        )
        Log.d(TAG, "🎙️ album=「$current」 artist=「$next」")
    }
}

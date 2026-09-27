package com.qqt.music.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import com.qqt.music.data.api.model.Song
import com.qqt.music.download.DownloadManager
import com.qqt.music.player.AudioCache
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 歌曲是否整曲本地可得（与缓存染色同口径，ADR 0013）：已下载，或被动缓存整曲命中。
 *
 * 已下载是内存态，随下载列表变化即时刷新；被动缓存查询要读缓存索引（磁盘），放到 IO 线程，
 * 避免列表滚动时逐行查询卡住主线程。缓存未初始化时按未缓存计，不触发初始化副作用。
 */
@Composable
fun rememberFullyCached(song: Song): Boolean {
    val downloadedSongs by DownloadManager.downloadedSongs.collectAsState()
    if (downloadedSongs.any { it.id == song.id }) return true
    val passive by produceState(initialValue = false, key1 = song.url) {
        value = withContext(Dispatchers.IO) { AudioCache.cacheVisual(song.url).fullyCached }
    }
    return passive
}

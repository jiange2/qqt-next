package com.qqt.music.player

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.media3.common.C
import androidx.media3.datasource.cache.ContentMetadata
import com.qqt.music.data.api.ApiClient
import com.qqt.music.data.api.model.Song
import com.qqt.music.data.local.PrefsManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * 访问事实与时长写回上报（仓库级 ADR 0008）
 *
 * fire-and-forget：失败即丢，不缓存重试、不阻塞播放。
 * 命中口径「整曲命中」：装载未产生媒体网络流量——本地文件直读直接计命中；远程装载按
 * SimpleCache 元数据 content length（CacheDataSource 打开整文件响应时自动写入，首次播放
 * 即有）已知且 0 至全长全区间缓存命中判定。元数据缺失（该设备从未缓存过或整 key 已被
 * 驱逐）或部分缓存一律未命中。长度来自本地缓存自学习，不依赖服务端下发。
 */
object AccessFactReporter {
    private const val TAG = "AccessFactReporter"

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /** 时长写回的会话级去重：一首歌一进程最多发一次 */
    private val durationReported = mutableSetOf<String>()

    /**
     * 上报一条访问事实（每次装载播放一条，由 PlayerViewModel 的 onMediaItemTransition 触发）。
     * 存储快照随事实携带；缓存预算尚未初始化时省略，服务端按可空处理。
     */
    fun reportFact(context: Context, song: Song, playbackUri: Uri) {
        val cache = AudioCache.get(context)
        val hit = if (playbackUri.scheme == "file") {
            true
        } else {
            val key = playbackUri.toString()
            val length = ContentMetadata.getContentLength(cache.getContentMetadata(key))
            length != C.LENGTH_UNSET.toLong() && cache.isCached(key, 0, length)
        }
        val params = mutableMapOf<String, Any>(
            "method_name" to "record_song_access",
            "song_id" to song.id,
            "cache_hit" to if (hit) "1" else "0",
            "device_id" to PrefsManager.getOrCreateDeviceId(),
        )
        val budget = AudioCache.cacheBudget()
        if (budget >= 0) {
            params["allocated_storage"] = budget
            params["used_storage"] = cache.cacheSpace
        }
        scope.launch {
            try {
                ApiClient.apiService.callApi(ApiClient.buildData(params))
                Log.d(TAG, "fact reported: song=${song.id} hit=$hit")
            } catch (e: Exception) {
                Log.d(TAG, "fact report dropped (fire-and-forget): ${e.message}")
            }
        }
    }

    /**
     * 时长写回：播放就绪后的真实时长与服务器现值（秒，四舍五入比对）不一致才上报，
     * 由 PlayerViewModel 的 READY 状态触发。
     */
    fun reportDurationIfChanged(song: Song, playerDurationMs: Long) {
        if (playerDurationMs <= 0) return
        val seconds = ((playerDurationMs + 500) / 1000).toInt()
        if (seconds <= 0) return
        if (seconds == (song.duration.toIntOrNull() ?: 0)) return
        synchronized(durationReported) {
            if (!durationReported.add(song.id)) return
        }
        scope.launch {
            try {
                ApiClient.apiService.callApi(
                    ApiClient.buildData(
                        mapOf(
                            "method_name" to "update_song_duration",
                            "song_id" to song.id,
                            "duration" to seconds,
                        )
                    )
                )
                Log.d(TAG, "duration reported: song=${song.id} ${seconds}s")
            } catch (e: Exception) {
                Log.d(TAG, "duration report dropped (fire-and-forget): ${e.message}")
            }
        }
    }
}

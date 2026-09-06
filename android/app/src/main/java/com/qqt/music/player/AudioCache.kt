package com.qqt.music.player

import android.content.Context
import android.os.StatFs
import androidx.media3.datasource.cache.LeastRecentlyUsedCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import java.io.File

/**
 * 单例被动缓存管理器
 *
 * 使用 SimpleCache + LRU 驱逐策略，被动缓存占用不超过启动时刻的缓存预算（总容量预留制，见 docs/adr/0004）。
 * 存放于 filesDir 而非 cacheDir：部分厂商 ROM 会定期清理 cacheDir（ADR 0003）。
 */
object AudioCache {
    @Volatile
    private var instance: SimpleCache? = null

    fun get(context: Context): SimpleCache {
        return instance ?: synchronized(this) {
            instance ?: SimpleCache(
                File(context.filesDir, "audio_cache"),
                LeastRecentlyUsedCacheEvictor(cacheBudgetBytes(context))
            ).also { instance = it }
        }
    }

    /**
     * 计算缓存预算（启动时刻快照，总容量预留制）：
     * 预算 = max(空闲空间 − max(总容量×5%, 1 GiB), 0)，预算为 0 时 LRU 即写即删。
     */
    private fun cacheBudgetBytes(context: Context): Long {
        val stat = StatFs(context.filesDir.absolutePath)
        val reserveBytes = maxOf(stat.totalBytes * 5 / 100, 1L shl 30)
        return maxOf(stat.availableBytes - reserveBytes, 0L)
    }
}

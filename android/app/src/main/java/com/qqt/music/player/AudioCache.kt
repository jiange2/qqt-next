package com.qqt.music.player

import android.content.Context
import android.os.StatFs
import androidx.media3.datasource.cache.LeastRecentlyUsedCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import java.io.File

/**
 * 单例缓存管理器
 *
 * 使用 SimpleCache + LRU 驱逐策略，自动维护 60% 磁盘容量的音频缓存。
 */
object AudioCache {
    @Volatile
    private var instance: SimpleCache? = null

    fun get(context: Context): SimpleCache {
        return instance ?: synchronized(this) {
            instance ?: SimpleCache(
                File(context.cacheDir, "audio_cache"),
                LeastRecentlyUsedCacheEvictor(availableCacheBytes(context))
            ).also { instance = it }
        }
    }

    /**
     * 计算可用缓存字节数（设备可用磁盘空间的 60%）
     */
    private fun availableCacheBytes(context: Context): Long {
        val stat = StatFs(context.cacheDir.absolutePath)
        val freeBytes = stat.availableBlocksLong * stat.blockSizeLong
        return (freeBytes * 0.6).toLong()
    }
}

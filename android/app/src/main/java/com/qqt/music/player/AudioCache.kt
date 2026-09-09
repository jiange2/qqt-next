package com.qqt.music.player

import android.content.Context
import android.os.StatFs
import androidx.media3.common.C
import androidx.media3.datasource.cache.ContentMetadata
import androidx.media3.datasource.cache.LeastRecentlyUsedCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import java.io.File

/**
 * 播放器进度条缓存可视化状态（ADR 0013，见 CONTEXT.md「缓存前缀」「缓存染色」）：
 * [fullyCached] 整曲本地可得（被动缓存整曲命中或已下载）；[prefixFraction] 缓存前缀比例 [0,1]。
 */
data class CacheVisual(
    val fullyCached: Boolean,
    val prefixFraction: Float,
) {
    companion object {
        val NOT_CACHED = CacheVisual(false, 0f)
        val FULLY_AVAILABLE = CacheVisual(true, 1f)
    }
}

/**
 * 单例被动缓存管理器
 *
 * 使用 SimpleCache + LRU 驱逐策略，被动缓存占用不超过启动时刻的缓存预算（总容量预留制，见 docs/adr/0004）。
 * 存放于 filesDir 而非 cacheDir：部分厂商 ROM 会定期清理 cacheDir（ADR 0003）。
 */
object AudioCache {

    /**
     * 被动缓存键版本后缀（仓库级 ADR 0011 割接）：缓存语义（字节混淆参数、媒体 host 等）变更时 +1。
     * 新条目一律写新键；旧版本键由 [create] 启动时后台清扫一次性清除并腾回 LRU 配额。
     * v3：作废混淆初版（无嗅探、连明文 CDN 测试）写下的乱码条目。
     */
    private const val CACHE_KEY_VERSION = 3

    /** 被动缓存键（与 CacheDataSource 的 CacheKeyFactory 同口径，缓存可视化查询共用） */
    fun cacheKey(url: String): String = "$url#v$CACHE_KEY_VERSION"

    @Volatile
    private var instance: SimpleCache? = null

    /** 本进程缓存预算（字节），SimpleCache 创建时快照；未初始化为 -1 */
    @Volatile
    private var budgetBytes = -1L

    fun get(context: Context): SimpleCache {
        return instance ?: synchronized(this) {
            instance ?: create(context).also { instance = it }
        }
    }

    private fun create(context: Context): SimpleCache {
        val budget = cacheBudgetBytes(context)
        budgetBytes = budget
        return SimpleCache(
            File(context.filesDir, "audio_cache"),
            LeastRecentlyUsedCacheEvictor(budget)
        ).also { cache ->
            // 旧版本键一次性清扫（后台线程）：SimpleCache 线程安全，本时点无播放占用；
            // 极端竞态下被锁 span 清不掉的残余由 LRU 驱逐兜底
            Thread {
                cache.keys.filterNot { it.endsWith("#v$CACHE_KEY_VERSION") }
                    .forEach { cache.removeResource(it) }
            }.start()
        }
    }

    /** 缓存预算快照（访问事实上报用），未初始化返回 -1（上报侧转为缺省） */
    fun cacheBudget(): Long = budgetBytes

    /** 删除一条缓存（ADR 0011 错误重试自愈）：只在本进程已初始化时操作，不触发初始化副作用 */
    fun removeEntry(url: String) {
        instance?.removeResource(cacheKey(url))
    }

    /**
     * 播放器进度条缓存可视化查询（ADR 0013）：
     * 入参为播放 URI 字符串，内部经 [cacheKey] 版本化后与 CacheDataSource 键同口径。
     * 整曲命中与访问事实上报同口径（C4）——元数据 contentLength 已知且 [0, length) 全区间命中；
     * 缓存前缀 = 自 0 到首个缓存空洞的连续已缓存字节 ÷ 整曲长度，跨会话留存。
     * SimpleCache 未初始化（本进程尚未装载缓存）时整表视为未缓存，不触发初始化副作用。
     */
    fun cacheVisual(url: String): CacheVisual {
        val cache = instance ?: return CacheVisual.NOT_CACHED
        val key = cacheKey(url)
        val contentLength = ContentMetadata.getContentLength(cache.getContentMetadata(key))
        if (contentLength == C.LENGTH_UNSET.toLong() || contentLength <= 0L) {
            return CacheVisual.NOT_CACHED
        }
        val fullyCached = cache.isCached(key, 0, contentLength)
        val prefixBytes = if (fullyCached) {
            contentLength
        } else {
            cache.getCachedLength(key, 0, contentLength).coerceAtLeast(0L)
        }
        return CacheVisual(fullyCached, (prefixBytes.toFloat() / contentLength).coerceIn(0f, 1f))
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

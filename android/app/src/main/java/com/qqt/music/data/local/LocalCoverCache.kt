package com.qqt.music.data.local

import android.content.Context
import android.media.MediaMetadataRetriever
import android.util.Log
import java.io.File
import java.security.MessageDigest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * 本地专辑封面（spec「专辑与封面」）：音频内嵌图，没有时返回 null（页面显示占位图）。
 * 系统预抽取的专辑图属票据 04，届时整体替换为按专辑 ID 取媒体库专辑图。
 *
 * - 只在被展示时解析（懒加载），结果按专辑键写进 App 私有缓存目录，不放进音频缓存（那是下载流量的预算）
 * - 缓存有容量上限，超出按最近使用淘汰：本进程访问过的键记在 [recent] 里，没访问过的按文件修改时间，
 *   不会随专辑数量无限增长
 * - 库刷新完成后由调用方 [clear] 清空：媒体库内容变了，要等刷新才被感知
 * - 全部读盘与解析在 IO 线程，不在主线程开解析器
 */
class LocalCoverCache(private val context: Context) {

    private val cacheDir = File(context.cacheDir, CACHE_DIR)

    /** 封面解析与淘汰都改缓存目录，同一把锁串行化 */
    private val lock = Mutex()

    /** 最近使用的缓存条目（插入序 = 访问序，命中时先删后插挪到队尾） */
    private val recent = LinkedHashMap<String, Long>()

    /**
     * 本进程内确认过「没有任何封面」的专辑键：避免同一张无封面专辑每次滚回屏幕都重开一次解析器
     * （开一次 MediaMetadataRetriever 不便宜）。刷新清空缓存时一并作废。
     */
    private val missing = LinkedHashSet<String>()

    /**
     * 取专辑封面：命中缓存直接返回，否则按优先级解析并写入缓存。
     * 返回 null 表示这张专辑没有任何封面可用（音频没有内嵌图）。
     */
    suspend fun cover(album: LocalAlbum): File? = withContext(Dispatchers.IO) {
        lock.withLock { resolve(album) }
    }

    /**
     * 指定专辑键的封面作废（元数据编辑改了专辑名 / 专辑艺术家，分组成员就变了）：
     * 删掉缓存文件并清掉「没有封面」的记录，下次展示时按新成员重新解析来源。
     */
    suspend fun invalidate(keys: Collection<String>) = withContext(Dispatchers.IO) {
        lock.withLock {
            for (key in keys) {
                val name = entryName(key)
                recent.remove(name)
                missing.remove(key)
                runCatching { File(cacheDir, name).delete() }
                    .onFailure { e -> Log.w(TAG, "封面缓存作废失败：$name", e) }
            }
        }
    }

    /** 清空缓存：库刷新提交后用，好让「专辑成员变化」在刷新后生效 */
    suspend fun clear() = withContext(Dispatchers.IO) {
        lock.withLock {
            recent.clear()
            missing.clear()
            cacheDir.listFiles()?.forEach { file -> runCatching { file.delete() } }
        }
    }

    private fun resolve(album: LocalAlbum): File? {
        val cached = File(cacheDir, entryName(album.key))
        if (cached.isFile && cached.length() > 0) {
            markUsed(cached.name)
            return cached
        }
        if (album.key in missing) return null
        val bytes = readEmbeddedCover(album.coverSong) ?: return rememberMissing(album.key)
        val written = writeEntry(cached, bytes) ?: return null
        markUsed(written.name)
        trim()
        return written
    }

    private fun rememberMissing(key: String): File? {
        missing.remove(key)
        missing.add(key)
        while (missing.size > MAX_TRACKED) {
            val oldest = missing.firstOrNull() ?: break
            missing.remove(oldest)
        }
        return null
    }

    /** 缓存文件名：专辑键的 SHA-256，键里带路径与分隔符也不用担心文件名非法 */
    private fun entryName(key: String): String = sha256Hex(key) + ENTRY_SUFFIX

    private fun markUsed(name: String) {
        recent.remove(name)
        recent[name] = System.currentTimeMillis()
        // 记录表本身也要有界：超出上限时丢掉最久未用的那条（文件按需淘汰，不影响正确性）
        while (recent.size > MAX_TRACKED) {
            val oldest = recent.keys.firstOrNull() ?: break
            recent.remove(oldest)
        }
    }

    /**
     * 超额淘汰：总量超过 [MAX_BYTES] 时按最近使用从旧到新删。
     * 本进程访问过的条目按 [recent] 的顺序，其余按文件修改时间（上次进程最后一次写入 / 使用的时刻）。
     */
    private fun trim() {
        val files = cacheDir.listFiles()?.filter { it.isFile } ?: return
        var total = files.sumOf { it.length() }
        if (total <= MAX_BYTES) return
        val candidates = files.sortedBy { recent[it.name] ?: it.lastModified() }
        for (file in candidates) {
            if (total <= MAX_BYTES) break
            val length = file.length()
            if (runCatching { file.delete() }.getOrDefault(false)) {
                total -= length
                recent.remove(file.name)
            }
        }
    }

    /** 临时文件 + rename：并发读到半个封面的情况不会落在正式文件名上 */
    private fun writeEntry(target: File, bytes: ByteArray): File? = runCatching {
        if (!cacheDir.exists()) cacheDir.mkdirs()
        val temp = File(cacheDir, target.name + TEMP_SUFFIX)
        temp.writeBytes(bytes)
        if (target.exists()) target.delete()
        if (!temp.renameTo(target)) error("封面缓存 rename 失败：${target.name}")
        target
    }.getOrElse { e ->
        Log.w(TAG, "封面缓存写入失败", e)
        null
    }

    /** 音频内嵌图：读 MediaStore content URI 上的音频文件（已持有音频读取权限），失败按无封面处理 */
    private fun readEmbeddedCover(song: LocalSong): ByteArray? {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(context, android.net.Uri.parse(song.contentUri))
            retriever.embeddedPicture?.takeIf { it.isNotEmpty() }
        } catch (e: Exception) {
            Log.w(TAG, "内嵌封面读取失败：${song.contentUri}", e)
            null
        } finally {
            runCatching { retriever.release() }
        }
    }

    private fun sha256Hex(value: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(value.toByteArray(Charsets.UTF_8))
        val hex = CharArray(digest.size * 2)
        for (i in digest.indices) {
            val byte = digest[i].toInt() and 0xFF
            hex[i * 2] = HEX_DIGITS[byte ushr 4]
            hex[i * 2 + 1] = HEX_DIGITS[byte and 0x0F]
        }
        return String(hex)
    }

    private companion object {
        const val TAG = "LocalCoverCache"
        const val CACHE_DIR = "local_covers"
        const val ENTRY_SUFFIX = ".img"
        const val TEMP_SUFFIX = ".tmp"

        /** 缓存容量上限：专辑封面几百 KB 量级，16MB 够几百张，超出按最近使用淘汰 */
        const val MAX_BYTES = 16L * 1024 * 1024

        /** 访问记录表的条目上限（只为淘汰排序用，不影响缓存文件本身） */
        const val MAX_TRACKED = 256

        val HEX_DIGITS = "0123456789abcdef".toCharArray()
    }
}

package com.qqt.music.download

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.media3.common.C
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.cache.CacheDataSource
import com.qqt.music.MEDIA_REFERER
import com.qqt.music.data.api.model.Song
import com.qqt.music.data.local.PrefsManager
import com.qqt.music.player.AudioCache
import com.qqt.music.player.DeobfuscatingDataSourceFactory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL

/**
 * 主动缓存管理器（用户界面称"下载"），设计见 android/docs/adr/0003。
 *
 * - 下载 = 把整首 MP3 流式落成 `filesDir/downloads/` 下的完整文件，永不自动驱逐
 * - 以被动缓存（[AudioCache]）为上游：已缓存字节零流量复用，只从网络补缺（缓存升格）
 * - 进程内串行队列；中断后残留 `.part` 半文件，下次点"下载"从断点续传
 * - 下载列表（[downloadedSongs]）在 init 时与磁盘对账，是"已下载"的唯一事实来源
 */
object DownloadManager {

    private const val TAG = "DownloadManager"
    private const val DIR_NAME = "downloads"
    private const val PART_SUFFIX = ".part"
    private const val BUFFER_SIZE = 64 * 1024

    /** 进度上报的最小间隔（毫秒），避免状态流高频刷新 */
    private const val PROGRESS_INTERVAL_MS = 250L

    /** 下载中的活动状态（"未下载/已下载"不在此列，后者见 [downloadedSongs]） */
    sealed interface ActiveState {
        /** 串行队列中等待 */
        data object Queued : ActiveState

        /** 下载中；progress ∈ [0,1]，内容总长未知时为 -1 */
        data class Downloading(val progress: Float) : ActiveState
    }

    data class ActiveDownload(val song: Song, val state: ActiveState)

    private class DownloadException(message: String) : Exception(message)

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val queue = Channel<Song>(Channel.UNLIMITED)
    private val cancelledIds = mutableSetOf<String>()

    private val _downloadedSongs = MutableStateFlow<List<Song>>(emptyList())
    val downloadedSongs: StateFlow<List<Song>> = _downloadedSongs.asStateFlow()

    private val _activeDownloads = MutableStateFlow<Map<String, ActiveDownload>>(emptyMap())
    val activeDownloads: StateFlow<Map<String, ActiveDownload>> = _activeDownloads.asStateFlow()

    /** 下载失败等需要轻提示的消息（MainActivity 收集后 Toast） */
    private val _errors = MutableSharedFlow<String>(extraBufferCapacity = 8)
    val errors: SharedFlow<String> = _errors.asSharedFlow()

    private lateinit var appContext: Context
    private lateinit var downloadsDir: File

    fun init(context: Context) {
        if (::downloadsDir.isInitialized) return
        appContext = context.applicationContext
        downloadsDir = File(appContext.filesDir, DIR_NAME).apply { mkdirs() }

        // 列表与磁盘对账：文件缺失的条目剔除（旧版快照数据因此清空），孤儿完整文件删除；
        // `.part` 半文件保留，供下次点"下载"时断点续传
        val stored = PrefsManager.getDownloadedSongs()
        val valid = stored.filter { completedFile(it.id).exists() }
        val validNames = valid.map { fileName(it.id) }.toSet()
        downloadsDir.listFiles()?.forEach { file ->
            if (!file.name.endsWith(PART_SUFFIX) && file.name !in validNames) file.delete()
        }
        _downloadedSongs.value = valid
        if (valid.size != stored.size) persist()

        // 单消费者协程 = 串行下载队列
        scope.launch {
            for (song in queue) {
                if (song.id in cancelledIds) {
                    cancelledIds.remove(song.id)
                    continue
                }
                runDownload(song)
            }
        }
    }

    // ========== 对外操作 ==========

    /** 入队下载（已下载或已在队列时忽略）。以被动缓存为上游，缺多少补多少。 */
    fun enqueue(song: Song) {
        if (!::downloadsDir.isInitialized) return
        if (isDownloaded(song.id) || _activeDownloads.value.containsKey(song.id)) return
        _activeDownloads.update { it + (song.id to ActiveDownload(song, ActiveState.Queued)) }
        queue.trySend(song)
    }

    /** 删除下载：进行中的取消并删半文件，已完成的删文件；按钮随之恢复可点 */
    fun deleteDownload(songId: String) {
        cancelledIds.add(songId)
        _activeDownloads.update { it - songId }
        _downloadedSongs.update { songs -> songs.filterNot { it.id == songId } }
        persist()
        completedFile(songId).delete()
        partFile(songId).delete()
    }

    fun isDownloaded(songId: String): Boolean = _downloadedSongs.value.any { it.id == songId }

    /** 已下载歌曲的本地播放 URI；未下载返回 null（走网络） */
    fun localUri(songId: String): Uri? {
        val file = completedFile(songId)
        return if (isDownloaded(songId) && file.exists()) Uri.fromFile(file) else null
    }

    // ========== 下载核心 ==========

    private suspend fun runDownload(song: Song) {
        val part = partFile(song.id)
        val offset = if (part.exists()) part.length() else 0L
        try {
            _activeDownloads.update {
                it + (song.id to ActiveDownload(song, ActiveState.Downloading(-1f)))
            }

            // 1. 预检内容总长（用于空间校验与进度）；拿不到则跳过校验、进度不确定
            val total = probeContentLength(song.url)
            val remaining = if (total > 0) total - offset else -1L
            if (remaining == 0L) {
                finalizeDownload(song, part)
                return
            }
            if (remaining > 0 && remaining > downloadsDir.usableSpace) {
                throw DownloadException("存储空间不足")
            }

            // 2. 流式补齐：被动缓存命中零流量，未命中按 Range 走网络，追加到半文件
            val dataSource = createPassiveUpstreamDataSource()
            var written = 0L
            var lastReport = 0L
            try {
                dataSource.open(DataSpec(Uri.parse(song.url), offset, C.LENGTH_UNSET.toLong()))
                FileOutputStream(part, true).use { out ->
                    val buffer = ByteArray(BUFFER_SIZE)
                    while (true) {
                        if (song.id in cancelledIds) throw DownloadException("已取消")
                        val read = dataSource.read(buffer, 0, buffer.size)
                        if (read == C.RESULT_END_OF_INPUT) break
                        if (read > 0) {
                            out.write(buffer, 0, read)
                            written += read
                        }
                        val now = System.currentTimeMillis()
                        if (total > 0 && now - lastReport >= PROGRESS_INTERVAL_MS) {
                            lastReport = now
                            reportProgress(song, (offset + written).toFloat() / total)
                        }
                    }
                }
            } finally {
                runCatching { dataSource.close() }
            }

            if (song.id in cancelledIds) throw DownloadException("已取消")
            finalizeDownload(song, part)
        } catch (e: DownloadException) {
            if (song.id in cancelledIds) {
                // 用户删除导致的中断：半文件一并清掉
                cancelledIds.remove(song.id)
                part.delete()
                Log.d(TAG, "🗑️ deleted mid-download: ${song.title}")
            } else {
                // 失败：保留半文件供断点续传，按钮回到"未下载"
                _errors.tryEmit("「${song.title}」下载失败：${e.message}")
                Log.w(TAG, "download failed: ${song.title}", e)
            }
        } catch (e: Exception) {
            cancelledIds.remove(song.id)
            _errors.tryEmit("「${song.title}」下载失败：网络错误")
            Log.w(TAG, "download failed: ${song.title}", e)
        } finally {
            _activeDownloads.update { it - song.id }
        }
    }

    private fun reportProgress(song: Song, progress: Float) {
        _activeDownloads.update {
            it + (song.id to ActiveDownload(song, ActiveState.Downloading(progress.coerceIn(0f, 1f))))
        }
    }

    private fun finalizeDownload(song: Song, part: File) {
        val target = completedFile(song.id)
        if (target.exists()) target.delete()
        if (!part.renameTo(target)) throw DownloadException("文件保存失败")
        _downloadedSongs.update { songs -> listOf(song) + songs.filterNot { it.id == song.id } }
        persist()
        Log.d(TAG, "✅ downloaded: ${song.title}")
    }

    private fun persist() {
        PrefsManager.setDownloadedSongs(_downloadedSongs.value)
    }

    /** 被动缓存（AudioCache）作为上游：命中零流量，未命中走网络（解混淆后为明文）；下载过程不回写被动缓存 */
    private fun createPassiveUpstreamDataSource(): DataSource =
        CacheDataSource.Factory()
            .setCache(AudioCache.get(appContext))
            // 与 MusicPlayerService 同口径的版本化缓存键（仓库级 ADR 0011 割接）
            .setCacheKeyFactory { dataSpec -> AudioCache.cacheKey(dataSpec.uri.toString()) }
            .setUpstreamDataSourceFactory(
                DeobfuscatingDataSourceFactory(
                    DefaultHttpDataSource.Factory()
                        .setDefaultRequestProperties(mapOf("Referer" to MEDIA_REFERER))
                )
            )
            .setCacheWriteDataSinkFactory(null)
            .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)
            .createDataSource()

    /** HEAD 探测内容总长；失败返回 -1（跳过空间校验，进度不确定） */
    private fun probeContentLength(url: String): Long = runCatching {
        val conn = URL(url).openConnection() as HttpURLConnection
        conn.requestMethod = "HEAD"
        conn.setRequestProperty("Referer", MEDIA_REFERER)
        conn.connectTimeout = 10_000
        conn.readTimeout = 10_000
        val len = conn.contentLengthLong
        conn.disconnect()
        len
    }.getOrDefault(-1L)

    private fun fileName(songId: String) = "${songId.replace(Regex("[^A-Za-z0-9_-]"), "_")}.mp3"

    private fun completedFile(songId: String) = File(downloadsDir, fileName(songId))

    private fun partFile(songId: String) = File(downloadsDir, fileName(songId) + PART_SUFFIX)
}

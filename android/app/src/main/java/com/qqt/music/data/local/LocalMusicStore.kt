package com.qqt.music.data.local

import android.content.Context
import android.net.Uri
import android.util.Log
import com.google.gson.Gson
import java.io.File
import java.io.FileOutputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * 本地音乐的落盘（spec「持久化」）：扫描索引与覆盖层各一个 App 私有 JSON 文件。
 *
 * - 索引 = 根目录列表 + 每首歌的身份、定位、标签快照与可用状态；覆盖层 = 身份 → 用户改过的字段
 * - 两个文件都带版本号。索引读到不认识的版本或内容按 [IndexLoad.Unreadable] 降级（空列表 + 提示重扫），
 *   不猜测迁移、不崩溃；覆盖层同理由调用方按「无覆盖」处理（展示回落文件标签）
 * - 均原子写入：先写临时文件并 fsync，再 rename 覆盖目标。写入途中被杀进程，
 *   重启读到的要么是上一次的完整状态，要么是这次的完整状态，不会半截
 * - 全部读写都在 IO 线程；写操作经同一把锁串行，串行扫描落盘与根目录落盘不会互相覆盖
 */
object LocalMusicStore {

    private const val TAG = "LocalMusicStore"
    private const val INDEX_FILE = "local_music_index.json"
    private const val OVERLAY_FILE = "local_music_overlay.json"

    /** 索引格式版本（改字段即升版本，旧版本数据按「无法识别」降级） */
    private const val INDEX_VERSION = 1

    /** 覆盖层格式版本 */
    private const val OVERLAY_VERSION = 1

    private val gson = Gson()

    /** 两处写盘可能并发（扫描完成、添加根目录），串行化整次「写临时文件 → rename」 */
    private val writeLock = Mutex()

    /** 索引快照：根目录与歌曲一起写，冷启动读回即用，不必再扫一遍 */
    data class Snapshot(val roots: List<LocalMusicRoot>, val songs: List<LocalSong>)

    /** 索引加载结果：没写过 / 读到完整状态 / 版本或内容无法识别 */
    sealed interface IndexLoad {
        /** 首次启动或索引已被清掉 */
        data object Absent : IndexLoad

        data class Loaded(val roots: List<LocalMusicRoot>, val songs: List<LocalSong>) : IndexLoad

        /** 版本不认识或内容读不出来：调用方降级为空列表并提示重新扫描 */
        data object Unreadable : IndexLoad
    }

    /**
     * 读扫描索引。[LocalSong] 里不含覆盖值——覆盖层是另一个文件，由调用方合并（ADR 0020）。
     */
    suspend fun loadIndex(context: Context): IndexLoad = withContext(Dispatchers.IO) {
        val file = indexFile(context)
        discardStaleTemp(file)
        if (!file.exists()) return@withContext IndexLoad.Absent
        val index = runCatching { gson.fromJson(file.readText(Charsets.UTF_8), IndexFile::class.java) }
            .onFailure { e -> Log.w(TAG, "索引读取失败", e) }
            .getOrNull() ?: return@withContext IndexLoad.Unreadable
        if (index.version != INDEX_VERSION || index.roots == null || index.songs == null) {
            Log.w(TAG, "索引版本或内容无法识别（version=${index.version}），按空列表降级")
            return@withContext IndexLoad.Unreadable
        }
        IndexLoad.Loaded(
            roots = index.roots.mapNotNull { it.toRoot() },
            songs = index.songs.mapNotNull { it.toSong() },
        )
    }

    /** 整表写扫描索引（根目录 + 歌曲）；写失败只记日志，内存里的列表照常可用 */
    suspend fun saveIndex(context: Context, snapshot: Snapshot) = withContext(Dispatchers.IO) {
        val json = gson.toJson(
            IndexFile(
                version = INDEX_VERSION,
                roots = snapshot.roots.map { RootDto(it.treeUri.toString(), it.name) },
                songs = snapshot.songs.map { it.toDto() },
            )
        )
        writeAtomically(indexFile(context), json)
    }

    /** 读覆盖层：身份 → 用户改过的字段；没有、损坏或版本不认识都返回空表（展示回落文件标签） */
    suspend fun loadOverlay(context: Context): Map<String, LocalSongOverride> = withContext(Dispatchers.IO) {
        val file = overlayFile(context)
        discardStaleTemp(file)
        if (!file.exists()) return@withContext emptyMap()
        val overlay = runCatching { gson.fromJson(file.readText(Charsets.UTF_8), OverlayFile::class.java) }
            .onFailure { e -> Log.w(TAG, "覆盖层读取失败", e) }
            .getOrNull() ?: return@withContext emptyMap()
        if (overlay.version != OVERLAY_VERSION) {
            Log.w(TAG, "覆盖层版本无法识别（version=${overlay.version}），按无覆盖处理")
            return@withContext emptyMap()
        }
        overlay.entries.orEmpty()
            .mapNotNull { (fingerprint, entry) ->
                if (fingerprint.isBlank()) return@mapNotNull null
                entry?.toOverride()?.takeUnless { it.isEmpty }?.let { fingerprint to it }
            }
            .toMap()
    }

    /** 整表写覆盖层（元数据编辑接入后由票据 09 调用）；空条目不入盘，保持「只存非空值」 */
    suspend fun saveOverlay(context: Context, overlay: Map<String, LocalSongOverride>) =
        withContext(Dispatchers.IO) {
            val entries = overlay.filterValues { !it.isEmpty }.mapValues { it.value.toDto() }
            val json = gson.toJson(OverlayFile(version = OVERLAY_VERSION, entries = entries))
            writeAtomically(overlayFile(context), json)
        }

    // ========== 原子写入 ==========

    /**
     * 写临时文件 → fsync → rename 覆盖目标。进程在任一步被杀，目标文件要么还是旧的完整内容，
     * 要么已经是新的完整内容；留下的 `.tmp` 是垃圾，下次读取时顺手清掉。
     * 调用方负责在 IO 线程上调用（序列化也在那边做）。
     */
    private suspend fun writeAtomically(target: File, json: String) {
        writeLock.withLock {
            runCatching {
                val temp = File(target.parentFile, target.name + TEMP_SUFFIX)
                FileOutputStream(temp).use { out ->
                    out.write(json.toByteArray(Charsets.UTF_8))
                    // rename 之前必须刷盘，否则可能把空文件换上去
                    out.fd.sync()
                }
                if (!temp.renameTo(target) && !(target.delete() && temp.renameTo(target))) {
                    error("rename 失败：${temp.name} -> ${target.name}")
                }
            }.onFailure { e -> Log.w(TAG, "写入失败：${target.name}", e) }
        }
    }

    /** 上次写到一半被杀留下的临时文件：目标文件仍是上一次的完整状态，这里只做清理 */
    private fun discardStaleTemp(target: File) {
        val temp = File(target.parentFile, target.name + TEMP_SUFFIX)
        if (temp.exists() && !temp.delete()) Log.w(TAG, "残留临时文件清不掉：${temp.name}")
    }

    private fun indexFile(context: Context) = File(context.filesDir, INDEX_FILE)

    private fun overlayFile(context: Context) = File(context.filesDir, OVERLAY_FILE)

    // ========== 落盘形态（DTO）==========
    // Gson 走反射注入、绕过 Kotlin 默认值，字段一律可空，映射时统一 orEmpty / 兜底，
    // 免得旧文件或手改文件缺字段时在读取端 NPE。

    private data class IndexFile(
        val version: Int = 0,
        val roots: List<RootDto>? = null,
        val songs: List<SongDto>? = null,
    )

    private data class RootDto(val treeUri: String? = null, val name: String? = null) {
        fun toRoot(): LocalMusicRoot? {
            val rawUri = treeUri?.takeIf { it.isNotBlank() } ?: return null
            val parsed = runCatching { Uri.parse(rawUri) }.getOrNull() ?: return null
            return LocalMusicRoot(parsed, name?.takeIf { it.isNotBlank() } ?: parsed.lastPathSegment.orEmpty())
        }
    }

    private data class TagsDto(
        val title: String? = null,
        val artist: String? = null,
        val album: String? = null,
        val albumArtist: String? = null,
        val trackNo: Int? = null,
        val year: Int? = null,
    )

    private data class SongDto(
        val fingerprint: String? = null,
        val treeUri: String? = null,
        val documentUri: String? = null,
        val relativePath: String? = null,
        val displayName: String? = null,
        val folderName: String? = null,
        val size: Long? = null,
        val lastModified: Long? = null,
        val durationMs: Long? = null,
        val tags: TagsDto? = null,
        val available: Boolean? = null,
    ) {
        fun toSong(): LocalSong? {
            // 身份与定位信息缺一不可：缺了这条记录既播不了也去不了重，直接丢
            val savedFingerprint = fingerprint?.takeIf { it.isNotBlank() } ?: return null
            val savedTreeUri = treeUri?.takeIf { it.isNotBlank() } ?: return null
            val savedDocumentUri = documentUri?.takeIf { it.isNotBlank() } ?: return null
            return LocalSong(
                fingerprint = savedFingerprint,
                treeUri = savedTreeUri,
                documentUri = savedDocumentUri,
                relativePath = relativePath.orEmpty(),
                displayName = displayName.orEmpty(),
                folderName = folderName.orEmpty(),
                size = size ?: 0L,
                lastModified = lastModified ?: 0L,
                durationMs = durationMs ?: 0L,
                tags = LocalSongTags(
                    title = tags?.title.orEmpty(),
                    artist = tags?.artist.orEmpty(),
                    album = tags?.album.orEmpty(),
                    albumArtist = tags?.albumArtist.orEmpty(),
                    trackNo = tags?.trackNo,
                    year = tags?.year,
                ),
                available = available ?: true,
            )
        }

        companion object {
            fun from(song: LocalSong) = SongDto(
                fingerprint = song.fingerprint,
                treeUri = song.treeUri,
                documentUri = song.documentUri,
                relativePath = song.relativePath,
                displayName = song.displayName,
                folderName = song.folderName,
                size = song.size,
                lastModified = song.lastModified,
                durationMs = song.durationMs,
                tags = TagsDto(
                    title = song.tags.title,
                    artist = song.tags.artist,
                    album = song.tags.album,
                    albumArtist = song.tags.albumArtist,
                    trackNo = song.tags.trackNo,
                    year = song.tags.year,
                ),
                available = song.available,
            )
        }
    }

    private fun LocalSong.toDto() = SongDto.from(this)

    private data class OverlayFile(
        val version: Int = 0,
        // 值可空：手改文件里可能出现 null 条目，读取时丢掉
        val entries: Map<String, OverrideDto?>? = null,
    )

    private data class OverrideDto(
        val title: String? = null,
        val artist: String? = null,
        val album: String? = null,
        val albumArtist: String? = null,
        val trackNo: Int? = null,
        val year: Int? = null,
    ) {
        fun toOverride() = LocalSongOverride(
            title = title?.takeIf { it.isNotBlank() },
            artist = artist?.takeIf { it.isNotBlank() },
            album = album?.takeIf { it.isNotBlank() },
            albumArtist = albumArtist?.takeIf { it.isNotBlank() },
            trackNo = trackNo,
            year = year,
        )
    }

    private fun LocalSongOverride.toDto() = OverrideDto(
        title = title,
        artist = artist,
        album = album,
        albumArtist = albumArtist,
        trackNo = trackNo,
        year = year,
    )

    private const val TEMP_SUFFIX = ".tmp"
}

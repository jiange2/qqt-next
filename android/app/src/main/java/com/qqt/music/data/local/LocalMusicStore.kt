package com.qqt.music.data.local

import android.content.Context
import android.util.Log
import com.google.gson.Gson
import java.io.File
import java.io.FileOutputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * 本地音乐的落盘（spec「库快照与刷新」）：库快照与元数据覆盖层各一个 App 私有 JSON 文件。
 *
 * - 库快照 = 歌曲行 + 排除清单（排除 UI 属票据 03，这里先把结构留出来）；覆盖层 = 身份 → 用户改过的字段
 * - 两个文件都带版本号。版本 2 起歌曲身份是媒体库行 ID；旧版本（按内容指纹记身份）读到即整份丢弃，
 *   不转换、不报错（spec「升级迁移」：覆盖层数据量小、重编辑成本低）
 * - 均原子写入：先写临时文件并 fsync，再 rename 覆盖目标。写入途中被杀进程，
 *   重启读到的要么是上一次的完整状态，要么是这次的完整状态，不会半截
 * - 全部读写都在 IO 线程；写操作经同一把锁串行
 */
object LocalMusicStore {

    private const val TAG = "LocalMusicStore"
    private const val INDEX_FILE = "local_music_index.json"
    private const val OVERLAY_FILE = "local_music_overlay.json"

    /** 库快照格式版本（改字段即升版本，旧版本数据整份丢弃） */
    private const val INDEX_VERSION = 2

    /** 覆盖层格式版本（键从指纹换成行 ID 身份，同批升版丢弃旧数据） */
    private const val OVERLAY_VERSION = 2

    private val gson = Gson()

    /** 两处写盘可能并发（库刷新提交、元数据编辑），串行化整次「写临时文件 → rename」 */
    private val writeLock = Mutex()

    /** 库快照：歌曲行 + 排除清单一起写，冷启动读回即用，不必再查媒体库 */
    data class Snapshot(val songs: List<LocalSong>, val excludedFolders: List<String> = emptyList())

    /** 库快照加载结果：没写过（含旧版本被丢弃）/ 读到完整状态 */
    sealed interface LibraryLoad {
        data object Absent : LibraryLoad

        data class Loaded(val songs: List<LocalSong>, val excludedFolders: List<String>) : LibraryLoad
    }

    /**
     * 读库快照。[LocalSong] 里不含覆盖值——覆盖层是另一个文件，由调用方合并（ADR 0020）。
     * 版本不认识或内容读不出来时整份丢弃（删文件），下次启动不再重复丢弃。
     */
    suspend fun loadLibrary(context: Context): LibraryLoad = withContext(Dispatchers.IO) {
        val file = indexFile(context)
        discardStaleTemp(file)
        if (!file.exists()) return@withContext LibraryLoad.Absent
        val index = runCatching { gson.fromJson(file.readText(Charsets.UTF_8), IndexFile::class.java) }
            .onFailure { e -> Log.w(TAG, "库快照读取失败", e) }
            .getOrNull()
        if (index == null || index.version != INDEX_VERSION || index.songs == null) {
            Log.w(TAG, "库快照版本或内容无法识别（version=${index?.version}），整份丢弃")
            deleteQuietly(file)
            return@withContext LibraryLoad.Absent
        }
        LibraryLoad.Loaded(
            songs = index.songs.mapNotNull { it.toSong() },
            excludedFolders = index.excludedFolders.orEmpty().filter { it.isNotBlank() },
        )
    }

    /** 整表写库快照（歌曲行 + 排除清单）；写失败只记日志，内存里的列表照常可用 */
    suspend fun saveLibrary(context: Context, snapshot: Snapshot) = withContext(Dispatchers.IO) {
        val json = gson.toJson(
            IndexFile(
                version = INDEX_VERSION,
                songs = snapshot.songs.map { it.toDto() },
                excludedFolders = snapshot.excludedFolders,
            )
        )
        writeAtomically(indexFile(context), json)
    }

    /** 读覆盖层：身份 → 用户改过的字段；没有、损坏或版本不认识都整份丢弃，返回空表（展示回落媒体库标签） */
    suspend fun loadOverlay(context: Context): Map<String, LocalSongOverride> = withContext(Dispatchers.IO) {
        val file = overlayFile(context)
        discardStaleTemp(file)
        if (!file.exists()) return@withContext emptyMap()
        val overlay = runCatching { gson.fromJson(file.readText(Charsets.UTF_8), OverlayFile::class.java) }
            .onFailure { e -> Log.w(TAG, "覆盖层读取失败", e) }
            .getOrNull()
        if (overlay == null || overlay.version != OVERLAY_VERSION) {
            Log.w(TAG, "覆盖层版本无法识别（version=${overlay?.version}），整份丢弃")
            deleteQuietly(file)
            return@withContext emptyMap()
        }
        overlay.entries.orEmpty()
            .mapNotNull { (identity, entry) ->
                if (identity.isBlank()) return@mapNotNull null
                entry?.toOverride()?.takeUnless { it.isEmpty }?.let { identity to it }
            }
            .toMap()
    }

    /** 整表写覆盖层（元数据编辑保存 / 恢复默认后调用）；空条目不入盘，保持「只存非空值」 */
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
                if (!temp.renameTo(target)) {
                    // 兜底分支：极少数文件系统不允许覆盖式 rename，只能删旧重试。
                    // 仍失败就把新内容留在 .tmp（下次写覆盖它，读取时清理），旧文件在该情况下已不可保留
                    if (!(target.exists() && target.delete() && temp.renameTo(target))) {
                        error("rename 失败：${temp.name} -> ${target.name}")
                    }
                }
            }.onFailure { e -> Log.w(TAG, "写入失败：${target.name}", e) }
        }
    }

    /** 上次写到一半被杀留下的临时文件：目标文件仍是上一次的完整状态，这里只做清理 */
    private fun discardStaleTemp(target: File) {
        val temp = File(target.parentFile, target.name + TEMP_SUFFIX)
        if (temp.exists() && !temp.delete()) Log.w(TAG, "残留临时文件清不掉：${temp.name}")
    }

    private fun deleteQuietly(file: File) {
        if (file.exists() && !file.delete()) Log.w(TAG, "旧数据文件删不掉：${file.name}")
    }

    private fun indexFile(context: Context) = File(context.filesDir, INDEX_FILE)

    private fun overlayFile(context: Context) = File(context.filesDir, OVERLAY_FILE)

    // ========== 落盘形态（DTO）==========
    // Gson 走反射注入、绕过 Kotlin 默认值，字段一律可空，映射时统一 orEmpty / 兜底，
    // 免得旧文件或手改文件缺字段时在读取端 NPE。

    private data class IndexFile(
        val version: Int = 0,
        val songs: List<SongDto>? = null,
        val excludedFolders: List<String>? = null,
    )

    private data class TagsDto(
        val title: String? = null,
        val artist: String? = null,
        val album: String? = null,
        val albumArtist: String? = null,
        val trackNo: Int? = null,
        val year: Int? = null,
    )

    private data class SongDto(
        val rowId: Long? = null,
        val volume: String? = null,
        val contentUri: String? = null,
        val path: String? = null,
        val displayName: String? = null,
        val size: Long? = null,
        val lastModified: Long? = null,
        val durationMs: Long? = null,
        val albumId: Long? = null,
        val tags: TagsDto? = null,
        val available: Boolean? = null,
    ) {
        fun toSong(): LocalSong? {
            // 身份与播放 URI 缺一不可：缺了这条记录既播不了也认不出，直接丢
            val savedRowId = rowId?.takeIf { it > 0L } ?: return null
            val savedContentUri = contentUri?.takeIf { it.isNotBlank() } ?: return null
            return LocalSong(
                rowId = savedRowId,
                volume = volume.orEmpty(),
                contentUri = savedContentUri,
                path = path.orEmpty(),
                displayName = displayName.orEmpty(),
                size = size ?: 0L,
                lastModified = lastModified ?: 0L,
                durationMs = durationMs ?: 0L,
                albumId = albumId ?: 0L,
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
                rowId = song.rowId,
                volume = song.volume,
                contentUri = song.contentUri,
                path = song.path,
                displayName = song.displayName,
                size = song.size,
                lastModified = song.lastModified,
                durationMs = song.durationMs,
                albumId = song.albumId,
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

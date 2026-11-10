package com.qqt.music.data.local

import android.Manifest
import android.content.ContentUris
import android.content.Context
import android.content.pm.PackageManager
import android.database.Cursor
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.util.Log
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 本地音乐库的唯一来源接缝（spec「接缝」）：音频权限检查 + 整库查询 + 行归一化。
 * 它下游的排除过滤（票据 03）、覆盖合并、专辑分组、排序、新增/移除 diff 是一条纯 Kotlin 管线，
 * JVM 单测的注入点就在这个接口上（伪造 [LocalSong] 行列表即可）。
 */
interface LocalMusicLibrarySource {

    /** 音频读取权限是否已授予（[queryLibrary] 的前提，页面进入与授权回调时都查一遍） */
    fun hasAudioPermission(): Boolean

    /**
     * 整库查询：按卷查 `MediaStore.Audio.Media` 并归一化为歌曲行。
     * 铃声明 / 闹钟 / 通知 / 录音 / 播客由 `IS_MUSIC != 0` 天然排除，`.nomedia` 与隐藏文件
     * 由系统媒体扫描器排除——App 不再重复判断（spec「扫描来源与权限」）。
     * 失败与真空库分开报告（[LocalLibraryResult]）：失败时刷新方保留上一次快照原样不动，
     * 不把空结果当「没有歌」提交（spec「播放与失效」：不放空列表假装没歌）。
     */
    suspend fun queryLibrary(): LocalLibraryResult
}

/**
 * 整库查询结果：查询失败与「真空库」是两回事——空列表也是一种查询事实（媒体库确实没有音乐），
 * 可以提交；失败则不携带任何行，刷新方保留上一次快照（内存与磁盘都不动），静默刷新无声跳过。
 */
sealed interface LocalLibraryResult {
    /** 全部卷查询成功：[rows] 为整库归一化行，可能为空（真空库） */
    data class Success(val rows: List<LocalSong>) : LocalLibraryResult

    /** 至少一卷查询失败（卷不可用、授权中途被收、provider 出错等） */
    data class Failure(val cause: Throwable) : LocalLibraryResult
}

/** 单卷查询结果（[MediaStoreLibrarySource] 逐卷产出，[mergeVolumeQueries] 的入参） */
internal sealed interface VolumeQueryResult {
    data class Rows(val rows: List<LocalSong>) : VolumeQueryResult

    data class Failed(val cause: Throwable) : VolumeQueryResult
}

/**
 * 多卷合并（纯函数，JVM 可测）：任一卷失败即整体 [LocalLibraryResult.Failure]——
 * 多卷合并是全有或全无：只把成功卷的部分结果当全量提交，会把失败卷的歌从快照里悄悄抹掉；
 * 全部成功则按卷序合并行（空卷清单与空结果同样是合法的 [LocalLibraryResult.Success]）。
 */
internal fun mergeVolumeQueries(results: List<VolumeQueryResult>): LocalLibraryResult {
    val rows = ArrayList<LocalSong>()
    for (result in results) {
        when (result) {
            is VolumeQueryResult.Rows -> rows += result.rows
            is VolumeQueryResult.Failed -> return LocalLibraryResult.Failure(result.cause)
        }
    }
    return LocalLibraryResult.Success(rows)
}

/**
 * 音频读取权限名按系统版本取用（spec「扫描来源与权限」，票据02）：Android 13（API 33）起
 * 申请音乐粒度的 `READ_MEDIA_AUDIO`，Android 12（API 32）及以下申请 `READ_EXTERNAL_STORAGE`。
 * 运行时请求（页面）与权限核对（[MediaStoreLibrarySource.hasAudioPermission]）共用这一个事实源，
 * 与清单的 `maxSdkVersion="32"` 约束保持同一版本边界；JVM 断言见 AudioReadPermissionTest。
 */
fun audioReadPermission(sdkInt: Int): String =
    if (sdkInt >= 33) Manifest.permission.READ_MEDIA_AUDIO
    else Manifest.permission.READ_EXTERNAL_STORAGE

/**
 * MediaStore 系统媒体库来源（spec，推翻 ADR 0019 的 SAF 目录扫描）：
 *
 * - 权限：Android 13+ 申请 `READ_MEDIA_AUDIO`，12 及以下申请 `READ_EXTERNAL_STORAGE`（清单标 maxSdkVersion 32）
 * - 多存储卷：Android 10+ 用 `getExternalVolumeNames` 逐外置卷查询合并（机身存储 + SD 卡）；
 *   Android 9 及以下只有主外置卷一个集合
 * - 失败口径：任一卷查询失败即整体失败（[LocalLibraryResult.Failure]），不把部分结果当全量提交——
 *   刷新方据此保留上一次快照，不放空列表假装没歌（spec「播放与失效」）
 * - 行归一化：行 ID、卷名、路径、文件名、大小、修改时间、时长、标签字段（专辑艺术家 Android 11+ 起
 *   取 `ALBUM_ARTIST` 列）、专辑 ID；音轨号按 Android 多碟惯例解出（碟位编码在高位）
 * - 不读标签解析器、不算指纹：查询即事实，标题等都是系统预抽取的现成结果
 */
class MediaStoreLibrarySource(
    private val context: Context,
    /** 注入设备 API 级别：列可用性与查询条件按它分支（测试可指定） */
    private val sdkInt: Int = Build.VERSION.SDK_INT,
) : LocalMusicLibrarySource {

    override fun hasAudioPermission(): Boolean = when {
        // 23 起才有运行时权限：按版本取对应权限名核对（13+ 音乐粒度 / 12 及以下外部存储）
        sdkInt >= 23 -> isGranted(audioReadPermission(sdkInt))
        // Android 5.x 及以下没有运行时权限：清单声明即授予
        else -> true
    }

    private fun isGranted(permission: String): Boolean =
        ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED

    override suspend fun queryLibrary(): LocalLibraryResult = withContext(Dispatchers.IO) {
        val volumes = if (sdkInt >= 29) {
            // 卷清单本身也可能取不到（个别 ROM 异常）：与单卷查询同口径按失败处理，不做部分合并
            runCatching { MediaStore.getExternalVolumeNames(context).toList() }
                .getOrElse { return@withContext LocalLibraryResult.Failure(it) }
        } else {
            // Android 9 及以下：只有主外置卷；卷名留空，歌曲身份退化为纯行 ID（单卷不会撞号）
            listOf(VOLUME_LEGACY)
        }
        mergeVolumeQueries(volumes.map { queryVolume(it) })
    }

    /** 单卷查询：失败不吞成空列表（[VolumeQueryResult.Failed]），整体成败由 [mergeVolumeQueries] 裁决 */
    private fun queryVolume(volumeName: String): VolumeQueryResult {
        val collection = if (sdkInt >= 29) {
            MediaStore.Audio.Media.getContentUri(volumeName)
        } else {
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        }
        return runCatching {
            context.contentResolver.query(
                collection,
                projection(),
                selection(),
                null,
                null,
            )?.use { cursor -> buildRows(cursor, volumeName, collection) } ?: emptyList()
        }.fold(
            onSuccess = { VolumeQueryResult.Rows(it) },
            onFailure = { e ->
                Log.w(TAG, "媒体库查询失败：$volumeName", e)
                VolumeQueryResult.Failed(e)
            },
        )
    }

    /** 查询条件：`IS_MUSIC != 0`，Android 10+ 排除 pending，Android 11+ 排除回收站（列按版本可用） */
    private fun selection(): String = buildString {
        append(MediaStore.Audio.Media.IS_MUSIC).append(" != 0")
        if (sdkInt >= 29) append(" AND ").append(MediaStore.Audio.Media.IS_PENDING).append(" = 0")
        if (sdkInt >= 30) append(" AND ").append(MediaStore.Audio.Media.IS_TRASHED).append(" = 0")
    }

    /**
     * 投影列与列索引的唯一事实源：列常量与「该列自哪个 API 起可读」成对、顺序即投影顺序，
     * 投影数组（[projection]）与列名 → 索引映射（[columnIndexOf]）都从这一份清单派生——
     * 读列处不再手写位置，重排或增删列只改声明，不会出现两处顺序对不上的错位读。
     */
    private val usableColumns: List<String> = mediaStoreProjection(sdkInt)

    private fun projection(): Array<String> = usableColumns.toTypedArray()

    /** 列名 → 投影内索引（与投影同源派生；未进投影的列取 -1，读取处按「无该列」处理） */
    private val columnIndexByName: Map<String, Int> =
        usableColumns.withIndex().associate { (index, name) -> name to index }

    private fun columnIndexOf(name: String): Int = columnIndexByName[name] ?: -1

    private fun buildRows(cursor: Cursor, volumeName: String, collection: Uri): List<LocalSong> {
        // 列索引全部从唯一声明派生（[columnIndexOf]），此处不手写位置；低版本不进投影的列为 -1
        val colId = columnIndexOf(MediaStore.Audio.Media._ID)
        val colData = columnIndexOf(MediaStore.Audio.Media.DATA)
        val colSize = columnIndexOf(MediaStore.Audio.Media.SIZE)
        val colDateModified = columnIndexOf(MediaStore.Audio.Media.DATE_MODIFIED)
        val colDuration = columnIndexOf(MediaStore.Audio.Media.DURATION)
        val colTitle = columnIndexOf(MediaStore.Audio.Media.TITLE)
        val colArtist = columnIndexOf(MediaStore.Audio.Media.ARTIST)
        val colAlbum = columnIndexOf(MediaStore.Audio.Media.ALBUM)
        val colAlbumId = columnIndexOf(MediaStore.Audio.Media.ALBUM_ID)
        val colTrack = columnIndexOf(MediaStore.Audio.Media.TRACK)
        val colYear = columnIndexOf(MediaStore.Audio.Media.YEAR)
        val colDisplayName = columnIndexOf(MediaStore.Audio.Media.DISPLAY_NAME)
        val colRelativePath = columnIndexOf(MediaStore.Audio.Media.RELATIVE_PATH)
        val colAlbumArtist = columnIndexOf(MediaStore.Audio.Media.ALBUM_ARTIST)
        val rows = ArrayList<LocalSong>()
        while (cursor.moveToNext()) {
            val rowId = cursor.getLong(colId)
            val data = cursor.text(colData)
            rows.add(
                LocalSong(
                    rowId = rowId,
                    volume = volumeName,
                    contentUri = ContentUris.withAppendedId(collection, rowId).toString(),
                    // 路径：29+ 优先 RELATIVE_PATH（排除清单与专辑分组都以它为准），缺了退回 DATA
                    path = cursor.text(colRelativePath).ifBlank { data },
                    // 文件名：29+ 直接读 DISPLAY_NAME，低版本（无该列）从 DATA 的尾段取
                    displayName = cursor.text(colDisplayName).ifBlank { data.substringAfterLast('/') },
                    size = cursor.getLong(colSize),
                    // DATE_MODIFIED 的单位是秒，换算成毫秒与文件系统口径一致
                    lastModified = cursor.getLong(colDateModified) * 1000L,
                    durationMs = cursor.getLong(colDuration),
                    albumId = cursor.getLong(colAlbumId),
                    tags = LocalSongTags(
                        title = cursor.text(colTitle),
                        artist = cursor.text(colArtist),
                        album = cursor.text(colAlbum),
                        // 专辑艺术家 Android 11+ 起才有该列，低版本为空、走现有兜底路径
                        albumArtist = cursor.text(colAlbumArtist),
                        trackNo = decodeMediaStoreTrack(cursor.getInt(colTrack)),
                        year = cursor.getInt(colYear).takeIf { it > 0 },
                    ),
                )
            )
        }
        return rows
    }

    /** NULL 读成空串并裁掉首尾空白（媒体库标签值可能带脏空白）；索引 -1（该列未进投影）同样按空串 */
    private fun Cursor.text(index: Int): String =
        if (index >= 0 && !isNull(index)) getString(index)?.trim().orEmpty() else ""

    private companion object {
        const val TAG = "MediaStoreLibrarySource"

        /** Android 9 及以下的唯一外置卷：不参与身份限定，仅作占位 */
        const val VOLUME_LEGACY = ""
    }
}

/**
 * 投影列的唯一声明（[MediaStoreLibrarySource] 的 [MediaStoreLibrarySource.projection] 与列索引
 * 都从这里派生）：列名与「该列自哪个 API 起可读」成对，顺序即投影顺序——基础列全程可用，
 * `DISPLAY_NAME` / `RELATIVE_PATH` 为 29+、`ALBUM_ARTIST` 为 30+（与 spec 的版本口径一致）。
 * 顶层纯函数，JVM 可测（[com.qqt.music.data.local.MediaStoreProjectionTest] 断言版本门槛）。
 */
internal fun mediaStoreColumnDeclarations(): List<Pair<String, Int>> = listOf(
    MediaStore.Audio.Media._ID to 0,
    MediaStore.Audio.Media.DATA to 0,
    MediaStore.Audio.Media.SIZE to 0,
    MediaStore.Audio.Media.DATE_MODIFIED to 0,
    MediaStore.Audio.Media.DURATION to 0,
    MediaStore.Audio.Media.TITLE to 0,
    MediaStore.Audio.Media.ARTIST to 0,
    MediaStore.Audio.Media.ALBUM to 0,
    MediaStore.Audio.Media.ALBUM_ID to 0,
    MediaStore.Audio.Media.TRACK to 0,
    MediaStore.Audio.Media.YEAR to 0,
    MediaStore.Audio.Media.DISPLAY_NAME to 29,
    MediaStore.Audio.Media.RELATIVE_PATH to 29,
    MediaStore.Audio.Media.ALBUM_ARTIST to 30,
)

/** 指定 API 级别实际进投影的列名（按声明顺序保留；低版本列不进投影也不读） */
internal fun mediaStoreProjection(sdkInt: Int): List<String> =
    mediaStoreColumnDeclarations().filter { sdkInt >= it.second }.map { it.first }

/**
 * `TRACK` 列的音轨号解码（Android 多碟惯例，官方文档：碟位编码在千位——
 * 第一张碟是 1xxx、第二张碟是 2xxx）：去掉碟位部分只留音轨号；没有碟位编码的原值直出。
 */
fun decodeMediaStoreTrack(raw: Int): Int? {
    val track = if (raw >= 1000) raw % 1000 else raw
    return track.takeIf { it > 0 }
}

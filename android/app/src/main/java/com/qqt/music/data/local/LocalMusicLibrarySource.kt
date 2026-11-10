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
     */
    suspend fun queryLibrary(): List<LocalSong>
}

/**
 * MediaStore 系统媒体库来源（spec，推翻 ADR 0019 的 SAF 目录扫描）：
 *
 * - 权限：Android 13+ 申请 `READ_MEDIA_AUDIO`，12 及以下申请 `READ_EXTERNAL_STORAGE`（清单标 maxSdkVersion 32）
 * - 多存储卷：Android 10+ 用 `getExternalVolumeNames` 逐外置卷查询合并（机身存储 + SD 卡）；
 *   Android 9 及以下只有主外置卷一个集合
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
        sdkInt >= 33 -> isGranted(Manifest.permission.READ_MEDIA_AUDIO)
        sdkInt >= 23 -> isGranted(Manifest.permission.READ_EXTERNAL_STORAGE)
        // Android 5.x 及以下没有运行时权限：清单声明即授予
        else -> true
    }

    private fun isGranted(permission: String): Boolean =
        ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED

    override suspend fun queryLibrary(): List<LocalSong> = withContext(Dispatchers.IO) {
        if (sdkInt >= 29) {
            MediaStore.getExternalVolumeNames(context).flatMap { volume -> queryVolume(volume) }
        } else {
            // Android 9 及以下：只有主外置卷；卷名留空，歌曲身份退化为纯行 ID（单卷不会撞号）
            queryVolume(VOLUME_LEGACY)
        }
    }

    /** 单卷查询：单点失败（卷不可用、授权中途被收）只损失该卷，不影响其它卷合并 */
    private fun queryVolume(volumeName: String): List<LocalSong> {
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
        }.getOrElse { e ->
            Log.w(TAG, "媒体库查询失败：$volumeName", e)
            emptyList()
        }
    }

    /** 查询条件：`IS_MUSIC != 0`，Android 10+ 排除 pending，Android 11+ 排除回收站（列按版本可用） */
    private fun selection(): String = buildString {
        append(MediaStore.Audio.Media.IS_MUSIC).append(" != 0")
        if (sdkInt >= 29) append(" AND ").append(MediaStore.Audio.Media.IS_PENDING).append(" = 0")
        if (sdkInt >= 30) append(" AND ").append(MediaStore.Audio.Media.IS_TRASHED).append(" = 0")
    }

    /**
     * 投影列与 [COL_XXX] 索引一一对应：基础列全程可用，`DISPLAY_NAME` / `RELATIVE_PATH`
     * 为 29+、`ALBUM_ARTIST` 为 30+，低版本不进投影也不读。
     */
    private fun projection(): Array<String> = buildList {
        add(MediaStore.Audio.Media._ID)
        add(MediaStore.Audio.Media.DATA)
        add(MediaStore.Audio.Media.SIZE)
        add(MediaStore.Audio.Media.DATE_MODIFIED)
        add(MediaStore.Audio.Media.DURATION)
        add(MediaStore.Audio.Media.TITLE)
        add(MediaStore.Audio.Media.ARTIST)
        add(MediaStore.Audio.Media.ALBUM)
        add(MediaStore.Audio.Media.ALBUM_ID)
        add(MediaStore.Audio.Media.TRACK)
        add(MediaStore.Audio.Media.YEAR)
        if (sdkInt >= 29) {
            add(MediaStore.Audio.Media.DISPLAY_NAME)
            add(MediaStore.Audio.Media.RELATIVE_PATH)
        }
        if (sdkInt >= 30) {
            add(MediaStore.Audio.Media.ALBUM_ARTIST)
        }
    }.toTypedArray()

    private fun buildRows(cursor: Cursor, volumeName: String, collection: Uri): List<LocalSong> {
        val rows = ArrayList<LocalSong>()
        while (cursor.moveToNext()) {
            val rowId = cursor.getLong(COL_ID)
            val data = cursor.text(COL_DATA)
            // 文件名：29+ 直接读 DISPLAY_NAME，低版本从 DATA 的尾段取
            val displayName = if (colDisplayName >= 0) cursor.text(colDisplayName).ifBlank {
                data.substringAfterLast('/')
            } else {
                data.substringAfterLast('/')
            }
            // 路径：29+ 优先 RELATIVE_PATH（排除清单与专辑分组都以它为准），缺了退回 DATA
            val path = if (colRelativePath >= 0) {
                cursor.text(colRelativePath).ifBlank { data }
            } else {
                data
            }
            rows.add(
                LocalSong(
                    rowId = rowId,
                    volume = volumeName,
                    contentUri = ContentUris.withAppendedId(collection, rowId).toString(),
                    path = path,
                    displayName = displayName,
                    size = cursor.getLong(COL_SIZE),
                    // DATE_MODIFIED 的单位是秒，换算成毫秒与文件系统口径一致
                    lastModified = cursor.getLong(COL_DATE_MODIFIED) * 1000L,
                    durationMs = cursor.getLong(COL_DURATION),
                    albumId = cursor.getLong(COL_ALBUM_ID),
                    tags = LocalSongTags(
                        title = cursor.text(COL_TITLE),
                        artist = cursor.text(COL_ARTIST),
                        album = cursor.text(COL_ALBUM),
                        // 专辑艺术家 Android 11+ 起才有该列，低版本为空、走现有兜底路径
                        albumArtist = if (colAlbumArtist >= 0) cursor.text(colAlbumArtist) else "",
                        trackNo = decodeMediaStoreTrack(cursor.getInt(COL_TRACK)),
                        year = cursor.getInt(COL_YEAR).takeIf { it > 0 },
                    ),
                )
            )
        }
        return rows
    }

    /** NULL 读成空串并裁掉首尾空白（媒体库标签值可能带脏空白） */
    private fun Cursor.text(index: Int): String =
        if (index >= 0 && !isNull(index)) getString(index)?.trim().orEmpty() else ""

    /** 29+ 才进投影，低版本置 -1 表示该列不存在（跟随注入的 [sdkInt]，测试可指定） */
    private val colDisplayName = if (sdkInt >= 29) 11 else -1
    private val colRelativePath = if (sdkInt >= 29) 12 else -1
    private val colAlbumArtist = if (sdkInt >= 30) 13 else -1

    private companion object {
        const val TAG = "MediaStoreLibrarySource"

        /** Android 9 及以下的唯一外置卷：不参与身份限定，仅作占位 */
        const val VOLUME_LEGACY = ""

        const val COL_ID = 0
        const val COL_DATA = 1
        const val COL_SIZE = 2
        const val COL_DATE_MODIFIED = 3
        const val COL_DURATION = 4
        const val COL_TITLE = 5
        const val COL_ARTIST = 6
        const val COL_ALBUM = 7
        const val COL_ALBUM_ID = 8
        const val COL_TRACK = 9
        const val COL_YEAR = 10
    }
}

/**
 * `TRACK` 列的音轨号解码（Android 多碟惯例，官方文档：碟位编码在千位——
 * 第一张碟是 1xxx、第二张碟是 2xxx）：去掉碟位部分只留音轨号；没有碟位编码的原值直出。
 */
fun decodeMediaStoreTrack(raw: Int): Int? {
    val track = if (raw >= 1000) raw % 1000 else raw
    return track.takeIf { it > 0 }
}

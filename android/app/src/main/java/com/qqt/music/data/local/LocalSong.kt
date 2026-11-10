package com.qqt.music.data.local

import com.qqt.music.data.api.model.Song

/** 本地歌曲 ID 命名空间（spec：`local:<指纹十六进制>`），与服务端数字 ID 天然隔离 */
const val LOCAL_SONG_ID_PREFIX = "local:"

/**
 * 文件标签快照（ADR 0019 用 MediaMetadataRetriever 读取）。空串 / null 表示文件没有该标签，
 * 展示值由 [LocalSong] 的 displayXxx() 兜底；用户覆盖层（ADR 0020）在后续票据接入。
 */
data class LocalSongTags(
    val title: String = "",
    val artist: String = "",
    val album: String = "",
    val albumArtist: String = "",
    val trackNo: Int? = null,
    val year: Int? = null,
)

/**
 * 本地歌曲（ADR 0021）：身份是内容指纹，路径与 SAF documentId 只用于定位文件。
 * [folderName] 是所在文件夹名（文件直接躺在根目录时即根目录名），供专辑名兜底使用。
 */
data class LocalSong(
    val fingerprint: String,
    val treeUri: String,
    val documentUri: String,
    val relativePath: String,
    val displayName: String,
    val folderName: String,
    val size: Long,
    val lastModified: Long,
    val durationMs: Long,
    val tags: LocalSongTags,
) {
    /** 播放兼容层 ID：`local:<指纹十六进制>` */
    val id: String get() = LOCAL_SONG_ID_PREFIX + fingerprint

    /** 展示标题：标签缺失回退为去掉扩展名的文件名 */
    fun displayTitle(): String = tags.title.ifBlank { displayName.substringBeforeLast('.') }

    fun displayArtist(): String = tags.artist

    fun displayAlbum(): String = tags.album

    fun displayAlbumArtist(): String = tags.albumArtist

    /**
     * 队列元素沿用现有歌曲模型：URL 为文件 content URI（播放数据源按 scheme 直读），
     * 标题 / 艺术家 / 时长取展示值，封面与歌词交给后续票据（封面缓存、本地歌不加载歌词）。
     */
    fun toSong(): Song = Song(
        id = id,
        title = displayTitle(),
        url = documentUri,
        artist = displayArtist(),
        duration = (durationMs / 1000).toString(),
        mp3Type = "local",
    )
}

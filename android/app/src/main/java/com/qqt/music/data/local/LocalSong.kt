package com.qqt.music.data.local

import com.qqt.music.data.api.model.Song

/** 本地歌曲 ID 命名空间（spec：`local:<指纹十六进制>`），与服务端数字 ID 天然隔离 */
const val LOCAL_SONG_ID_PREFIX = "local:"

/**
 * 播放模型侧的本地歌曲判定（UI 与播放层共用同一判据）：ID 走 `local:` 命名空间（spec 契约），
 * 或 [Song.url] 是 SAF 的 content:// 直读。本地歌曲没有服务端歌与歌词，收藏 / 歌单 / 评分 / 下载 / 歌词入口
 * 与相关请求一律不成立。
 *
 * 判据读的是 [Song.url] 本身：在线歌与已下载歌的 url 都是 http(s)（已下载只在播放时由 `resolveUri()`
 * 换成 file://），两者都不会命中。改这里的取值口径时需同步 `PlayerViewModel.resolveUri()`
 */
fun Song.isLocalSong(): Boolean =
    id.startsWith(LOCAL_SONG_ID_PREFIX) || url.startsWith("content://")

/**
 * 文件标签快照（ADR 0019 用 MediaMetadataRetriever 读取）。空串 / null 表示文件没有该标签，
 * 展示值由 [LocalSong] 的 displayXxx() 兜底。
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
 * 用户改过的字段（ADR 0020 覆盖层）：只存非空值，null 表示该字段仍取文件标签。
 * 与 [LocalSongTags] 分开落盘（[LocalMusicStore] 的覆盖层文件）：重扫与索引重建都不动它，
 * 所以文件移动、改名之后同样生效。
 */
data class LocalSongOverride(
    val title: String? = null,
    val artist: String? = null,
    val album: String? = null,
    val albumArtist: String? = null,
    val trackNo: Int? = null,
    val year: Int? = null,
) {
    /** 一个字段都没改：落盘时不写这种空壳（「恢复默认」等于删掉该歌曲的覆盖项） */
    val isEmpty: Boolean
        get() = title == null && artist == null && album == null &&
            albumArtist == null && trackNo == null && year == null
}

/**
 * 本地歌曲（ADR 0021）：身份是内容指纹，路径与 SAF documentId 只用于定位文件。
 * [folderName] 是所在文件夹名（文件直接躺在根目录时即根目录名），供专辑名兜底使用。
 * [tags] 是文件标签快照，[overlay] 是用户覆盖值：展示值 = 覆盖值优先、缺失取标签快照（ADR 0020）。
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
    val overlay: LocalSongOverride? = null,
    /** 可用状态：文件被删或读不到时置 false 供列表灰显（标记流程属票据 10，随索引一起落盘） */
    val available: Boolean = true,
) {
    /** 播放兼容层 ID：`local:<指纹十六进制>` */
    val id: String get() = LOCAL_SONG_ID_PREFIX + fingerprint

    /** 展示标题：覆盖值优先；标签缺失回退为去掉扩展名的文件名 */
    fun displayTitle(): String = overlay?.title ?: tags.title.ifBlank { displayName.substringBeforeLast('.') }

    fun displayArtist(): String = overlay?.artist ?: tags.artist

    fun displayAlbum(): String = overlay?.album ?: tags.album

    fun displayAlbumArtist(): String = overlay?.albumArtist ?: tags.albumArtist

    /** 音轨号与年份：覆盖值优先（专辑内排序按它，票据 08） */
    fun displayTrackNo(): Int? = overlay?.trackNo ?: tags.trackNo

    fun displayYear(): Int? = overlay?.year ?: tags.year

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

/**
 * 把覆盖层盖到歌曲上：列表、播放器、专辑分组都只认这一份合并结果（ADR 0020）。
 * 扫描出来的原始结果不带上覆盖值，索引落盘时也不写它——覆盖层是自己的文件。
 */
fun LocalSong.withOverride(overlay: LocalSongOverride?): LocalSong = copy(overlay = overlay)

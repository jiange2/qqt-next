package com.qqt.music.data.local

import android.net.Uri
import com.qqt.music.data.api.model.Song

/** 本地歌曲 ID 命名空间（spec：`local:<媒体库行 ID>`，Android 10+ 带卷名限定），与服务端数字 ID 天然隔离 */
const val LOCAL_SONG_ID_PREFIX = "local:"

/**
 * 播放模型侧的本地歌曲判定（UI 与播放层共用同一判据）：ID 走 `local:` 命名空间（spec 契约），
 * 或 [Song.url] 是 MediaStore 的 content:// 直读。本地歌曲没有服务端歌与歌词，收藏 / 歌单 / 评分 / 下载 /
 * 歌词入口与相关请求一律不成立。
 *
 * 判据读的是 [Song.url] 本身：在线歌与已下载歌的 url 都是 http(s)（已下载只在播放时由 `resolveUri()`
 * 换成 file://），两者都不会命中。改这里的取值口径时需同步 `PlayerViewModel.resolveUri()`
 */
fun Song.isLocalSong(): Boolean =
    id.startsWith(LOCAL_SONG_ID_PREFIX) || url.startsWith("content://")

/**
 * 文件标签快照（来自系统媒体库预抽取的列值）。空串 / null 表示文件没有该标签，
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
 * 与 [LocalSongTags] 分开落盘（[LocalMusicStore] 的覆盖层文件）：重扫与库刷新都不动它。
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
 * 本地歌曲（spec「身份」）：身份是媒体库的行 ID（[rowId] + [volume]，同一内容的多份副本各自成行，
 * App 不做内容级去重）。[contentUri] 是按卷 + 行 ID 构造的 MediaStore content URI，播放数据源按
 * scheme 直读；[path] 是所在目录（Android 10+ 为 RELATIVE_PATH，低版本为文件全路径），
 * 供专辑名兜底与排除清单（票据 03）派生文件夹使用。[tags] 是媒体库列值快照，
 * [overlay] 是用户覆盖值：展示值 = 覆盖值优先、缺失取标签快照（ADR 0020）。
 */
data class LocalSong(
    val rowId: Long,
    val volume: String,
    val contentUri: String,
    val path: String,
    val displayName: String,
    val size: Long,
    val lastModified: Long,
    val durationMs: Long,
    val albumId: Long,
    val tags: LocalSongTags,
    val overlay: LocalSongOverride? = null,
    /** 可用状态：文件被删或读不到时置 false 供列表灰显（下次刷新后该行自然消失） */
    val available: Boolean = true,
) {
    /**
     * 歌曲身份键：Android 10+ 为「卷名:行 ID」（防跨卷撞号），低版本只有行 ID（单卷不会撞）。
     * 播放兼容层 ID 与覆盖层键都用它。
     */
    val identityKey: String
        get() = if (volume.isBlank()) "$rowId" else "$volume:$rowId"

    /** 播放兼容层 ID：`local:<身份键>` */
    val id: String get() = LOCAL_SONG_ID_PREFIX + identityKey

    /**
     * 所在文件夹名（专辑名兜底用，spec 用户故事 23）：文件直接在存储根时为空串。
     * 与排除清单共用同一份目录段推导（[folderNameOf]）——RELATIVE_PATH 形态（Android 10+）
     * 取目录名，DATA 全路径形态（低版本）取目录段尾名；两种形态不再各推各的，
     * 此前本处自行 trimEnd + 取尾段，低版本 DATA 形态会错把文件名当文件夹名。
     */
    val folderName: String
        get() = folderNameOf(this)

    /** 本行所属媒体库专辑的系统预抽取专辑图 URI（spec「专辑与封面」），图片加载器直接加载与缓存 */
    val albumArtUri: Uri
        get() = Uri.parse(albumArtUriString(volume, albumId))

    /** 展示标题：覆盖值优先；标签缺失回退为去掉扩展名的文件名 */
    fun displayTitle(): String = overlay?.title ?: tags.title.ifBlank { displayName.substringBeforeLast('.') }

    fun displayArtist(): String = overlay?.artist ?: tags.artist

    fun displayAlbum(): String = overlay?.album ?: tags.album

    fun displayAlbumArtist(): String = overlay?.albumArtist ?: tags.albumArtist

    /** 音轨号与年份：覆盖值优先（专辑内排序按它） */
    fun displayTrackNo(): Int? = overlay?.trackNo ?: tags.trackNo

    fun displayYear(): Int? = overlay?.year ?: tags.year

    /**
     * 队列元素沿用现有歌曲模型：URL 为 MediaStore content URI（播放数据源按 scheme 直读），
     * 标题 / 艺术家 / 时长取展示值，歌词字段为空（本地歌不加载歌词）。
     * 封面不在队列元素上：专辑卡片与专辑页按成员行的媒体库专辑 ID 取系统预抽取的专辑图
     * （[albumArtUri]），由图片加载器直接加载与缓存。
     */
    fun toSong(): Song = Song(
        id = id,
        title = displayTitle(),
        url = contentUri,
        artist = displayArtist(),
        duration = (durationMs / 1000).toString(),
        mp3Type = "local",
    )
}

/**
 * 把覆盖层盖到歌曲上：列表、播放器、专辑分组都只认这一份合并结果（ADR 0020）。
 * 查询出来的原始结果不带上覆盖值，快照落盘时也不写它——覆盖层是自己的文件。
 */
fun LocalSong.withOverride(overlay: LocalSongOverride?): LocalSong = copy(overlay = overlay)

/**
 * 系统预抽取专辑图的 albumart URI 字符串口径（[LocalSong.albumArtUri] 用它解析成 [Uri]）：
 * `content://media/<卷>/audio/albumart/<专辑 ID>`，由图片加载器按行取图并缓存——App 不再自己
 * 解析或落盘封面（票据 04 删除自管 LRU 封面文件缓存）。卷名进 URI 使请求键 = 卷 + 专辑 ID：
 * 元数据编辑换组后，新旧专辑各自按当前成员行取图、同号专辑跨存储卷互不串图；卷名缺失
 * （Android 9 及以下只有主外置卷）统一回退旧式外置卷段。系统没有该专辑的预抽取图时
 * 内容提供方按文件不存在报错，UI 据此显示占位图。全程 content:// 本地读取，不发起网络请求。
 *
 * 纯字符串不碰 [Uri]，JVM 单测（[com.qqt.music.data.local.LocalAlbumCoverTest]）可直接断言。
 */
fun albumArtUriString(volume: String, albumId: Long): String =
    "content://media/" + (volume.ifBlank { VOLUME_EXTERNAL_LEGACY }) + "/audio/albumart/" + albumId

/** 旧式外置主卷卷名：albumart URI 的卷段在行没有卷名（Android 9 及以下）时用它 */
private const val VOLUME_EXTERNAL_LEGACY = "external"

/** 一次库刷新与上一次列表按身份比较的差异（spec：重扫完成后提示「新增 x、移除 y」） */
data class LibraryDiff(val added: Int, val removed: Int)

/**
 * 按身份（[LocalSong.id]）比较两次库内容：文件被移动不算「移除 + 新增」（同卷内移动行 ID 不变），
 * 同一内容的新副本会算成新增（各自行 ID，不做内容级去重）。
 */
fun List<LocalSong>.diffAgainst(previous: Collection<LocalSong>): LibraryDiff {
    val currentIds = mapTo(HashSet()) { it.id }
    val previousIds = previous.mapTo(HashSet()) { it.id }
    return LibraryDiff(
        added = currentIds.count { it !in previousIds },
        removed = previousIds.count { it !in currentIds },
    )
}

/**
 * 重扫提示的差异口径（spec 用户故事 17：完成后提示「新增 x、移除 y」，以便知道这次刷新改变了什么）：
 * 在排除过滤后的可见库上比较——被排除文件夹里的增删列表根本看不见，不该计入提示；
 * 两侧用同一份（当前的）排除清单：用户此刻看到的列表正是「旧库按现清单过滤」的形态，
 * 切换排除开关的即时重过滤已把所见对齐，这里再按它算才是「这次刷新改变了什么」。
 */
fun visibleLibraryDiff(
    current: List<LocalSong>,
    previous: List<LocalSong>,
    excludedFolders: Collection<String>,
): LibraryDiff =
    current.excludingFolders(excludedFolders).diffAgainst(previous.excludingFolders(excludedFolders))

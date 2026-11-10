package com.qqt.music.data.local

import android.util.Base64
import java.text.Collator
import java.util.Locale

/**
 * 本地专辑（spec「专辑与封面」）：把本地歌曲按专辑归并成一张唱片。
 *
 * [key] 是分组键，不是展示文案——同名专辑是否合并全看它；[name] / [artist] 是卡片与专辑页上的展示值。
 * 歌曲已按专辑内顺序排好（音轨号升序，音轨号为空按标题）。
 */
data class LocalAlbum(
    val key: String,
    val name: String,
    val artist: String,
    val songs: List<LocalSong>,
) {
    /**
     * 封面取样歌曲：文件夹封面取它所在目录，没有封面文件时取它的内嵌图。
     *
     * 元数据编辑可以把不同文件夹的歌改到同一张专辑，所以「同一张专辑的歌曲同在一个目录」不再是必然：
     * 取样的是这张专辑里排在最前那首所在的目录，文件夹封面只代表那一个来源（ADR 0020：封面归专辑，不归单曲）。
     */
    val coverSong: LocalSong get() = songs.first()
}

/**
 * 按「专辑名 + 专辑艺术家」的合并展示值分组（ADR 0020：分组只认覆盖层优先的展示值，
 * 编辑专辑名后歌曲立即换组）；专辑名为空时回退到「所在文件夹名 + 文件夹路径」，
 * 于是不同文件夹里的同名专辑互不合并（spec：专辑视图不出现串组）。
 *
 * 专辑之间按专辑名升序、专辑内按音轨号升序（音轨号为空按标题），全部用 [Locale.CHINA] 的中文排序规则。
 */
fun List<LocalSong>.groupIntoAlbums(): List<LocalAlbum> {
    val collator = Collator.getInstance(Locale.CHINA)
    val grouped = LinkedHashMap<String, MutableList<LocalSong>>()
    for (song in this) {
        grouped.getOrPut(song.albumKey()) { ArrayList() }.add(song)
    }
    val byTrack = trackOrder(collator)
    val albums = grouped.map { (key, songs) ->
        val ordered = songs.sortedWith(byTrack)
        val first = ordered.first()
        // 专辑名为空时用所在文件夹名当专辑名（散装音乐也能成组展示）
        val albumName = first.displayAlbum().ifBlank { first.folderName }
        LocalAlbum(
            key = key,
            name = albumName,
            // 卡片上的艺术家：专辑艺术家优先，缺失取该专辑第一首的艺术家
            artist = first.displayAlbumArtist().ifBlank { first.displayArtist() },
            songs = ordered,
        )
    }
    val byName = Comparator<LocalAlbum> { left, right ->
        val byAlbumName = collator.compare(left.name, right.name)
        if (byAlbumName != 0) {
            byAlbumName
        } else {
            // 同名不同专辑（不同文件夹、不同专辑艺术家）：再按艺术家与键定序，保证列表稳定
            val byArtist = collator.compare(left.artist, right.artist)
            if (byArtist != 0) byArtist else left.key.compareTo(right.key)
        }
    }
    return albums.sortedWith(byName)
}

/**
 * 专辑分组键的取值口径（[groupIntoAlbums] 用它归并歌曲）。
 *
 * - 有专辑名：专辑名 + 专辑艺术家
 * - 无专辑名：文件夹名 + 文件夹路径（文件夹路径带上所属树 URI）——只靠文件夹名会让
 *   `Music/Live` 与 `SD/Live` 两个目录被合并成一张专辑
 *
 * 元数据编辑（票据 09）也读它：改了专辑名 / 专辑艺术家，同一首歌的新旧键会落进两张不同的
 * 专辑，封面缓存要按这两个键分别作废重取。
 */
fun LocalSong.albumKey(): String {
    val album = displayAlbum()
    if (album.isNotBlank()) return "album\u0001$album\u0001${displayAlbumArtist()}"
    val folderPath = relativePath.substringBeforeLast('/', "")
    return "folder\u0001$treeUri\u0001$folderPath\u0001$folderName"
}

/** 专辑内顺序：音轨号升序，音轨号为空的一律排在后面并按标题；同一音轨号按标题 */
private fun trackOrder(collator: Collator): Comparator<LocalSong> = Comparator { left, right ->
    val leftTrack = left.displayTrackNo()
    val rightTrack = right.displayTrackNo()
    if (leftTrack == null || rightTrack == null) {
        // 一方没有音轨号：有号的在前面，两边都没号就按标题
        when {
            leftTrack == null && rightTrack == null -> collator.compare(left.displayTitle(), right.displayTitle())
            leftTrack == null -> 1
            else -> -1
        }
    } else if (leftTrack != rightTrack) {
        leftTrack.compareTo(rightTrack)
    } else {
        collator.compare(left.displayTitle(), right.displayTitle())
    }
}

/**
 * 歌曲视图顺序：按合并展示标题升序，与专辑分组共用 [Locale.CHINA] 的中文排序规则。
 * 扫描提交与元数据编辑都走这一份口径——改了标题，列表顺序也跟着变。
 */
fun Collection<LocalSong>.sortedByDisplayTitle(): List<LocalSong> {
    val collator = Collator.getInstance(Locale.CHINA)
    return sortedWith(Comparator { left, right -> collator.compare(left.displayTitle(), right.displayTitle()) })
}

/**
 * 专辑键 → 路由参数：Base64 URL-safe（字符集只有 `A-Z a-z 0-9 - _`），
 * 不含 `/`、`%`、`?` 等会被导航当成路径分隔或转义的字符（spec：专辑键需要 URL 安全编码）。
 */
fun encodeLocalAlbumKey(key: String): String = Base64.encodeToString(
    key.toByteArray(Charsets.UTF_8),
    Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING,
)

/** 路由参数 → 专辑键；不是合法 Base64 时不猜，由页面按「专辑不存在」处理 */
fun decodeLocalAlbumKey(encoded: String): String? = runCatching {
    String(
        Base64.decode(encoded, Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING),
        Charsets.UTF_8,
    )
}.getOrNull()
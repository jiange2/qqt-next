package com.qqt.music.data.local

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

/**
 * 票据04：专辑封面改系统预抽取专辑图（spec「专辑与封面」）。
 *
 * 只测纯 Kotlin 行为（spec「Testing Decisions」：给接缝喂归一化行、断言专辑的最终形态）：
 * - 专辑图请求键 = 卷 + 媒体库专辑 ID（albumart URI 的字符串口径，Android 10+ 带卷限定防跨卷串图）
 * - 元数据编辑换组后，新旧专辑各自按「当前成员行」重新取图键，不沿用换组前的旧键
 *
 * 断言全部用独立字面量（不是按实现重算），Uri 解析与 Coil 加载留给人工验收（本仓不跑 Gradle）。
 */
class LocalAlbumCoverTest {

    @Test
    fun albumArtUriString_isVolumeQualifiedAlbumartUri() {
        assertEquals(
            "content://media/external_primary/audio/albumart/42",
            albumArtUriString("external_primary", 42L),
        )
    }

    @Test
    fun albumArtUriString_fallsBackToLegacyExternalVolumeWhenVolumeBlank() {
        // Android 9 及以下只有主外置卷，行不带卷名：统一落旧式外置卷段
        assertEquals(
            "content://media/external/audio/albumart/7",
            albumArtUriString("", 7L),
        )
    }

    @Test
    fun albumArtUriString_distinguishesVolumesForSameAlbumId() {
        // 同号专辑在不同存储卷是两张专辑：请求键必须含卷，否则 SD 卡串用机身存储的图
        assertNotEquals(
            albumArtUriString("external_primary", 7L),
            albumArtUriString("8888-16FA", 7L),
        )
    }

    @Test
    fun regroupedAlbums_recomputeCoverFromCurrentMembers() {
        fun row(id: Long, albumId: Long, album: String, title: String, track: Int) = LocalSong(
            rowId = id,
            volume = "external_primary",
            contentUri = "content://media/external_primary/audio/media/$id",
            path = "Music/Albums/",
            displayName = "$title.mp3",
            size = 1L,
            lastModified = 0L,
            durationMs = 0L,
            albumId = albumId,
            tags = LocalSongTags(title = title, album = album, trackNo = track),
        )
        // a、b 在媒体库专辑 7（专辑名 X）；c 在媒体库专辑 9（专辑名 Z）
        val a = row(1L, 7L, "X", "Alpha", 1)
        val b = row(2L, 7L, "X", "Bravo", 2)
        val c = row(3L, 9L, "Z", "Charlie", 1)

        val before = listOf(a, b, c).groupIntoAlbums()
        val zBefore = before.first { it.name == "Z" }
        assertEquals(
            "content://media/external_primary/audio/albumart/9",
            albumArtUriString(zBefore.songs.first().volume, zBefore.songs.first().albumId),
        )

        // 编辑 a 的专辑名 X → Z：a 换组。换组后 X 只剩 b，Z 并入 a（同音轨号按标题序，Alpha 在前）
        val moved = a.withOverride(LocalSongOverride(album = "Z"))
        val after = listOf(moved, b, c).groupIntoAlbums()
        val xAfter = after.first { it.name == "X" }
        val zAfter = after.first { it.name == "Z" }

        // 旧专辑 X：封面键跟随当前成员行 b（仍是媒体库专辑 7 的图）
        assertEquals(
            "content://media/external_primary/audio/albumart/7",
            albumArtUriString(xAfter.songs.first().volume, xAfter.songs.first().albumId),
        )
        // 新专辑 Z：封面键跟随换入的成员行 a（媒体库专辑 7 的图），不再沿用换组前 Z 的 9 号图
        assertEquals(1L, zAfter.songs.first().rowId)
        assertEquals(
            "content://media/external_primary/audio/albumart/7",
            albumArtUriString(zAfter.songs.first().volume, zAfter.songs.first().albumId),
        )
        assertEquals(listOf(1L, 3L), zAfter.songs.map { it.rowId })
    }
}

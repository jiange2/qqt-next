package com.qqt.music.data.local

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 票据04：专辑页批量提交后的去向推算（spec「专辑页编辑模式」，[batchAlbumNewKeys]）。
 *
 * 只测纯 Kotlin 行为：空集 = 留在原页（未全选 / 全选但键不变），单元素 = 跟随唯一新专辑，
 * 多元素 = 拆成多张、无唯一去向。断言全部用独立字面量（不是按实现重算）。
 * 提交后展示值口径与 [LocalSong.batchAlbumOverride] 一致：trim 后非空输入即新展示值、
 * 留空保留原展示值、与标签同值清覆盖不改变展示值。
 * 导航接线（等 albums 派生追上再替换条目、超时兜底返回）依赖宿主与调度，留真机手工验收
 * （本仓不跑 Gradle）。
 */
class LocalBatchFollowTest {

    private fun row(
        rowId: Long,
        title: String,
        album: String = "旧专辑",
        albumArtist: String = "艺术家甲",
        available: Boolean = true,
        path: String = "Music/",
    ): LocalSong = LocalSong(
        rowId = rowId,
        volume = "external_primary",
        contentUri = "content://media/external_primary/audio/media/$rowId",
        path = path,
        displayName = "$title.mp3",
        size = 1L,
        lastModified = 0L,
        durationMs = 1000L,
        albumId = 1L,
        tags = LocalSongTags(title = title, album = album, albumArtist = albumArtist),
        available = available,
    )

    @Test
    fun `未全选时留在原页`() {
        val a = row(1L, "Alpha")
        val b = row(2L, "Bravo")

        // 未选中行保留旧键，原专辑仍在——即使输入会改键也不跟随
        assertEquals(emptySet<String>(), batchAlbumNewKeys(listOf(a, b), setOf(a.id), "新专辑", ""))
    }

    @Test
    fun `灰显行不可勾选因而不算全选留在原页`() {
        val a = row(1L, "Alpha")
        val b = row(2L, "Bravo", available = false)

        // 灰显行选不中、保留旧键：原专辑不会消失
        assertEquals(emptySet<String>(), batchAlbumNewKeys(listOf(a, b), setOf(a.id), "新专辑", ""))
    }

    @Test
    fun `全选但两字段留空或纯空白时留在原页`() {
        val a = row(1L, "Alpha")
        val b = row(2L, "Bravo")
        val selected = setOf(a.id, b.id)

        assertEquals(emptySet<String>(), batchAlbumNewKeys(listOf(a, b), selected, "", ""))
        assertEquals(emptySet<String>(), batchAlbumNewKeys(listOf(a, b), selected, "   ", "  "))
    }

    @Test
    fun `全选改专辑名时跟随唯一新键`() {
        val a = row(1L, "Alpha")
        val b = row(2L, "Bravo")

        assertEquals(
            setOf("album\u0001新专辑\u0001艺术家甲"),
            batchAlbumNewKeys(listOf(a, b), setOf(a.id, b.id), "新专辑", ""),
        )
    }

    @Test
    fun `全选改专辑艺术家时跟随唯一新键`() {
        val a = row(1L, "Alpha")
        val b = row(2L, "Bravo")

        assertEquals(
            setOf("album\u0001旧专辑\u0001新艺术家"),
            batchAlbumNewKeys(listOf(a, b), setOf(a.id, b.id), "", "新艺术家"),
        )
    }

    @Test
    fun `全选输入与当前展示值相同时留在原页`() {
        val a = row(1L, "Alpha").withOverride(LocalSongOverride(album = "覆盖专辑"))
        val b = row(2L, "Bravo").withOverride(LocalSongOverride(album = "覆盖专辑"))
        val selected = setOf(a.id, b.id)

        // 展示值 = 覆盖值「覆盖专辑」；输入同值（含首尾空白裁剪后同值）键不变
        assertEquals(emptySet<String>(), batchAlbumNewKeys(listOf(a, b), selected, "覆盖专辑", ""))
        assertEquals(emptySet<String>(), batchAlbumNewKeys(listOf(a, b), selected, "  覆盖专辑  ", ""))
    }

    @Test
    fun `全选输入与文件标签同值清覆盖后按标签值换键`() {
        val a = row(1L, "Alpha", album = "文件专辑").withOverride(LocalSongOverride(album = "覆盖专辑"))
        val b = row(2L, "Bravo", album = "文件专辑").withOverride(LocalSongOverride(album = "覆盖专辑"))

        // 与标签同值 → 清覆盖 → 展示值从「覆盖专辑」变回「文件专辑」，键跟着变
        assertEquals(
            setOf("album\u0001文件专辑\u0001艺术家甲"),
            batchAlbumNewKeys(listOf(a, b), setOf(a.id, b.id), "文件专辑", ""),
        )
    }

    @Test
    fun `文件夹兜底专辑只改专辑艺术家时键不变`() {
        val a = row(1L, "Alpha", album = "", path = "Music/Live/")
        val b = row(2L, "Bravo", album = "", path = "Music/Live/")

        // 专辑名为空 → 文件夹键不含专辑艺术家：只改艺术家不改键
        assertEquals(emptySet<String>(), batchAlbumNewKeys(listOf(a, b), setOf(a.id, b.id), "", "新艺术家"))
    }

    @Test
    fun `文件夹兜底专辑填专辑名且成员艺术家一致时跟随唯一新键`() {
        val a = row(1L, "Alpha", album = "", path = "Music/Live/")
        val b = row(2L, "Bravo", album = "", path = "Music/Live/")

        assertEquals(
            setOf("album\u0001新专辑\u0001艺术家甲"),
            batchAlbumNewKeys(listOf(a, b), setOf(a.id, b.id), "新专辑", ""),
        )
    }

    @Test
    fun `文件夹兜底专辑填专辑名且成员艺术家不同时拆成多张`() {
        val a = row(1L, "Alpha", album = "", albumArtist = "艺术家甲", path = "Music/Live/")
        val b = row(2L, "Bravo", album = "", albumArtist = "艺术家乙", path = "Music/Live/")

        // 专辑艺术家留空保留各自原值 → 同名不同艺术家拆成两张，无唯一去向
        assertEquals(
            setOf("album\u0001新专辑\u0001艺术家甲", "album\u0001新专辑\u0001艺术家乙"),
            batchAlbumNewKeys(listOf(a, b), setOf(a.id, b.id), "新专辑", ""),
        )
    }

    @Test
    fun `普通专辑只填专辑名时成员艺术家一致仍跟随唯一新键`() {
        val a = row(1L, "Alpha")
        val b = row(2L, "Bravo")

        assertEquals(
            setOf("album\u0001新专辑\u0001艺术家甲"),
            batchAlbumNewKeys(listOf(a, b), setOf(a.id, b.id), "新专辑", ""),
        )
    }

    @Test
    fun `空专辑列表返回空集`() {
        assertEquals(emptySet<String>(), batchAlbumNewKeys(emptyList(), emptySet(), "新专辑", ""))
    }

    @Test
    fun `albumKeyOf取当前展示值时与albumKey一致`() {
        val normal = row(1L, "Alpha").withOverride(LocalSongOverride(album = "覆盖专辑", albumArtist = "覆盖艺术家"))
        val folder = row(2L, "Bravo", album = "", path = "Music/Live/")

        assertEquals(normal.albumKey(), normal.albumKeyOf(normal.displayAlbum(), normal.displayAlbumArtist()))
        assertEquals(folder.albumKey(), folder.albumKeyOf(folder.displayAlbum(), folder.displayAlbumArtist()))
        assertEquals("folder\u0001external_primary\u0001Music/Live\u0001Live", folder.albumKey())
    }
}

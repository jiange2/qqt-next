package com.qqt.music.data.local

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 票据01：批量覆盖规则与一次提交链路（spec「批量提交语义（数据层）」「Testing Decisions」）。
 *
 * 只测纯 Kotlin 行为：批量面板两个原始输入 → 单首新覆盖（留空保留原覆盖、与文件标签同值清覆盖、
 * 其余四字段原样、trim），以及批量提交后的整表形态（空壳删键、未选中键保留、只换选中行、
 * 可用态取快照本值、标题排序、按新值换组）。断言全部用独立字面量（不是按实现重算）。
 * editLock 串行、一次原子落盘、Toast「已修改 n 首」与重启保留依赖 ViewModel 宿主与磁盘，
 * 留真机手工验收（本仓不跑 Gradle）。
 */
class LocalBatchMetadataTest {

    private fun row(
        rowId: Long,
        title: String,
        album: String = "文件专辑",
        albumArtist: String = "文件艺术家",
        available: Boolean = true,
    ): LocalSong = LocalSong(
        rowId = rowId,
        volume = "external_primary",
        contentUri = "content://media/external_primary/audio/media/$rowId",
        path = "Music/",
        displayName = "$title.mp3",
        size = 1L,
        lastModified = 0L,
        durationMs = 1000L,
        albumId = 1L,
        tags = LocalSongTags(title = title, album = album, albumArtist = albumArtist),
        available = available,
    )

    // ========== 批量覆盖生成（单首纯规则） ==========

    @Test
    fun `只填专辑时新专辑写入且专辑艺术家保留原覆盖`() {
        val song = row(1L, "Alpha")
            .withOverride(LocalSongOverride(album = "旧专辑", albumArtist = "旧艺术家"))

        val override = song.batchAlbumOverride("新专辑", "")

        assertEquals(LocalSongOverride(album = "新专辑", albumArtist = "旧艺术家"), override)
    }

    @Test
    fun `只填专辑艺术家时专辑保留原覆盖`() {
        val song = row(1L, "Alpha")
            .withOverride(LocalSongOverride(album = "旧专辑", albumArtist = "旧艺术家"))

        val override = song.batchAlbumOverride("", "新艺术家")

        assertEquals(LocalSongOverride(album = "旧专辑", albumArtist = "新艺术家"), override)
    }

    @Test
    fun `留空与纯空白输入都保留原覆盖字段`() {
        val song = row(1L, "Alpha")
            .withOverride(LocalSongOverride(album = "旧专辑", albumArtist = "旧艺术家"))

        // 批量面板「留空 = 不改」，与单首面板「留空 = 恢复文件原值」相反
        assertEquals(LocalSongOverride(album = "旧专辑", albumArtist = "旧艺术家"), song.batchAlbumOverride("", ""))
        assertEquals(LocalSongOverride(album = "旧专辑", albumArtist = "旧艺术家"), song.batchAlbumOverride("   ", "  "))
    }

    @Test
    fun `填值与文件标签相同时清除该字段覆盖`() {
        val song = row(1L, "Alpha")
            .withOverride(LocalSongOverride(album = "旧专辑", albumArtist = "旧艺术家"))

        val override = song.batchAlbumOverride("文件专辑", "文件艺术家")

        // 两字段都回到标签值：不制造把标签「冻结」住的假覆盖，整条覆盖成空壳
        assertEquals(LocalSongOverride(), override)
        assertTrue(override.isEmpty)
    }

    @Test
    fun `与标签同值只清该字段另一字段照常写入`() {
        val song = row(1L, "Alpha")
            .withOverride(LocalSongOverride(album = "旧专辑"))

        val override = song.batchAlbumOverride("文件专辑", "新艺术家")

        assertEquals(LocalSongOverride(albumArtist = "新艺术家"), override)
    }

    @Test
    fun `标题艺术家音轨号年份四字段原样保留`() {
        val song = row(1L, "Alpha").withOverride(
            LocalSongOverride(title = "标题", artist = "艺术家", album = "旧专辑", trackNo = 3, year = 2020),
        )

        val override = song.batchAlbumOverride("新专辑", "新艺术家")

        assertEquals(
            LocalSongOverride(
                title = "标题",
                artist = "艺术家",
                album = "新专辑",
                albumArtist = "新艺术家",
                trackNo = 3,
                year = 2020,
            ),
            override,
        )
    }

    @Test
    fun `输入首尾空白裁剪后存储`() {
        val song = row(1L, "Alpha")

        val override = song.batchAlbumOverride("  新专辑  ", "\t新艺术家\n")

        assertEquals(LocalSongOverride(album = "新专辑", albumArtist = "新艺术家"), override)
    }

    @Test
    fun `无覆盖的歌填值生成仅含两字段的覆盖`() {
        val song = row(1L, "Alpha")

        val override = song.batchAlbumOverride("新专辑", "新艺术家")

        assertEquals(LocalSongOverride(album = "新专辑", albumArtist = "新艺术家"), override)
        assertFalse(override.isEmpty)
    }

    // ========== 批量提交后的列表 / 覆盖表形态 ==========

    @Test
    fun `提交后空壳覆盖删键其余键与未选中键原样保留`() {
        val a = row(1L, "Alpha").withOverride(LocalSongOverride(album = "旧专辑", title = "标题"))
        val b = row(2L, "Bravo").withOverride(LocalSongOverride(album = "旧专辑"))
        val c = row(3L, "Charlie").withOverride(LocalSongOverride(album = "别的专辑"))
        val overlay = mapOf(
            a.id to LocalSongOverride(album = "旧专辑", title = "标题"),
            b.id to LocalSongOverride(album = "旧专辑"),
            c.id to LocalSongOverride(album = "别的专辑"),
        )

        // a 的专辑回到标签值但标题覆盖还在 → 键保留；b 整条成空壳 → 删键；c 未选中 → 原样
        val commit = batchAlbumCommit(listOf(a, b, c), overlay, setOf(a.id, b.id), "文件专辑", "")

        assertEquals(
            mapOf(
                a.id to LocalSongOverride(title = "标题"),
                c.id to LocalSongOverride(album = "别的专辑"),
            ),
            commit.overlay,
        )
    }

    @Test
    fun `提交后只替换选中行且可用状态取快照本值`() {
        val selected = row(1L, "Alpha", available = false)
        val other = row(2L, "Bravo")

        val commit = batchAlbumCommit(listOf(selected, other), emptyMap(), setOf(selected.id), "新专辑", "新艺术家")

        assertEquals(listOf(selected.id, other.id), commit.songs.map { it.id })
        val updated = commit.songs[0]
        assertEquals(LocalSongOverride(album = "新专辑", albumArtist = "新艺术家"), updated.overlay)
        // 灰显快照行改完仍灰显：available 取快照本值，不因提交复活
        assertFalse(updated.available)
        // 未选中行原样（连覆盖都不动）
        assertEquals(other, commit.songs[1])
    }

    @Test
    fun `提交后列表按展示标题重排`() {
        val c = row(3L, "Charlie")
        val a = row(1L, "Alpha")
        val b = row(2L, "Bravo")

        val commit = batchAlbumCommit(listOf(c, a, b), emptyMap(), setOf(a.id), "新专辑", "")

        assertEquals(listOf(a.id, b.id, c.id), commit.songs.map { it.id })
    }

    @Test
    fun `选中行已不在快照时覆盖表与列表都不动`() {
        val a = row(1L, "Alpha")

        val commit = batchAlbumCommit(listOf(a), emptyMap(), setOf("local:external_primary:999"), "新专辑", "")

        assertEquals(emptyMap<String, LocalSongOverride>(), commit.overlay)
        assertEquals(listOf(a), commit.songs)
    }

    @Test
    fun `两字段都填时选中歌并进同一张专辑`() {
        val a = row(1L, "Alpha", album = "旧A", albumArtist = "艺术家甲")
        val b = row(2L, "Bravo", album = "旧B", albumArtist = "艺术家乙")

        val commit = batchAlbumCommit(listOf(a, b), emptyMap(), setOf(a.id, b.id), "合辑", "合辑艺术家")
        val albums = commit.songs.groupIntoAlbums()

        // 归并键 = 专辑名 + 专辑艺术家：两字段一致 → 同一张专辑
        assertEquals(1, albums.size)
        assertEquals("合辑", albums[0].name)
        assertEquals("合辑艺术家", albums[0].artist)
        assertEquals(listOf(a.id, b.id), albums[0].songs.map { it.id })
    }

    @Test
    fun `只填专辑名时按各自专辑艺术家拆成同名不同艺术家的专辑`() {
        val a = row(1L, "Alpha", album = "旧A", albumArtist = "艺术家甲")
        val b = row(2L, "Bravo", album = "旧B", albumArtist = "艺术家乙")

        val commit = batchAlbumCommit(listOf(a, b), emptyMap(), setOf(a.id, b.id), "合辑", "")
        val albums = commit.songs.groupIntoAlbums()

        // 专辑艺术家保持原展示值 → 归并键不同 → 两张同名专辑（与单首编辑一致）
        assertEquals(2, albums.size)
        assertTrue(albums.all { it.name == "合辑" })
        assertEquals(setOf("艺术家甲", "艺术家乙"), albums.map { it.artist }.toSet())
        assertEquals(listOf(1, 1), albums.map { it.songs.size })
    }

    @Test
    fun `只填专辑时专辑艺术家展示值保持不变`() {
        val a = row(1L, "Alpha", album = "旧A", albumArtist = "艺术家甲")

        val commit = batchAlbumCommit(listOf(a), emptyMap(), setOf(a.id), "新专辑", "")

        val updated = commit.songs[0]
        assertEquals("新专辑", updated.displayAlbum())
        assertEquals("艺术家甲", updated.displayAlbumArtist())
    }
}
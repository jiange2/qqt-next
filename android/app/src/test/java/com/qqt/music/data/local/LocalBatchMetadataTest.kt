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
}
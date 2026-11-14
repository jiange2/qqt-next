package com.qqt.music.data.local

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 编辑候选派生与过滤（spec「候选池（数据层）」「Testing Decisions」）。
 *
 * 只测纯 Kotlin 行为：专辑候选（真实专辑归并、文件夹兜底排除、覆盖值优先、专辑艺术家缺失
 * 回退第一首艺术家、同名不同艺术家两行、展示相同去重、中文排序）、艺术家候选（去重、空值排除、
 * 覆盖优先）、专辑艺术家候选（并集去重）、过滤（空查询全量、忽略大小写、次文本命中、无匹配、trim）。
 * 断言全部用独立字面量（不是按实现重算）。控件展开 / 点选联动依赖 Compose 宿主，留真机手工验收
 * （本仓不跑 Gradle）。
 */
class LocalMetadataSuggestionsTest {

    private fun row(
        rowId: Long,
        title: String,
        artist: String = "",
        album: String = "",
        albumArtist: String = "",
        trackNo: Int? = null,
        path: String = "Music/",
        overlay: LocalSongOverride? = null,
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
        tags = LocalSongTags(title = title, artist = artist, album = album, albumArtist = albumArtist, trackNo = trackNo),
        overlay = overlay,
    )

    // ========== 专辑候选 ==========

    @Test
    fun `同专辑两首歌归并成一行候选`() {
        val songs = listOf(
            row(1L, "Alpha", album = "专辑甲", albumArtist = "张三"),
            row(2L, "Beta", album = "专辑甲", albumArtist = "张三"),
        )

        assertEquals(listOf(MetadataSuggestion("专辑甲", "张三")), songs.albumSuggestions())
    }

    @Test
    fun `文件夹兜底专辑不进候选`() {
        val songs = listOf(
            // 专辑标签为空：专辑视图里按文件夹名 Live 展示，但改标签无法归并进文件夹组
            row(1L, "Alpha", album = "", path = "Music/Live/"),
            row(2L, "Beta", album = "真实专辑", albumArtist = "张三"),
        )

        assertEquals(listOf(MetadataSuggestion("真实专辑", "张三")), songs.albumSuggestions())
    }

    @Test
    fun `专辑候选取覆盖后的展示值`() {
        val songs = listOf(
            row(
                1L, "Alpha", album = "旧专辑", albumArtist = "旧艺术家",
                overlay = LocalSongOverride(album = "新专辑", albumArtist = "新艺术家"),
            ),
        )

        assertEquals(listOf(MetadataSuggestion("新专辑", "新艺术家")), songs.albumSuggestions())
    }

    @Test
    fun `专辑艺术家缺失时回退按音轨序第一首的艺术家`() {
        val songs = listOf(
            row(1L, "B曲", artist = "歌手乙", album = "合辑", trackNo = 2),
            row(2L, "A曲", artist = "歌手甲", album = "合辑", trackNo = 1),
        )

        // 卡片口径：专辑艺术家为空取该专辑按音轨序第一首（A曲）的艺术家
        assertEquals(listOf(MetadataSuggestion("合辑", "歌手甲")), songs.albumSuggestions())
    }

    @Test
    fun `同名不同专辑艺术家是两行候选`() {
        val songs = listOf(
            row(1L, "Alpha", album = "同名", albumArtist = "张三"),
            row(2L, "Beta", album = "同名", albumArtist = "李四"),
        )

        assertEquals(
            listOf(MetadataSuggestion("同名", "李四"), MetadataSuggestion("同名", "张三")),
            songs.albumSuggestions(),
        )
    }

    @Test
    fun `展示相同的专辑候选去重`() {
        val songs = listOf(
            // 组一专辑艺术家为空、回退艺术家「回退B」；组二显式专辑艺术家「回退B」——展示相同
            row(1L, "Alpha", artist = "回退B", album = "同名", albumArtist = "", trackNo = 1),
            row(2L, "Beta", artist = "别人", album = "同名", albumArtist = "回退B"),
        )

        assertEquals(listOf(MetadataSuggestion("同名", "回退B")), songs.albumSuggestions())
    }

    @Test
    fun `专辑候选按中文序排列`() {
        val songs = listOf(
            row(1L, "Alpha", album = "张三专辑", albumArtist = "甲"),
            row(2L, "Beta", album = "李四专辑", albumArtist = "乙"),
            row(3L, "Gamma", album = "王五专辑", albumArtist = "丙"),
        )

        assertEquals(
            listOf("李四专辑", "王五专辑", "张三专辑"),
            songs.albumSuggestions().map { it.value },
        )
    }

    // ========== 艺术家候选 ==========

    @Test
    fun `艺术家候选去重且排除空值`() {
        val songs = listOf(
            row(1L, "Alpha", artist = "张三"),
            row(2L, "Beta", artist = "李四"),
            row(3L, "Gamma", artist = "张三"),
            row(4L, "Delta", artist = ""),
            row(5L, "Epsilon", artist = "   "),
        )

        assertEquals(listOf(MetadataSuggestion("李四"), MetadataSuggestion("张三")), songs.artistSuggestions())
    }

    @Test
    fun `艺术家候选取覆盖后的展示值`() {
        val songs = listOf(row(1L, "Alpha", artist = "旧艺术家", overlay = LocalSongOverride(artist = "新艺术家")))

        assertEquals(listOf(MetadataSuggestion("新艺术家")), songs.artistSuggestions())
    }

    // ========== 专辑艺术家候选 ==========

    @Test
    fun `专辑艺术家候选是专辑艺术家与艺术家的并集去重`() {
        val songs = listOf(
            row(1L, "Alpha", artist = "阿宝", albumArtist = "陈哥"),
            row(2L, "Beta", artist = "波仔", albumArtist = ""),
            row(3L, "Gamma", artist = "阿宝", albumArtist = "陈哥"),
        )

        assertEquals(
            listOf(MetadataSuggestion("阿宝"), MetadataSuggestion("波仔"), MetadataSuggestion("陈哥")),
            songs.albumArtistSuggestions(),
        )
    }

    @Test
    fun `空库三个候选函数都为空`() {
        val songs = emptyList<LocalSong>()

        assertTrue(songs.albumSuggestions().isEmpty())
        assertTrue(songs.artistSuggestions().isEmpty())
        assertTrue(songs.albumArtistSuggestions().isEmpty())
    }

    // ========== 过滤 ==========

    @Test
    fun `空查询与纯空白查询返回全量`() {
        val suggestions = listOf(MetadataSuggestion("专辑甲", "张三"), MetadataSuggestion("专辑乙", "李四"))

        assertEquals(suggestions, suggestions.filterSuggestions(""))
        assertEquals(suggestions, suggestions.filterSuggestions("   "))
    }

    @Test
    fun `过滤忽略大小写`() {
        val suggestions = listOf(MetadataSuggestion("ABC乐队"), MetadataSuggestion("xyz"))

        assertEquals(listOf(MetadataSuggestion("ABC乐队")), suggestions.filterSuggestions("abc"))
    }

    @Test
    fun `次文本命中也算匹配`() {
        val suggestions = listOf(MetadataSuggestion("专辑甲", "张三"), MetadataSuggestion("专辑乙", "李四"))

        assertEquals(listOf(MetadataSuggestion("专辑甲", "张三")), suggestions.filterSuggestions("张三"))
    }

    @Test
    fun `无匹配返回空`() {
        val suggestions = listOf(MetadataSuggestion("专辑甲", "张三"))

        assertTrue(suggestions.filterSuggestions("不存在的词").isEmpty())
    }

    @Test
    fun `查询首尾空白被裁剪`() {
        val suggestions = listOf(MetadataSuggestion("专辑甲", "张三"), MetadataSuggestion("专辑乙", "李四"))

        assertEquals(listOf(MetadataSuggestion("专辑甲", "张三")), suggestions.filterSuggestions("  张三  "))
    }
}
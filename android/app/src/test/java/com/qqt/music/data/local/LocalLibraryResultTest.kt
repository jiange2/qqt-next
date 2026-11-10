package com.qqt.music.data.local

import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 整库查询结果（[LocalLibraryResult]）多卷合并口径的行为测试：任一卷失败 → 整体失败（不带任何行，
 * 刷新方据此保留旧快照）；全部成功 → 按卷序合并。只测外部行为，不测内部实现（spec「Testing Decisions」）。
 *
 * 这条口径守住的是 spec「播放与失效」的底线：查询失败不能被吞成空列表提交——
 * 否则一次卷不可用就会把整份快照放空，页面从「有歌」闪成「没有音乐」。
 */
class LocalLibraryResultTest {

    private var nextRowId = 1L

    private fun row(title: String): LocalSong = LocalSong(
        rowId = nextRowId++,
        volume = "primary",
        contentUri = "content://media/primary/audio/$nextRowId",
        path = "Music/",
        displayName = "$title.mp3",
        size = 1L,
        lastModified = 0L,
        durationMs = 1000L,
        albumId = 1L,
        tags = LocalSongTags(title = title),
    )

    @Test
    fun `任一卷失败则整体失败且不携带任何行`() {
        val error = IllegalStateException("卷不可用")
        val result = mergeVolumeQueries(
            listOf(
                VolumeQueryResult.Rows(listOf(row("a"))),
                VolumeQueryResult.Failed(error),
                VolumeQueryResult.Rows(listOf(row("b"))),
            ),
        )
        assertTrue(result is LocalLibraryResult.Failure)
        // 失败原因原样带出，方便排查是哪一卷、为什么
        assertSame(error, (result as LocalLibraryResult.Failure).cause)
    }

    @Test
    fun `全部成功时按卷序合并行`() {
        val primary = listOf(row("a"), row("b"))
        val sdCard = listOf(row("c"))
        val result = mergeVolumeQueries(
            listOf(VolumeQueryResult.Rows(primary), VolumeQueryResult.Rows(sdCard)),
        )
        assertEquals(LocalLibraryResult.Success(primary + sdCard), result)
    }

    @Test
    fun `空卷清单与空结果都是成功——真空库也是一种查询事实`() {
        assertEquals(LocalLibraryResult.Success(emptyList()), mergeVolumeQueries(emptyList()))
        assertEquals(
            LocalLibraryResult.Success(emptyList()),
            mergeVolumeQueries(listOf(VolumeQueryResult.Rows(emptyList()))),
        )
    }
}

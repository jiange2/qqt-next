package com.qqt.music.data.local

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 重扫提示差异口径（[visibleLibraryDiff]，spec 用户故事 17）的行为测试：提示的是「这次刷新
 * 改变了什么」——即用户看得见的列表变了多少。被排除文件夹里的增删列表根本不显示，不能计入。
 */
class VisibleLibraryDiffTest {

    /** [rowId] 显式传入：跨「上一次库 / 这次库」必须同 ID 的行共用一个 ID（身份 = 卷 + 行 ID） */
    private fun row(rowId: Long, path: String, displayName: String): LocalSong = LocalSong(
        rowId = rowId,
        volume = "primary",
        contentUri = "content://media/primary/audio/$rowId",
        path = path,
        displayName = displayName,
        size = 1L,
        lastModified = 0L,
        durationMs = 1000L,
        albumId = 1L,
        tags = LocalSongTags(title = displayName.substringBeforeLast('.')),
    )

    @Test
    fun `可见文件夹里的新增与移除计入提示`() {
        val previous = listOf(row(1, "Music/Foo/", "old.mp3"))
        val current = listOf(
            row(2, "Music/Foo/", "new.mp3"),
            row(3, "Music/Bar/", "added.mp3"),
        )
        val diff = visibleLibraryDiff(current, previous, excludedFolders = emptySet())
        assertEquals(LibraryDiff(added = 2, removed = 1), diff)
    }

    @Test
    fun `被排除文件夹里的增删不计入提示——列表根本看不见这些变化`() {
        val previous = listOf(
            row(1, "Music/Foo/", "kept.mp3"),
            row(2, "Download/Noise/", "gone.mp3"),
        )
        val current = listOf(
            row(1, "Music/Foo/", "kept.mp3"),
            row(3, "Download/Noise/", "arrived.mp3"),
        )
        // Download/Noise 整棵被排除：里面移除一行、新增一行，用户看到的列表纹丝不动
        val diff = visibleLibraryDiff(current, previous, excludedFolders = setOf("Download/Noise"))
        assertEquals(LibraryDiff(added = 0, removed = 0), diff)
    }

    @Test
    fun `两侧按同一份当前排除清单过滤——先前可见后被排除的行不算移除`() {
        val previous = listOf(row(1, "Music/Foo/", "now-excluded.mp3"))
        val current = listOf(row(1, "Music/Foo/", "now-excluded.mp3"))
        // 这行还在媒体库里，只是刚被用户排除：它从列表消失是排除开关即时生效的结果，
        // 不是这次重扫造成的，重扫提示不该说「移除 1」
        val diff = visibleLibraryDiff(current, previous, excludedFolders = setOf("Music/Foo"))
        assertEquals(LibraryDiff(added = 0, removed = 0), diff)
    }

    @Test
    fun `排除清单为空时与全库 diff 一致`() {
        val previous = listOf(row(1, "Music/Foo/", "old.mp3"))
        val current = listOf(row(1, "Music/Foo/", "old.mp3"), row(2, "Music/Bar/", "new.mp3"))
        assertEquals(
            current.diffAgainst(previous),
            visibleLibraryDiff(current, previous, excludedFolders = emptySet()),
        )
    }
}

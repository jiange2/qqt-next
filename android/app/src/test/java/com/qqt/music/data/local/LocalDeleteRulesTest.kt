package com.qqt.music.data.local

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 删除 UI 侧纯规则（票据02，spec「Testing Decisions」）的行为测试：删除结果 → 整库列表整形、
 * App 确认框的版本门槛、删除后的专辑分组与计数。只测外部行为，不测内部实现；断言用独立字面量。
 *
 * 这些口径守住的是删除的列表联动底线：成功只移除目标行（灰显行、被排除文件夹的行都不受牵连），
 * 失败 / 取消一行不动；App 确认框只在 ≤28 出现（29/30+ 系统框即唯一确认，不得叠加）。
 * 系统框 / 权限框 / 落盘 / Toast 依赖框架与 ViewModel 宿主，留真机手工验收（本仓不跑 Gradle）。
 */
class LocalDeleteRulesTest {

    private fun row(
        rowId: Long,
        title: String,
        path: String = "Music/",
        album: String = "X",
        available: Boolean = true,
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
        tags = LocalSongTags(title = title, album = album),
        available = available,
    )

    @Test
    fun `删除成功只移除目标行其余原样保留`() {
        val normal = row(1L, "Alpha")
        val target = row(2L, "Bravo")
        val grey = row(3L, "Charlie", available = false)
        val excluded = row(4L, "Delta", path = "Excluded/")
        val songs = listOf(normal, target, grey, excluded)

        val updated = songsAfterDelete(songs, target.id, LocalDeleteResult.Success)

        // 整形的是整库：灰显行与被排除文件夹的行照常保留（展示过滤只发生在派生层），顺序不变
        assertEquals(listOf(normal, grey, excluded), updated)
    }

    @Test
    fun `删除失败与用户取消都不动列表`() {
        val songs = listOf(row(1L, "Alpha"), row(2L, "Bravo"))
        val targetId = songs[0].id

        // 失败（含带异常与不带异常两种）与取消：一行不删。契约是内容级「一行不动」，
        // 不绑定返回实例——返回等价副本也算对，故按内容断言
        assertEquals(songs, songsAfterDelete(songs, targetId, LocalDeleteResult.Failure()))
        assertEquals(songs, songsAfterDelete(songs, targetId, LocalDeleteResult.Failure(IllegalStateException("provider 出错"))))
        assertEquals(songs, songsAfterDelete(songs, targetId, LocalDeleteResult.Cancelled))
    }

    @Test
    fun `目标行已不在列表时成功结果也不改变内容`() {
        val songs = listOf(row(1L, "Alpha"))

        // 与在途刷新竞态下目标行可能已不在：不报错、其余行原样
        assertEquals(songs, songsAfterDelete(songs, "local:external_primary:999", LocalDeleteResult.Success))
        assertEquals(emptyList<LocalSong>(), songsAfterDelete(emptyList(), "local:external_primary:1", LocalDeleteResult.Success))
    }

    // ========== App 确认框版本门槛 ==========

    @Test
    fun `仅28及以下需要App内删除确认框`() {
        // ≤28 无系统框，App 确认框兜底（21–22 安装即授予写入权限，确认框照弹）
        assertTrue(needsAppDeleteConfirm(21))
        assertTrue(needsAppDeleteConfirm(22))
        assertTrue(needsAppDeleteConfirm(23))
        assertTrue(needsAppDeleteConfirm(28))
    }

    @Test
    fun `29及以上系统框即唯一确认不叠加App框`() {
        // 29 系统授权框 / 30+ 系统删除框即确认，再弹 App 框就是双重确认（spec「确认交互」）
        assertFalse(needsAppDeleteConfirm(29))
        assertFalse(needsAppDeleteConfirm(30))
        assertFalse(needsAppDeleteConfirm(35))
    }

    // ========== 删除后的专辑分组与计数 ==========

    @Test
    fun `删除后专辑分组与计数随之更新`() {
        val a1 = row(1L, "Alpha", album = "X")
        val a2 = row(2L, "Bravo", album = "X")
        val b1 = row(3L, "Charlie", album = "Z")

        val updated = songsAfterDelete(listOf(a1, a2, b1), a1.id, LocalDeleteResult.Success)
        val albums = updated.groupIntoAlbums()

        // 卡片与专辑页计数都是 songs.size 派生：X 剩 1 首、Z 不受影响
        assertEquals(2, albums.size)
        assertEquals(listOf(a2), albums.first { it.name == "X" }.songs)
        assertEquals(listOf(b1), albums.first { it.name == "Z" }.songs)
    }

    @Test
    fun `删空专辑后该专辑从分组中消失`() {
        val only = row(1L, "Alpha", album = "X")
        val other = row(2L, "Bravo", album = "Z")

        val updated = songsAfterDelete(listOf(only, other), only.id, LocalDeleteResult.Success)

        assertEquals(listOf("Z"), updated.groupIntoAlbums().map { it.name })
    }

    // ========== 专辑剩余可见行数（删空自动返回判据） ==========

    @Test
    fun `专辑剩余可见行数只数同专辑的可见行`() {
        val visible1 = row(1L, "Alpha", album = "X")
        val visible2 = row(2L, "Bravo", album = "X")
        val hidden = row(3L, "Charlie", album = "X", path = "Excluded/")
        val otherAlbum = row(4L, "Delta", album = "Z")
        val excludedFolders = setOf("Excluded")
        val songs = listOf(visible1, visible2, hidden, otherAlbum)

        // 同专辑 2 可见 + 1 被排除 → 2：被排除文件夹的行不计入
        assertEquals(2, remainingVisibleInAlbum(songs, visible1.albumKey(), excludedFolders))
        // 别的专辑的行不掺进来：Z 只有自己的 1 行
        assertEquals(1, remainingVisibleInAlbum(songs, otherAlbum.albumKey(), excludedFolders))
        // 整张专辑的行都在被排除文件夹 → 0（可见口径与专辑视图一致）
        assertEquals(0, remainingVisibleInAlbum(listOf(hidden), visible1.albumKey(), excludedFolders))
        // 空列表 → 0
        assertEquals(0, remainingVisibleInAlbum(emptyList(), visible1.albumKey(), excludedFolders))
        // 删掉专辑最后一首可见行 → 0：这正是「删空自动返回」的触发判据（被排除的行不算剩余）
        val soloVisible = row(5L, "Echo", album = "Solo")
        val soloHidden = row(6L, "Foxtrot", album = "Solo", path = "Excluded/")
        val afterLast = songsAfterDelete(listOf(soloVisible, soloHidden), soloVisible.id, LocalDeleteResult.Success)
        assertEquals(0, remainingVisibleInAlbum(afterLast, soloVisible.albumKey(), excludedFolders))
    }

    @Test
    fun `删除后专辑剩余可见行数与重新分组同口径`() {
        val visible1 = row(1L, "Alpha", album = "X")
        val visible2 = row(2L, "Bravo", album = "X")
        val hidden = row(3L, "Charlie", album = "X", path = "Excluded/")
        val excludedFolders = setOf("Excluded")

        val updated = songsAfterDelete(listOf(visible1, visible2, hidden), visible1.id, LocalDeleteResult.Success)

        // 端到端：删除整形 → 剩余可见行数 1；与专辑视图重新分组后的计数一致（各自对字面量断言）
        assertEquals(1, remainingVisibleInAlbum(updated, visible1.albumKey(), excludedFolders))
        assertEquals(1, updated.excludingFolders(excludedFolders).groupIntoAlbums().first { it.name == "X" }.songs.size)
    }
}
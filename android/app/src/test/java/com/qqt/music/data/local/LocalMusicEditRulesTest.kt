package com.qqt.music.data.local

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 票据02：编辑模式选择态与批量面板的 UI 侧纯规则（spec「编辑模式状态与选择态」「批量面板」）。
 *
 * 只测纯 Kotlin 行为：可见列表 → 可勾选行（灰显行跳过）、全选切换（已全选再点清空）、
 * 已全选判据（顶栏「全选 / 取消全选」文案），以及批量面板保存门槛（两字段 trim 皆空禁用）。
 * 断言全部用独立字面量（不是按实现重算）。勾选框渲染、点行 / 长按切换、顶栏 / 底栏 / 面板
 * 与退出路径依赖 Compose 宿主，留真机手工验收（本仓不跑 Gradle）。
 */
class LocalMusicEditRulesTest {

    private fun row(
        rowId: Long,
        title: String,
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
        tags = LocalSongTags(title = title),
        available = available,
    )

    // ========== 可勾选行（全选候选集） ==========

    @Test
    fun `可勾选行只取可用行的身份键`() {
        val songs = listOf(row(1L, "Alpha"), row(2L, "Bravo", available = false), row(3L, "Charlie"))

        // 灰显行（文件不可用 / 权限被收回）不可勾选：全选候选集里没有它
        assertEquals(setOf("local:external_primary:1", "local:external_primary:3"), selectableSongIds(songs))
    }

    @Test
    fun `空列表没有可勾选行`() {
        assertEquals(emptySet<String>(), selectableSongIds(emptyList()))
    }

    // ========== 全选切换 ==========

    @Test
    fun `未勾选时全选勾上全部可用行`() {
        val songs = listOf(row(1L, "Alpha"), row(2L, "Bravo", available = false), row(3L, "Charlie"))

        val selected = toggleSelectAll(songs, emptySet())

        // 灰显行跳过：只勾上两首可用行
        assertEquals(setOf("local:external_primary:1", "local:external_primary:3"), selected)
    }

    @Test
    fun `部分勾选时全选补齐为全部可用行`() {
        val songs = listOf(row(1L, "Alpha"), row(2L, "Bravo"))

        val selected = toggleSelectAll(songs, setOf("local:external_primary:1"))

        assertEquals(setOf("local:external_primary:1", "local:external_primary:2"), selected)
    }

    @Test
    fun `已全选时再点清空勾选`() {
        val songs = listOf(row(1L, "Alpha"), row(2L, "Bravo", available = false), row(3L, "Charlie"))

        val selected = toggleSelectAll(songs, setOf("local:external_primary:1", "local:external_primary:3"))

        assertEquals(emptySet<String>(), selected)
    }

    @Test
    fun `残留勾选不影响全选与清空判定`() {
        val songs = listOf(row(1L, "Alpha"), row(2L, "Bravo"))
        // 勾选后行被在途刷新移除：集合里留下不在可见列表的身份键
        val stale = "local:external_primary:999"

        // 未全选（Bravo 没勾）→ 补齐为全部可用行，残留键不带进去
        assertEquals(
            setOf("local:external_primary:1", "local:external_primary:2"),
            toggleSelectAll(songs, setOf("local:external_primary:1", stale)),
        )
        // 已全选（残留键不算候选）→ 清空，残留键一并丢弃
        assertEquals(
            emptySet<String>(),
            toggleSelectAll(songs, setOf("local:external_primary:1", "local:external_primary:2", stale)),
        )
    }

    @Test
    fun `没有可用行时全选是空操作`() {
        // 空库 / 整库灰显：无行可选，全选不产生任何勾选（spec：空库进入后全选为 no-op）
        assertEquals(emptySet<String>(), toggleSelectAll(emptyList(), emptySet()))
        assertEquals(
            emptySet<String>(),
            toggleSelectAll(listOf(row(1L, "Alpha", available = false)), emptySet()),
        )
    }

    // ========== 已全选判据（顶栏「全选 / 取消全选」文案） ==========

    @Test
    fun `全部可用行已勾选才算已全选`() {
        val songs = listOf(row(1L, "Alpha"), row(2L, "Bravo", available = false), row(3L, "Charlie"))

        // 灰显行不参与判定：两首可用行都勾上即已全选
        assertTrue(isAllSelected(songs, setOf("local:external_primary:1", "local:external_primary:3")))
    }

    @Test
    fun `部分勾选与零勾选都不算已全选`() {
        val songs = listOf(row(1L, "Alpha"), row(2L, "Bravo"))

        assertFalse(isAllSelected(songs, setOf("local:external_primary:1")))
        assertFalse(isAllSelected(songs, emptySet()))
    }

    @Test
    fun `没有可用行时不算已全选`() {
        // 空库 / 整库灰显：顶栏保持「全选」文案，点击是 no-op，不显示「取消全选」
        assertFalse(isAllSelected(emptyList(), emptySet()))
        assertFalse(isAllSelected(listOf(row(1L, "Alpha", available = false)), emptySet()))
    }

    @Test
    fun `残留勾选不影响已全选判定`() {
        val songs = listOf(row(1L, "Alpha"))

        assertTrue(isAllSelected(songs, setOf("local:external_primary:1", "local:external_primary:999")))
    }

    // ========== 批量面板保存门槛 ==========

    @Test
    fun `两字段皆空或纯空白时不可保存`() {
        assertFalse(batchPanelCanSave("", ""))
        assertFalse(batchPanelCanSave("   ", "\t"))
    }

    @Test
    fun `任一字段有内容即可保存`() {
        assertTrue(batchPanelCanSave("新专辑", ""))
        assertTrue(batchPanelCanSave("", "新艺术家"))
        // 首尾空白不算留空：trim 后有内容就放行（保存时由数据层统一裁剪）
        assertTrue(batchPanelCanSave("  新专辑  ", ""))
    }
}
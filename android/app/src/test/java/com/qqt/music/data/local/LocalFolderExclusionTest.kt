package com.qqt.music.data.local

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 文件夹黑名单（spec「文件夹黑名单」，票据 03）的行为测试：给归一化歌曲行与排除清单，
 * 断言过滤后的列表形态与候选文件夹清单——只测外部行为，不测内部实现（spec「Testing Decisions」）。
 *
 * 路径形态覆盖两种来源（[com.qqt.music.data.local.MediaStoreLibrarySource] 的行归一化）：
 * - Android 10+：[LocalSong.path] 是 RELATIVE_PATH（目录、尾带 `/`），如 `Music/Foo/`
 * - Android 9 及以下：[LocalSong.path] 是 DATA（文件全路径），如 `/storage/emulated/0/Music/Foo/a.mp3`
 */
class LocalFolderExclusionTest {

    private var nextRowId = 1L

    private fun row(path: String, displayName: String = "a.mp3"): LocalSong = LocalSong(
        rowId = nextRowId++,
        volume = "primary",
        contentUri = "content://media/primary/audio/$nextRowId",
        path = path,
        displayName = displayName,
        size = 1L,
        lastModified = 0L,
        durationMs = 1000L,
        albumId = 1L,
        tags = LocalSongTags(title = displayName.substringBeforeLast('.')),
    )

    // ========== 行所在文件夹的派生 ==========

    @Test
    fun `RELATIVE_PATH 形态的行目录就是去掉尾斜杠的路径本身`() {
        val song = row(path = "Music/Foo/")
        assertEquals("Music/Foo", song.let(::folderPathOf))
    }

    @Test
    fun `DATA 全路径形态的行目录是文件的目录段`() {
        val song = row(path = "/storage/emulated/0/Music/Foo/a.mp3")
        assertEquals("/storage/emulated/0/Music/Foo", folderPathOf(song))
    }

    @Test
    fun `展示名取目录名——RELATIVE_PATH 与 DATA 两种形态一致`() {
        assertEquals("Foo", folderNameOf(row(path = "Music/Foo/")))
        assertEquals("Foo", folderNameOf(row(path = "/storage/emulated/0/Music/Foo/a.mp3")))
    }

    @Test
    fun `文件直接在存储根时目录与目录名为空串`() {
        assertEquals("", folderPathOf(row(path = "a.mp3")))
        assertEquals("", folderNameOf(row(path = "/a.mp3")))
    }

    // ========== 候选文件夹清单（排除面板自动列出，无需手输路径）==========

    @Test
    fun `候选清单按行目录去重并带目录名`() {
        val folders = listOf(
            row(path = "Music/Foo/"),
            row(path = "Music/Foo/"),
            row(path = "/storage/emulated/0/Download/Bar/b.mp3"),
        ).distinctFolders()

        assertEquals(listOf("Bar", "Foo"), folders.map { it.name })
        assertTrue(folders.any { it.path == "Music/Foo" })
        assertTrue(folders.any { it.path == "/storage/emulated/0/Download/Bar" })
    }

    @Test
    fun `候选清单含已被排除的文件夹——否则无法取消排除`() {
        val rows = listOf(row(path = "Music/Foo/"))
        val folders = rows.distinctFolders()
        assertTrue(isFolderExcluded(folders.single().path, setOf("Music/Foo")))
        // 清单仍然列出它（派生自库行，与排除状态无关）
        assertEquals("Foo", folders.single().name)
    }

    // ========== 排除匹配语义：任一排除路径在其目录段边界上为前缀即排除 ==========

    @Test
    fun `排除文件夹本身则该文件夹的行不进列表`() {
        val rows = listOf(row(path = "Music/Foo/"), row(path = "Music/Bar/"))
        val visible = rows.excludingFolders(setOf("Music/Foo"))
        assertEquals(listOf("Music/Bar"), visible.map { it.path })
    }

    @Test
    fun `排除文件夹其全部子文件夹一并被排除`() {
        val rows = listOf(
            row(path = "Music/"),
            row(path = "Music/Live/"),
            row(path = "Music/Live/2024/"),
            row(path = "Music/Studio/"),
        )
        val visible = rows.excludingFolders(setOf("Music"))
        // 四行全在 Music 之下（含 Music 本身与两级子文件夹）：整棵目录树一起消失
        assertTrue(visible.isEmpty())
    }

    @Test
    fun `前缀必须落在目录段边界上——同名前缀目录不误伤`() {
        val rows = listOf(row(path = "MusicX/"), row(path = "Music/Foo/"))
        val visible = rows.excludingFolders(setOf("Music"))
        assertEquals(listOf("MusicX"), visible.map { folderPathOf(it) })
    }

    @Test
    fun `排除嵌套子文件夹不影响同级与父级`() {
        val rows = listOf(row(path = "Music/Live/"), row(path = "Music/Studio/"))
        val visible = rows.excludingFolders(setOf("Music/Live"))
        assertEquals(listOf("Music/Studio"), visible.map { folderPathOf(it) })
    }

    @Test
    fun `低版本 DATA 全路径形态按同样的边界语义匹配`() {
        val rows = listOf(
            row(path = "/storage/emulated/0/Download/x.mp3"),
            row(path = "/storage/emulated/0/DownloadList/y.mp3"),
        )
        val visible = rows.excludingFolders(setOf("/storage/emulated/0/Download"))
        assertEquals(
            listOf("/storage/emulated/0/DownloadList/y.mp3"),
            visible.map { it.path },
        )
    }

    @Test
    fun `排除清单里的尾斜杠与首尾空白不影响匹配`() {
        val rows = listOf(row(path = "Music/Foo/"))
        assertTrue(isFolderExcluded("Music/Foo", setOf("Music/Foo/", " Music/Bar/ ")))
        assertTrue(rows.excludingFolders(setOf(" Music/Foo//")).isEmpty())
    }

    @Test
    fun `排除清单为空时列表原样返回`() {
        val rows = listOf(row(path = "Music/Foo/"))
        assertEquals(rows, rows.excludingFolders(emptySet()))
        assertFalse(isFolderExcluded("Music/Foo", emptySet()))
    }

    @Test
    fun `存储根的散装行不受排除影响`() {
        val rows = listOf(row(path = "a.mp3"))
        assertEquals(rows, rows.excludingFolders(setOf("Music")))
    }

    // ========== 端到端：被排除的行不进专辑分组（spec「文件夹黑名单」）==========

    @Test
    fun `过滤后的列表做专辑分组时被排除文件夹的专辑一并消失`() {
        val rows = listOf(
            row(path = "Music/Foo/", displayName = "one.mp3"),
            row(path = "Music/Bar/", displayName = "two.mp3"),
        )
        val albums = rows.excludingFolders(setOf("Music/Foo")).groupIntoAlbums()
        assertEquals(1, albums.size)
        assertEquals("Bar", albums.single().name)
    }
}

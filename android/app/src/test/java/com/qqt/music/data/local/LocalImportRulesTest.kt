package com.qqt.music.data.local

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 本地音乐导入接缝（[LocalMusicImporter]，spec「唯一写侧接缝」，票据01）的纯函数行为测试：
 * 版本路由、重名解析、展示名规范化、落库目录口径、≤28 写入权限门槛、结果形状。
 * 只测外部行为，不测内部实现（spec「Testing Decisions」）。
 *
 * 这些口径守住的是导入落库的边界：29 起必须走 MediaStore 插入（清单无 requestLegacyExternalStorage，
 * 29 上 File 直写公共目录被 scoped storage 挡）；≤28 直写公共 Music/导入/ 再触发扫描；
 * 重名加后缀不覆盖（各自成行，ADR 0022）——29+ 精确匹配、≤28 大小写不敏感（外置存储文件系统
 * 大小写不敏感，大小写撞名同样加后缀而非失败）；展示名去非法字符、空名兜底、无扩展名补 .mp3
 * 保证进库（扩展名只是入库提示，播放器按字节嗅探）。
 * ContentResolver / MediaStore / MediaScannerConnection 的真实交互依赖框架，留真机手工验收（本仓不跑 Gradle）。
 * 断言用独立字面量（后缀格式 "name (2).ext"、默认名 "导入音频.mp3"、目录 "Music/导入/"），不与实现同源。
 */
class LocalImportRulesTest {

    // ========== 结果形状 ==========

    @Test
    fun `失败结果默认无原因`() {
        // 无异常的失败（插入被拒 / 未授予写入权限）原因为 null，与 LocalDeleteResult 同口径
        assertNull(LocalImportResult.Failure().cause)
    }

    @Test
    fun `失败结果可携带异常原因`() {
        val cause = IllegalStateException("provider 出错")
        assertSame(cause, LocalImportResult.Failure(cause).cause)
    }

    // ========== 版本路由 ==========

    @Test
    fun `Android 10 及以上走 MediaStore 插入`() {
        // 29 是分界：清单无 requestLegacyExternalStorage，29 上 File 直写公共目录被 scoped storage 挡，
        // 必须走 MediaStore 插入自有行（与 deleteRoute 的 29 分界同口径）
        assertEquals(ImportRoute.MediaStoreInsert, importRoute(29))
        assertEquals(ImportRoute.MediaStoreInsert, importRoute(30))
        assertEquals(ImportRoute.MediaStoreInsert, importRoute(35))
    }

    @Test
    fun `Android 9 及以下直写公共目录并触发扫描`() {
        assertEquals(ImportRoute.DirectWrite, importRoute(28))
        assertEquals(ImportRoute.DirectWrite, importRoute(23))
        assertEquals(ImportRoute.DirectWrite, importRoute(22))
        assertEquals(ImportRoute.DirectWrite, importRoute(21))
    }

    // ========== 重名解析 ==========

    @Test
    fun `无冲突原样返回`() {
        assertEquals("song.mp3", resolveImportFileName("song.mp3", emptyList()))
        assertEquals("song.mp3", resolveImportFileName("song.mp3", listOf("other.mp3", "song.flac")))
    }

    @Test
    fun `冲突加后缀并保留扩展名`() {
        // 后缀格式钉死 "name (2).ext"：从 2 起、括号前一个空格、扩展名原样保留
        assertEquals("song (2).mp3", resolveImportFileName("song.mp3", listOf("song.mp3")))
    }

    @Test
    fun `多次冲突递增直到不占用`() {
        assertEquals(
            "song (4).mp3",
            resolveImportFileName("song.mp3", listOf("song.mp3", "song (2).mp3", "song (3).mp3")),
        )
    }

    @Test
    fun `无扩展名冲突后缀直接追加`() {
        assertEquals("song (2)", resolveImportFileName("song", listOf("song")))
        assertEquals("song (3)", resolveImportFileName("song", listOf("song", "song (2)")))
    }

    @Test
    fun `空名兜底默认名`() {
        assertEquals("导入音频.mp3", resolveImportFileName("", emptyList()))
        assertEquals("导入音频.mp3", resolveImportFileName("   ", listOf("other.mp3")))
    }

    @Test
    fun `空名兜底后仍参与重名解析`() {
        assertEquals("导入音频 (2).mp3", resolveImportFileName("", listOf("导入音频.mp3")))
    }

    @Test
    fun `仅扩展名不同的同名不算冲突`() {
        // 重名按完整文件名精确匹配：song.flac 不挡 song.mp3（各自成行、不覆盖）
        assertEquals("song.mp3", resolveImportFileName("song.mp3", listOf("song.flac")))
    }

    @Test
    fun `点号开头的名按无扩展名处理`() {
        // ".mp3" 没有主名，整体视作无扩展名名，后缀直接追加
        assertEquals(".mp3 (2)", resolveImportFileName(".mp3", listOf(".mp3")))
    }

    @Test
    fun `结果保证不在现有名集合中`() {
        val existing = listOf("a.mp3", "a (2).mp3", "a (3).mp3", "a (4).mp3")
        val resolved = resolveImportFileName("a.mp3", existing)
        assertEquals("a (5).mp3", resolved)
        assertFalse(resolved in existing)
    }

    @Test
    fun `大小写不敏感撞名自动加后缀`() {
        // ≤28 外置存储文件系统大小写不敏感：Song.mp3 撞已有 song.mp3 必须加后缀而非失败（用户故事 10）；
        // 结果名保留来源名自身的大小写，后缀格式与精确匹配一致
        assertEquals("Song (2).mp3", resolveImportFileName("Song.mp3", listOf("song.mp3"), ignoreCase = true))
        assertEquals("song (2).mp3", resolveImportFileName("song.mp3", listOf("SONG.MP3"), ignoreCase = true))
        // 不撞名原样返回（大小写不敏感比较不改写结果名）
        assertEquals("Song.mp3", resolveImportFileName("Song.mp3", listOf("other.mp3"), ignoreCase = true))
    }

    @Test
    fun `大小写不敏感的后缀候选也判占用`() {
        // 后缀候选同样按大小写不敏感判占用：SONG (2).MP3 挡住 song (2).mp3，递增到 (3)
        assertEquals(
            "song (3).mp3",
            resolveImportFileName("song.mp3", listOf("Song.mp3", "SONG (2).MP3"), ignoreCase = true),
        )
        // 无扩展名名同口径
        assertEquals("Song (3)", resolveImportFileName("Song", listOf("song", "SONG (2)"), ignoreCase = true))
    }

    @Test
    fun `精确匹配放行大小写不同的名`() {
        // 29+ MediaStore 链路保持精确匹配（ignoreCase 默认 false）：大小写不同不算撞名、原样穿过，
        // 后缀候选也不按大小写判占用——29+ 行为与评审修复前完全一致
        assertEquals("Song.mp3", resolveImportFileName("Song.mp3", listOf("song.mp3")))
        assertEquals("Song.mp3", resolveImportFileName("Song.mp3", listOf("song.mp3"), ignoreCase = false))
        assertEquals("song (2).mp3", resolveImportFileName("song.mp3", listOf("song.mp3", "Song (2).mp3")))
    }

    // ========== 展示名规范化 ==========

    @Test
    fun `去掉路径分隔符与文件名非法字符`() {
        // / \ : * ? " < > | 全部剔除，避免路径穿越与跨平台 / MediaStore 非法名
        assertEquals("abcdefghij.mp3", normalizeImportDisplayName("a/b\\c:d*e?f\"g<h>i|j.mp3"))
    }

    @Test
    fun `剔除控制字符`() {
        assertEquals("song.mp3", normalizeImportDisplayName("song\u0000\u001F.mp3"))
    }

    @Test
    fun `裁掉首尾空白`() {
        assertEquals("song.mp3", normalizeImportDisplayName("  song.mp3  "))
    }

    @Test
    fun `保留扩展名与中文`() {
        assertEquals("夜曲.mp3", normalizeImportDisplayName(" 夜曲.mp3 "))
    }

    @Test
    fun `空名与全非法字符兜底默认名`() {
        assertEquals("导入音频.mp3", normalizeImportDisplayName(""))
        assertEquals("导入音频.mp3", normalizeImportDisplayName("   "))
        assertEquals("导入音频.mp3", normalizeImportDisplayName("///"))
        assertEquals("导入音频.mp3", normalizeImportDisplayName(":*?"))
    }

    @Test
    fun `纯点号名兜底默认名`() {
        // "." / ".." 是目录引用名，不能作文件名；整体点号同样兜底
        assertEquals("导入音频.mp3", normalizeImportDisplayName("."))
        assertEquals("导入音频.mp3", normalizeImportDisplayName(".."))
        assertEquals("导入音频.mp3", normalizeImportDisplayName("..."))
    }

    @Test
    fun `无扩展名来源名补 mp3 保证进库`() {
        // 微信语音等裸名：无扩展名时 MediaStore / 扫描器 MIME 推断失败、IS_MUSIC=0，
        // 提示成功却库里看不到（用户故事 15）——补 .mp3 入库提示，实际格式播放器按字节嗅探
        assertEquals("voice_123.mp3", normalizeImportDisplayName("voice_123"))
        assertEquals("夜曲.mp3", normalizeImportDisplayName("夜曲"))
    }

    @Test
    fun `已有扩展名原样保留不改写`() {
        // 扩展名只是入库提示：已有扩展名（含非 mp3 与大写）一律不动，不强行改成 .mp3
        assertEquals("song.flac", normalizeImportDisplayName("song.flac"))
        assertEquals("song.MP3", normalizeImportDisplayName("song.MP3"))
    }

    @Test
    fun `点号开头与结尾点号名视作无扩展名补 mp3`() {
        // 与 resolveImportFileName 同口径：点号开头无主名视作无扩展名；结尾点号扩展名为空同样补
        assertEquals(".mp3.mp3", normalizeImportDisplayName(".mp3"))
        assertEquals("song..mp3", normalizeImportDisplayName("song."))
    }

    // ========== 落库目录口径 ==========

    @Test
    fun `导入相对路径带尾斜杠`() {
        // RELATIVE_PATH 是目录本身、尾带 '/'（LocalFolderExclusion 匹配口径）；尾斜杠必须有，
        // 否则 29+ 重名查询 selection 匹配不到目标目录
        assertEquals("Music/导入/", IMPORT_RELATIVE_PATH)
    }

    @Test
    fun `导入文件夹名为导入`() {
        assertEquals("导入", IMPORT_FOLDER_NAME)
    }

    // ========== 写入权限门槛（≤28 直写） ==========

    @Test
    fun `仅 23 到 28 直写链路需要核对写入权限`() {
        assertTrue(legacyImportRequiresWriteGrant(23))
        assertTrue(legacyImportRequiresWriteGrant(28))
    }

    @Test
    fun `23 以下安装即授予不核对`() {
        // 运行时权限框从 API 23 起才有；21–22 清单声明即授予，直接写
        assertFalse(legacyImportRequiresWriteGrant(21))
        assertFalse(legacyImportRequiresWriteGrant(22))
    }

    @Test
    fun `29 及以上走 MediaStore 不核对写入权限`() {
        // 29+ 插入自有行不需要 WRITE（scoped storage 下 WRITE 也不授予公共目录直写）
        assertFalse(legacyImportRequiresWriteGrant(29))
        assertFalse(legacyImportRequiresWriteGrant(30))
        assertFalse(legacyImportRequiresWriteGrant(35))
    }
}
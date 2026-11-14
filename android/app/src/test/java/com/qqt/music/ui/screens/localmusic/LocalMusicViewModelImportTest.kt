package com.qqt.music.ui.screens.localmusic

import android.app.Application
import android.net.TestUri
import android.net.Uri
import com.qqt.music.data.local.LocalDeleteAction
import com.qqt.music.data.local.LocalImportResult
import com.qqt.music.data.local.LocalLibraryResult
import com.qqt.music.data.local.LocalMusicDeleter
import com.qqt.music.data.local.LocalMusicImporter
import com.qqt.music.data.local.LocalMusicLibrarySource
import com.qqt.music.data.local.LocalSong
import com.qqt.music.data.local.LocalSongTags
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/**
 * 导入编排（[LocalMusicViewModel.importAudio]，spec「ViewModel 编排」，票据02）的可观察结果测试：
 * 成功 → 提示「已导入到本地音乐」+ 整库刷新把新歌带进列表；失败 → 提示「导入失败」、列表不动；
 * 无音频权限 → 导入照样提示成功、刷新在权限门槛静默跳过不崩。只测外部行为、不断言内部调用顺序
 * （spec「Testing Decisions」）；文案字面量是 spec 钉死的独立事实源，不与实现同源。
 * 导入 fake 拆成功 / 失败两个无分支 fake（mocking.md「mock 内无分支」）：成功 fake 把新歌行放进
 * 来源模拟落库，失败 fake 不动来源。
 *
 * 基础设施（spec 缺口、票据02 裁定）：fake 接缝经 internal 构造器注入；Dispatchers.setMain 换成
 * StandardTestDispatcher 驱动 viewModelScope；testOptions.unitTests.isReturnDefaultValues=true
 * 让 Application() 可构造、框架方法返回默认值；测试 Application 覆写 getFilesDir 到每例独立的
 * 临时目录，快照读写走真实文件且用例间隔离。VM 内部 withContext(Dispatchers.IO/Default) 是真实
 * 调度器跳线程，runTest 对其按实时等待（官方文档口径，60s 超时兜底），故用挂起的 first{} 等可观察
 * 结果落定，不靠单次 advanceUntilIdle。不引 Robolectric/mockk；不触碰播放器（隔离红线：本 VM 与
 * PlayerViewModel 零耦合）。
 */
@OptIn(ExperimentalCoroutinesApi::class)
class LocalMusicViewModelImportTest {

    private val mainDispatcher = StandardTestDispatcher()

    /** 每例独立的私有目录：快照读写落在临时目录，用例之间不串库、也不往工作目录留文件 */
    @get:Rule
    val tempFolder = TemporaryFolder()

    private lateinit var app: Application

    @Before
    fun setUp() {
        Dispatchers.setMain(mainDispatcher)
        // mockable jar 下 getFilesDir() 返回 null，快照会落到工作目录相对路径（用例间互相污染）；
        // 覆写到临时目录（ContextWrapper.getFilesDir 非 final，javap 核实），读写走真实文件、各例隔离
        app = object : Application() {
            override fun getFilesDir(): File = tempFolder.root
        }
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    // ========== 成功：提示 + 刷新 ==========

    @Test
    fun `导入成功提示已导入到本地音乐并刷新出新歌`() = runTest(mainDispatcher.scheduler) {
        val source = FakeLibrarySource(permission = true, rows = emptyList())
        val vm = LocalMusicViewModel(
            app,
            source,
            UnusedDeleter(),
            FakeSuccessImporter(source, listOf(importedSong())),
        )
        vm.onPageEntered()
        // 等冷启动快照读回与进页面首次刷新落定：导入前列表为空
        vm.loading.first { !it }
        vm.refreshing.first { !it }
        assertTrue(vm.songs.value.isEmpty())

        vm.importAudio(TestUri("content://com.tencent.mm.external.fileprovider/audio/夜曲.mp3"))

        assertEquals("已导入到本地音乐", vm.notice.first { it != null })
        // 刷新发生：fake 来源在导入时才放入的新歌行，经 songs 可见（不刷新就看不到）
        val visible = vm.songs.first { it.isNotEmpty() }
        assertEquals(1, visible.size)
        assertEquals("夜曲.mp3", visible[0].displayName)
        assertEquals(42L, visible[0].rowId)
        // 已授权：新歌行可播，不灰显
        assertTrue(visible[0].available)
    }

    // ========== 失败：提示 + 列表不动 ==========

    @Test
    fun `导入失败提示导入失败且列表保持不动`() = runTest(mainDispatcher.scheduler) {
        val existing = existingSong()
        val source = FakeLibrarySource(permission = true, rows = listOf(existing))
        val vm = LocalMusicViewModel(
            app,
            source,
            UnusedDeleter(),
            FakeFailureImporter(LocalImportResult.Failure(IllegalStateException("来源不可读"))),
        )
        vm.onPageEntered()
        // 首次刷新把既有库带进列表，作为「不动」的基线
        val baseline = vm.songs.first { it.isNotEmpty() }
        assertEquals(1, baseline.size)

        vm.importAudio(TestUri("content://com.tencent.mm.external.fileprovider/audio/坏文件.mp3"))

        assertEquals("导入失败", vm.notice.first { it != null })
        advanceUntilIdle()
        // 失败不触发刷新、不改列表：既有歌原样还在
        assertEquals(baseline, vm.songs.value)
    }

    // ========== 无音频权限：导入照样成功，刷新静默跳过 ==========

    @Test
    fun `无音频权限导入仍提示成功且刷新静默跳过`() = runTest(mainDispatcher.scheduler) {
        val source = FakeLibrarySource(permission = false, rows = emptyList())
        val vm = LocalMusicViewModel(
            app,
            source,
            UnusedDeleter(),
            FakeSuccessImporter(source, listOf(importedSong())),
        )
        vm.onPageEntered()

        vm.importAudio(TestUri("content://com.tencent.mm.external.fileprovider/audio/夜曲.mp3"))

        // 落库不需要音频读取权限：照样提示成功（spec 用户故事 14）
        assertEquals("已导入到本地音乐", vm.notice.first { it != null })
        // 刷新跑到权限门槛静默跳过：等它落定后列表仍为空，不崩、不放空快照假装没歌
        vm.refreshing.first { !it }
        advanceUntilIdle()
        assertTrue(vm.songs.value.isEmpty())
    }

    // ========== 测试替身 ==========

    /** 伪造库来源：权限与行列表可控，查询即返回现值（无框架 IO） */
    private class FakeLibrarySource(
        private val permission: Boolean,
        var rows: List<LocalSong>,
    ) : LocalMusicLibrarySource {
        override fun hasAudioPermission(): Boolean = permission
        override suspend fun queryLibrary(): LocalLibraryResult = LocalLibraryResult.Success(rows)
    }

    /** 伪造导入接缝（成功）：把新歌行放进来源（模拟「文件已落库」）并返回 Success——无分支（mocking.md） */
    private class FakeSuccessImporter(
        private val source: FakeLibrarySource,
        private val importedRows: List<LocalSong>,
    ) : LocalMusicImporter {
        override suspend fun importAudio(sourceUri: Uri): LocalImportResult {
            source.rows = importedRows
            return LocalImportResult.Success
        }
    }

    /** 伪造导入接缝（失败）：不动来源（库保持原样），返回预置失败——无分支（mocking.md） */
    private class FakeFailureImporter(
        private val failure: LocalImportResult.Failure,
    ) : LocalMusicImporter {
        override suspend fun importAudio(sourceUri: Uri): LocalImportResult = failure
    }

    /** 删除接缝与导入无关：被调到即响亮失败（编排不越界的信号） */
    private class UnusedDeleter : LocalMusicDeleter {
        override suspend fun startDelete(song: LocalSong): LocalDeleteAction =
            throw NotImplementedError("导入编排不应调用删除接缝")

        override suspend fun onSystemDialogResult(song: LocalSong, resultCode: Int): LocalDeleteAction =
            throw NotImplementedError("导入编排不应调用删除接缝")

        override suspend fun onWritePermissionResult(song: LocalSong, granted: Boolean): LocalDeleteAction =
            throw NotImplementedError("导入编排不应调用删除接缝")
    }

    /** 导入落库后的新歌行（独立字面量，照 LocalLibraryResultTest 的 row() 先例） */
    private fun importedSong(): LocalSong = LocalSong(
        rowId = 42L,
        volume = "external_primary",
        contentUri = "content://media/external_primary/audio/media/42",
        path = "Music/导入/",
        displayName = "夜曲.mp3",
        size = 1024L,
        lastModified = 1_700_000_000_000L,
        durationMs = 200_000L,
        albumId = 7L,
        tags = LocalSongTags(title = "夜曲", artist = "周杰伦"),
    )

    /** 库里既有的歌（失败用例的「列表不动」基线） */
    private fun existingSong(): LocalSong = LocalSong(
        rowId = 1L,
        volume = "external_primary",
        contentUri = "content://media/external_primary/audio/media/1",
        path = "Music/",
        displayName = "晴天.mp3",
        size = 2048L,
        lastModified = 1_600_000_000_000L,
        durationMs = 269_000L,
        albumId = 3L,
        tags = LocalSongTags(title = "晴天", artist = "周杰伦"),
    )
}
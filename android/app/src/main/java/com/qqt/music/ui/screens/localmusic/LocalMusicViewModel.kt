package com.qqt.music.ui.screens.localmusic

import android.app.Application
import android.content.Intent
import android.net.Uri
import android.os.SystemClock
import android.provider.DocumentsContract
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.qqt.music.data.local.LocalAlbum
import com.qqt.music.data.local.LocalCoverCache
import com.qqt.music.data.local.LocalMetadataDraft
import com.qqt.music.data.local.LocalMusicRoot
import com.qqt.music.data.local.LocalMusicScanner
import com.qqt.music.data.local.LocalMusicStore
import com.qqt.music.data.local.LocalNumberInput
import com.qqt.music.data.local.LocalSong
import com.qqt.music.data.local.LocalSongOverride
import com.qqt.music.data.local.albumKey
import com.qqt.music.data.local.findOverlap
import com.qqt.music.data.local.groupIntoAlbums
import com.qqt.music.data.local.overrideFrom
import com.qqt.music.data.local.sortedByDisplayTitle
import com.qqt.music.data.local.trackNoInput
import com.qqt.music.data.local.valueOrNull
import com.qqt.music.data.local.withOverride
import com.qqt.music.data.local.yearInput
import java.io.File
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/** 扫描状态：目录树给不出可靠总量，进度只报已处理文件数与已收录歌曲数 */
data class LocalScanState(
    val running: Boolean = false,
    val scannedFiles: Int = 0,
    val foundSongs: Int = 0,
    val currentName: String = "",
    val cancelled: Boolean = false,
    /** 本次扫描失败：索引保持上一次完整结果，页面给失败提示（空列表时用失败空态） */
    val failed: Boolean = false,
)

/**
 * 元数据保存结果：写入成功（带合并后的歌曲，调用方拿去同步播放器展示值）或字段非法：
 * 面板上按字段提示（音轨号只接受正整数、年份只接受四位数字）。
 */
sealed interface LocalMetadataSaveResult {
    data class Saved(val song: LocalSong) : LocalMetadataSaveResult

    data class Invalid(
        val trackNoError: String? = null,
        val yearError: String? = null,
    ) : LocalMetadataSaveResult
}

/**
 * 本地音乐页状态：授权目录、扫描状态与扫描结果。
 *
 * 索引与覆盖层各一个 App 私有 JSON 文件（[LocalMusicStore]）：启动时在 IO 线程读回并合并，
 * 冷启动直接出列表、不必先扫一遍；扫描完成后再落盘。页面与顶栏菜单共用同一个实例（提升在导航层），
 * 离开页面扫描继续、回到页面不重复发起；扫描结果整体替换：取消或失败都保留上一次完整结果。
 * 重扫是增量的：把当前索引交给扫描器，未变文件跳过指纹与标签读取，
 * 完成时按内容身份（指纹）提示新增 / 移除条数。
 * 根目录可以授权多个（结果合并成一份库）：与已有目录重叠的授权被拒绝，
 * 移除某个目录只按树剔除索引里的歌曲，不触发全量重扫。
 * 专辑视图与专辑页共用同一份索引派生出的 [albums]；封面按专辑懒解析、带容量上限（[coverCache]）。
 * 元数据编辑（[saveMetadata] / [restoreDefaultMetadata]）只改写覆盖层与展示值，不重扫、不碰文件。
 * 权限与失效：进入页面核对根目录的持久化授权（[checkRootPermissions]），失效目录的歌曲灰显；
 * 播放时文件被删 / 读不到（[markUnavailable]）只标记该行不可用，不自动重扫，也不影响其它歌曲。
 */
class LocalMusicViewModel(application: Application) : AndroidViewModel(application) {

    private val scanner = LocalMusicScanner(application)

    /** 本地专辑封面缓存：文件夹封面 → 内嵌图，按专辑键落到 App 私有目录，容量有上限 */
    private val coverCache = LocalCoverCache(application)

    private val _roots = MutableStateFlow<List<LocalMusicRoot>>(emptyList())
    val roots: StateFlow<List<LocalMusicRoot>> = _roots.asStateFlow()

    private val _songs = MutableStateFlow<List<LocalSong>>(emptyList())

    /**
     * 持久化授权失效的根目录（tree URI）。授权检查在 IO 线程异步进行，不阻塞页面首帧；
     * 只影响展示：这里不写回索引，重新授权成功后歌曲自然恢复可播。
     */
    private val _revokedTrees = MutableStateFlow<Set<String>>(emptySet())
    val revokedTrees: StateFlow<Set<String>> = _revokedTrees.asStateFlow()

    /**
     * 展示用歌曲列表：来自被回收授权的目录的歌曲在这里标成不可用（灰显、不可点），
     * 但不改动磁盘上的可用状态——重新授权后无需重扫或清数据即恢复（spec「权限与失效」）。
     */
    val songs: StateFlow<List<LocalSong>> = combine(_songs, _revokedTrees) { songs, revoked ->
        if (revoked.isEmpty()) {
            songs
        } else {
            songs.map { if (it.treeUri in revoked && it.available) it.copy(available = false) else it }
        }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    /** 所有根目录的授权都被回收：页面改为「重新选择目录」的空态，而不是让用户以为歌丢了 */
    val allRootsRevoked: StateFlow<Boolean> = combine(_roots, _revokedTrees) { roots, revoked ->
        roots.isNotEmpty() && roots.all { it.treeUri.toString() in revoked }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, false)

    /**
     * 专辑视图（spec「专辑与封面」）：完全从索引派生，按「专辑名 + 专辑艺术家」分组并排好序。
     * 不单独存状态——歌曲变了（扫描提交、移除目录、编辑覆盖层）专辑分组自然跟着变。
     */
    val albums: StateFlow<List<LocalAlbum>> = songs
        // 分组与排序（中文 Collator）放在默认调度器上：歌多时也不占主线程
        .map { songs -> withContext(Dispatchers.Default) { songs.groupIntoAlbums() } }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    /**
     * 封面缓存代次：每次清空缓存（重扫提交）加一。专辑键不变而封面文件可能换了，
     * 卡片以它为 key 重新取封面，不必等用户退出再进入页面。
     */
    private val _coverGeneration = MutableStateFlow(0)
    val coverGeneration: StateFlow<Int> = _coverGeneration.asStateFlow()

    private val _scan = MutableStateFlow(LocalScanState())
    val scan: StateFlow<LocalScanState> = _scan.asStateFlow()

    /** 启动时索引还没读回来：首帧不把「还没读出来」当成「没有音乐」 */
    private val _loading = MutableStateFlow(true)
    val loading: StateFlow<Boolean> = _loading.asStateFlow()

    /** 索引不可识别：已按空列表降级（根目录仍从系统授权重建），页面提示重新扫描 */
    private val _indexUnreadable = MutableStateFlow(false)
    val indexUnreadable: StateFlow<Boolean> = _indexUnreadable.asStateFlow()

    /**
     * 一次性轻提示（扫描完成的新增 / 移除数、扫描失败）：页面 Toast 后清空。
     * 用 StateFlow 而不是 SharedFlow，提示在页面尚未重组完成时也不会丢。
     */
    private val _notice = MutableStateFlow<String?>(null)
    val notice: StateFlow<String?> = _notice.asStateFlow()

    /** 覆盖层（ADR 0020）：身份 → 用户改过的字段；自己的文件，重扫只换索引不动它 */
    private var overlay: Map<String, LocalSongOverride> = emptyMap()

    /** 元数据编辑串行化：「换列表 → 写覆盖层 → 作废封面」是一条链，两次编辑不交错 */
    private val editLock = Mutex()

    /** 同一时刻只允许一个扫描任务：重复触发（重新选目录、菜单重扫、重新进页面）都不叠加 */
    private var scanJob: Job? = null

    /** 扫描中又被要求重扫：取消当前任务，收尾后按最新目录重跑一次（同一时刻只有一个扫描任务） */
    private var rescanRequested = false

    init {
        restoreLibrary()
    }

    /**
     * 冷启动读回索引与覆盖层并在 IO 线程合并，不做任何网络请求；成功后列表直接可用。
     * 索引读不出来时降级为空列表，根目录从系统里的持久化授权重建——授权还在，点「重新扫描」即可恢复。
     */
    private fun restoreLibrary() {
        viewModelScope.launch {
            val restored = try {
                withContext(Dispatchers.IO) {
                    val savedOverlay = LocalMusicStore.loadOverlay(application)
                    when (val index = LocalMusicStore.loadIndex(application)) {
                        is LocalMusicStore.IndexLoad.Loaded -> RestoredLibrary(
                            overlay = savedOverlay,
                            roots = index.roots,
                            // 展示值走「覆盖层优先、缺失取标签」的同一份合并结果（ADR 0020）
                            songs = index.songs.map { it.withOverride(savedOverlay[it.fingerprint]) },
                        )

                        LocalMusicStore.IndexLoad.Unreadable -> RestoredLibrary(
                            overlay = savedOverlay,
                            roots = persistedRoots(),
                            indexUnreadable = true,
                        )

                        LocalMusicStore.IndexLoad.Absent -> RestoredLibrary(
                            overlay = savedOverlay,
                            roots = persistedRoots(),
                            // 有授权但没索引（首次升级上来、索引被清掉）：等同首次进入，自动扫一次
                            needsScan = true,
                        )
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // 加载本身出错也不能把页面卡在加载态：按空列表走空态，用户可手动重扫
                Log.w(TAG, "本地索引加载失败，按空列表处理", e)
                RestoredLibrary(overlay = overlay)
            }
            overlay = restored.overlay
            _roots.value = restored.roots
            _songs.value = restored.songs
            _indexUnreadable.value = restored.indexUnreadable
            _loading.value = false
            // 进入本地音乐页 / 冷启动后核对持久化授权（异步）：权限被系统回收时在列表上标出来
            checkRootPermissions()
            // 根目录已有但索引还没有：直接开扫，不必让用户先看到「目录里没有音乐」
            if (restored.needsScan) startScan()
        }
    }

    /**
     * 核对每个根目录的持久化读授权（spec「权限与失效」）：
     * 全部失效 → 页面给重新选择的空态；部分失效 → 顶部提示 + 管理入口，对应歌曲灰显。
     * 检查在 IO 线程异步进行，不阻塞页面首帧；不写索引，重新授权成功后歌曲自然恢复。
     */
    fun checkRootPermissions() {
        val roots = _roots.value
        if (roots.isEmpty()) return
        viewModelScope.launch {
            val granted = withContext(Dispatchers.IO) {
                runCatching {
                    getApplication<Application>().contentResolver.persistedUriPermissions
                        .filter { it.isReadPermission }
                        .mapTo(HashSet()) { it.uri.toString().trimEnd('/') }
                }.getOrNull()
            } ?: return@launch
            _revokedTrees.value = roots
                .map { it.treeUri.toString() }
                .filterNot { it.trimEnd('/') in granted }
                .toSet()
        }
    }

    /**
     * 收录用户选中的目录树并立刻扫描。同一棵树重复选择只记一次；
     * 与已有根目录重叠（包含 / 被包含 / 同一棵树）时拒绝并说明——否则同一批文件会被两棵子树重复扫进来。
     */
    fun addRoot(treeUri: Uri) {
        if (_roots.value.any { it.treeUri == treeUri }) {
            // 同一棵树重新选择：也可能是在授权被回收后重新授权（spec：重新授权即恢复可播）。
            // 拾取器给的只是临时授权，这里重新持久化并核对一次，然后重扫把标签与可用状态刷新回来
            viewModelScope.launch {
                persistPermission(treeUri)
                checkRootPermissions()
                startScan()
            }
            return
        }
        viewModelScope.launch {
            // 树名查询是跨进程 IO，不放在主线程
            val name = withContext(Dispatchers.IO) {
                queryTreeName(treeUri) ?: treeUri.lastPathSegment ?: "音乐文件夹"
            }
            val overlapping = _roots.value.findOverlap(treeUri)
            if (overlapping != null) {
                // 拒绝说明：用户刚选完目录，用轻提示告诉他为什么没加进来
                _notice.value = "「$name」与已添加的「${overlapping.name}」重叠，请选择不重叠的文件夹"
                return@launch
            }
            persistPermission(treeUri)
            val root = LocalMusicRoot(treeUri, name)
            if (_roots.value.none { it.treeUri == root.treeUri }) {
                _roots.value = _roots.value + root
            }
            checkRootPermissions()
            // 根目录立即落盘：这次扫描被取消，下次启动也不至于丢掉这个授权
            LocalMusicStore.saveIndex(application, LocalMusicStore.Snapshot(_roots.value, _songs.value))
            startScan()
        }
    }

    /** 选择目录即是唯一一次授权：从此本机可长期读取该树，无任何存储权限弹窗 */
    private suspend fun persistPermission(treeUri: Uri) = withContext(Dispatchers.IO) {
        runCatching {
            getApplication<Application>().contentResolver.takePersistableUriPermission(
                treeUri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION,
            )
        }
        Unit
    }

    /**
     * 移除一个已授权的根目录：直接按树把它的歌曲从内存索引里剔除（spec：不触发全量重扫）并落盘。
     * 覆盖层按身份保留，重新添加同一目录后用户改过的信息仍在。
     *
     * 不动系统里的持久化授权：正在播放的歌曲也是该树下的 content URI，撤回授权会让它在 seek 时
     * 直接读不到文件，与「正在播放的歌曲不受影响」相违；索引不再列出这棵树，不扫也就不会再读它。
     */
    fun removeRoot(treeUri: Uri) {
        val root = _roots.value.firstOrNull { it.treeUri == treeUri } ?: return
        _roots.value = _roots.value - root
        _revokedTrees.value = _revokedTrees.value - root.treeUri.toString()
        _songs.value = _songs.value.filterNot { it.treeUri == treeUri.toString() }
        // 落盘不交给重扫：根目录被清空、用户取消、扫描失败都走不到写盘那一步，
        // 磁盘会停在含该目录的旧快照，重启就把它连歌一起捡回来。
        // 根目录与歌曲在同一次主线程执行里取出，建出的快照必然是同一时刻的状态
        val snapshot = LocalMusicStore.Snapshot(_roots.value, _songs.value)
        viewModelScope.launch { LocalMusicStore.saveIndex(application, snapshot) }
        // 扫描在跑时它的遍历范围里还有这个目录：取消并按新目录重跑
        if (scanJob != null) startScan()
    }

    /**
     * 扫描全部已授权目录。同一时刻只允许一个扫描任务：已有任务在跑时，新请求取消旧任务，
     * 旧任务收尾后按最新的目录与索引重跑一次，不会有两个任务同时遍历。
     */
    fun startScan() {
        if (scanJob != null) {
            rescanRequested = true
            scanJob?.cancel()
            return
        }
        val roots = _roots.value
        if (roots.isEmpty()) return
        // 开始新一轮扫描：上次还没被页面消费的结果提示作废，避免与本次结果混在一起
        _notice.value = null
        _scan.value = LocalScanState(running = true)
        scanJob = viewModelScope.launch {
            try {
                // 上一份索引交给扫描器做增量比对：未变文件直接复用指纹与标签，不重新读盘（spec）
                val previous = _songs.value
                // 进度节流：扫描每秒可能过上百个文件，合并成每 100ms 一次，
                // 免得主线程被进度重组刷满
                var lastProgressAt = 0L
                val result = scanner.scan(roots, previous) { progress ->
                    val now = SystemClock.elapsedRealtime()
                    if (now - lastProgressAt >= PROGRESS_INTERVAL_MS) {
                        lastProgressAt = now
                        _scan.value = LocalScanState(
                            running = true,
                            scannedFiles = progress.scannedFiles,
                            foundSongs = progress.foundSongs,
                            currentName = progress.currentName,
                        )
                    }
                }
                // 扫描期间根目录可能被移除（removeRoot）：只提交仍属于当前根目录的歌曲。
                // 遍历范围是扫描开始时捕获的 roots，内存里的根目录表才是当下的事实
                val liveTreeUris = _roots.value.mapTo(HashSet()) { it.treeUri.toString() }
                val scanned = result.songs.filter { it.treeUri in liveTreeUris }
                // 展示值统一走「覆盖层优先、缺失取标签」的合并结果（ADR 0020）；
                // 顺序按合并后的标题（改过标题的歌在重扫后也不会跳回旧位置），排序与专辑分组一样离开主线程。
                // 覆盖层在主线程先取一份快照：扫描期间它可能正被元数据编辑改写
                val overlaySnapshot = overlay
                val merged = withContext(Dispatchers.Default) {
                    scanned.map { it.withOverride(overlaySnapshot[it.fingerprint]) }.sortedByDisplayTitle()
                }
                // 原子提交：整份索引构建完成才替换，此前列表一直是上一次的完整结果
                _songs.value = merged
                _indexUnreadable.value = false
                // 索引落盘：下次冷启动直接出列表，不必再扫一遍。
                // 写盘已不可取消：列表已经换了，此时点「取消」不该把成功的扫描标成取消、让磁盘停在旧索引。
                // 快照取写盘那一刻的内存状态，而不是扫描开始时捕获的目录 + 这份结果：
                // 两者必须同源，否则会把已移除目录的歌跟一份不含该目录的根目录表一起写进索引
                withContext(NonCancellable) {
                    val snapshot = LocalMusicStore.Snapshot(_roots.value, _songs.value)
                    LocalMusicStore.saveIndex(application, snapshot)
                    // 提交后封面缓存作废：文件夹里换了封面文件，只能靠一次重扫被感知（spec）
                    coverCache.clear()
                }
                _coverGeneration.value += 1
                // 扫描完成轻提示本次的增量结果（spec「扫描与索引」）
                _notice.value = "新增 ${result.added} 首、移除 ${result.removed} 首"
                Log.d(TAG, "扫描完成：${merged.size} 首")
            } catch (e: CancellationException) {
                Log.d(TAG, "扫描已取消，保留上一次结果")
                // 取消是为了换新请求重跑时不把「已取消」闪给用户；用户主动取消才展示该状态
                if (!rescanRequested) _scan.value = _scan.value.copy(cancelled = true)
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "扫描失败，保留上一次结果", e)
                // 索引不动：列表仍是上一次的完整结果；空列表时页面另有失败空态，不重复轻提示
                _scan.value = _scan.value.copy(failed = true)
                if (_songs.value.isNotEmpty()) _notice.value = "扫描失败，请重试"
            } finally {
                _scan.value = _scan.value.copy(running = false)
                scanJob = null
                if (rescanRequested) {
                    rescanRequested = false
                    startScan()
                }
            }
        }
    }

    /** 页面 Toast 后清空一次性提示 */
    fun consumeNotice() {
        _notice.value = null
    }

    /**
     * 文件在播放时已被删除或不可读（spec「权限与失效」）：把这条索引标为不可用并落盘，
     * 列表灰显、不可点；不自动重扫（下次重扫时该文件不在目录树里，自然从索引移除）。
     * 其它歌曲照常可播——播放器下一次装载会重新构建队列，不受这条错误状态影响。
     */
    fun markUnavailable(songId: String) {
        val current = _songs.value
        val index = current.indexOfFirst { it.id == songId }
        if (index < 0 || !current[index].available) return
        val updated = current.toMutableList().apply {
            this[index] = this[index].copy(available = false)
        }
        _songs.value = updated
        val snapshot = LocalMusicStore.Snapshot(_roots.value, updated)
        viewModelScope.launch { LocalMusicStore.saveIndex(application, snapshot) }
    }

    /**
     * 保存元数据编辑（spec「元数据编辑」，ADR 0020）：只写覆盖层，音频文件本身一个字节都不改，
     * 只作用于 App 内的展示。带校验——音轨号只接受正整数、年份只接受四位数字，非法即拒收。
     */
    fun saveMetadata(song: LocalSong, draft: LocalMetadataDraft): LocalMetadataSaveResult {
        val trackNo = song.trackNoInput(draft.trackNo)
        val year = song.yearInput(draft.year)
        if (trackNo is LocalNumberInput.Invalid || year is LocalNumberInput.Invalid) {
            return LocalMetadataSaveResult.Invalid(
                trackNoError = (trackNo as? LocalNumberInput.Invalid)?.message,
                yearError = (year as? LocalNumberInput.Invalid)?.message,
            )
        }
        val override = song
            .overrideFrom(
                draft = draft,
                // 已过了非法检查，剩下的只可能是 Ok（留空为 null）
                trackNo = trackNo.valueOrNull,
                year = year.valueOrNull,
            )
            // 六个字段都回到标签值：等同「恢复默认」，不落一条空壳覆盖项
            .takeUnless { it.isEmpty }
        return LocalMetadataSaveResult.Saved(applyOverride(song, override))
    }

    /**
     * 「恢复默认」：删掉这首歌的整条覆盖，全部回到文件标签值（ADR 0020：不需要备份原标签）。
     * 返回合并后的歌曲，供调用方同步播放器上的展示值。
     */
    fun restoreDefaultMetadata(song: LocalSong): LocalSong = applyOverride(song, null)

    /**
     * 覆盖层落位并刷新展示：只替换这一条记录——索引不动、不重扫、不碰文件，
     * 所以重扫、移动文件、改名之后改动照样生效（覆盖层以内容身份为键）。
     *
     * 列表、专辑分组与封面都从 [_songs] 派生，换掉这一条即全部刷新；新旧专辑键的封面分别作废重取
     * （改了专辑名或专辑艺术家，分组成员就变了，封面来源可能在文件夹封面与内嵌图之间切换）。
     */
    private fun applyOverride(song: LocalSong, override: LocalSongOverride?): LocalSong {
        overlay = if (override == null) {
            overlay - song.fingerprint
        } else {
            overlay + (song.fingerprint to override)
        }
        val updated = song.withOverride(override)
        val touchedAlbumKeys = listOf(song.albumKey(), updated.albumKey()).distinct()
        viewModelScope.launch {
            // 整条链排在一把锁里：换列表要基于上一次编辑提交后的结果，落盘也只按调用顺序走
            editLock.withLock {
                // 顺序也跟着标题走（spec：歌曲视图按标题升序），排序与专辑分组一样不占主线程。
                // 可用状态取索引里的本值：传进来的 song 可能是因授权失效而临时标记的展示副本
                _songs.value = withContext(Dispatchers.Default) {
                    _songs.value
                        .map { existing ->
                            if (existing.fingerprint == updated.fingerprint) {
                                updated.copy(available = existing.available)
                            } else {
                                existing
                            }
                        }
                        .sortedByDisplayTitle()
                }
                // 覆盖层是独立文件：写完才作废封面并抬代次，卡片重新取图时看到的已经是新分组成员
                LocalMusicStore.saveOverlay(application, overlay)
                coverCache.invalidate(touchedAlbumKeys)
                _coverGeneration.value += 1
            }
        }
        return updated
    }

    /**
     * 专辑封面文件：已缓存直接给文件，否则按「文件夹封面 → 内嵌图」解析后落进封面缓存；
     * 都没有时返回 null，卡片与专辑页显示占位图。完全离线，不发起任何网络请求。
     *
     * 传专辑对象而不是专辑键：卡片拿到的就是当下这批成员，不会赶上「刚编辑完封面已作废、
     * 专辑分组还没重算」的错位（按键反查 [albums] 就可能找不到这张专辑）。
     */
    suspend fun albumCover(album: LocalAlbum): File? = coverCache.cover(album)

    /** 取消当前扫描：本次结果整体丢弃，列表回到上一次扫描的完整状态 */
    fun cancelScan() {
        rescanRequested = false
        scanJob?.cancel()
    }

    /** SAF 只给树 URI，目录名需要查询树根 document 的 DISPLAY_NAME */
    private fun queryTreeName(treeUri: Uri): String? = runCatching {
        val rootUri = DocumentsContract.buildDocumentUriUsingTree(
            treeUri,
            DocumentsContract.getTreeDocumentId(treeUri),
        )
        getApplication<Application>().contentResolver.query(
            rootUri,
            arrayOf(DocumentsContract.Document.COLUMN_DISPLAY_NAME),
            null,
            null,
            null,
        )?.use { cursor -> if (cursor.moveToFirst()) cursor.getString(0) else null }
    }.getOrNull()

    /**
     * 索引缺失或损坏时的根目录兜底：选目录时已把授权持久化进系统，按授权重建列表。
     * 目录名查不到就用 URI 尾段——只有索引丢了的路径才走到这里，展示名可以从宽。
     */
    private fun persistedRoots(): List<LocalMusicRoot> =
        getApplication<Application>().contentResolver.persistedUriPermissions
            .filter { it.isReadPermission }
            .map { permission ->
                val treeUri = permission.uri
                LocalMusicRoot(treeUri, queryTreeName(treeUri) ?: treeUri.lastPathSegment ?: "音乐文件夹")
            }

    /** 启动恢复的结果：索引与覆盖层一起从 IO 线程带回，主线程只做状态落位 */
    private data class RestoredLibrary(
        val overlay: Map<String, LocalSongOverride>,
        val roots: List<LocalMusicRoot> = emptyList(),
        val songs: List<LocalSong> = emptyList(),
        val indexUnreadable: Boolean = false,
        val needsScan: Boolean = false,
    )

    private companion object {
        const val TAG = "LocalMusicViewModel"

        /** 进度上报最短间隔：进度只用于“App 正在做事”的感知，无需逐文件刷新 */
        const val PROGRESS_INTERVAL_MS = 100L
    }
}

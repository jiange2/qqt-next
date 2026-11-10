package com.qqt.music.ui.screens.localmusic

import android.app.Application
import android.content.Intent
import android.net.Uri
import android.os.SystemClock
import android.provider.DocumentsContract
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.qqt.music.data.local.LocalMusicRoot
import com.qqt.music.data.local.LocalMusicScanner
import com.qqt.music.data.local.LocalMusicStore
import com.qqt.music.data.local.LocalSong
import com.qqt.music.data.local.LocalSongOverride
import com.qqt.music.data.local.withOverride
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
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
 * 本地音乐页状态：授权目录、扫描状态与扫描结果。
 *
 * 索引与覆盖层各一个 App 私有 JSON 文件（[LocalMusicStore]）：启动时在 IO 线程读回并合并，
 * 冷启动直接出列表、不必先扫一遍；扫描完成后再落盘。页面与顶栏菜单共用同一个实例（提升在导航层），
 * 离开页面扫描继续、回到页面不重复发起；扫描结果整体替换：取消或失败都保留上一次完整结果。
 * 重扫是增量的：把当前索引交给扫描器，未变文件跳过指纹与标签读取，
 * 完成时按内容身份（指纹）提示新增 / 移除条数。
 */
class LocalMusicViewModel(application: Application) : AndroidViewModel(application) {

    private val scanner = LocalMusicScanner(application)

    private val _roots = MutableStateFlow<List<LocalMusicRoot>>(emptyList())
    val roots: StateFlow<List<LocalMusicRoot>> = _roots.asStateFlow()

    private val _songs = MutableStateFlow<List<LocalSong>>(emptyList())
    val songs: StateFlow<List<LocalSong>> = _songs.asStateFlow()

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
            // 根目录已有但索引还没有：直接开扫，不必让用户先看到「目录里没有音乐」
            if (restored.needsScan) startScan()
        }
    }

    /** 收录用户选中的目录树并立刻扫描；同一棵树重复选择只记一次（重叠根的拒绝属票据 07） */
    fun addRoot(treeUri: Uri) {
        if (_roots.value.any { it.treeUri == treeUri }) {
            startScan()
            return
        }
        viewModelScope.launch {
            // 授权与树名查询都是跨进程 IO，不放在主线程
            val root = withContext(Dispatchers.IO) {
                // 选择目录即是唯一一次授权：从此本机可长期读取该树，无任何存储权限弹窗
                runCatching {
                    getApplication<Application>().contentResolver.takePersistableUriPermission(
                        treeUri,
                        Intent.FLAG_GRANT_READ_URI_PERMISSION,
                    )
                }
                LocalMusicRoot(treeUri, queryTreeName(treeUri) ?: treeUri.lastPathSegment ?: "音乐文件夹")
            }
            if (_roots.value.none { it.treeUri == root.treeUri }) {
                _roots.value = _roots.value + root
            }
            // 根目录立即落盘：这次扫描被取消，下次启动也不至于丢掉这个授权
            LocalMusicStore.saveIndex(application, LocalMusicStore.Snapshot(_roots.value, _songs.value))
            startScan()
        }
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
                // 展示值统一走「覆盖层优先、缺失取标签」的合并结果（ADR 0020）
                val merged = result.songs.map { it.withOverride(overlay[it.fingerprint]) }
                // 原子提交：整份索引构建完成才替换，此前列表一直是上一次的完整结果
                _songs.value = merged
                _indexUnreadable.value = false
                // 索引落盘：下次冷启动直接出列表，不必再扫一遍。
                // 写盘已不可取消：列表已经换了，此时点「取消」不该把成功的扫描标成取消、让磁盘停在旧索引
                withContext(NonCancellable) {
                    LocalMusicStore.saveIndex(application, LocalMusicStore.Snapshot(_roots.value, merged))
                }
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

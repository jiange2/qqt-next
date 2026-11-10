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
import com.qqt.music.data.local.LocalSong
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** 扫描状态：目录树给不出可靠总量，进度只报已处理文件数与已收录歌曲数 */
data class LocalScanState(
    val running: Boolean = false,
    val scannedFiles: Int = 0,
    val foundSongs: Int = 0,
    val currentName: String = "",
    val cancelled: Boolean = false,
)

/**
 * 本地音乐页状态：授权目录、扫描状态与扫描结果（本进程内存，跨重启的索引持久化属票据 05）。
 *
 * 页面与顶栏菜单共用同一个实例（提升在导航层），离开页面扫描继续、回到页面不重复发起；
 * 扫描结果整体替换：取消或失败都保留上一次完整结果，不留下半份索引。
 */
class LocalMusicViewModel(application: Application) : AndroidViewModel(application) {

    private val scanner = LocalMusicScanner(application)

    private val _roots = MutableStateFlow<List<LocalMusicRoot>>(emptyList())
    val roots: StateFlow<List<LocalMusicRoot>> = _roots.asStateFlow()

    private val _songs = MutableStateFlow<List<LocalSong>>(emptyList())
    val songs: StateFlow<List<LocalSong>> = _songs.asStateFlow()

    private val _scan = MutableStateFlow(LocalScanState())
    val scan: StateFlow<LocalScanState> = _scan.asStateFlow()

    /** 同一时刻只允许一个扫描任务：重复触发（重新选目录、菜单重扫、重新进页面）都不叠加 */
    private var scanJob: Job? = null

    /** 扫描中又被要求重扫（例如扫描中又加了目录）：当前任务结束后补跑一次，避免两个任务同时遍历同一批文件 */
    private var rescanRequested = false

    /** 收录用户选中的目录树并立刻扫描；同一棵树重复选择只记一次（重叠根的拒绝属票据 07） */
    fun addRoot(treeUri: Uri) {
        if (_roots.value.none { it.treeUri == treeUri }) {
            // 选择目录即是唯一一次授权：从此本机可长期读取该树，无任何存储权限弹窗
            runCatching {
                getApplication<Application>().contentResolver.takePersistableUriPermission(
                    treeUri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION,
                )
            }
            val name = queryTreeName(treeUri) ?: treeUri.lastPathSegment ?: "音乐文件夹"
            _roots.value = _roots.value + LocalMusicRoot(treeUri, name)
        }
        startScan()
    }

    /** 扫描全部已授权目录；已有扫描在跑时只记一个“结束后补跑”的意图，不并发起第二个任务 */
    fun startScan() {
        if (scanJob?.isActive == true) {
            rescanRequested = true
            return
        }
        val roots = _roots.value
        if (roots.isEmpty()) return
        _scan.value = LocalScanState(running = true)
        scanJob = viewModelScope.launch {
            try {
                // 进度节流：扫描每秒可能过上百个文件，合并成每 100ms 一次，
                // 免得主线程被进度重组刷满
                var lastProgressAt = 0L
                val scanned = scanner.scan(roots) { progress ->
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
                // 原子提交：整份索引构建完成才替换，此前列表一直是上一次的完整结果
                _songs.value = scanned
                Log.d(TAG, "扫描完成：${scanned.size} 首")
            } catch (e: CancellationException) {
                Log.d(TAG, "扫描已取消，保留上一次结果")
                _scan.value = _scan.value.copy(cancelled = true)
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "扫描失败，保留上一次结果", e)
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

    private companion object {
        const val TAG = "LocalMusicViewModel"

        /** 进度上报最短间隔：进度只用于“App 正在做事”的感知，无需逐文件刷新 */
        const val PROGRESS_INTERVAL_MS = 100L
    }
}

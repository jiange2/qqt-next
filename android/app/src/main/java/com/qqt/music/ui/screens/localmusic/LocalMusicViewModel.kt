package com.qqt.music.ui.screens.localmusic

import android.app.Application
import android.os.Build
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.qqt.music.data.local.LocalAlbum
import com.qqt.music.data.local.LocalDeleteAction
import com.qqt.music.data.local.LocalDeleteResult
import com.qqt.music.data.local.LocalFolder
import com.qqt.music.data.local.LocalLibraryResult
import com.qqt.music.data.local.LocalMetadataDraft
import com.qqt.music.data.local.LocalMusicDeleter
import com.qqt.music.data.local.LocalMusicLibrarySource
import com.qqt.music.data.local.LocalMusicStore
import com.qqt.music.data.local.LocalNumberInput
import com.qqt.music.data.local.LocalSong
import com.qqt.music.data.local.LocalSongOverride
import com.qqt.music.data.local.MediaStoreDeleter
import com.qqt.music.data.local.MediaStoreLibrarySource
import com.qqt.music.data.local.albumKey
import com.qqt.music.data.local.distinctFolders
import com.qqt.music.data.local.excludingFolders
import com.qqt.music.data.local.groupIntoAlbums
import com.qqt.music.data.local.needsAppDeleteConfirm
import com.qqt.music.data.local.overrideFrom
import com.qqt.music.data.local.remainingVisibleInAlbum
import com.qqt.music.data.local.songsAfterDelete
import com.qqt.music.data.local.sortedByDisplayTitle
import com.qqt.music.data.local.trackNoInput
import com.qqt.music.data.local.valueOrNull
import com.qqt.music.data.local.visibleLibraryDiff
import com.qqt.music.data.local.withOverride
import com.qqt.music.data.local.yearInput
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

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
 * 删除成功的一次性事件（导航层消费，消费后清空）：被删歌曲所属专辑的分组键与删后剩余可见行数——
 * 剩余 0 即专辑被删空，专辑页据此自动返回本地音乐页。剩余数在删除提交时同步算好：
 * [LocalMusicViewModel.albums] 派生经默认调度器异步重算，事件消费时读它会拿到删除前的旧值。
 */
data class LocalDeleteSuccess(val albumKey: String, val remainingSongs: Int)

/**
 * 本地音乐页状态：音频读取权限、库列表与一次性提示。
 *
 * 来源是系统媒体库（[LocalMusicLibrarySource]）：授权后整库一次查询（毫秒级），没有扫描进度与取消。
 * 库快照与覆盖层各一个 App 私有 JSON 文件（[LocalMusicStore]）：启动在 IO 线程读回并合并，
 * 冷启动直接出列表；旧版本（按内容指纹记身份）的数据读到即整份丢弃，不转换、不报错。
 * 进入页面（[onPageEntered]）与授权成功（[onPermissionResult]）后静默刷新一次：整库重查 → 按身份
 * diff → 合并覆盖层 → 原子提交；顶栏「重新扫描」（[rescan]）走同一管线，完成后提示「新增 x、移除 y」，
 * 静默刷新不提示。刷新期间旧列表照常可用、播放不受影响（身份不变，队列里的条目不重建）。
 * 文件夹黑名单（票据 03，[excludedFolders] / [setFolderExcluded]）：被排除的文件夹及其子文件夹
 * 不进列表、不进专辑分组、不进播放队列候选；切换只重过滤、不重扫，状态随快照落盘。
 * 权限被系统关闭后进页面：快照保留并灰显，重新授权后自动刷新恢复。
 * 专辑视图与专辑页共用同一份列表派生出的 [albums]；封面由图片加载器按成员行的媒体库专辑 ID
 * 直载系统预抽取的专辑图（[com.qqt.music.data.local.LocalAlbum.albumArtUri]），ViewModel 不经手封面。
 * 元数据编辑（[saveMetadata] / [restoreDefaultMetadata]）只改写覆盖层与展示值（按歌曲身份记键），
 * 不重查、不碰文件；重扫后改动仍然生效。
 * 左滑删除（[requestDelete]）经分版本接缝 [LocalMusicDeleter]：成功即时移除行并落盘快照，
 * 失败提示「删除失败」、仅在确认文件已不在时标记不可用，≤28 先弹 App 确认框（29/30+ 系统框即唯一确认）。
 */
class LocalMusicViewModel(private val application: Application) : AndroidViewModel(application) {

    private var source: LocalMusicLibrarySource = MediaStoreLibrarySource(application)

    private var deleter: LocalMusicDeleter = MediaStoreDeleter(application)

    /** 测试注入点：伪造行列表即可驱动整条纯 Kotlin 管线（spec「Testing Decisions」）；删除接缝同口径注入 */
    internal constructor(
        application: Application,
        source: LocalMusicLibrarySource,
        deleter: LocalMusicDeleter,
    ) : this(application) {
        this.source = source
        this.deleter = deleter
    }

    private val _songs = MutableStateFlow<List<LocalSong>>(emptyList())

    /**
     * 音频读取权限的当前状态：进入页面与授权回调时核对。未授予时列表整体灰显
     * （快照还在磁盘上，歌没丢，只是权限没了——spec 用户故事 6）。
     */
    private val _permissionGranted = MutableStateFlow(false)
    val permissionGranted: StateFlow<Boolean> = _permissionGranted.asStateFlow()

    /**
     * 用户是否拒绝过系统授权弹窗（本进程内记一次）：拒绝过后「授权音乐访问」的单一入口升级为
     * 「重试 + 去系统设置」双入口——重复拒绝（或勾了不再询问）后系统可能不再弹窗，
     * 此时只有系统设置能重新开启（票据02）。
     */
    private val _permissionDeniedOnce = MutableStateFlow(false)
    val permissionDeniedOnce: StateFlow<Boolean> = _permissionDeniedOnce.asStateFlow()

    /**
     * 排除清单（spec「文件夹黑名单」）：被排除的文件夹路径集合。[_songs] 始终保存整库
     * （含被排除的行）——这样取消排除立即恢复，不需要重查媒体库；过滤只发生在展示派生上。
     */
    private val _excludedFolders = MutableStateFlow<Set<String>>(emptySet())
    val excludedFolders: StateFlow<Set<String>> = _excludedFolders.asStateFlow()

    /**
     * 展示用歌曲列表：先按排除清单滤掉被排除的文件夹及其子文件夹（票据 03，纯内存过滤，
     * 切换开关即时生效），再在权限未授予时把剩余行全部灰显（不可点），但不改动快照里的
     * 可用状态——重新授权并刷新后自然恢复。快照本身按「覆盖层优先、缺失取媒体库标签」合并好（ADR 0020）。
     */
    val songs: StateFlow<List<LocalSong>> =
        combine(_songs, _permissionGranted, _excludedFolders) { songs, granted, excluded ->
            val visible = songs.excludingFolders(excluded)
            if (granted) {
                visible
            } else {
                visible.map { if (it.available) it.copy(available = false) else it }
            }
        }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    /**
     * 排除面板的候选文件夹（spec：自动列出当前库中所有含音乐的文件夹并带开关，无需手输路径）：
     * 从整库行路径派生——含已被排除的文件夹，否则取消排除无从谈起。纯派生，不查媒体库，
     * 列表变了（刷新提交）清单自然跟着变。
     */
    val libraryFolders: StateFlow<List<LocalFolder>> = _songs
        .map { rows -> withContext(Dispatchers.Default) { rows.distinctFolders() } }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    /**
     * 专辑视图（spec「专辑与封面」）：完全从列表派生，按「专辑名 + 专辑艺术家」分组并排好序。
     * 不单独存状态——歌曲变了（刷新提交、编辑覆盖层）专辑分组自然跟着变。
     */
    val albums: StateFlow<List<LocalAlbum>> = songs
        // 分组与排序（中文 Collator）放在默认调度器上：歌多时也不占主线程
        .map { songs -> withContext(Dispatchers.Default) { songs.groupIntoAlbums() } }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    /** 启动时快照还没读回来：首帧不把「还没读出来」当成「没有音乐」 */
    private val _loading = MutableStateFlow(true)
    val loading: StateFlow<Boolean> = _loading.asStateFlow()

    /**
     * 是否有库刷新在跑。查询毫秒级，页面只在「空快照 + 已授权 + 刷新在途」用它：首次授权后
     * 第一次刷新提交前不闪「没有音乐」的空态——空结果要等刷新落定才能下结论。
     */
    private val _refreshing = MutableStateFlow(false)
    val refreshing: StateFlow<Boolean> = _refreshing.asStateFlow()

    /**
     * 一次性轻提示（重扫完成的新增 / 移除数、刷新失败、删除失败）：导航层 Toast 后清空。
     * 用 StateFlow 而不是 SharedFlow，提示在页面尚未重组完成时也不会丢。
     */
    private val _notice = MutableStateFlow<String?>(null)
    val notice: StateFlow<String?> = _notice.asStateFlow()

    /** ≤28 App 删除确认框的待确认歌曲：非空即弹框（导航层渲染，两页共用）；29/30+ 恒为 null */
    private val _pendingDeleteConfirm = MutableStateFlow<LocalSong?>(null)
    val pendingDeleteConfirm: StateFlow<LocalSong?> = _pendingDeleteConfirm.asStateFlow()

    /** 待导航层拉起的删除动作（系统框 / 写入权限框）：拉起后清空（与 [notice] 同范式） */
    private val _pendingDeleteAction = MutableStateFlow<LocalDeleteAction?>(null)
    val pendingDeleteAction: StateFlow<LocalDeleteAction?> = _pendingDeleteAction.asStateFlow()

    /** 删除成功的一次性事件：导航层据此把删空的专辑页自动返回（消费后清空） */
    private val _deleteSucceeded = MutableStateFlow<LocalDeleteSuccess?>(null)
    val deleteSucceeded: StateFlow<LocalDeleteSuccess?> = _deleteSucceeded.asStateFlow()

    /** 系统框 / 权限框拉起后、回调返回前保管的待删歌曲（接缝无状态，同一首歌喂回回调） */
    private var pendingDeleteSong: LocalSong? = null

    /** 覆盖层（ADR 0020）：歌曲身份 → 用户改过的字段；自己的文件，刷新只换快照不动它 */
    private var overlay: Map<String, LocalSongOverride> = emptyMap()

    /** 元数据编辑串行化：「换列表 → 写覆盖层」是一条链，两次编辑不交错 */
    private val editLock = Mutex()

    /**
     * 最近一次启动的刷新任务，供 [rescan] 做串行判断。静默刷新入口（进页面 / 授权回调 / 回前台）
     * 不查它、允许重叠：查询是毫秒级整库重读，提交是整份列表原子替换，各次结果同为各自查询时刻的
     * 最新事实、后提交者胜出且内容不旧，重叠无害，不值得为它排队。
     * 重扫则尽量排队（在途时记 [rescanRequested]，跑完当前这次后非静默补跑一次）：它要提示
     * 「新增 / 移除」数量，与在途刷新交错时 diff 基线算不清——查询毫秒级即回，重叠窗口本就极小。
     */
    private var refreshJob: Job? = null

    /** 刷新中又被要求重扫：跑完当前这次后按非静默重跑一次（提示数量） */
    private var rescanRequested = false

    init {
        restoreLibrary()
    }

    /**
     * 冷启动读回库快照与覆盖层并在 IO 线程合并，不做任何网络请求；成功后列表直接可用。
     * 读不出来（旧版本、损坏）时按空列表走空态，不报错——授权后刷新一次即可重建。
     */
    private fun restoreLibrary() {
        viewModelScope.launch {
            val restored = try {
                withContext(Dispatchers.IO) {
                    val savedOverlay = LocalMusicStore.loadOverlay(application)
                    val snapshot = when (val load = LocalMusicStore.loadLibrary(application)) {
                        is LocalMusicStore.LibraryLoad.Loaded -> load
                        LocalMusicStore.LibraryLoad.Absent -> null
                    }
                    RestoredLibrary(
                        overlay = savedOverlay,
                        songs = snapshot?.songs?.map { it.withOverride(savedOverlay[it.id]) } ?: emptyList(),
                        excludedFolders = snapshot?.excludedFolders ?: emptyList(),
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // 加载本身出错也不能把页面卡在加载态：按空列表走空态，用户可手动重扫
                Log.w(TAG, "库快照加载失败，按空列表处理", e)
                RestoredLibrary(overlay = overlay)
            }
            overlay = restored.overlay
            _excludedFolders.value = restored.excludedFolders.toSet()
            _songs.value = restored.songs
            _loading.value = false
        }
    }

    /**
     * 进入本地音乐页：核对音频权限；已授予则静默刷新一次（spec：进页面库被静默刷新，
     * 在外部增删文件后回到 App 就是新状态）。权限被系统关闭后进页面：快照保留并灰显，
     * 页面据 [permissionGranted] 出重新授权横幅，不空列表假装没歌（spec 用户故事 6）。
     */
    fun onPageEntered() {
        _permissionGranted.value = source.hasAudioPermission()
        if (_permissionGranted.value) refresh(notify = false)
    }

    /**
     * 回到前台（从系统设置页返回等时机）重新核对音频权限：状态有变化才动作——
     * 重新开启 → 更新状态并静默刷新（灰显的行恢复可播，spec 用户故事 6）；
     * 又被关闭 → 灰显与重新授权横幅立即出现，不用等再进页面。
     * 与 [onPageEntered] 不同：无变化不刷新，避免每次回前台都白查一遍库。
     */
    fun onAppResumed() {
        val granted = source.hasAudioPermission()
        if (granted == _permissionGranted.value) return
        _permissionGranted.value = granted
        if (granted) refresh(notify = false)
    }

    /**
     * 进入本地专辑页：只核对音频权限、不刷新（专辑页沿用共享快照，刷新仍由本地音乐页入口触发）。
     * 专辑页不走 [onPageEntered]，进程被杀后直接恢复到专辑页时 [permissionGranted] 还是初值 false——
     * 不核对会把正常已授权的库误判成权限被关：整库灰显、左滑删除也露不出（票据02 权限门槛）。
     */
    fun onAlbumPageEntered() {
        _permissionGranted.value = source.hasAudioPermission()
    }

    /**
     * 系统权限弹窗的回调（首次授权 / 拒绝 / 在系统设置里重新开启后都会走到）：
     * 授权成功即静默刷新（重新授权后自动刷新，灰显的行恢复可播）；
     * 拒绝则记住（[permissionDeniedOnce]），页面把入口升级为「重试 + 去系统设置」。
     */
    fun onPermissionResult(granted: Boolean) {
        if (!granted) _permissionDeniedOnce.value = true
        _permissionGranted.value = granted
        if (granted) refresh(notify = false)
    }

    /**
     * 顶栏「重新扫描」：与静默刷新同一条管线，完成后提示「新增 x、移除 y」。
     * 已有刷新在跑时不打断它，跑完自动按提示口径补一次（查询很快，不值得取消重跑）。
     */
    fun rescan() {
        if (refreshJob != null) {
            rescanRequested = true
            return
        }
        refresh(notify = true)
    }

    /** 导航层 Toast 后清空一次性提示 */
    fun consumeNotice() {
        _notice.value = null
    }

    /**
     * 切换一个文件夹的排除状态（spec：切换排除开关后列表立即生效、不需要重新扫描）：
     * 歌曲列表、专辑分组与播放队列候选都从 [songs] 派生，改 [_excludedFolders] 即时生效；
     * 随后把整库快照（含被排除的行，取消排除时无需重查）带最新排除清单原子落盘，重启后仍然生效。
     * 落盘异步进行、与刷新共用同一把写锁：并发刷新时各自按提交时刻的现值落盘，最后一次写入胜出。
     */
    fun setFolderExcluded(folderPath: String, excluded: Boolean) {
        val updated = if (excluded) _excludedFolders.value + folderPath else _excludedFolders.value - folderPath
        if (updated == _excludedFolders.value) return
        _excludedFolders.value = updated
        // 落盘的行列表此刻取好：协程排队期间即使又来一次刷新提交，这次写入的也是一致的完整状态
        val songsToSave = _songs.value
        viewModelScope.launch {
            LocalMusicStore.saveLibrary(application, LocalMusicStore.Snapshot(songsToSave, updated.toList()))
        }
    }

    /**
     * 整库静默刷新 / 重扫（spec「库快照与刷新」）：等快照读回 → 整库重查 → 按身份 diff →
     * 合并覆盖层 → 原子提交并落盘。全程无进度状态：查询即事实，期间旧列表照常可用。
     * 查询失败（任一卷）不算空库：快照原样保留、直接跳过这次刷新（spec「播放与失效」：
     * 不放空列表假装没歌），只有真正的查询结果才提交。
     */
    private fun refresh(notify: Boolean) {
        // 先同步置「刷新中」再起协程：重扫排队补跑（finally → refresh）时两值同帧切换，页面不闪空态
        _refreshing.value = true
        refreshJob = viewModelScope.launch {
            try {
                // 冷启动时快照可能还没读回：先等它落位，diff 与落盘都以它为基线
                _loading.first { !it }
                if (!source.hasAudioPermission()) return@launch
                val previous = _songs.value
                val rows = when (val result = withContext(Dispatchers.IO) { source.queryLibrary() }) {
                    is LocalLibraryResult.Failure -> {
                        // 查询失败 ≠ 空库：内存与磁盘快照都原样保留（spec「播放与失效」：不放空列表假装没歌），
                        // 静默刷新无声跳过；重扫与下方 catch 同口径轻提示失败
                        Log.w(TAG, "库查询失败，保留上一次快照", result.cause)
                        if (notify && _songs.value.isNotEmpty()) _notice.value = "刷新失败，请重试"
                        return@launch
                    }
                    is LocalLibraryResult.Success -> result.rows
                }
                // 覆盖层在主线程先取一份快照：查询期间它可能正被元数据编辑改写。
                // 顺序按合并后的标题（改过标题的歌在刷新后也不会跳回旧位置），
                // 排序与专辑分组一样离开主线程
                val overlaySnapshot = overlay
                val merged = withContext(Dispatchers.Default) {
                    rows.map { it.withOverride(overlaySnapshot[it.id]) }.sortedByDisplayTitle()
                }
                // 原子提交：整份列表构建完成才替换，此前列表一直是上一次的完整结果
                _songs.value = merged
                withContext(NonCancellable) {
                    // 排除清单以提交时刻的现值为准：刷新期间用户切换开关也不会把旧状态写回去
                    LocalMusicStore.saveLibrary(application, LocalMusicStore.Snapshot(merged, _excludedFolders.value.toList()))
                }
                if (notify) {
                    // 提示的是可见库的变化（spec 用户故事 17）：被排除文件夹里的增删列表看不见，不计入
                    val diff = visibleLibraryDiff(merged, previous, _excludedFolders.value)
                    _notice.value = "新增 ${diff.added}、移除 ${diff.removed}"
                }
                Log.d(TAG, "库刷新完成：${merged.size} 首")
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "库刷新失败，保留上一次列表", e)
                // 快照不动：列表仍是上一次的完整结果；空列表时页面另有空态，不重复轻提示
                if (notify && _songs.value.isNotEmpty()) _notice.value = "刷新失败，请重试"
            } finally {
                refreshJob = null
                _refreshing.value = false
                if (rescanRequested) {
                    rescanRequested = false
                    refresh(notify = true)
                }
            }
        }
    }

    /**
     * 文件在播放时已被删除或不可读（spec「播放与失效」）：把这条快照标为不可用并落盘，
     * 列表灰显、不可点；不自动刷新（下次刷新时媒体库里已无此行，自然消失）。
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
        viewModelScope.launch {
            LocalMusicStore.saveLibrary(application, LocalMusicStore.Snapshot(updated, _excludedFolders.value.toList()))
        }
    }

    /**
     * 左滑「删除」的唯一入口（本地音乐页与专辑页共用）：≤28 先弹 App 确认框
     * （[pendingDeleteConfirm]，导航层渲染），29/30+ 系统框即唯一确认、直接进删除接缝。
     * 版本门槛是纯函数 [needsAppDeleteConfirm]（JVM 可测）。
     */
    fun requestDelete(song: LocalSong) {
        if (needsAppDeleteConfirm(Build.VERSION.SDK_INT)) {
            _pendingDeleteConfirm.value = song
        } else {
            startDelete(song)
        }
    }

    /** App 确认框点「删除」：清空待确认并进删除接缝（仅 ≤28 链路会走到） */
    fun confirmDelete() {
        val song = _pendingDeleteConfirm.value ?: return
        _pendingDeleteConfirm.value = null
        startDelete(song)
    }

    /** App 确认框取消 / 关闭：不删、行保持原样 */
    fun cancelDeleteConfirm() {
        _pendingDeleteConfirm.value = null
    }

    /**
     * 系统删除 / 授权框回调（导航层注册的 launcher）：把原始结果码喂回接缝。
     * [launchFailed] = IntentSender 发送自身失败（契约回 RESULT_CANCELED + EXTRA_SEND_INTENT_EXCEPTION）：
     * 没有真实框结果，不喂接缝、直接按失败处理——文件还在，只提示不灰显。
     */
    fun onSystemDeleteDialogResult(resultCode: Int, launchFailed: Boolean = false) {
        val song = pendingDeleteSong
        if (song == null) {
            // 系统框 / 权限框开着时进程被杀 → ViewModel 重建、待删歌曲丢失：
            // 给「删除失败」提示而非静默（两个回调同口径）
            _notice.value = "删除失败"
            return
        }
        pendingDeleteSong = null
        if (launchFailed) {
            applyDeleteResult(song, LocalDeleteResult.Failure())
            return
        }
        viewModelScope.launch { driveDelete(song, deleter.onSystemDialogResult(song, resultCode)) }
    }

    /** 写入权限申请回调（仅 ≤28 链路）：授予即删，拒绝失败不删 */
    fun onWritePermissionResult(granted: Boolean) {
        val song = pendingDeleteSong
        if (song == null) {
            // 同上：进程死亡丢失待删歌曲也要给提示
            _notice.value = "删除失败"
            return
        }
        pendingDeleteSong = null
        viewModelScope.launch { driveDelete(song, deleter.onWritePermissionResult(song, granted)) }
    }

    /** 导航层拉起系统框 / 权限框后清空待执行动作 */
    fun consumePendingDeleteAction() {
        _pendingDeleteAction.value = null
    }

    /** 导航层消费删除成功事件后清空 */
    fun consumeDeleteSucceeded() {
        _deleteSucceeded.value = null
    }

    private fun startDelete(song: LocalSong) {
        viewModelScope.launch { driveDelete(song, deleter.startDelete(song)) }
    }

    /** 接缝「动作 → 回调」交接：终态就地处理；系统框 / 权限框动作记下待删歌曲后交导航层拉起 */
    private fun driveDelete(song: LocalSong, action: LocalDeleteAction) {
        when (action) {
            is LocalDeleteAction.Finished -> applyDeleteResult(song, action.result)
            is LocalDeleteAction.LaunchSystemDialog, is LocalDeleteAction.RequestWritePermission -> {
                pendingDeleteSong = song
                _pendingDeleteAction.value = action
            }
        }
    }

    /**
     * 删除结果的列表联动（spec「列表联动与快照」）：成功即从整库移除该行并落盘快照
     * （照 [markUnavailable] 范式，不新增锁），专辑分组与计数是纯派生、自动更新；
     * 失败提示「删除失败」，仅在确认媒体库行已不在时把该行标记不可用；取消不动。
     * 不清理派生数据（覆盖层、排除清单、播放队列快照——spec 明确的已知取舍）。
     */
    private fun applyDeleteResult(song: LocalSong, result: LocalDeleteResult) {
        when (result) {
            LocalDeleteResult.Success -> {
                val current = _songs.value
                val target = current.firstOrNull { it.id == song.id } ?: song
                val updated = songsAfterDelete(current, song.id, result)
                _songs.value = updated
                viewModelScope.launch {
                    LocalMusicStore.saveLibrary(application, LocalMusicStore.Snapshot(updated, _excludedFolders.value.toList()))
                }
                // 专辑剩余可见行数此刻同步算好放进事件：albums 派生是异步的，消费时读它会拿到旧值
                val albumKey = target.albumKey()
                val remaining = remainingVisibleInAlbum(updated, albumKey, _excludedFolders.value)
                _deleteSucceeded.value = LocalDeleteSuccess(albumKey, remaining)
            }
            is LocalDeleteResult.Failure -> {
                _notice.value = "删除失败"
                // 只有确认媒体库行已不在（delete 返回 0 行）才灰显：灰显会落盘并阻断播放，
                // 拒绝写入权限 / provider 异常 / 异常框结果码都不证明文件不可播，行保持可播
                if (result.fileUnavailable) markUnavailable(song.id)
            }
            LocalDeleteResult.Cancelled -> Unit
        }
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
     * 「恢复默认」：删掉这首歌的整条覆盖，全部回到媒体库标签值（ADR 0020：不需要备份原标签）。
     * 返回合并后的歌曲，供调用方同步播放器上的展示值。
     */
    fun restoreDefaultMetadata(song: LocalSong): LocalSong = applyOverride(song, null)

    /**
     * 覆盖层落位并刷新展示：只替换这一条记录——快照不动、不重查、不碰文件，
     * 所以重扫、文件被删重加之外（行 ID 变化，已知取舍）的刷新之后改动照样生效（覆盖层按歌曲身份记键）。
     *
     * 列表、专辑分组与封面都从 [_songs] 派生，换掉这一条即全部刷新；改专辑名 / 专辑艺术家后歌曲
     * 换组，新旧专辑各自按当前成员行的媒体库专辑 ID 重新取图（URI 带卷 + 专辑 ID，
     * 图片加载器按 URI 分别请求，不串用换组前的旧图）。
     */
    private fun applyOverride(song: LocalSong, override: LocalSongOverride?): LocalSong {
        overlay = if (override == null) {
            overlay - song.id
        } else {
            overlay + (song.id to override)
        }
        val updated = song.withOverride(override)
        viewModelScope.launch {
            // 整条链排在一把锁里：换列表要基于上一次编辑提交后的结果，落盘也只按调用顺序走
            editLock.withLock {
                // 顺序也跟着标题走（spec：歌曲视图按标题升序），排序与专辑分组一样不占主线程。
                // 可用状态取快照里的本值：传进来的 song 可能是因权限失效而临时标记的展示副本
                _songs.value = withContext(Dispatchers.Default) {
                    _songs.value
                        .map { existing ->
                            if (existing.id == updated.id) {
                                updated.copy(available = existing.available)
                            } else {
                                existing
                            }
                        }
                        .sortedByDisplayTitle()
                }
                // 覆盖层是独立文件，与快照分开落盘
                LocalMusicStore.saveOverlay(application, overlay)
            }
        }
        return updated
    }

    /** 启动恢复的结果：快照与覆盖层一起从 IO 线程带回，主线程只做状态落位 */
    private data class RestoredLibrary(
        val overlay: Map<String, LocalSongOverride>,
        val songs: List<LocalSong> = emptyList(),
        val excludedFolders: List<String> = emptyList(),
    )

    private companion object {
        const val TAG = "LocalMusicViewModel"
    }
}

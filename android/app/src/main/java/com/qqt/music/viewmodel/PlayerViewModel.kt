package com.qqt.music.viewmodel

import android.app.Application
import android.net.Uri
import android.os.SystemClock
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import com.qqt.music.AppConfig
import com.qqt.music.data.api.model.Song
import com.qqt.music.data.local.PrefsManager
import com.qqt.music.download.DownloadManager
import com.qqt.music.player.AccessFactReporter
import com.qqt.music.player.AudioCache
import com.qqt.music.player.CacheVisual
import com.qqt.music.player.LastPlayedStore
import com.qqt.music.player.MediaControllerManager
import com.qqt.music.player.PlayerSettingsManager
import com.qqt.music.player.QueueSource
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * 全局播放状态管理（单 ExoPlayer 架构）
 *
 * 不再拥有独立的 ExoPlayer 实例，而是通过 MediaController 连接到
 * MusicPlayerService 中的 ExoPlayer。
 *
 * 优势：
 * - 只有一个播放源
 * - App 前后台切换播放继续
 * - 系统媒体控制与 UI 同步
 * - 内存占用更少
 */
class PlayerViewModel(private val application: Application) : AndroidViewModel(application) {

    companion object {
        private const val TAG = "PlayerViewModel"
    }

    // ========== 播放状态 ==========
    private val _currentSong = MutableStateFlow<Song?>(null)
    val currentSong: StateFlow<Song?> = _currentSong.asStateFlow()

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    private val _queue = MutableStateFlow<List<Song>>(emptyList())
    val queue: StateFlow<List<Song>> = _queue.asStateFlow()

    // 当前歌曲在队列中的索引，-1 表示未开始播放
    private val _currentIndex = MutableStateFlow(-1)
    val currentIndex: StateFlow<Int> = _currentIndex.asStateFlow()

    private val _currentPosition = MutableStateFlow(0L)
    val currentPosition: StateFlow<Long> = _currentPosition.asStateFlow()

    private val _duration = MutableStateFlow(0L)
    val duration: StateFlow<Long> = _duration.asStateFlow()

    private val _isBuffering = MutableStateFlow(false)
    val isBuffering: StateFlow<Boolean> = _isBuffering.asStateFlow()

    // 缓存可视化（ADR 0013）：整曲本地可得与缓存前缀比例，随切曲与 500ms 轮询刷新
    private val _cacheVisual = MutableStateFlow(CacheVisual.NOT_CACHED)
    val cacheVisual: StateFlow<CacheVisual> = _cacheVisual.asStateFlow()

    private var mediaController: MediaController? = null
    private var currentQueue: List<Song> = emptyList()

    /** 分钟模式定时倒计时协程；播完本曲模式无倒计时，由切曲事件结算 */
    private var sleepJob: Job? = null

    /** 错误重试自愈进行中的曲子（无 fragment 的原始 URI，ADR 0011）；READY 即痊愈清空 */
    private var selfHealUri: String? = null

    init {
        startPositionPolling()
        // 连接到 Service：SessionToken 绑定会自行拉起服务（ADR 0014），无需显式 startForegroundService
        connectToService()
        // Activity 重建时续跑未到点的分钟模式定时
        if (PlayerSettingsManager.sleepTimer.value?.endOfTrack == false) restartSleepTicker()
        Log.d(TAG, "PlayerViewModel created (single ExoPlayer architecture)")
    }

    // ========== 连接到 Service ==========
    private fun connectToService() {
        viewModelScope.launch {
            try {
                MediaControllerManager.connect(application)
                MediaControllerManager.mediaController.collect { controller ->
                    if (controller != null && mediaController == null) {
                        mediaController = controller
                        setupPlayerListener()
                        // 冷启动静默恢复上次播放（队列快照，ADR 0010）
                        tryRestoreLastPlayed()
                        Log.d(TAG, "✅ connected to MusicPlayerService")
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "❌ Failed to connect to MusicPlayerService", e)
            }
        }
    }

    // ========== 播放器监听器 ==========
    private fun setupPlayerListener() {
        mediaController?.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(playing: Boolean) {
                _isPlaying.value = playing
                // 暂停立即保存；缓冲引起的假暂停（非 READY 态）不写盘（ADR 0010）
                if (!playing && mediaController?.playbackState == Player.STATE_READY) {
                    mediaController?.let { saveCurrentProgress(it) }
                }
            }

            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                val idx = mediaController?.currentMediaItemIndex ?: 0
                _currentIndex.value = idx
                val song = currentQueue.getOrNull(idx)
                _currentSong.value = song
                _duration.value = mediaController?.duration?.coerceAtLeast(0L) ?: 0L
                Log.d(TAG, "📻 now playing: ${song?.title} (idx=$idx)")
                // 访问事实：每次装载播放一条，含恢复装载（ADR 0008）
                song?.let { AccessFactReporter.reportFact(application, it, resolveUri(it)) }
                // 缓存可视化切曲即刷（装载时刻判定），此后随轮询生长（ADR 0013）
                refreshCacheVisual()
                // 切歌立即保存（ADR 0010）
                mediaController?.let { saveCurrentProgress(it) }
                handleSleepTimerOnTransition(reason)
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                val isBuffering = playbackState == Player.STATE_BUFFERING
                _isBuffering.value = isBuffering
                val state = when (playbackState) {
                    Player.STATE_IDLE -> "IDLE"
                    Player.STATE_BUFFERING -> "BUFFERING"
                    Player.STATE_READY -> "READY"
                    Player.STATE_ENDED -> "ENDED"
                    else -> "UNKNOWN"
                }
                Log.d(TAG, "🎵 playback state: $state")
                // 时长写回：READY 后真实时长与服务器现值比对，不一致才上报（ADR 0008）
                if (playbackState == Player.STATE_READY) {
                    selfHealUri = null
                    _currentSong.value?.let { song ->
                        AccessFactReporter.reportDurationIfChanged(song, mediaController?.duration ?: 0L)
                    }
                }
            }

            // 错误重试自愈（ADR 0011）：割接窗口连明文 CDN 时写下的毒化缓存条目不随 CDN 刷新消失，
            // 须端侧清除后重取。护栏：仅解析/解码类错误参与（乱码数据的确切表现，IO 类是网络问题）；
            // 仅媒体 host；同曲再败升级 #raw 通道（旁路 -31 直读、独立缓存键），#raw 再败即停防循环。
            override fun onPlayerError(error: PlaybackException) {
                val controller = mediaController ?: return
                val item = controller.currentMediaItem ?: return
                val uri = item.localConfiguration?.uri ?: return
                val base = uri.buildUpon().fragment(null).build().toString()
                if (error.errorCode !in 3000..4999 || uri.host != AppConfig.MEDIA_HOST) return
                if (base == selfHealUri && uri.fragment == "raw") return
                Log.w(TAG, "🩹 parse/decode error ${error.errorCode}, self-heal: ${uri.lastPathSegment}")
                AudioCache.removeEntry(base)
                if (base == selfHealUri) {
                    controller.replaceMediaItem(
                        controller.currentMediaItemIndex,
                        item.buildUpon().setUri(uri.buildUpon().fragment("raw").build()).build()
                    )
                }
                selfHealUri = base
                controller.prepare()
                controller.play()
            }
        })
    }

    // ========== 进度轮询（每 100ms 更新一次）：粒度过粗会让歌词高亮与进度条滞后最多半个周期 ==========
    private fun startPositionPolling() {
        viewModelScope.launch {
            var pollCount = 0
            while (isActive) {
                mediaController?.let { controller ->
                    _currentPosition.value = controller.currentPosition.coerceAtLeast(0L)
                    _duration.value = controller.duration.coerceAtLeast(0L)

                    // 缓存可视化随轮询刷新：前缀实时生长、长满即翻金（ADR 0013）
                    refreshCacheVisual()

                    // 每 50 个周期（5秒）兜底保存一次（有当前歌即保存，ADR 0010）
                    if (pollCount++ % 50 == 0 && _currentSong.value != null) {
                        saveCurrentProgress(controller)
                    }
                }
                delay(100)
            }
        }
    }

    // ========== 缓存可视化（ADR 0013）==========

    /**
     * 刷新缓存染色状态：已下载歌曲（file:// 直读）直接整曲本地可得；
     * 流式播放按缓存 key（播放 URI 字符串，经 AudioCache.cacheKey 版本化，与 CacheDataSource 键同口径）
     * 查命中与前缀。
     */
    private fun refreshCacheVisual() {
        val song = _currentSong.value
        if (song == null) {
            _cacheVisual.value = CacheVisual.NOT_CACHED
            return
        }
        val uri = resolveUri(song)
        if (uri.scheme == "file") {
            _cacheVisual.value = CacheVisual.FULLY_AVAILABLE
        } else {
            _cacheVisual.value = AudioCache.cacheVisual(uri.toString())
        }
    }

    // ========== 进度保存 ==========

    /** 保存队列快照；距曲尾不足 3 秒按 0 存（ADR 0010） */
    private fun saveCurrentProgress(controller: MediaController) {
        val queue = currentQueue
        if (queue.isEmpty()) return
        val idx = controller.currentMediaItemIndex.coerceAtLeast(0)
        val duration = controller.duration.coerceAtLeast(0L)
        val pos = controller.currentPosition.coerceAtLeast(0L)
        val savedPos = if (duration > 0 && duration - pos < 3_000L) 0L else pos
        LastPlayedStore.save(application, queue, idx, savedPos, PlayerSettingsManager.currentSource)
        Log.d(TAG, "💾 saved snapshot: ${queue.size} songs, idx=$idx, pos=${savedPos}ms")
    }

    // ========== 播放控制 ==========

    /**
     * 播放指定歌曲及其队列。[sourceDescriptor] 必传显式打标队列来源（五键见 [QueueSource]，ADR 0015），
     * 切播放模式时据此写入对应来源的记忆。
     */
    fun playSong(song: Song, queue: List<Song> = listOf(song), sourceDescriptor: String) {
        if (mediaController == null) {
            Log.w(TAG, "⚠️ MediaController not ready yet")
            return
        }

        PlayerSettingsManager.setSource(sourceDescriptor)
        val startIndex = queue.indexOfFirst { it.id == song.id }.coerceAtLeast(0)
        currentQueue = queue

        _queue.value = queue
        _currentSong.value = song
        _currentIndex.value = startIndex

        mediaController?.setMediaItems(buildMediaItems(queue), startIndex, 0L)
        mediaController?.prepare()
        mediaController?.play()

        // 添加到最近播放
        PrefsManager.addRecentId(song.id)
        Log.d(TAG, "▶️ play song: ${song.title}")
    }

    /**
     * 恢复上次播放：重建队列、prepare 不 play，MiniPlayer 就位，点播放续播；
     * 不写最近播放，守「点播过」语义（ADR 0010）。
     */
    private fun restoreLastPlayed(tracks: List<Song>, startIndex: Int, positionMs: Long) {
        if (mediaController == null || tracks.isEmpty() || startIndex !in tracks.indices) {
            Log.w(TAG, "⚠️ Cannot restore: controller=${mediaController != null}, tracks=${tracks.size}, idx=$startIndex")
            return
        }

        val song = tracks[startIndex]
        currentQueue = tracks

        _queue.value = tracks
        _currentSong.value = song
        _currentIndex.value = startIndex

        mediaController?.setMediaItems(buildMediaItems(tracks), startIndex, positionMs)
        mediaController?.prepare()

        Log.d(TAG, "🔄 restored: ${song.title} @ ${positionMs}ms")
    }

    /** 冷启动恢复入口：读队列快照，无快照或索引越界即完全静默（ADR 0010）；
     *  快照来源落位播放模式记忆，旧快照无该字段按 default（ADR 0015） */
    private fun tryRestoreLastPlayed() {
        val snapshot = LastPlayedStore.load(application) ?: return
        PlayerSettingsManager.setSource(snapshot.sourceDescriptor ?: QueueSource.DEFAULT)
        restoreLastPlayed(snapshot.queue, snapshot.trackIndex, snapshot.positionMs)
    }

    fun togglePlayPause() {
        mediaController?.let { controller ->
            if (controller.isPlaying) {
                controller.pause()
                Log.d(TAG, "⏸️ paused")
            } else {
                controller.play()
                Log.d(TAG, "▶️ resumed")
            }
        }
    }

    fun skipNext() {
        mediaController?.seekToNextMediaItem()
        Log.d(TAG, "⏭️ skip to next")
    }

    fun skipPrev() {
        mediaController?.seekToPreviousMediaItem()
        Log.d(TAG, "⏮️ skip to previous")
    }

    /** 队列内跳播：不重建队列，直接切到队列第 index 首（队列入口浮层点选） */
    fun skipTo(index: Int) {
        val controller = mediaController ?: return
        if (index < 0 || index >= currentQueue.size) return
        controller.seekTo(index, 0L)
        controller.play()
        Log.d(TAG, "⏭️ skip to queue #$index")
    }

    fun seekTo(positionMs: Long) {
        mediaController?.seekTo(positionMs)
        Log.d(TAG, "📍 seek to ${positionMs}ms")
    }

    /** 播放模式（ADR 0008）：持久化于 PlayerSettingsManager，Service 收集后落到 ExoPlayer */
    fun setPlayMode(mode: PlayerSettingsManager.PlayMode) {
        PlayerSettingsManager.setPlayMode(mode)
        Log.d(TAG, "🎛️ play mode: ${mode.label}")
    }

    /** 菜单打开时继续点主控图标：按 随机→顺序→单曲循环 轮转到下一模式 */
    fun cyclePlayMode() {
        val modes = PlayerSettingsManager.PlayMode.values()
        val next = modes[(PlayerSettingsManager.playMode.value.ordinal + 1) % modes.size]
        setPlayMode(next)
    }

    /** 已下载歌曲用本地文件 URI 离线直读，未下载走网络（ADR 0003） */
    private fun resolveUri(track: Song): Uri =
        DownloadManager.localUri(track.id) ?: Uri.parse(track.url)

    /** Song 队列 → MediaItem 列表（点播与恢复共用同一构建） */
    private fun buildMediaItems(queue: List<Song>): List<MediaItem> = queue.map { track ->
        MediaItem.Builder()
            .setUri(resolveUri(track))
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(track.title)
                    .setArtist(track.artist)
                    .setArtworkUri(Uri.parse(track.thumbnailBig))
                    .build()
            )
            .build()
    }

    // ========== 定时关闭 ==========

    /**
     * 「播完本曲」在切曲时结算：自然放完（AUTO）→ 暂停并撤销；手动切歌/点播新歌（SEEK/PLAYLIST_CHANGED）
     * 视为用户主动干预，直接撤销定时（共识：自动化被显式操作打断即退场）。分钟模式与切曲无关。
     */
    private fun handleSleepTimerOnTransition(reason: Int) {
        val timer = PlayerSettingsManager.sleepTimer.value ?: return
        if (!timer.endOfTrack) return
        when (reason) {
            Player.MEDIA_ITEM_TRANSITION_REASON_AUTO -> {
                mediaController?.pause()
                PlayerSettingsManager.clearSleepTimer()
                Log.d(TAG, "⏱️ end-of-track timer fired, paused")
            }
            Player.MEDIA_ITEM_TRANSITION_REASON_SEEK -> {
                PlayerSettingsManager.clearSleepTimer()
                Log.d(TAG, "⏱️ end-of-track timer cancelled by user navigation (reason=$reason)")
            }
            Player.MEDIA_ITEM_TRANSITION_REASON_PLAYLIST_CHANGED -> {
                // #raw 自愈升级重试引发的过渡是程序行为，不算用户主动干预，不撤销定时（ADR 0011）
                if (mediaController?.currentMediaItem?.localConfiguration?.uri?.fragment != "raw") {
                    PlayerSettingsManager.clearSleepTimer()
                    Log.d(TAG, "⏱️ end-of-track timer cancelled by user navigation (reason=$reason)")
                }
            }
        }
    }

    fun startSleepTimer(minutes: Int) {
        PlayerSettingsManager.startSleepTimer(minutes)
        restartSleepTicker()
        Log.d(TAG, "⏱️ sleep timer: ${minutes}min")
    }

    fun startSleepTimerEndOfTrack() {
        sleepJob?.cancel()
        PlayerSettingsManager.startEndOfTrackTimer()
        Log.d(TAG, "⏱️ sleep timer: end of track")
    }

    fun clearSleepTimer() {
        sleepJob?.cancel()
        PlayerSettingsManager.clearSleepTimer()
        Log.d(TAG, "⏱️ sleep timer cleared")
    }

    /** 分钟模式倒计时：到点暂停并撤销定时；Activity 重建时从已有到点时刻续跑 */
    private fun restartSleepTicker() {
        sleepJob?.cancel()
        val timer = PlayerSettingsManager.sleepTimer.value ?: return
        if (timer.endOfTrack) return
        sleepJob = viewModelScope.launch {
            while (isActive) {
                val remainMs = timer.endAtElapsedRealtime - SystemClock.elapsedRealtime()
                if (remainMs <= 0L) {
                    PlayerSettingsManager.updateSleepRemaining(0L)
                    mediaController?.pause()
                    PlayerSettingsManager.clearSleepTimer()
                    Log.d(TAG, "⏱️ sleep timer fired, paused")
                    break
                }
                // 向上取整到秒，显示从 90:00 递减；250ms tick 保证到点误差小
                PlayerSettingsManager.updateSleepRemaining((remainMs + 999) / 1000)
                delay(250)
            }
        }
    }

    override fun onCleared() {
        mediaController?.let { controller ->
            saveCurrentProgress(controller)
        }
        MediaControllerManager.disconnect()
        Log.d(TAG, "🗑️ PlayerViewModel cleared")
        super.onCleared()
    }
}

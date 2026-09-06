package com.qqt.music.viewmodel

import android.app.Application
import android.net.Uri
import android.os.SystemClock
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import com.qqt.music.data.api.model.Song
import com.qqt.music.data.local.PrefsManager
import com.qqt.music.download.DownloadManager
import com.qqt.music.player.AccessFactReporter
import com.qqt.music.player.LastPlayedStore
import com.qqt.music.player.MediaControllerManager
import com.qqt.music.player.PlayerSettingsManager
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

    private var mediaController: MediaController? = null
    private var currentQueue: List<Song> = emptyList()

    /** 分钟模式定时倒计时协程；播完本曲模式无倒计时，由切曲事件结算 */
    private var sleepJob: Job? = null

    init {
        startPositionPolling()
        // 延迟连接到 Service，确保 Service 已启动
        connectToService()
        // Activity 重建时续跑未到点的分钟模式定时
        if (PlayerSettingsManager.sleepTimer.value?.endOfTrack == false) restartSleepTicker()
        Log.d(TAG, "PlayerViewModel created (single ExoPlayer architecture)")
    }

    // ========== 连接到 Service ==========
    private fun connectToService() {
        viewModelScope.launch {
            // 等待 100ms 确保 Service 已启动
            delay(100)
            try {
                MediaControllerManager.connect(application)
                MediaControllerManager.mediaController.collect { controller ->
                    if (controller != null && mediaController == null) {
                        mediaController = controller
                        // 恢复持久化倍速（单例在 MainActivity.init 时已载入）
                        controller.setPlaybackSpeed(PlayerSettingsManager.speed.value)
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
                    _currentSong.value?.let { song ->
                        AccessFactReporter.reportDurationIfChanged(song, mediaController?.duration ?: 0L)
                    }
                }
            }
        })
    }

    // ========== 进度轮询（每 500ms 更新一次）==========
    private fun startPositionPolling() {
        viewModelScope.launch {
            var pollCount = 0
            while (isActive) {
                mediaController?.let { controller ->
                    _currentPosition.value = controller.currentPosition.coerceAtLeast(0L)
                    _duration.value = controller.duration.coerceAtLeast(0L)

                    // 每 10 个周期（5秒）兜底保存一次（有当前歌即保存，ADR 0010）
                    if (pollCount++ % 10 == 0 && _currentSong.value != null) {
                        saveCurrentProgress(controller)
                    }
                }
                delay(500)
            }
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
        LastPlayedStore.save(application, queue, idx, savedPos)
        Log.d(TAG, "💾 saved snapshot: ${queue.size} songs, idx=$idx, pos=${savedPos}ms")
    }

    // ========== 播放控制 ==========

    /**
     * 播放指定歌曲及其队列
     */
    fun playSong(song: Song, queue: List<Song> = listOf(song)) {
        if (mediaController == null) {
            Log.w(TAG, "⚠️ MediaController not ready yet")
            return
        }

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

    /** 冷启动恢复入口：读队列快照，无快照或索引越界即完全静默（ADR 0010） */
    private fun tryRestoreLastPlayed() {
        val snapshot = LastPlayedStore.load(application) ?: return
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

    /** 设置倍速（变速不变调），持久化跨启动保持 */
    fun setSpeed(speed: Float) {
        PlayerSettingsManager.setSpeed(speed)
        mediaController?.setPlaybackSpeed(speed)
        Log.d(TAG, "🐢 playback speed: ${speed}x")
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
            Player.MEDIA_ITEM_TRANSITION_REASON_SEEK,
            Player.MEDIA_ITEM_TRANSITION_REASON_PLAYLIST_CHANGED,
            -> {
                PlayerSettingsManager.clearSleepTimer()
                Log.d(TAG, "⏱️ end-of-track timer cancelled by user navigation (reason=$reason)")
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

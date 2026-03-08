package com.qqt.music.viewmodel

import android.app.Application
import android.net.Uri
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import com.qqt.music.data.api.model.Song
import com.qqt.music.data.local.PrefsManager
import com.qqt.music.player.LastPlayedStore
import com.qqt.music.player.MediaControllerManager
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

    private val _currentPosition = MutableStateFlow(0L)
    val currentPosition: StateFlow<Long> = _currentPosition.asStateFlow()

    private val _duration = MutableStateFlow(0L)
    val duration: StateFlow<Long> = _duration.asStateFlow()

    private val _isBuffering = MutableStateFlow(false)
    val isBuffering: StateFlow<Boolean> = _isBuffering.asStateFlow()

    private var mediaController: MediaController? = null
    private var currentAlbumId: Int = -1
    private var currentQueue: List<Song> = emptyList()

    init {
        startPositionPolling()
        // 延迟连接到 Service，确保 Service 已启动
        connectToService()
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
                        setupPlayerListener()
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
            }

            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                val idx = mediaController?.currentMediaItemIndex ?: 0
                val song = currentQueue.getOrNull(idx)
                _currentSong.value = song
                _duration.value = mediaController?.duration?.coerceAtLeast(0L) ?: 0L
                Log.d(TAG, "📻 now playing: ${song?.title} (idx=$idx)")
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

                    // 每 10 个周期（5秒）保存一次进度
                    if (pollCount++ % 10 == 0 && currentAlbumId != -1) {
                        saveCurrentProgress(controller)
                    }
                }
                delay(500)
            }
        }
    }

    // ========== 进度保存 ==========
    private fun saveCurrentProgress(controller: MediaController) {
        LastPlayedStore.save(
            application,
            albumId = currentAlbumId,
            trackIndex = controller.currentMediaItemIndex.coerceAtLeast(0),
            positionMs = controller.currentPosition
        )
        Log.d(TAG, "💾 saved progress: albumId=$currentAlbumId, idx=${controller.currentMediaItemIndex}, pos=${controller.currentPosition}ms")
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
        // 使用 catId 作为分类 ID（代替 albumId）
        currentAlbumId = song.catId.toIntOrNull() ?: -1
        currentQueue = queue

        _queue.value = queue
        _currentSong.value = song

        val items = queue.map { track ->
            MediaItem.Builder()
                .setUri(track.url)
                .setMediaMetadata(
                    MediaMetadata.Builder()
                        .setTitle(track.title)
                        .setArtist(track.artist)
                        .setArtworkUri(Uri.parse(track.thumbnailBig))
                        .build()
                )
                .build()
        }
        mediaController?.setMediaItems(items, startIndex, 0L)
        mediaController?.prepare()
        mediaController?.play()

        // 添加到最近播放
        PrefsManager.addRecentId(song.id)
        Log.d(TAG, "▶️ play song: ${song.title}")
    }

    /**
     * 恢复上次播放的进度
     */
    fun restoreLastPlayed(tracks: List<Song>, startIndex: Int, positionMs: Long) {
        if (mediaController == null || tracks.isEmpty() || startIndex < 0) {
            Log.w(TAG, "⚠️ Cannot restore: controller=${mediaController != null}, tracks=${tracks.size}, idx=$startIndex")
            return
        }

        val song = tracks.getOrNull(startIndex) ?: return
        currentAlbumId = song.catId.toIntOrNull() ?: -1
        currentQueue = tracks

        _queue.value = tracks
        _currentSong.value = song

        val items = tracks.map { track ->
            MediaItem.Builder()
                .setUri(track.url)
                .setMediaMetadata(
                    MediaMetadata.Builder()
                        .setTitle(track.title)
                        .setArtist(track.artist)
                        .setArtworkUri(Uri.parse(track.thumbnailBig))
                        .build()
                )
                .build()
        }
        mediaController?.setMediaItems(items, startIndex, positionMs)
        mediaController?.prepare()

        Log.d(TAG, "🔄 restored: ${song.title} @ ${positionMs}ms")
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

    fun seekTo(positionMs: Long) {
        mediaController?.seekTo(positionMs)
        Log.d(TAG, "📍 seek to ${positionMs}ms")
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

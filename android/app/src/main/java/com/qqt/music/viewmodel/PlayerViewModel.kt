package com.qqt.music.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.qqt.music.data.api.model.Song
import com.qqt.music.data.local.PrefsManager
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class PlayerViewModel(application: Application) : AndroidViewModel(application) {

    val player: ExoPlayer = ExoPlayer.Builder(application).build()

    private val _currentSong = MutableStateFlow<Song?>(null)
    val currentSong: StateFlow<Song?> = _currentSong.asStateFlow()

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    private val _queue = MutableStateFlow<List<Song>>(emptyList())
    val queue: StateFlow<List<Song>> = _queue.asStateFlow()

    init {
        player.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(playing: Boolean) {
                _isPlaying.value = playing
            }
            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                val idx = player.currentMediaItemIndex
                _currentSong.value = _queue.value.getOrNull(idx)
            }
        })
    }

    fun playSong(song: Song, queue: List<Song> = listOf(song)) {
        val startIndex = queue.indexOfFirst { it.id == song.id }.coerceAtLeast(0)
        _queue.value = queue
        _currentSong.value = song
        val items = queue.map { MediaItem.fromUri(it.url) }
        player.setMediaItems(items, startIndex, 0L)
        player.prepare()
        player.play()
        PrefsManager.addRecentId(song.id)
    }

    fun togglePlayPause() {
        if (player.isPlaying) player.pause() else player.play()
    }

    fun skipNext() {
        player.seekToNextMediaItem()
    }

    fun skipPrev() {
        player.seekToPreviousMediaItem()
    }

    override fun onCleared() {
        player.release()
        super.onCleared()
    }
}

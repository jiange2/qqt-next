package com.qqt.music.player

import android.content.ComponentName
import android.content.Context
import android.util.Log
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.MoreExecutors
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * MediaController 单例管理器
 *
 * PlayerViewModel 通过此管理器连接到 MusicPlayerService 的 ExoPlayer
 * 而不是创建独立的 ExoPlayer 实例。
 *
 * 好处：
 * - 只有一个 ExoPlayer（在 Service 中）
 * - App 前后台切换，播放继续
 * - UI 与系统媒体控制同步
 */
object MediaControllerManager {

    private const val TAG = "MediaControllerManager"
    
    private var mediaControllerFuture: ListenableFuture<MediaController>? = null
    
    private val _mediaController = MutableStateFlow<MediaController?>(null)
    val mediaController = _mediaController.asStateFlow()

    /**
     * 异步连接到 MusicPlayerService
     */
    fun connect(context: Context) {
        // 如果已经在连接中或已连接，跳过
        if (mediaControllerFuture != null || _mediaController.value != null) {
            Log.d(TAG, "Already connected or connecting, skipping...")
            return
        }

        try {
            val sessionToken = SessionToken(context, ComponentName(context, MusicPlayerService::class.java))
            mediaControllerFuture = MediaController.Builder(context, sessionToken).buildAsync()
            
            mediaControllerFuture?.addListener({
                try {
                    val controller = mediaControllerFuture?.get()
                    _mediaController.value = controller
                    Log.d(TAG, "✅ MediaController connected successfully")
                } catch (e: Exception) {
                    Log.e(TAG, "❌ Failed to get MediaController", e)
                    mediaControllerFuture = null
                }
            }, MoreExecutors.directExecutor())
        } catch (e: Exception) {
            Log.e(TAG, "❌ Failed to create SessionToken", e)
            mediaControllerFuture = null
        }
    }

    /**
     * 断开连接
     */
    fun disconnect() {
        _mediaController.value?.release()
        _mediaController.value = null
        mediaControllerFuture = null
        Log.d(TAG, "Disconnected")
    }
}

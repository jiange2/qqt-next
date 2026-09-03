package com.qqt.music.player

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.wifi.WifiManager
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.qqt.music.MEDIA_REFERER
import com.qqt.music.MainActivity
import com.qqt.music.R

/**
 * 前台媒体播放服务
 *
 * 职责：
 * 1. 维持前台服务状态（不被系统杀死）
 * 2. 创建和管理 ExoPlayer + MediaSession（系统媒体控制）
 * 3. 管理 WiFi 锁（防止 WiFi 进入省电模式）
 * 4. 响应 onTaskRemoved（App 被清除时不停止）
 */
class MusicPlayerService : MediaSessionService() {

    companion object {
        private const val TAG = "MusicPlayerService"
        private const val NOTIFICATION_ID = 8001
        private const val CHANNEL_ID = "music_playback_channel"
    }

    private var mediaSession: MediaSession? = null
    private var wifiLock: WifiManager.WifiLock? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()

        // 1. 创建 WiFi Lock
        wifiLock = (getSystemService(Context.WIFI_SERVICE) as? WifiManager)
            ?.createWifiLock(WifiManager.WIFI_MODE_FULL_HIGH_PERF, "AudioPlayer:WifiLock")

        // 2. 构建缓存数据源
        val cacheDataSourceFactory = CacheDataSource.Factory()
            .setCache(AudioCache.get(this))
            // CDN 防盗链：音频请求携带约定 Referer（仓库级 ADR 0006；媒体 URL 均为 http(s)，无需 file/本地数据源）
            .setUpstreamDataSourceFactory(
                DefaultHttpDataSource.Factory()
                    .setDefaultRequestProperties(mapOf("Referer" to MEDIA_REFERER))
            )
            .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)

        // 3. 创建 ExoPlayer
        val player = ExoPlayer.Builder(this)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                    .setUsage(C.USAGE_MEDIA)
                    .build(),
                true  // handleAudioFocus
            )
            .setHandleAudioBecomingNoisy(true)
            .setWakeMode(C.WAKE_MODE_NETWORK)
            .setMediaSourceFactory(DefaultMediaSourceFactory(cacheDataSourceFactory))
            .build()
            .also { it.repeatMode = ExoPlayer.REPEAT_MODE_ALL }

        // 4. 创建 MediaSession
        val sessionActivityIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val sessionActivityPendingIntent = PendingIntent.getActivity(
            this, 0, sessionActivityIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        mediaSession = MediaSession.Builder(this, player)
            .setSessionActivity(sessionActivityPendingIntent)
            .build()

        // 5. WiFi Lock 跟随播放状态自动获取/释放
        // 以及监听播放进度和元数据变化
        player.addListener(object : androidx.media3.common.Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                if (isPlaying) {
                    acquireWifiLock()
                } else {
                    releaseWifiLock()
                }
            }

            override fun onMediaItemTransition(mediaItem: androidx.media3.common.MediaItem?, reason: Int) {
                val metadata = mediaItem?.mediaMetadata
                Log.d(TAG, "🎵 onMediaItemTransition: title=${metadata?.title}, artist=${metadata?.artist}")
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                val state = when (playbackState) {
                    androidx.media3.common.Player.STATE_IDLE -> "IDLE"
                    androidx.media3.common.Player.STATE_BUFFERING -> "BUFFERING"
                    androidx.media3.common.Player.STATE_READY -> "READY"
                    androidx.media3.common.Player.STATE_ENDED -> "ENDED"
                    else -> "UNKNOWN"
                }
                Log.d(TAG, "🔊 playback state: $state")
            }
        })

        // 6. 启动前台服务
        startForeground(NOTIFICATION_ID, buildNotification())
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        // START_STICKY：系统杀死服务后自动重启
        return START_STICKY
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        // 不调用 stopSelf()，保持服务运行
        Log.d(TAG, "🛡️ onTaskRemoved — service stays alive")
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? {
        return mediaSession
    }

    override fun onDestroy() {
        releaseWifiLock()
        mediaSession?.run {
            player.release()
            release()
        }
        mediaSession = null
        Log.d(TAG, "✋ onDestroy")
        super.onDestroy()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "音乐播放",
                NotificationManager.IMPORTANCE_LOW
            )
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    private fun buildNotification(): android.app.Notification {
        val contentIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            this, 0, contentIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("音乐播放器")
            .setContentText("正在播放")
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setSilent(true)
            .build()
    }

    private fun acquireWifiLock() {
        wifiLock?.let {
            if (!it.isHeld) {
                it.acquire()
                Log.d(TAG, "📶 WiFi Lock acquired")
            }
        }
    }

    private fun releaseWifiLock() {
        wifiLock?.let {
            if (it.isHeld) {
                it.release()
                Log.d(TAG, "📶 WiFi Lock released")
            }
        }
    }
}

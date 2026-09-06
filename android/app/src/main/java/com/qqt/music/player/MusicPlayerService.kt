package com.qqt.music.player

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.media.audiofx.Equalizer
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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
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

    /** 播放会话上的均衡器效果（平台 AudioFX，非 ExoPlayer 内建）；生命周期随 Service */
    private var equalizer: Equalizer? = null

    /** Service 内协程：收集播放设置变化热切换均衡器 */
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()

        // 1. 创建 WiFi Lock
        wifiLock = (getSystemService(Context.WIFI_SERVICE) as? WifiManager)
            ?.createWifiLock(WifiManager.WIFI_MODE_FULL_HIGH_PERF, "AudioPlayer:WifiLock")

        // 2. 构建缓存数据源
        val cacheDataSourceFactory = CacheDataSource.Factory()
            .setCache(AudioCache.get(this))
            // CDN 防盗链：音频请求携带约定 Referer（仓库级 ADR 0006）
            .setUpstreamDataSourceFactory(
                DefaultHttpDataSource.Factory()
                    .setDefaultRequestProperties(mapOf("Referer" to MEDIA_REFERER))
            )
            .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)

        // 已下载歌曲以 file:// URI 离线直读本地文件，按 scheme 分流（ADR 0003）
        val playbackDataSourceFactory = SchemeRoutingDataSourceFactory(cacheDataSourceFactory)

        // 3. 创建 ExoPlayer
        // 显式生成 audioSessionId：均衡器等 AudioFX 效果要挂在播放会话上，默认会话 ID 无法预知
        val sessionId = (getSystemService(Context.AUDIO_SERVICE) as AudioManager)
            .generateAudioSessionId()
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
            .setMediaSourceFactory(DefaultMediaSourceFactory(playbackDataSourceFactory))
            .build()
        // 播放模式不再硬编码：由下方 applyPlayMode 按 PlayerSettingsManager 持久化值初始化（ADR 0008），
        // 未持久化时默认「顺序播放」= REPEAT_MODE_ALL，与历史行为一致
        // Media3 1.4.0 的 ExoPlayer.Builder 没有 setAudioSessionId（更高版本才提供），只能在实例上设置
        player.setAudioSessionId(sessionId)

        // 3.5 均衡器：探测设备支持后随播放设置热切换预设
        setupEqualizer(sessionId)
        serviceScope.launch {
            PlayerSettingsManager.eqPreset.collect { applyEqPreset(it) }
        }

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

        // 4.5 播放模式：初始应用持久化值，随后收集变化热切换（ADR 0008）。
        // 必须在 mediaSession 创建之后：applyPlayMode 经 mediaSession?.player 取播放器，
        // 放在 session 之前会因 mediaSession 为 null 空转，引擎停留默认 REPEAT_MODE_OFF（播完即止）
        applyPlayMode(PlayerSettingsManager.playMode.value)
        serviceScope.launch {
            PlayerSettingsManager.playMode.collect { applyPlayMode(it) }
        }

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
        serviceScope.cancel()
        equalizer?.release()
        equalizer = null
        mediaSession?.run {
            player.release()
            release()
        }
        mediaSession = null
        Log.d(TAG, "✋ onDestroy")
        super.onDestroy()
    }

    /** 探测设备 AudioFX 支持；效果实例生命周期随 Service，预设切换由 collect 驱动 */
    private fun setupEqualizer(sessionId: Int) {
        try {
            equalizer = Equalizer(0, sessionId)
            PlayerSettingsManager.setEqAvailable(true)
        } catch (e: Exception) {
            // 部分设备/模拟器无均衡器实现：置不可用，面板显示提示文案
            equalizer = null
            PlayerSettingsManager.setEqAvailable(false)
            Log.w(TAG, "EQ unavailable on this device", e)
        }
    }

    /** 播放模式落到 ExoPlayer（ADR 0008）：随机=乱序+整队列循环、顺序=按序循环整队列、单曲循环=单曲重复 */
    private fun applyPlayMode(mode: PlayerSettingsManager.PlayMode) {
        val player = mediaSession?.player ?: return
        when (mode) {
            PlayerSettingsManager.PlayMode.SHUFFLE -> {
                player.shuffleModeEnabled = true
                player.repeatMode = ExoPlayer.REPEAT_MODE_ALL
            }
            PlayerSettingsManager.PlayMode.SEQUENTIAL -> {
                player.shuffleModeEnabled = false
                player.repeatMode = ExoPlayer.REPEAT_MODE_ALL
            }
            PlayerSettingsManager.PlayMode.REPEAT_ONE -> {
                player.shuffleModeEnabled = false
                player.repeatMode = ExoPlayer.REPEAT_MODE_ONE
            }
        }
        Log.d(TAG, "🎛️ play mode: ${mode.label}")
    }

    /** 写入一个预设：「正常」直接关效果；平台预设走 usePreset；重低音按实际频段数抬最低两段 */
    private fun applyEqPreset(preset: PlayerSettingsManager.EqPreset) {
        val eq = equalizer ?: return
        try {
            when {
                preset.bypass -> eq.setEnabled(false)
                preset.platformPreset != null -> {
                    eq.setEnabled(true)
                    // Equalizer 的预设写入方法是 usePreset(short)，没有 setCurrentPreset，
                    // 因此 currentPreset 是 Kotlin 合成的只读属性，不能直接赋值
                    eq.usePreset(preset.platformPreset)
                }
                preset.bandLevelsDb != null -> {
                    eq.setEnabled(true)
                    val range = eq.bandLevelRange
                    for (band in 0 until eq.numberOfBands) {
                        val db = preset.bandLevelsDb.getOrNull(band) ?: 0f
                        // setBandLevel 单位是 milliBel；超出设备范围时钳制
                        val mb = (db * 100).toInt()
                            .coerceIn(range[0].toInt(), range[1].toInt())
                            .toShort()
                        eq.setBandLevel(band.toShort(), mb)
                    }
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "apply eq preset failed: ${preset.key}", e)
        }
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

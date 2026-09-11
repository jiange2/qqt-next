package com.qqt.music.player

import android.app.PendingIntent
import android.content.Intent
import android.util.Log
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.cache.CacheDataSink
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import androidx.media3.session.CacheBitmapLoader
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import com.qqt.music.MEDIA_REFERER
import com.qqt.music.MainActivity

/**
 * 前台媒体播放服务（保活仅由本服务在播放期承担，ADR 0014）
 *
 * 职责：
 * 1. 创建和管理 ExoPlayer + MediaSession（系统媒体控制）
 * 2. 播放期间由 Media3 提升为前台服务并发布媒体通知，空闲且无控制器连接时自动停止
 */
class MusicPlayerService : MediaSessionService() {

    companion object {
        private const val TAG = "MusicPlayerService"
    }

    private var mediaSession: MediaSession? = null

    /** Service 内协程：收集播放模式变化热切换 */
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    override fun onCreate() {
        super.onCreate()

        // 1. 构建缓存数据源
        val cacheDataSourceFactory = CacheDataSource.Factory()
            .setCache(AudioCache.get(this))
            // 缓存键版本化（仓库级 ADR 0011 割接）：与 AudioCache.cacheKey 同口径，
            // 缓存语义变更时旧键条目由 AudioCache 启动清扫一次性作废
            .setCacheKeyFactory { dataSpec -> AudioCache.cacheKey(dataSpec.uri.toString()) }
            // CDN 防盗链 Referer + 字节解混淆上游（仓库级 ADR 0006 / 0011）：
            // 网络字节先还原再入缓存，被动缓存与已下载文件保持明文，存量明文缓存仍有效
            .setUpstreamDataSourceFactory(
                DeobfuscatingDataSourceFactory(
                    DefaultHttpDataSource.Factory()
                        .setDefaultRequestProperties(mapOf("Referer" to MEDIA_REFERER))
                )
            )
            .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)
            // 分片提交 1MiB（默认 5MiB）：CacheDataSink 只在分片写满或流关闭时 commitFile，
            // 默认值下整首歌播放期间新写数据对查询不可见，前缀条只在切歌/播完瞬间出现；
            // 官方建议分片 ≥2MiB（低于仅打印警告），取 1MiB 换取前缀条随播放阶梯生长（ADR 0013）
            .setCacheWriteDataSinkFactory(
                CacheDataSink.Factory()
                    .setCache(AudioCache.get(this))
                    .setFragmentSize(1024L * 1024L)
            )

        // 已下载歌曲以 file:// URI 离线直读本地文件，按 scheme 分流（ADR 0003）
        val playbackDataSourceFactory = SchemeRoutingDataSourceFactory(cacheDataSourceFactory)

        // 2. 创建 ExoPlayer
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

        // 3. 创建 MediaSession
        val sessionActivityIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val sessionActivityPendingIntent = PendingIntent.getActivity(
            this, 0, sessionActivityIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        mediaSession = MediaSession.Builder(this, player)
            .setSessionActivity(sessionActivityPendingIntent)
            // 通知封面走解混淆 + Referer 加载器（仓库级 ADR 0006 / 0011）；
            // CacheBitmapLoader 缓存结果，避免每次通知刷新重复下载
            .setBitmapLoader(CacheBitmapLoader(MediaBitmapLoader()))
            .build()

        // 3.5 播放模式：不再硬编码，初始应用 PlayerSettingsManager 持久化值，随后收集变化热切换（ADR 0008）；
        // 未持久化时默认「顺序播放」= REPEAT_MODE_ALL，与历史行为一致。
        // 必须在 mediaSession 创建之后：applyPlayMode 经 mediaSession?.player 取播放器，
        // 放在 session 之前会因 mediaSession 为 null 空转，引擎停留默认 REPEAT_MODE_OFF（播完即止）
        applyPlayMode(PlayerSettingsManager.playMode.value)
        serviceScope.launch {
            PlayerSettingsManager.playMode.collect { applyPlayMode(it) }
        }

        // 3.6 车机歌词同步（ADR 0016）：借专辑行承载当前句、作者行承载下一句；由 serviceScope 协程
        // 持有，无需保存引用
        CarLyricsSync(player, serviceScope)

        // 4. 监听播放进度和元数据变化
        player.addListener(object : androidx.media3.common.Player.Listener {
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
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? {
        return mediaSession
    }

    override fun onDestroy() {
        serviceScope.cancel()
        mediaSession?.run {
            player.release()
            release()
        }
        mediaSession = null
        Log.d(TAG, "✋ onDestroy")
        super.onDestroy()
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
}

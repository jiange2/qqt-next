# Android 播放器核心实现设计文档

> 本文档总结 Audio Player Android App 的核心技术实现，包含 ExoPlayer、MediaSession、双前台保活、WiFi Lock 和自启动恢复，可作为其他项目的参考。

---

## 目录

1. [整体架构](#整体架构)
2. [ExoPlayer 播放引擎](#exoplayer-播放引擎)
3. [MediaSession 系统控制](#mediasession-系统控制)
4. [双层前台服务保活](#双层前台服务保活)
5. [WiFi 锁管理](#wifi-锁管理)
6. [自启动恢复](#自启动恢复)
7. [进度保存和恢复](#进度保存和恢复)
8. [数据持久化](#数据持久化)
9. [技术栈和配置](#技术栈和配置)

---

## 整体架构

```
┌─────────────────────────────────────────────┐
│         MainScreen (Jetpack Compose)        │
│  AlbumsScreen | PlayerScreen | PlaylistScreen│
└────────────────┬────────────────────────────┘
                 │
         ┌───────┼────────┐
         │       │        │
    ┌────▼───┐  ┌▼─────┐  ┌▼──────────────┐
    │PlayerVM│  │Albums│  │PlaylistViewModel
    │        │  │ViewModel
    └────┬───┘  └──────┘  └───────────────┘
         │
    ┌────▼────────────────────────────────────────┐
    │ MediaController (Media3 Session)           │
    │ 连接 PlayerService 的 MediaSession         │
    └────┬─────────────────────────────────────────┘
         │
    ┌────▼─────────────────────────────────────────────────┐
    │                  PlayerService                        │
    │  ┌──────────────────────────────────────────────┐   │
    │  │ ExoPlayer（CacheDataSource + LRU MP3 缓存）  │   │
    │  │ - SimpleCache: 60% 磁盘容量                  │   │
    │  │ - LeastRecentlyUsedCacheEvictor              │   │
    │  │ - WifiLock: 播放时自动获取                  │   │
    │  └──────────────────────────────────────────────┘   │
    │  MediaSession（系统通知栏、锁屏、蓝牙）             │
    └────────────────────────────────────────────────────────┘
         │
    ┌────▼──────────────────────┐
    │  KeepAliveService         │
    │  (IMPORTANCE_MIN，独立前台)│
    └───────────────────────────┘
         │
    ┌────▼──────────────────────────────┐
    │  BootReceiver                     │
    │  BOOT_COMPLETED | QUICKBOOT_POWERON
    └───────────────────────────────────┘
         │
    ┌────▼────────────────────────────────┐
    │  LastPlayedStore (SharedPreferences)│
    │  - 保存播放进度                     │
    │  - 10 秒自动保存一次                │
    └──────────────────────────────────────┘
```

---

## ExoPlayer 播放引擎

### 核心类：`PlayerService.kt`

PlayerService 是后台播放的心脏，继承 `MediaSessionService`，整合了 ExoPlayer、LRU 缓存、MediaSession 和 WiFi 锁。

#### 初始化流程 (onCreate)

```kotlin
class PlayerService : MediaSessionService() {
    
    private var mediaSession: MediaSession? = null
    private var wifiLock: WifiManager.WifiLock? = null

    override fun onCreate() {
        super.onCreate()

        // 1. 创建 WiFi Lock（防止 WiFi 进入省电模式）
        wifiLock = (applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager)
            ?.createWifiLock(WifiManager.WIFI_MODE_FULL_HIGH_PERF, "AudioPlayer:WifiLock")

        // 2. 构建缓存数据源
        val cacheDataSourceFactory = CacheDataSource.Factory()
            .setCache(AudioCache.get(this))  // 单例缓存
            .setUpstreamDataSourceFactory(DefaultDataSource.Factory(this))
            .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)  // 缓存出错时降级到网络

        // 3. 初始化 ExoPlayer
        val player = ExoPlayer.Builder(this)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                    .setUsage(C.USAGE_MEDIA)
                    .build(),
                handleAudioFocus = true
            )
            .setHandleAudioBecomingNoisy(true)  // 拔耳机自动暂停
            .setWakeMode(C.WAKE_MODE_NETWORK)   // 保持 CPU + WiFi 唤醒
            .setMediaSourceFactory(DefaultMediaSourceFactory(cacheDataSourceFactory))
            .build()
            .also { it.repeatMode = Player.REPEAT_MODE_ALL }  // 默认列表循环

        // 4. 构建 MediaSession
        val sessionActivityIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val sessionActivityPendingIntent = PendingIntent.getActivity(
            this, 0, sessionActivityIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        mediaSession = MediaSession.Builder(this, player)
            .setSessionActivity(sessionActivityPendingIntent)  // 点通知回到 App
            .build()

        // 5. WiFi Lock 跟随播放状态自动获取/释放
        player.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                if (isPlaying) {
                    acquireWifiLock()
                } else {
                    releaseWifiLock()
                }
            }
        })
    }

    // START_STICKY：系统杀死服务后自动重启
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        return START_STICKY
    }

    // 不调用 stopSelf()，保持播放继续
    override fun onTaskRemoved(rootIntent: Intent?) {
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
        super.onDestroy()
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
```

### LRU MP3 缓存

```kotlin
object AudioCache {
    @Volatile
    private var instance: SimpleCache? = null

    fun get(context: Context): SimpleCache {
        return instance ?: synchronized(this) {
            instance ?: SimpleCache(
                File(context.cacheDir, "audio_cache"),
                LeastRecentlyUsedCacheEvictor(availableCacheBytes(context)),
            ).also { instance = it }
        }
    }

    private fun availableCacheBytes(context: Context): Long {
        val stat = StatFs(context.cacheDir.absolutePath)
        val freeBytes = stat.availableBlocksLong * stat.blockSizeLong
        return (freeBytes * 0.6).toLong()  // 使用 60% 的可用存储空间
    }
}
```

### 播放控制：`PlayerViewModel.kt`

```kotlin
data class PlayerUiState(
    val isPlaying: Boolean = false,
    val currentPosition: Long = 0L,       // 毫秒
    val duration: Long = 0L,              // 毫秒
    val title: String = "",
    val artist: String = "",
    val albumArtUrl: String? = null,
    val isLoading: Boolean = false,
    val error: String? = null,
    val currentIndex: Int = 0,
    val playlistSize: Int = 0,
    val hasPrevious: Boolean = false,
    val hasNext: Boolean = false,
    val isShuffleOn: Boolean = false,
    val isRepeatOne: Boolean = false,
)

class PlayerViewModel(private val context: Context) : ViewModel() {

    private val _uiState = MutableStateFlow(PlayerUiState())
    val uiState: StateFlow<PlayerUiState> = _uiState.asStateFlow()

    private var controller: MediaController? = null
    private var currentMusicList: List<Music> = emptyList()
    private var currentSavedAlbumId: Int = -1

    init {
        connectToService()
    }

    private fun connectToService() {
        val sessionToken = SessionToken(context, ComponentName(context, PlayerService::class.java))
        val controllerFuture = MediaController.Builder(context, sessionToken).buildAsync()
        controllerFuture.addListener({
            controller = controllerFuture.get()
            controller?.addListener(playerListener)
            startPositionPolling()
        }, MoreExecutors.directExecutor())
    }

    fun playMusicList(tracks: List<Music>, startIndex: Int) {
        currentMusicList = tracks
        val mediaItems = tracks.map { track ->
            MediaItem.Builder()
                .setMediaId(track.id.toString())
                .setMediaMetadata(
                    MediaMetadata.Builder()
                        .setTitle(track.name)
                        .setArtist(track.artist)
                        .setArtworkUri(Uri.parse(track.albumCoverUrl))
                        .build()
                )
                .setUri(track.mp3Url)
                .build()
        }
        controller?.setMediaItems(mediaItems, startIndex, 0)
        controller?.prepare()
        controller?.play()
    }

    fun restoreLastPlayed(tracks: List<Music>, startIndex: Int, positionMs: Long) {
        currentMusicList = tracks
        val mediaItems = tracks.map { /* ... */ }
        controller?.setMediaItems(mediaItems, startIndex, positionMs)
        controller?.prepare()
    }

    private fun startPositionPolling() {
        viewModelScope.launch {
            var pollCount = 0
            while (isActive) {
                controller?.let { c ->
                    _uiState.update {
                        it.copy(
                            isPlaying = c.isPlaying,
                            currentPosition = c.currentPosition.coerceAtLeast(0L),
                            duration = c.duration.coerceAtLeast(0L),
                        )
                    }
                    if (pollCount++ % 20 == 0) {
                        LastPlayedStore.save(context, currentSavedAlbumId, c.currentMediaItemIndex, c.currentPosition)
                    }
                }
                delay(500)
            }
        }
    }

    private val playerListener = object : Player.Listener {
        override fun onIsPlayingChanged(isPlaying: Boolean) {
            _uiState.update { it.copy(isPlaying = isPlaying) }
        }

        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            val c = controller ?: return
            mediaItem?.let { item ->
                _uiState.update {
                    it.copy(
                        title = item.mediaMetadata.title?.toString() ?: "",
                        artist = item.mediaMetadata.artist?.toString() ?: "",
                        albumArtUrl = item.mediaMetadata.artworkUri?.toString(),
                        duration = c.duration.coerceAtLeast(0L),
                        currentIndex = c.currentMediaItemIndex,
                    )
                }
            }
        }

        override fun onPlaybackStateChanged(playbackState: Int) {
            _uiState.update { it.copy(isLoading = playbackState == Player.STATE_BUFFERING) }
        }
    }
}
```

---

## MediaSession 系统控制

PlayerService 建立的 MediaSession 自动向系统注册，允许用户通过系统通知栏、锁屏界面和蓝牙耳机控制播放。

### AndroidManifest.xml 注册

```xml
<service
    android:name=".player.PlayerService"
    android:exported="true"
    android:foregroundServiceType="mediaPlayback">
    <intent-filter>
        <action android:name="androidx.media3.session.MediaSessionService" />
    </intent-filter>
</service>
```

---

## 双层前台服务保活

### 核心思想

国产 ROM 对后台服务的限制非常激进。使用双层前台服务：
1. **PlayerService** - 前台服务，处理播放逻辑
2. **KeepAliveService** - 独立的轻量级前台服务，纯粹维持进程优先级

### KeepAliveService 实现

```kotlin
class KeepAliveService : Service() {

    companion object {
        private const val CHANNEL_ID = "keep_alive_channel"
        private const val NOTIFICATION_ID = 9001
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, buildNotification())
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return START_STICKY
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        // 不调用 stopSelf()，保持服务运行
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "后台守护",
                NotificationManager.IMPORTANCE_MIN
            )
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    private fun buildNotification(): Notification {
        val contentIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            this, 0, contentIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("音乐播放器")
            .setContentText("守护中")
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .setSilent(true)
            .build()
    }
}
```

### MainActivity 启动

```kotlin
override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    ContextCompat.startForegroundService(this, Intent(this, KeepAliveService::class.java))
    // ...
}
```

---

## WiFi 锁管理

### 实现

```kotlin
wifiLock = (applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager)
    ?.createWifiLock(WifiManager.WIFI_MODE_FULL_HIGH_PERF, "AudioPlayer:WifiLock")

player.addListener(object : Player.Listener {
    override fun onIsPlayingChanged(isPlaying: Boolean) {
        if (isPlaying) acquireWifiLock() else releaseWifiLock()
    }
})

private fun acquireWifiLock() {
    wifiLock?.let { if (!it.isHeld) it.acquire() }
}

private fun releaseWifiLock() {
    wifiLock?.let { if (it.isHeld) it.release() }
}
```

---

## 自启动恢复

### BootReceiver 实现

```kotlin
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED ||
            intent.action == "android.intent.action.QUICKBOOT_POWERON") {
            val serviceIntent = Intent(context, KeepAliveService::class.java)
            try {
                ContextCompat.startForegroundService(context, serviceIntent)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to start service on boot")
            }
        }
    }
}
```

### ManufacturerCompat 厂商兼容层

```kotlin
object ManufacturerCompat {
    fun getAutoStartIntent(): Intent? {
        val manufacturer = Build.MANUFACTURER.lowercase()
        return when {
            manufacturer.contains("xiaomi") -> Intent().apply {
                component = ComponentName(
                    "com.miui.securitycenter",
                    "com.miui.permcenter.autostart.AutoStartManagementActivity"
                )
            }
            manufacturer.contains("huawei") -> Intent().apply {
                component = ComponentName(
                    "com.huawei.systemmanager",
                    "com.huawei.systemmanager.startupmgr.ui.StartupNormalAppListActivity"
                )
            }
            manufacturer.contains("oppo") -> Intent().apply {
                component = ComponentName(
                    "com.coloros.safecenter",
                    "com.coloros.safecenter.startupapp.StartupAppListActivity"
                )
            }
            manufacturer.contains("vivo") -> Intent().apply {
                component = ComponentName(
                    "com.iqoo.secure",
                    "com.iqoo.secure.ui.phoneoptimize.BgStartUpManager"
                )
            }
            else -> null
        }
    }
}
```

---

## 进度保存和恢复

### LastPlayedStore

```kotlin
data class LastPlayed(val albumId: Int, val trackIndex: Int, val positionMs: Long = 0L)

object LastPlayedStore {
    private const val PREFS_NAME = "last_played"

    fun save(context: Context, albumId: Int, trackIndex: Int, positionMs: Long = 0L) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit()
            .putInt("album_id", albumId)
            .putInt("track_index", trackIndex)
            .putLong("position_ms", positionMs)
            .apply()
    }

    fun load(context: Context): LastPlayed {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return LastPlayed(
            prefs.getInt("album_id", -1),
            prefs.getInt("track_index", 0),
            prefs.getLong("position_ms", 0L)
        )
    }
}
```

### MainScreen 启动恢复

```kotlin
LaunchedEffect(Unit) {
    val lastPlayed = LastPlayedStore.load(context)
    if (lastPlayed.albumId != -1) {
        val tracks = loadTracksForAlbum(lastPlayed.albumId)
        playerViewModel.restoreLastPlayed(tracks, lastPlayed.trackIndex, lastPlayed.positionMs)
    }
}
```

---

## 数据持久化

### Retrofit 配置

```kotlin
object RetrofitClient {
    private const val BASE_URL = "https://a.yunshangzhiai7.top/"

    val apiService: ApiService by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(createOkHttpClient())
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(ApiService::class.java)
    }

    private fun createOkHttpClient(): OkHttpClient {
        return OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .addInterceptor(HttpLoggingInterceptor().apply {
                level = HttpLoggingInterceptor.Level.BASIC
            })
            .build()
    }
}

interface ApiService {
    @GET("player/api/albums")
    suspend fun getAlbums(): ApiResponse<List<Album>>

    @GET("player/api/albums/{id}")
    suspend fun getAlbumById(@Path("id") id: Int): ApiResponse<Album>

    @GET("player/api/music/album/{albumId}")
    suspend fun getMusicByAlbum(@Path("albumId") albumId: Int): ApiResponse<List<Music>>
}
```

---

## 技术栈和配置

### 关键依赖版本

| 依赖 | 版本 |
|------|------|
| Kotlin | 2.0.21 |
| Compose | 2024.06.00 |
| Media3 | 1.3.1 |
| Retrofit | 2.11.0 |
| OkHttp | 4.12.0 |
| Coroutines | 1.8.1 |

### AndroidManifest.xml 权限

```xml
<uses-permission android:name="android.permission.INTERNET" />
<uses-permission android:name="android.permission.FOREGROUND_SERVICE" />
<uses-permission android:name="android.permission.FOREGROUND_SERVICE_MEDIA_PLAYBACK" />
<uses-permission android:name="android.permission.FOREGROUND_SERVICE_SPECIAL_USE" />
<uses-permission android:name="android.permission.WAKE_LOCK" />
<uses-permission android:name="android.permission.POST_NOTIFICATIONS" />
<uses-permission android:name="android.permission.REQUEST_IGNORE_BATTERY_OPTIMIZATIONS" />
<uses-permission android:name="android.permission.RECEIVE_BOOT_COMPLETED" />
<uses-permission android:name="android.permission.ACCESS_WIFI_STATE" />
<uses-permission android:name="android.permission.CHANGE_WIFI_STATE" />
```

---

## 总结

本 Android 播放器实现通过以下核心技术确保了夜间后台播放的稳定性：

1. **ExoPlayer + Media3** - 现代化播放引擎，支持本地 LRU 缓存和 MediaSession 系统集成
2. **双层前台服务** - PlayerService + KeepAliveService 确保进程优先级不被系统杀死
3. **WiFi 锁** - 防止 WiFi 进入省电模式导致播放中断
4. **Boot 自启动** - 设备重启后自动恢复，支持多种国产 ROM 厂商
5. **进度持久化** - SharedPreferences 记录播放进度，App 重启自动恢复

这套方案经过生产验证，可作为其他音乐/视频播放类应用的参考实现。

---

**Last Updated**: 2024-03  
**Platform**: Android 24+  
**Language**: Kotlin 2.0.21 (K2)

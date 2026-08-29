# 音乐播放器

> **何时阅读**：当你要修改播放控制逻辑、播放队列管理、后台播放服务、WiFi 锁管理、或调整 MiniPlayer 显示时，阅读此文件。

## 功能概述

播放功能采用**单 ExoPlayer 架构**，由以下层次组成：

1. **MusicPlayerService** — 后台前台服务，持有唯一的 ExoPlayer 实例 + MediaSession（系统媒体控制）+ WiFi 锁 + 缓存管理
2. **MediaControllerManager** — 单例管理器，UI 通过 MediaController 连接到 Service 的 ExoPlayer
3. **PlayerViewModel** — 全局播放状态（通过 MediaController 控制播放）+ 进度轮询 + 进度保存/恢复
4. **AudioCache** — LRU 缓存，自动使用 60% 可用磁盘空间存储音频
5. **LastPlayedStore** — SharedPreferences，保存播放进度（专辑 ID、曲目索引、播放位置）
6. **KeepAliveService + BootReceiver** — 双层前台保活 + 设备重启自启动

## 关键文件

| 文件 | 职责 |
|------|------|
| `player/MusicPlayerService.kt` | MediaSessionService 前台服务，管理 ExoPlayer、MediaSession、WiFi 锁、缓存 |
| `player/MediaControllerManager.kt` | 单例，异步连接 MusicPlayerService，管理 MediaController 生命周期 |
| `viewmodel/PlayerViewModel.kt` | 全局播放状态，通过 MediaController 控制播放，定期保存进度 |
| `player/AudioCache.kt` | LRU 缓存单例，占用可用磁盘 60%，自动驱逐最旧数据 |
| `player/LastPlayedStore.kt` | SharedPreferences 封装，保存/加载最后播放的专辑、曲目、位置 |
| `service/KeepAliveService.kt` | 独立前台服务，与 MusicPlayerService 双层保活进程优先级 |
| `receiver/BootReceiver.kt` | BroadcastReceiver，BOOT_COMPLETED 后启动 KeepAliveService |
| `receiver/ManufacturerCompat.kt` | 工具类，获取小米/华为/OPPO/VIVO 自启动管理页面的 Intent |
| `ui/components/MiniPlayer.kt` | 底部迷你播放器 UI，通过 StateFlow 实时显示播放状态 |

## 新架构：单 ExoPlayer + MediaController

### 架构图

```
┌──────────────────────────────────────────┐
│   MusicPlayerService (Service)           │
│  ┌────────────────────────────────────┐  │
│  │ ExoPlayer (唯一实例)                │  │
│  │ + MediaSession                     │  │
│  │ + WiFi Lock                        │  │
│  │ + AudioCache (CacheDataSource)     │  │
│  └────────────────────────────────────┘  │
└────────────┬──────────────────────────────┘
             │ MediaSession 注册
             │
    ┌────────┴──────────┐
    │                   │
┌───▼────────────┐  ┌──▼──────────────┐
│ PlayerViewModel│  │ 系统媒体控制      │
│  (通过         │  │ 锁屏/通知栏/蓝牙  │
│ MediaController)  └──────────────────┘
│  控制播放      │
│  + StateFlow   │
│  + 进度轮询    │
│  + 进度保存    │
└────────────────┘
        │ collectAsState()
        ▼
    UI Composable
   (MiniPlayer等)
```

### 关键流程

#### 1. 播放歌曲 - playSong()

```
UI 点击歌曲
    │
    ▼
playerViewModel.playSong(song, queue)
    │
    ├─ 验证 MediaController 已连接
    ├─ 将 queue 转为 MediaItem 列表（含 MediaMetadata + 封面 URI）
    ├─ mediaController.setMediaItems(items, startIndex, 0L)
    ├─ mediaController.prepare() + mediaController.play()
    ├─ PrefsManager.addRecentId(song.id)
    │
    ▼
Service 播放
    ├─ ExoPlayer 开始播放
    ├─ WiFi Lock 自动获取
    ├─ MediaSession 更新元数据 → 系统媒体通知栏显示
    │
    ▼
PlayerViewModel 轮询进度
    ├─ 每 500ms 从 MediaController 读 currentPosition
    ├─ 每 5 秒保存一次进度到 LastPlayedStore
    │
    ▼
UI StateFlow 更新
    └─ MiniPlayer 显示歌曲名、进度条等
```

#### 2. App 重启 - 恢复进度

```
MainActivity.onCreate()
    │
    ├─ 启动 MusicPlayerService（ExoPlayer 单例启动）
    ├─ 启动 KeepAliveService（保活）
    │
    ▼
PlayerViewModel.init()
    │
    └─ connectToService()
       ├─ 延迟 100ms 确保 Service 已启动
       ├─ MediaControllerManager.connect() → MediaController 连接成功
       │
       ▼
       tryRestoreLastPlayed()（可选）
       ├─ LastPlayedStore.load() 获取上次专辑/曲目/位置
       ├─ 异步加载该专辑歌曲列表
       ├─ playerViewModel.restoreLastPlayed(tracks, idx, pos)
       │  ├─ setMediaItems(items, idx, pos)
       │  ├─ prepare()（不调用 play，等用户点击）
       │
       ▼
       UI 恢复（可选显示恢复提示）
```

#### 3. 后台保活

```
KeepAliveService（独立前台）+ MusicPlayerService（播放前台）
    │
    └─ 双层前台通知栏 → 进程优先级 IMPORTANCE_HIGH
       ├─ 不会被随意杀死
       ├─ 音乐继续播放（用户按 Home 或切后台）
       │
       ▼
设备重启
    │
    └─ BootReceiver 捕捉 BOOT_COMPLETED
       ├─ 启动 KeepAliveService
       ├─ 系统自动启动 MusicPlayerService（如 onStartCommand 返回 START_STICKY）
       │
       ▼
App 启动时 tryRestoreLastPlayed() 恢复进度
```

## PlayerViewModel 状态与方法

```kotlin
// 状态（StateFlow）
val currentSong: StateFlow<Song?>          // 当前播放歌曲（通过 MediaItem 元数据维护）
val isPlaying: StateFlow<Boolean>          // 播放/暂停
val queue: StateFlow<List<Song>>           // 当前队列
val currentPosition: StateFlow<Long>       // 当前播放位置（毫秒）
val duration: StateFlow<Long>              // 总时长（毫秒）
val isBuffering: StateFlow<Boolean>        // 缓冲中

// 控制方法
fun playSong(song: Song, queue: List<Song> = listOf(song))
    // 设置队列并开始播放，包含元数据（标题、艺术家、封面 URI）

fun restoreLastPlayed(tracks: List<Song>, startIndex: Int, positionMs: Long)
    // 从上次进度恢复，不自动播放

fun togglePlayPause()                      // 切换播放/暂停
fun skipNext()                             // 下一首
fun skipPrev()                             // 上一首
fun seekTo(positionMs: Long)               // 快进/快退
```

## MediaMetadata 元数据映射

创建 MediaItem 时，必须设置完整的 MediaMetadata，以便系统媒体通知栏、锁屏、蓝牙设备正确显示：

```kotlin
MediaItem.Builder()
    .setUri(track.url)
    .setMediaMetadata(
        MediaMetadata.Builder()
            .setTitle(track.title)              // 歌曲名
            .setArtist(track.artist)            // 艺术家
            .setArtworkUri(Uri.parse(track.thumbnailBig))  // 封面 URI
            .build()
    )
    .build()
```

**必须设置字段**：`title`、`artist`、`artworkUri`，否则系统媒体控制无法正确显示。

## 缓存和进度保存

### AudioCache - LRU 音频缓存

- 位置：`context.cacheDir/audio_cache/`
- 大小：自动使用设备可用磁盘空间的 60%
- 驱逐策略：LRU（最近最少使用）
- 集成：MusicPlayerService 构建 CacheDataSource 时使用

### LastPlayedStore - 进度持久化

- 存储：SharedPreferences（`last_played`）
- 保存的数据：
  - `categoryId`：分类 ID（来自 `song.catId`，用于恢复时重建队列）
  - `trackIndex`：队列中的曲目索引
  - `positionMs`：播放位置（毫秒）
- 保存频率：每 5 秒一次（通过 PlayerViewModel 轮询）
- 恢复时机：App 启动时（MainActivity 的 `tryRestoreLastPlayed()`）

## 注意事项

1. **MediaController 连接延迟**
   - PlayerViewModel 在 init 时延迟 100ms 再连接，确保 Service 已启动
   - 如果立即调用 `playSong()`，会返回警告日志，播放不会执行

2. **playSong() 替换整个队列**
   - 不是追加，而是完全替换。如果只播放单曲，队列默认为 `listOf(song)`

3. **MediaMetadata 必须完整**
   - 缺少 `artworkUri` 会导致系统通知栏无法显示封面
   - 缺少 `title`/`artist` 会导致锁屏无法显示歌曲信息

4. **WiFi Lock 的自动管理**
   - 播放时自动获取，暂停/停止时自动释放
   - 需要权限 `CHANGE_WIFI_STATE`

5. **异常处理**
   - MediaController 连接失败会输出日志，UI 功能降级（不播放）
   - SessionToken 创建失败通常是 Manifest 未正确声明 MediaSessionService

6. **国产 ROM 自启动**
   - ManufacturerCompat 可获取跳转到系统自启动管理的 Intent
   - 需要用户手动点击「允许自启动」，程序无法自动配置

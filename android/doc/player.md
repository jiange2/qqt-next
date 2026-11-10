# 音乐播放器

> **何时阅读**：当你要修改播放控制逻辑、播放队列管理、后台播放服务、WiFi 锁管理、或调整 MiniPlayer 显示时，阅读此文件。

## 功能概述

播放功能采用**单 ExoPlayer 架构**，由以下层次组成：

1. **MusicPlayerService** — 后台前台服务，持有唯一的 ExoPlayer 实例 + MediaSession（系统媒体控制）+ WiFi 锁 + 缓存管理
2. **MediaControllerManager** — 单例管理器，UI 通过 MediaController 连接到 Service 的 ExoPlayer
3. **PlayerViewModel** — 全局播放状态（通过 MediaController 控制播放）+ 进度轮询 + 进度保存/恢复
4. **AudioCache** — LRU 被动缓存，占用不超过启动时刻的缓存预算（总容量预留制，ADR 0004）
5. **LastPlayedStore** — SharedPreferences，保存整队列快照 + 曲目索引 + 播放位置 + 队列来源（ADR 0010、ADR 0015）
6. **PlayerSettingsManager** — 播放设置单例：播放模式（按队列来源记忆，含「本地音乐」来源）/ 定时关闭（播放模式持久化，定时会话级）
7. **KeepAliveService + BootReceiver** — 双层前台保活 + 设备重启自启动
8. **本地音乐播放** — 本地歌曲以 `local:<指纹>` 为 ID、`content://` 为播放 URI，队列来源 `QueueSource.LOCAL`

## 关键文件

| 文件 | 职责 |
|------|------|
| `player/MusicPlayerService.kt` | MediaSessionService 前台服务，管理 ExoPlayer、MediaSession、WiFi 锁、缓存 |
| `player/MediaControllerManager.kt` | 单例，异步连接 MusicPlayerService，管理 MediaController 生命周期 |
| `viewmodel/PlayerViewModel.kt` | 全局播放状态，通过 MediaController 控制播放，定期保存进度 |
| `player/AudioCache.kt` | LRU 被动缓存单例，占用不超过启动时刻的缓存预算，自动驱逐最旧数据 |
| `player/SchemeRoutingDataSource.kt` | 按 scheme 分流播放数据源：file:// 与 content:// 直读本地，http(s) 走被动缓存 |
| `player/LastPlayedStore.kt` | SharedPreferences 封装，保存/加载队列快照、曲目索引、播放位置与队列来源 |
| `player/PlayerSettingsManager.kt` | 播放设置单例（StateFlow + SharedPreferences），UI 与 Service 共享；队列来源记忆（含本地音乐） |
| `player/QueueSource.kt` | 队列来源键空间：default / favourites / downloads / local / album:<id> / local_playlist:<id> |
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
playerViewModel.playSong(song, queue, sourceDescriptor)
    │
    ├─ 验证 MediaController 已连接
    ├─ PlayerSettingsManager.setSource(sourceDescriptor)（按来源落位播放模式记忆）
    ├─ 将 queue 转为 MediaItem 列表（含 MediaMetadata + 封面 URI）
    ├─ mediaController.setMediaItems(items, startIndex, 0L)
    ├─ mediaController.prepare() + mediaController.play()
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
       ├─ LastPlayedStore.load() 获取队列快照（整队列 + 索引 + 位置 + 来源）
       ├─ 落位队列来源的播放模式记忆（旧快照无来源字段按 default）
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
fun playSong(song: Song, queue: List<Song> = listOf(song), sourceDescriptor: String)
    // 设置队列并开始播放，包含元数据（标题、艺术家、封面 URI）；
    // sourceDescriptor 必传（QueueSource 的六键：default / favourites / downloads / local / album:<id> / local_playlist:<id>）

fun restoreLastPlayed(tracks: List<Song>, startIndex: Int, positionMs: Long)
    // 从队列快照恢复，不自动播放

fun refreshLocalSongDisplay(id: String, title: String, artist: String)
    // 本地歌曲元数据编辑后同步队列副本与当前歌的展示值；不重新装载、不打断播放

fun togglePlayPause()                      // 切换播放/暂停
fun skipNext()                             // 下一首
fun skipPrev()                             // 上一首
fun seekTo(positionMs: Long)               // 快进/快退
```

另有 `unavailableLocalSongId: StateFlow<String?>` / `consumeUnavailableLocalSong()`：本地文件播放失败的一次性信号，由导航层消费后把该行标为不可用并提示。

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

## 本地歌曲播放（来源、入口差异与失效）

本地歌曲由 `LocalSong.toSong()` 转成播放队列元素：ID 为 `local:<内容指纹>`，URL 为 SAF 的 `content://` document URI，标题 / 艺术家 / 时长取合并后的展示值，歌词字段为空。

- **队列来源**：本地音乐页点歌以当前完整歌曲列表为队列，本地专辑页点歌以整张专辑为队列，两者都用 `QueueSource.LOCAL`（`player` 键空间扩展，ADR 0015）。播放模式按这个来源单独记忆，与在线专辑等来源互不影响。
- **队列快照**：队列整体（含 `content://` URI 与来源）照常落盘；持久化授权还在时，冷启动恢复后可直接继续播放，不需要重新扫描或请求后端。授权被回收时恢复装载仍会失败，按下面的失效路径处理。
- **不发起网络请求**：本地歌曲不请求 `song_info`、不加载歌词、不上报访问事实、不做时长写回；MediaItem 不带 `EXTRA_SONG_ID`，Service 侧车机歌词同步也不会拉歌词。
- **入口差异**：全屏播放器只剩播放设置与「更多」（里面是「编辑信息」）；收藏、下载、加入歌单、评分、歌词页与刷新入口全部不出现。本地音乐页 / 本地专辑页的歌曲行也不提供在线列表行的评分 / 下载按钮。
- **元数据编辑回显**：编辑保存后调用 `refreshLocalSongDisplay()` 替换队列副本与当前歌的标题 / 艺术家，MiniPlayer 与全屏播放器立即更新；不重新装载 MediaItem，通知栏与车机文案等下次装载时刷新。
- **播放失败（文件被删 / 不可读 / 授权被回收）**：`onPlayerError` 遇到本地歌曲不进入在线歌的「错误自愈」流程，只把歌曲 ID 写入 `unavailableLocalSongId` 并停止重试；导航层消费后把该行标记为不可用并轻提示「文件不可用」。不自动重扫，其它歌曲照常可播；下次重新扫描时文件不在目录树里，自然从索引移除。

## 播放设置（定时关闭）

`PlayerSettingsManager` 单例（模式对齐 DownloadManager）持有 StateFlow：播放模式 / 定时状态，UI 直接 collect。播放模式按队列来源持久化到 SharedPreferences（`player_settings`，ADR 0015），定时关闭会话级（进程被杀即失效）。

入口：全屏播放器功能图标行第二位（双滑杆图标），M3 ModalBottomSheet；定时生效期间图标右上角小圆点角标。

倍速与均衡器已全链路移除（UI / PlayerSettingsManager / Service 接线，2026-09）：倍速回归 ExoPlayer 默认 1.0x，播放会话无 Equalizer 挂载，显式 audioSessionId 一并撤销；SharedPreferences 中残留的 `playback_speed` / `eq_preset_key` 键无读取方，不清理。

### 定时关闭生效路径

UI → `PlayerViewModel.startSleepTimer()` 协程计时（250ms tick），到点 `controller.pause()` 后撤销；「播完本曲」由 `onMediaItemTransition` 结算：AUTO（自然放完）→ 暂停，SEEK/PLAYLIST_CHANGED（手动切歌/点播新歌）→ 撤销定时。分钟模式剩余秒数写入单例供面板显示，Activity 重建自动续跑。档位：30 / 60 / 90 / 120 / 240 分钟 + 播完本曲 + 关闭。

## 缓存和进度保存

### AudioCache - LRU 音频缓存（被动缓存）

- 位置：`context.filesDir/audio_cache/`（从 cacheDir 迁入：部分厂商 ROM 会定期清理 cacheDir，ADR 0003）
- 大小：受缓存预算约束（启动时刻按总容量预留制快照，ADR 0004）
- 驱逐策略：LRU（最近最少使用）
- 集成：MusicPlayerService 构建 CacheDataSource 时使用；DownloadManager 也以它为下载上游（缓存升格，ADR 0003）

### 本地直读播放（已下载歌曲与本地歌曲）

已下载歌曲以 `file://` URI（`filesDir/downloads/{id}.mp3`）、本地歌曲以内容提供者 `content://` URI 构建 MediaItem：MusicPlayerService 通过 `SchemeRoutingDataSourceFactory` 按 scheme 分流，`file` 走 FileDataSource 直读本地文件、`content` 走 ContentDataSource 经 SAF 持久化授权直读，两者都离线可播、不写被动缓存；http(s) 走被动缓存 + 网络回源。URI 替换在 `PlayerViewModel.resolveUri()` 统一完成，scheme 判定收敛在 `SchemeRoutingDataSource.kt` 的 `isLocalDirectReadUri()` / `isLocalSongUri()`。

本地直读（`content://`）的字节不经过网络栈，打开失败即播放失败，不回退到网络分支：

- 不进被动缓存，进度条缓存染色按「整曲本地可得」显示
- 不上报访问事实与时长写回；MediaItem 不带 `EXTRA_SONG_ID`，车机歌词同步因此不拉歌词
- 通知/媒体卡片封面只有 `file://`（本地封面缓存）与 `http(s)` 两种加载路径，其余 scheme 直接失败，不落到 OkHttp
- 错误自愈（ADR 0011）只作用于媒体 host，`content://` 不触发缓存清理与重取

### LastPlayedStore - 队列快照持久化

- 存储：SharedPreferences（`last_played`）
- 保存的数据（`LastPlayedSnapshot`）：
  - `queue_json`：整个播放队列的 `Song` 快照（含本地歌曲的 `content://` URL）
  - `track_index`：队列中的曲目索引
  - `position_ms`：播放位置（毫秒），距曲尾不足 3 秒按 0 保存
  - `source_descriptor`：队列来源（ADR 0015），冷启动恢复时据此落位播放模式记忆
- 保存频率：切歌 / 暂停立即保存，另有每 5 秒轮询兜底
- 恢复时机：App 启动、MediaController 连接成功后静默恢复（`prepare` 不 `play`）；无快照或索引越界时完全静默
- 旧「category_id + track_index + position_ms」三键格式废弃不迁移

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

# 音乐播放器

> **何时阅读**：当你要修改播放控制逻辑、播放队列管理、切歌行为、后台播放服务，或调整 MiniPlayer 显示时，阅读此文件。

## 功能概述

播放功能由三个层次组成：`PlayerViewModel`（全局播放状态 + ExoPlayer 控制，贯穿整个 App）、`MusicPlayerService`（前台 MediaSession 服务，支持后台播放 + 系统媒体控制），以及 `MusicPlayer`（早期简单封装，已被 ViewModel 替代）。

## 关键文件

| 文件 | 职责 |
|------|------|
| `viewmodel/PlayerViewModel.kt` | 全局播放状态（currentSong / isPlaying / queue），playSong() 设置队列并播放 |
| `player/MusicPlayerService.kt` | MediaSessionService 前台服务，Android 系统媒体控制（锁屏/通知栏） |
| `player/MusicPlayer.kt` | ExoPlayer 简单封装（早期版本，当前 ViewModel 未直接使用） |
| `ui/components/MiniPlayer.kt` | 底部迷你播放器 UI 组件，收集 PlayerViewModel 的 StateFlow |
| `MainActivity.kt` | `by viewModels()` 创建 PlayerViewModel，向下传递给所有页面 |

## 播放调用链

```
用户点击歌曲（SongListItem / BannerCarousel 等）
        │
        ▼
playerViewModel.playSong(song, queue)
        │  设置 _queue + _currentSong
        │  player.setMediaItems(queue.map { MediaItem.fromUri(it.url) }, startIndex, 0L)
        │  player.prepare() → player.play()
        │  PrefsManager.addRecentId(song.id)   ← 写入最近播放记录
        ▼
ExoPlayer.Listener 回调
  onIsPlayingChanged → _isPlaying.value = playing
  onMediaItemTransition → _currentSong.value = queue[currentMediaItemIndex]
        │
        ▼
MiniPlayer 等 Composable 通过 collectAsState() 响应更新
```

## PlayerViewModel 状态与方法

```kotlin
// 状态（StateFlow）
val currentSong: StateFlow<Song?>      // 当前播放歌曲
val isPlaying: StateFlow<Boolean>      // 播放/暂停状态
val queue: StateFlow<List<Song>>       // 当前播放队列

// 控制方法
fun playSong(song: Song, queue: List<Song> = listOf(song))  // 播放（替换整个队列）
fun togglePlayPause()                   // 切换播放/暂停
fun skipNext()                          // 下一首（player.seekToNextMediaItem()）
fun skipPrev()                          // 上一首（player.seekToPreviousMediaItem()）
```

`PlayerViewModel` 是 `AndroidViewModel`，持有 `ExoPlayer` 实例，在 `onCleared()` 中释放（`player.release()`）。

## MusicPlayerService

`MusicPlayerService` 继承自 `MediaSessionService`，在 `onCreate()` 中创建独立的 `ExoPlayer` + `MediaSession`，供系统媒体控制（锁屏通知栏按钮）使用。

> **当前架构说明**：`PlayerViewModel` 和 `MusicPlayerService` 各自持有独立的 ExoPlayer 实例，目前并未绑定。如果需要实现真正的后台播放（App 退出后继续播放），需要将 `PlayerViewModel` 改为连接到 `MusicPlayerService` 的 ExoPlayer（通过 `MediaController`）。

## MiniPlayer 组件

`MiniPlayer.kt` 通过 `collectAsState()` 收集 `playerViewModel.currentSong` 和 `playerViewModel.isPlaying`：

- `currentSong == null` 时隐藏（高度为 0）
- 显示：圆形缩略图（Coil）+ 歌曲名 + 艺术家名 + 上一首/播放暂停/下一首按钮
- 点击按钮直接调用 `playerViewModel` 方法

## 注意事项

- `playSong()` 会**替换整个队列**，不是追加。如果只播放单曲，`queue` 参数默认为 `listOf(song)` 
- `PrefsManager.addRecentId(song.id)` 在每次 `playSong()` 时调用，最多保留 50 条记录
- ExoPlayer 队列索引（`player.currentMediaItemIndex`）与 `_queue.value` 的索引一一对应，`playSong()` 中通过 `list.indexOfFirst { it.id == song.id }` 确定起始位置
- `MusicPlayer.kt` 目前未被主流程使用，保留作为低级封装参考，可安全忽略

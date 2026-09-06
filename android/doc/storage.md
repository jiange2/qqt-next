# 本地存储

> **何时阅读**：当你要修改最近播放记录逻辑、已下载歌曲的存储/清理，或需要持久化新的本地数据时，阅读此文件。

## 功能概述

`PrefsManager` 是 App 唯一的本地持久化模块，基于 `SharedPreferences` + Gson 序列化。管理两类数据：最近播放歌曲 ID 列表（最多 50 条，每次 `playSong()` 自动写入），以及已下载歌曲的完整 `Song` 对象列表（由 `DownloadManager` 整表驱动，见下文）。

## 关键文件

| 文件 | 职责 |
|------|------|
| `data/local/PrefsManager.kt` | SharedPreferences 单例封装：最近播放 ID、已下载歌曲快照 |
| `download/DownloadManager.kt` | 主动缓存（下载）管理器：串行下载队列、下载列表状态、与磁盘对账 |
| `MainActivity.kt` | `PrefsManager.init(applicationContext)` — 必须在使用前调用 |

## 数据结构

```
SharedPreferences name: "qqt_music_prefs"

Key: "recent_song_ids"   → JSON 序列化的 List<String>（歌曲 ID 字符串列表，最多 50 条）
Key: "downloaded_songs"  → JSON 序列化的 List<Song>（已下载歌曲快照，由 DownloadManager 整表写入）
```

## 最近播放

```kotlin
// 写入（每次 playerViewModel.playSong() 时自动调用）
PrefsManager.addRecentId(song.id)
// 内部逻辑：去重 + 插入头部 + 截断到 50 条

// 读取（RecentViewModel 中使用）
PrefsManager.getRecentIdsString()   // 返回逗号分隔的 ID 串，传给 API
PrefsManager.getRecentIds()         // 返回 List<String>
```

### 最近播放的查询流程

1. `PrefsManager.getRecentIdsString()` → `"123,456,789,..."`
2. 传给 `MusicRepository.getRecentSongs(songIds)` → API `get_recent_songs`
3. 后端根据 ID 查 `tbl_mp3`，返回完整歌曲信息

## 已下载歌曲

下载列表的事实来源是 `DownloadManager`（内存 StateFlow），`PrefsManager` 只负责持久化快照：

```kotlin
DownloadManager.init(context)              // MainActivity.onCreate 中调用（在 PrefsManager.init 之后）
DownloadManager.enqueue(song)              // 入队下载（串行），以被动缓存为上游补齐缺失字节
DownloadManager.deleteDownload(songId)     // 删除：取消进行中任务/删文件/移出列表
DownloadManager.downloadedSongs            // StateFlow<List<Song>>，DownloadScreen 直接收集
DownloadManager.activeDownloads            // StateFlow<Map<String, ActiveDownload>>：排队中/下载中(进度)
DownloadManager.localUri(songId)           // 已下载返回本地 file:// URI，未下载返回 null
```

- 完整文件位置：`filesDir/downloads/{songId}.mp3`，下载中为 `{songId}.mp3.part`（断点续传依据）
- init 时列表与磁盘对账：文件缺失的条目剔除、孤儿完整文件删除、`.part` 半文件保留
- 点"下载"不调用后端下载计数（ADR 0003）

## 初始化

`PrefsManager` 是 `object`（Kotlin 单例），需要在使用前调用 `init(context)` 注入 `Context`：

```kotlin
// MainActivity.onCreate()
override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    PrefsManager.init(applicationContext)  // 必须在任何 PrefsManager 调用之前
    ...
}
```

## 注意事项

- `PrefsManager` 本身未使用 Flow/LiveData；已下载歌曲的实时响应由 `DownloadManager` 的 StateFlow 提供，DownloadScreen 直接收集，不再读 PrefsManager
- 进程被杀后进行中的下载不会自动恢复（无持久化队列），残留 `.part` 文件由下次点"下载"断点续传
- 已下载歌曲存的是 `Song` 对象快照（序列化时的完整数据），后端数据更新后下载列表不会自动同步（如歌曲 URL 变更）
- `MAX_RECENT = 50`，超出时截断旧记录（保留最新的 50 条）
- 已下载列表防重复逻辑在 `DownloadManager.finalizeDownload()` 中（`listOf(song) + songs.filterNot { it.id == song.id }`），基于 `Song.id` 去重

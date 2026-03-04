# 本地存储

> **何时阅读**：当你要修改最近播放记录逻辑、已下载歌曲的存储/清理，或需要持久化新的本地数据时，阅读此文件。

## 功能概述

`PrefsManager` 是 App 唯一的本地持久化模块，基于 `SharedPreferences` + Gson 序列化。管理两类数据：最近播放歌曲 ID 列表（最多 50 条，每次 `playSong()` 自动写入），以及已下载歌曲的完整 `Song` 对象列表。

## 关键文件

| 文件 | 职责 |
|------|------|
| `data/local/PrefsManager.kt` | SharedPreferences 单例封装：最近播放 ID、已下载歌曲 |
| `MainActivity.kt` | `PrefsManager.init(applicationContext)` — 必须在使用前调用 |

## 数据结构

```
SharedPreferences name: "qqt_music_prefs"

Key: "recent_song_ids"   → JSON 序列化的 List<String>（歌曲 ID 字符串列表，最多 50 条）
Key: "downloaded_songs"  → JSON 序列化的 List<Song>（完整 Song 对象列表）
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

```kotlin
PrefsManager.saveDownloadedSong(song)    // 保存（防重复：跳过已有相同 id 的歌曲）
PrefsManager.getDownloadedSongs()        // 读取全部（按保存时间倒序）
PrefsManager.removeDownloadedSong(songId) // 删除指定歌曲
```

`DownloadScreen` 通过 `remember { PrefsManager.getDownloadedSongs() }` 读取，**初始化一次，不响应后续变化**。如果需要实时响应（如下载完成立即显示），需改为 StateFlow 或 MutableState。

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

- `PrefsManager` 未使用 Flow/LiveData，数据变更不会自动通知 UI；`DownloadScreen` 用 `remember` 只读取一次，如需动态更新需重构为 StateFlow
- 已下载歌曲存的是 `Song` 对象快照（序列化时的完整数据），后端数据更新后下载列表不会自动同步（如歌曲 URL 变更）
- `MAX_RECENT = 50`，超出时截断旧记录（保留最新的 50 条）
- `getDownloadedSongs()` 中防重复逻辑：`if (songs.none { it.id == song.id })`，基于 `Song.id` 去重

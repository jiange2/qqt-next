# 本地存储

> **何时阅读**：当你要修改本地音乐索引 / 元数据覆盖层、已下载歌曲的存储与清理、队列快照或其它本机持久化数据时，阅读此文件。

## 功能概述

本机持久化分四类：

1. **SharedPreferences（`PrefsManager`）** — 收藏 ID、我的歌单快照、已下载歌曲快照、设备 ID、已评记录
2. **本地音乐落盘（`LocalMusicStore`）** — 扫描索引与元数据覆盖层各一个 App 私有 JSON 文件
3. **本地专辑封面缓存（`LocalCoverCache`）** — 按专辑键缓存的封面图片，容量有上限
4. **队列快照（`LastPlayedStore`）** — 上次播放的整队列 + 索引 + 进度 + 队列来源（见 [player.md](player.md)）

「最近播放」已整体下线：不再有最近播放 ID 列表、页面与播放写入点；升级后首次启动由 `PrefsManager.init` 清除旧版残留键 `recent_song_ids`（写一次性标记，之后不再重复写盘）。

## 关键文件

| 文件 | 职责 |
|------|------|
| `data/local/PrefsManager.kt` | SharedPreferences 单例封装：收藏、我的歌单、已下载快照、设备 ID、已评记录 |
| `data/local/LocalMusicStore.kt` | 本地音乐索引与覆盖层的 JSON 落盘，原子写入与版本降级 |
| `data/local/LocalCoverCache.kt` | 本地专辑封面缓存（文件夹封面 → 内嵌图），容量上限与淘汰 |
| `download/DownloadManager.kt` | 主动缓存（下载）管理器：串行下载队列、下载列表状态、与磁盘对账 |
| `player/LastPlayedStore.kt` | 队列快照：保存/加载上次播放的队列、索引、进度与来源 |
| `MainActivity.kt` | `PrefsManager.init(applicationContext)` — 必须在使用前调用 |

## SharedPreferences（`PrefsManager`）

```
SharedPreferences name: "qqt_music_prefs"

Key: "favourite_song_ids"  → JSON 序列化的 List<String>（收藏 ID，最新在前，无上限）
Key: "local_playlists"     → JSON 序列化的 List<LocalPlaylist>（事实来源是 LocalPlaylistStore）
Key: "downloaded_songs"    → JSON 序列化的 List<Song>（事实来源是 DownloadManager）
Key: "device_id"           → 随机 UUID（首次需要时生成）
Key: "rated_songs"         → JSON 序列化的 Map<String, Int>（歌曲 ID → 星级）
```

`PrefsManager` 是 `object`（Kotlin 单例），使用前需调用 `init(context)` 注入 `Context`：

```kotlin
// MainActivity.onCreate()
override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    PrefsManager.init(applicationContext)  // 必须在任何 PrefsManager 调用之前
    ...
}
```

## 本地音乐：索引与覆盖层

两个文件都位于 `filesDir`，均带版本号；版本不认识或内容读不出来时按「无法识别」降级，不猜测迁移、不崩溃。

| 文件 | 内容 |
|------|------|
| `local_music_index.json` | 根目录列表 + 每首歌的身份、定位、标签快照与可用状态 |
| `local_music_overlay.json` | 身份（内容指纹）→ 用户改过的字段，只存非空值 |

索引的歌曲字段：`fingerprint`、`treeUri`、`documentUri`、`relativePath`、`displayName`、`folderName`、`size`、`lastModified`、`durationMs`、标签快照（标题 / 艺术家 / 专辑 / 专辑艺术家 / 音轨号 / 年份）、`available`。

### 原子写

两个文件都走同一条写入路径：

1. 先写同目录下的 `<文件名>.tmp`
2. `fsync` 刷盘
3. `rename` 覆盖目标文件

进程在任一步被杀，重启读到的要么是上一次的完整状态、要么是这次写入的完整状态，不会半截；残留的 `.tmp` 在下次读取时清理。写入经同一把锁串行，扫描落盘与根目录落盘不会互相覆盖。全部读写都在 IO 线程。

### 启动加载

`LocalMusicViewModel` 在 IO 线程读回索引与覆盖层并合并，冷启动直接出列表，不在启动时扫描、不发任何网络请求：

- 索引不存在：按系统里的持久化授权重建根目录列表；有授权但没索引时自动扫一次
- 索引无法识别（版本不认识或内容损坏）：空列表降级 + 页面提示重新扫描，根目录仍从系统授权重建
- 展示值 = 覆盖层优先、缺失取标签快照；所有列表、播放器与专辑分组都走这一份合并结果（ADR 0020）
- 清空某字段等于从覆盖层删除该字段，回到文件标签值；索引重建与重扫都不动覆盖层

### 封面缓存

- 位置：`cacheDir/local_covers/`，文件名是专辑键的 SHA-256 + `.img`
- 来源优先级：歌曲所在文件夹的封面文件（`cover / folder / front / album`，`jpg / jpeg / png / webp`）→ 音频内嵌图 → 占位图
- 懒加载；总量上限 16MB，超出按最近使用淘汰；单张封面读取上限 8MB
- 重扫提交后清空；元数据编辑改了专辑名 / 专辑艺术家时，按新旧专辑键分别作废

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

## 注意事项

- `PrefsManager` 本身未使用 Flow/LiveData；收藏、歌单、下载的实时响应由各自 Store / Manager 的 StateFlow 提供
- 进程被杀后进行中的下载不会自动恢复（无持久化队列），残留 `.part` 文件由下次点"下载"断点续传
- 已下载歌曲存的是 `Song` 对象快照，后端数据更新后下载列表不会自动同步（如歌曲 URL 变更）
- 已下载列表防重复逻辑在 `DownloadManager.finalizeDownload()` 中（`listOf(song) + songs.filterNot { it.id == song.id }`），基于 `Song.id` 去重
- 本地音乐的索引与覆盖层是两个文件：重扫只替换索引，覆盖层按内容身份保留；移除根目录只从索引剔除歌曲，不删覆盖层

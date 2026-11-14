# 本地存储

> **何时阅读**：当你要修改本地音乐库快照 / 元数据覆盖层 / 文件夹排除、已下载歌曲的存储与清理、队列快照或其它本机持久化数据时，阅读此文件。

## 功能概述

本机持久化分三类：

1. **SharedPreferences（`PrefsManager`）** — 收藏 ID、我的歌单快照、已下载歌曲快照、设备 ID、已评记录
2. **本地音乐落盘（`LocalMusicStore`）** — 库快照（歌曲行 + 排除文件夹清单）与元数据覆盖层各一个 App 私有 JSON 文件
3. **队列快照（`LastPlayedStore`）** — 上次播放的整队列 + 索引 + 进度 + 队列来源（见 [player.md](player.md)）

本地音乐的专辑封面不落盘：取系统媒体库预抽取的专辑图，由图片加载器（Coil）直接加载与缓存（见下文「封面」）。

「最近播放」已整体下线：不再有最近播放 ID 列表、页面与播放写入点；升级后首次启动由 `PrefsManager.init` 清除旧版残留键 `recent_song_ids`（写一次性标记，之后不再重复写盘）。

## 关键文件

| 文件 | 职责 |
|------|------|
| `data/local/PrefsManager.kt` | SharedPreferences 单例封装：收藏、我的歌单、已下载快照、设备 ID、已评记录 |
| `data/local/LocalMusicLibrarySource.kt` | 本地音乐来源接缝：音频权限检查、按卷查询 `MediaStore.Audio.Media`、行归一化 |
| `data/local/LocalMusicStore.kt` | 本地音乐库快照与覆盖层的 JSON 落盘，原子写入与旧版本整份丢弃 |
| `data/local/LocalFolderExclusion.kt` | 文件夹排除黑名单：目录段前缀匹配与过滤（纯函数） |
| `data/local/LocalMusicDeleter.kt` | 本地歌曲删除接缝：按系统版本路由（30+ 系统删除框 / 29 RSE 授权 / ≤28 写入权限直接删）与结果归一（成功 / 失败 / 取消），不持有 Activity |
| `data/local/LocalDeleteRules.kt` | 删除纯规则：删除结果 → 整库列表整形（成功移除目标行、失败与取消原样）、是否需要 App 确认框（仅 ≤28） |
| `data/local/LocalMusicImporter.kt` | 本地音乐导入接缝：按系统版本路由（29+ MediaStore 插入自有行 / ≤28 直写公共 `Music/导入/` + 触发扫描）与结果归一（成功 / 失败，无取消态），重名加后缀不覆盖，不持有 Activity |
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

## 本地音乐：库快照与覆盖层

两个文件都位于 `filesDir`，均带版本号；版本不认识或内容读不出来时按「无法识别」降级，不猜测迁移、不崩溃。v2 起歌曲身份是媒体库行 ID——旧版本（v1，按内容指纹记身份）与新身份对不上，升级后首次读取即整份丢弃（删除文件、不迁移不报错；覆盖层数据量小、重编辑成本低，ADR 0022）；旧 SAF 目录授权残留不清理也不使用。

| 文件 | 内容 |
|------|------|
| `local_music_index.json` | 库快照：媒体库查询归一化出的歌曲行 + 排除文件夹清单 |
| `local_music_overlay.json` | 身份（媒体库行 ID，Android 10+ 带卷名限定）→ 用户改过的字段，只存非空值 |

歌曲行字段：`rowId`、`volume`、`contentUri`、`path`（Android 10+ 为 `RELATIVE_PATH`，低版本为 `DATA` 全路径）、`displayName`、`size`、`lastModified`、`durationMs`、`albumId`、标签快照（标题 / 艺术家 / 专辑 / 专辑艺术家 / 音轨号 / 年份）、`available`。

歌曲身份 = 行 ID（同卷内移动 / 改名文件行 ID 不变；文件删除后重加则行 ID 变化，覆盖层编辑随之丢失——已知取舍）。同一内容的多个副本各自成行，不做内容级去重。查询与权限口径见 `LocalMusicLibrarySource`：`IS_MUSIC != 0`（Android 10+ 附加 `IS_PENDING = 0`、11+ 附加 `IS_TRASHED = 0`），Android 10+ 用 `getExternalVolumeNames` 逐外置卷查询合并。

排除清单是一组文件夹路径，匹配语义为「该文件夹及其全部子文件夹」（目录段边界上的前缀命中，`Music` 不吃掉 `MusicX`）；被排除的行不进歌曲列表、不进专辑分组、不进播放队列候选。整库行（含被排除的）都在快照里——取消排除立即恢复，不需要重查媒体库。过滤在 `LocalFolderExclusion`，纯函数。

本地歌曲删除成功后，该行从整库快照里移除、整份快照按下面同一条原子写路径重写（临时文件 + rename，写失败只记日志、内存列表照常可用）；元数据覆盖层、排除清单与播放队列快照刻意不做连带清理——覆盖层按行 ID 记键，文件删后重加即新行 ID（见「注意事项」的已知取舍）。删除本身经 `LocalMusicDeleter` 分版本链路（系统删除框 / 系统授权 / 写入权限直接删），落盘由 `LocalMusicViewModel` 按删除结果整形后触发（纯函数 `LocalDeleteRules`）。

本地音乐导入（来源 App「用其他应用打开 / 分享」递出的音频，入口与意图胶水见 [ui.md](ui.md)）经唯一写侧接缝 `LocalMusicImporter` 复制落库到公共音乐目录 `Music/导入/`，UI 与 ViewModel 不直接碰 MediaStore 写入 API。分版本：Android 10+（API 29+）`MediaStore.Audio.Media` 插入自有行（`RELATIVE_PATH = "Music/导入/"`，`IS_PENDING = 1` 期间写入字节、写完置 0 发布；任何失败清理自有 pending 行、不留半截——整库查询的 `IS_PENDING = 0` 过滤也保证清理前半截不可见），App 拥有自己创建的行、不需要新权限；Android 9 及以下（≤28）直写公共 `Music/导入/`（临时文件 + rename，目标已存在则不覆盖，真并发竞态重列目录 + 重解析有限重试）后 `MediaScannerConnection.scanFile` 触发扫描（扫描触发是辅助步骤：失败只记日志仍算成功，系统周期扫描兜底收录）。≤28 在 API 23+ 未授予 `WRITE_EXTERNAL_STORAGE` 时直接失败、不弹运行时申请框——导入是单步 suspend 接缝、不镜像删除的两步动作机，且 ≤28 的 READ/WRITE 同属 STORAGE 权限组、授过读即有写（API 21–22 安装即授予），影响面极小。目标目录重名自动加后缀 `name (2).ext`（n 从 2 递增、保留扩展名）、不覆盖旧文件——29+ 按现有名精确匹配，≤28 外置存储文件系统大小写不敏感、重名解析也按大小写不敏感比较（`Song.mp3` 撞 `song.mp3` 同样加后缀而非失败）。来源展示名无扩展名（微信语音裸名等）时规范化补 `.mp3`——扩展名是入库提示（MediaStore 与扫描器按扩展名推 MIME、置 `IS_MUSIC`，缺了它导入「成功」却库里看不到），实际容器格式由播放器按字节嗅探，已有扩展名原样保留。每次导入生成新文件、新媒体库行，各自成行、不做内容级去重（ADR 0022）。导入成功触发静默刷新，整库重查自然带上导入歌、整份快照按下面同一条原子写路径重写（重启仍在）；元数据覆盖层与排除清单对导入歌零特殊处理——覆盖层仍按行 ID 记键（文件被外部删除后重导 = 新行 = 编辑丢失，与其他本地歌曲同一已知取舍），`Music/导入/` 照常出现在排除面板候选、可被排除。

元数据编辑（单首与批量）只重写覆盖层、不动库快照。单首保存与批量保存共用 ViewModel 的同一把 `editLock`，「换列表 → 写覆盖层」整条链串行、两次编辑不交错：单首的覆盖表更新在锁外先行，锁内只替换 `_songs` 该行并 `saveOverlay`；批量（`saveBatchMetadata`）把覆盖表更新也放进锁内——读当前覆盖表 → 纯函数 `batchAlbumCommit` 整表变换（只替换勾选行；留空字段保留原覆盖、与文件标签同值清覆盖、空壳删键；`available` 取快照本值；按展示标题重排）→ 覆盖表与 `_songs` 一次落位 → `saveOverlay` 整表一次原子写 → 提示「已修改 n 首」（n = 提交数）。批量不走 N 次单首保存（避免 N 次落盘与 N 次重排）；空壳删键口径与单首一致，`saveOverlay` 落盘前也过滤空条目。落盘失败只记日志、内存态已更新（见下「原子写」）。

### 原子写

两个文件都走同一条写入路径：

1. 先写同目录下的 `<文件名>.tmp`
2. `fsync` 刷盘
3. `rename` 覆盖目标文件

进程在任一步被杀，重启读到的要么是上一次的完整状态、要么是这次写入的完整状态，不会半截；残留的 `.tmp` 在下次读取时清理。写入经同一把锁串行，库刷新落盘与排除开关落盘、元数据编辑落盘不会互相覆盖。全部读写都在 IO 线程。

### 启动加载

`LocalMusicViewModel` 在 IO 线程读回快照与覆盖层并合并，冷启动直接出列表，不在启动时查媒体库、不发任何网络请求：

- 快照不存在（含旧版本被整份丢弃）：空列表；进入本地音乐页核对音频权限后静默刷新一次，毫秒级查询即得全库——没有「有授权没索引就自动开扫」的等待
- 展示值 = 覆盖层优先、缺失取媒体库标签快照；所有列表、播放器与专辑分组都走这一份合并结果（ADR 0020）
- 清空某字段等于从覆盖层删除该字段，回到媒体库标签值（单首面板口径；批量面板「留空 = 不改」，见上文编辑提交）；库刷新与排除开关切换都不动覆盖层

### 封面

- 专辑封面 = 系统媒体库预抽取的专辑图：按专辑成员行的 `ALBUM_ID` 构造 `content://media/<卷>/audio/albumart/<专辑 ID>`，图片加载器（Coil）直接加载与缓存
- App 不再落盘任何封面文件：旧的自管 LRU 封面缓存（`cacheDir/local_covers/`）已删除，残留目录不清理也不读取；文件夹里的 `cover.jpg` 类封面文件与音频内嵌图不再使用
- 系统没有该专辑的预抽取图时按文件不存在报错，UI 显示占位图

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
- 本地音乐的库快照与覆盖层是两个文件：刷新只替换快照，覆盖层按行 ID 身份保留；同卷内移动 / 改名文件行 ID 不变、编辑不丢，文件删除后重加则行 ID 变化、覆盖层编辑丢失（ADR 0022 已知取舍）

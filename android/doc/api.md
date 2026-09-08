# API 请求与数据模型

> **何时阅读**：当你要新增 API 调用、修改请求参数、添加数据模型字段，或调试网络请求时，阅读此文件。

## 功能概述

Android 端所有网络请求通过 `ApiClient`（Retrofit 单例）发出，统一 POST 到 `api.php`，请求体用 `buildData()` 生成（JSON → urlencode → base64）。`MusicRepository` 封装所有业务 API 方法，供各 ViewModel 调用。

## 关键文件

| 文件 | 职责 |
|------|------|
| `data/api/ApiClient.kt` | Retrofit 实例 + 域名→IP 故障切换拦截器 + `buildData()` 签名编码 + MD5 工具 |
| `data/api/ResponseSnapshotInterceptor.kt` | 响应快照拦截器（ADR 0012）：只读接口回写/回放，断网降级 |
| `data/api/ResponseSnapshotStore.kt` | 响应快照存储：cacheDir 专用目录，5MB 预算 + LRU |
| `data/api/BooleanAdapter.kt` | Gson TypeAdapter：将后端 `"0"`/`"1"` 字符串转换为 Boolean |
| `data/api/model/Song.kt` | 歌曲数据模型 |
| `data/api/model/Models.kt` | Artist、SearchResults、Album、Playlist、Category、Banner、HomeData |
| `data/repository/MusicRepository.kt` | 所有 API 方法（suspend fun），统一错误处理 |
| `data/local/NetworkMonitor.kt` | 断网检测：默认网络有效性 StateFlow，供快照拦截器与离线横幅共用 |
| `AppConfig.kt` | BASE_URL（主用域名）、FALLBACK_BASE_URL（回退 IP）、PACKAGE_NAME、SIGN_KEY 配置 |

## 请求编码流程

```kotlin
// ApiClient.buildData(params: Map<String, Any>): String
val salt = System.currentTimeMillis().toString()
val sign = md5("${AppConfig.SIGN_KEY}$salt")   // md5("viaviweb" + salt)
val merged = params + {package_name, salt, sign}
val json = gson.toJson(merged)
val urlEncoded = URLEncoder.encode(json, "UTF-8")
return Base64.encodeToString(urlEncoded.toByteArray(), Base64.NO_WRAP)
```

所有 `MusicRepository` 方法调用 `ApiClient.buildData(mapOf("method_name" to "xxx", ...))` 构造请求。

## MusicRepository 方法列表

| 方法 | method_name | 返回类型 | 关键参数 |
|------|-------------|---------|---------|
| `getHome(userId)` | `home` + `home_new` | `HomeData?` | `user_id`，见下方说明 |
| `getAlbums(page)` | `album_list` | `List<Album>` | `page` |
| `getCategories(page)` | `cat_list` | `List<Category>` | `page` |
| `getLatestSongs(page, userId)` | `latest` | `List<Song>` | `page`, `user_id` |
| `getAllSongs(page, userId)` | `all_songs` | `List<Song>` | `page`, `user_id` |
| `getCategorySongs(catId, page, userId)` | `cat_songs` | `List<Song>` | `cat_id`, `page`, `user_id` |
| `getAlbumSongs(albumId, userId)` | `album_songs` | `List<Song>` | `album_id`, `user_id`（整单返回，不分页） |
| `getFavorites(userId, page)` | `get_favourite_post` | `List<Song>` | `user_id`, `type`="song", `page` |
| `getRecentSongs(songIds, page, userId)` | `get_recent_songs` | `List<Song>` | `songs_ids`(逗号串), `page`, `user_id` |
| `searchSongs(query, page, userId)` | `song_search` | `List<Song>` | `search_text`, `search_type`="songs", `page`, `user_id`（歌曲结果页全量分页通道） |
| `searchAll(query, page, userId)` | `song_search` | `SearchResults?` | `search_text`, `page`, `user_id`（不带 `search_type` 走组合分支，一次返回歌曲/专辑/艺术家三段；歌曲段每页 10 条，后两段各 20 条截断不分页） |
| `getArtistSongs(artistName, page, userId)` | `artist_name_songs` | `List<Song>` | `artist_name`, `page`, `user_id`（按名字精确匹配，每页 10 条，id 倒序；重名艺术家共用一页） |
| `toggleFavourite(songId, userId)` | `favourite_post` | `Boolean` | `post_id`, `user_id`, `type`="song" |
| `getAppDetails()` | `app_details` | `AppUpdateInfo?` | 无（更新检查用，见下方说明） |

## 数据模型关键字段

### Song

```kotlin
data class Song(
    val id: String,           // 歌曲 ID
    val title: String,        // 标题（mp3_title）
    val url: String,          // 播放 URL（mp3_url）
    val thumbnailBig: String, // 大封面 URL（mp3_thumbnail_b）
    val thumbnailSmall: String, // 小封面 URL（mp3_thumbnail_s）
    val artist: String,       // 歌手文本（mp3_artist，非实体的逗号分隔字符串，区别于 Artist 实体）
    val isFavourite: Boolean, // 是否已收藏（依赖传入的 user_id）
    val lrcText: String,      // 内嵌 LRC 歌词（mp3_lrc_txt）
    val lrcUrl: String,       // 外部 LRC 文件 URL（mp3_lrc_url）
    val mp3Type: String,      // "local" | "youtube" | "external"
    val totalViews: String,   // 播放量（字符串形式）
)
```

### HomeData

```kotlin
data class HomeData(
    val banners: List<Banner>,         // 首页横幅列表（含歌曲列表）
    val latestAlbums: List<Album>,
    val trendingSongs: List<Song>,     // 热门歌曲（实际取自 home_new，见注意事项）
    val recentSongs: List<Song>,
)
```

### getHome 的双接口合并

`getHome()` 先发 `home` 请求（横幅/最新专辑必须用它，`home_new` 的横幅 `songs_list` 为空），再发 `home_new` 请求取 `trending_songs` 覆盖 `home` 的热门榜：服务端 `home` 的热门查询有缺陷（同一首歌多条周播放记录霸榜且只取老歌），而 `home_new` 返回近一个月无重复的榜单，与老版 App 表现一致。`home_new` 请求失败或榜单为空时，兜底用 `home` 的 trending 按歌曲 id 去重。

## App 更新检查

`MusicRepository.getAppDetails()` 调 `app_details` 拿后台的更新配置（`AppUpdateInfo`：`app_update_status`/`app_new_version`/`app_update_desc`/`app_redirect_url`/`cancel_update_status`）。注意 `app_details` 的 `ONLINE_MP3` 是**单元素数组**（旧契约 `array_push` 行为，backend-next 逐字复刻），客户端取首元素解析（兼容数组/对象两种形状，曾因误判 `isJsonObject` 导致更新弹窗永不出现）。`update/AppUpdateChecker.check()` 在 `MainActivity.onCreate` 中异步执行：后台开启更新开关、且后台 `app_new_version`（Double，按版本段比较）大于本机 versionName 时返回结果，由 `ui/components/AppUpdateDialog` 弹窗；`cancel_update_status` 非 `"true"` 时为强制更新（弹窗不可关闭）；点击更新用 `ACTION_VIEW` 打开 `app_redirect_url`。每个跳过分支都会打 `AppUpdateChecker` 标签的 logcat 日志（跳过原因），排查“不弹”先看日志。注意：`app_new_version` 是 Double，无法区分 1.1 与 1.10，后台发版避免用两位修订号。

## 响应快照（断网降级，ADR 0012）

浏览/搜索/阅读等发现域在断网时由「响应快照」兜底（播放域由主动/被动缓存与队列快照覆盖，不在此列）。实现位于 OkHttp 拦截器层，Repository 与 ViewModel 零改动：

- **为何不用 HTTP 缓存**：业务请求是单一 POST api.php 且 data 含动态 salt，同一业务的两次请求字节永不相同，HTTP 层缓存原理性不可行——拦截器解码 data 提取 `method_name` + 业务参数（剔除 package_name/salt/sign）作业务语义 key
- **白名单**：全部只读接口可回放；`app_details` 与全部写接口（评分/访问上报/时长写回）不拦不写
- **回放时机**：断网（NetworkMonitor 判定，含已连网未通过验证）时命中即回放；弱网发包失败（IOException，含 failover 主域/回退都失败后）兜底回放
- **回写口径**：仅 ONLINE_MP3 非空有效的响应落盘，空列表不覆盖既有快照；在线时始终发真实请求并回写覆盖，无 TTL
- **实体级特例**：`get_recent_songs` 的 key 含 ID 串（最近播放每多一首即变，整串缓存必然 miss），特殊化为按歌曲 ID 的实体级缓存，回放时按请求 ID 串顺序切片重组（调用方本就按传入顺序重排，合并结果等价）
- **歌词纳入**：`fetchText` 走同一 client，GET 文本按 URL 留档；封面图维持 Coil 默认磁盘缓存
- **存储**：`cacheDir/response_snapshot/`，5MB 独立预算 LRU（不并入音频缓存预算），用户不可感知不可管理
- **离线横幅**：断网时 AppNavigation 内容区顶部常显「当前离线，展示最近一次内容」（`ui/components/OfflineBanner.kt`）

## 错误处理

`MusicRepository` 中所有方法用 `try-catch` 包裹，异常时返回 `null` 或空列表，不向上抛出异常。ViewModel 需检查返回值是否为 null/empty 来判断是否加载失败并设置 error 状态。

## 注意事项

- `BooleanAdapter` 处理后端返回 `"0"`/`"1"` 整数字符串作为 Boolean 的情况，**必须**在 Gson 实例构建时注册（`ApiClient` 中已配置）
- 后端所有数值字段（`id`、`total_views` 等）均以字符串形式返回，`Song.totalCount()` 提供了一个统一取条数的辅助方法
- 修改 `BASE_URL` / `FALLBACK_BASE_URL` 后不需要任何其他代码改动：所有请求都通过 `ApiClient.retrofit` 发出；域名入口连接层失败（DNS 解析失败、连接被拒、连接超时）时由故障切换拦截器自动改用回退地址，进程内粘性、冷启动恢复域名优先（仓库 docs/adr/0009）
- `page` 参数从 `1` 开始（非 0-based）；列表页每页条数由后端 `tbl_settings.api_latest_limit` 控制；`album_songs` 例外——不分页，一次性返回全量（后端 2000 条兜底截断，仓库级 ADR 0007）

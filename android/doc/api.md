# API 请求与数据模型

> **何时阅读**：当你要新增 API 调用、修改请求参数、添加数据模型字段，或调试网络请求时，阅读此文件。

## 功能概述

Android 端所有网络请求通过 `ApiClient`（Retrofit 单例）发出，统一 POST 到 `api.php`，请求体用 `buildData()` 生成（JSON → urlencode → base64）。`MusicRepository` 封装所有业务 API 方法，供各 ViewModel 调用。

## 关键文件

| 文件 | 职责 |
|------|------|
| `data/api/ApiClient.kt` | Retrofit 实例 + `buildData()` 签名编码 + MD5 工具 |
| `data/api/BooleanAdapter.kt` | Gson TypeAdapter：将后端 `"0"`/`"1"` 字符串转换为 Boolean |
| `data/api/model/Song.kt` | 歌曲数据模型 |
| `data/api/model/Models.kt` | Artist、Album、Playlist、Category、Banner、HomeData |
| `data/repository/MusicRepository.kt` | 所有 API 方法（suspend fun），统一错误处理 |
| `AppConfig.kt` | BASE_URL、PACKAGE_NAME、SIGN_KEY 配置 |

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
| `getArtists(page)` | `artist_list` | `List<Artist>` | `page` |
| `getAlbums(page)` | `album_list` | `List<Album>` | `page` |
| `getPlaylists(page)` | `playlist` | `List<Playlist>` | `page` |
| `getCategories(page)` | `cat_list` | `List<Category>` | `page` |
| `getLatestSongs(page, userId)` | `latest` | `List<Song>` | `page`, `user_id` |
| `getAllSongs(page, userId)` | `all_songs` | `List<Song>` | `page`, `user_id` |
| `getCategorySongs(catId, page, userId)` | `cat_songs` | `List<Song>` | `cat_id`, `page`, `user_id` |
| `getAlbumSongs(albumId, page, userId)` | `album_songs` | `List<Song>` | `album_id`, `page`, `user_id` |
| `getPlaylistSongs(playlistId, page, userId)` | `playlist_songs` | `List<Playlist>` | `playlist_id`, `page`, `user_id` |
| `getFavorites(userId, page)` | `get_favourite_post` | `List<Song>` | `user_id`, `type`="song", `page` |
| `getRecentSongs(songIds, page, userId)` | `get_recent_songs` | `List<Song>` | `songs_ids`(逗号串), `page`, `user_id` |
| `searchSongs(query, page, userId)` | `song_search` | `List<Song>` | `search_text`, `search_type`="songs", `page`, `user_id` |
| `toggleFavourite(songId, userId)` | `favourite_post` | `Boolean` | `post_id`, `user_id`, `type`="song" |

## 数据模型关键字段

### Song

```kotlin
data class Song(
    val id: String,           // 歌曲 ID
    val title: String,        // 标题（mp3_title）
    val url: String,          // 播放 URL（mp3_url）
    val thumbnailBig: String, // 大封面 URL（mp3_thumbnail_b）
    val thumbnailSmall: String, // 小封面 URL（mp3_thumbnail_s）
    val artist: String,       // 艺术家名（mp3_artist）
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
    val latestArtists: List<Artist>,
    val trendingSongs: List<Song>,     // 热门歌曲（实际取自 home_new，见注意事项）
    val recentSongs: List<Song>,
)
```

### getHome 的双接口合并

`getHome()` 先发 `home` 请求（横幅/最新专辑/歌手必须用它，`home_new` 的横幅 `songs_list` 为空），再发 `home_new` 请求取 `trending_songs` 覆盖 `home` 的热门榜：服务端 `home` 的热门查询有缺陷（同一首歌多条周播放记录霸榜且只取老歌），而 `home_new` 返回近一个月无重复的榜单，与老版 App 表现一致。`home_new` 请求失败或榜单为空时，兜底用 `home` 的 trending 按歌曲 id 去重。

## 错误处理

`MusicRepository` 中所有方法用 `try-catch` 包裹，异常时返回 `null` 或空列表，不向上抛出异常。ViewModel 需检查返回值是否为 null/empty 来判断是否加载失败并设置 error 状态。

## 注意事项

- `BooleanAdapter` 处理后端返回 `"0"`/`"1"` 整数字符串作为 Boolean 的情况，**必须**在 Gson 实例构建时注册（`ApiClient` 中已配置）
- 后端所有数值字段（`id`、`total_views` 等）均以字符串形式返回，`Song.totalCount()` 提供了一个统一取条数的辅助方法
- 修改 `AppConfig.BASE_URL` 后不需要任何其他代码改动（所有请求都通过 `ApiClient.retrofit` 发出）
- `page` 参数从 `1` 开始（非 0-based）；列表页每页条数由后端 `tbl_settings.api_latest_limit` 控制，但 `album_songs` 每页固定 10 首（后端硬编码，不可调）

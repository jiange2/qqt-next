# QQT Music — Android 项目文档

> 初版记录（2026-03-01）。本文档汇总项目结构、技术栈、文件说明及后续开发注意事项。

---

## 目录

1. [技术栈](#技术栈)
2. [项目配置](#项目配置)
3. [目录结构](#目录结构)
4. [架构概览](#架构概览)
5. [数据层](#数据层)
6. [导航体系](#导航体系)
7. [UI 组件](#ui-组件)
8. [页面 & ViewModel](#页面--viewmodel)
9. [播放器](#播放器)
10. [主题 & 样式](#主题--样式)
11. [API 签名机制](#api-签名机制)
12. [待办 / 后续优化](#待办--后续优化)

---

## 技术栈

| 类别 | 依赖 | 版本 |
|------|------|------|
| Kotlin | kotlin-android | 2.0.0 |
| AGP | android-application | 8.5.2 |
| Gradle Wrapper | — | 8.7 |
| Compose BOM | androidx.compose:compose-bom | 2024.08.00 |
| Material3 | androidx.compose.material3 | (BOM) |
| Navigation Compose | androidx.navigation:navigation-compose | 2.7.7 |
| ViewModel Compose | androidx.lifecycle:lifecycle-viewmodel-compose | 2.8.4 |
| Material Icons Extended | androidx.compose.material:material-icons-extended | (BOM) |
| Media3 / ExoPlayer | media3-exoplayer / hls / dash / ui / session | 1.4.0 |
| Retrofit | com.squareup.retrofit2:retrofit | 2.11.0 |
| Retrofit Gson | com.squareup.retrofit2:converter-gson | 2.11.0 |
| OkHttp Logging | com.squareup.okhttp3:logging-interceptor | 4.12.0 |
| Coil | io.coil-kt:coil-compose | 2.7.0 |
| compileSdk | — | 35 |
| minSdk | — | 26 |

---

## 项目配置

### AppConfig.kt

```kotlin
object AppConfig {
    const val BASE_URL    = "http://10.0.2.2/qqt/backend/"  // 改为实际服务器地址
    const val PACKAGE_NAME = "com.qqt.music"
    const val SIGN_KEY    = "viaviweb"
}
```

- **BASE_URL**：模拟器默认指向宿主机 `10.0.2.2`，真机调试时替换为局域网 IP 或线上域名。
- **SIGN_KEY**：与后端约定的签名盐值，用于生成请求签名。

### AndroidManifest.xml 权限

```
INTERNET
FOREGROUND_SERVICE
FOREGROUND_SERVICE_MEDIA_PLAYBACK
```

---

## 目录结构

```
android/
├── build.gradle.kts                   # 根级构建脚本
├── settings.gradle.kts                # 模块注册 + 仓库配置
├── gradle/
│   ├── libs.versions.toml             # 版本目录（统一管理依赖版本）
│   └── wrapper/
│       ├── gradle-wrapper.jar
│       └── gradle-wrapper.properties
└── app/
    ├── build.gradle.kts               # 模块构建脚本 + 依赖声明
    └── src/main/
        ├── AndroidManifest.xml
        ├── res/
        │   ├── values/strings.xml     # app_name = "QQT Music"
        │   └── xml/network_security_config.xml
        └── java/com/qqt/music/
            ├── AppConfig.kt           # 全局常量
            ├── MainActivity.kt        # 入口 Activity
            ├── data/
            │   ├── api/
            │   │   ├── ApiClient.kt   # Retrofit 单例 + 签名逻辑
            │   │   ├── BooleanAdapter.kt  # Gson TypeAdapter（0/1 → Boolean）
            │   │   └── model/
            │   │       ├── Song.kt    # Song 数据类
            │   │       └── Models.kt  # Artist / Album / Playlist / Category / Banner / HomeData
            │   ├── local/
            │   │   └── PrefsManager.kt    # SharedPreferences（最近播放 / 已下载）
            │   └── repository/
            │       └── MusicRepository.kt # 所有 API 调用封装
            ├── player/
            │   ├── MusicPlayer.kt         # ExoPlayer 封装（早期版本，已被 PlayerViewModel 替代）
            │   └── MusicPlayerService.kt  # 前台 Service（mediaPlayback）
            ├── ui/
            │   ├── components/
            │   │   ├── TopBar.kt          # MusicTopBar（橙色顶栏）
            │   │   ├── BottomBar.kt       # MusicBottomBar（5 Tab + 中央 FAB）
            │   │   ├── DrawerContent.kt   # 侧边抽屉（ModalDrawerSheet）
            │   │   ├── MiniPlayer.kt      # 底部迷你播放条
            │   │   ├── SongListItem.kt    # 歌曲列表行
            │   │   └── BannerCarousel.kt  # 首页轮播（HorizontalPager + 自动滚动）
            │   ├── navigation/
            │   │   ├── Screen.kt          # 路由定义
            │   │   └── AppNavigation.kt   # ModalNavigationDrawer + Scaffold + NavHost
            │   ├── screens/
            │   │   ├── home/              # HomeScreen + HomeViewModel
            │   │   ├── artist/            # ArtistScreen + ArtistViewModel
            │   │   ├── album/             # AlbumScreen + AlbumViewModel
            │   │   ├── playlist/          # PlaylistScreen + PlaylistViewModel
            │   │   ├── mylist/            # MyListScreen（无 VM，本地列表）
            │   │   ├── favorites/         # FavoritesScreen（无独立 VM）
            │   │   ├── settings/          # SettingsScreen
            │   │   ├── recent/            # RecentScreen + RecentViewModel
            │   │   ├── download/          # DownloadScreen（无 VM）
            │   │   ├── category/          # CategoryScreen + CategoryViewModel
            │   │   └── latest/            # LatestScreen + LatestViewModel
            │   └── theme/
            │       ├── Color.kt           # 橙色调色板
            │       ├── Theme.kt           # QQTMusicTheme（固定橙色，不跟随系统 Dynamic Color）
            │       └── Type.kt
            └── viewmodel/
                └── PlayerViewModel.kt     # AndroidViewModel，管理 ExoPlayer 全局状态
```

---

## 架构概览

```
MainActivity
    └── AppNavigation (Compose)
            ├── ModalNavigationDrawer
            │       └── DrawerContent（6 个抽屉项）
            └── Scaffold
                    ├── TopBar：MusicTopBar
                    ├── BottomBar：MiniPlayer + MusicBottomBar
                    └── NavHost（11 个目的地）

State 管理：ViewModel (StateFlow) → Compose collectAsState()
网络请求：MusicRepository (suspend fun) → ApiClient (Retrofit)
本地存储：PrefsManager (SharedPreferences)
媒体播放：PlayerViewModel → ExoPlayer
```

---

## 数据层

### ApiClient.kt

- `Retrofit` 单例，`BASE_URL` 来自 `AppConfig`
- `buildData(action, params)` 生成签名请求体：
  1. 将 params JSON 序列化后 `URLEncode` 再 `Base64` 编码 → `data` 字段
  2. `sign = MD5("viaviweb" + salt)`（`salt` = Base64 后的字符串）
- OkHttp `HttpLoggingInterceptor`（DEBUG 模式下输出请求日志）

### BooleanAdapter.kt

Gson `TypeAdapter<Boolean>`，将后端返回的 `0`/`1` 整数及 `"true"`/`"false"` 字符串统一转换为 Kotlin `Boolean`。

### Models.kt / Song.kt

| 数据类 | 主要字段 |
|--------|---------|
| `Song` | id, title, artist, url, thumbnail, duration, views, downloads, isFavorite |
| `Artist` | id, name, image, songCount |
| `Album` | id, title, artist, cover, songCount |
| `Playlist` | id, title, cover, songCount |
| `Category` | id, name, image |
| `Banner` | id, image, url |
| `HomeData` | banners, trendingSongs, latestAlbums, latestArtists |

### MusicRepository.kt

| 方法 | API action | 说明 |
|------|------------|------|
| `getHome()` | `home_new` | 首页聚合数据 |
| `getArtists(page)` | `all_artist` | 歌手列表（分页） |
| `getAlbums(page)` | `all_album` | 专辑列表（分页） |
| `getPlaylists(page)` | `all_playlist` | 播放列表（分页） |
| `getCategories(page)` | `all_category` | 分类列表（分页） |
| `getLatestSongs(page)` | `all_songs` | 最新歌曲（分页） |
| `getFavorites(page)` | `favorites` | 收藏（分页） |
| `getRecentSongs(ids, page)` | `recent_songs` | 最近播放（传 ID 串） |
| `searchSongs(query, page)` | `search` | 搜索 |
| `toggleFavourite(songId)` | `toggle_favourite` | 收藏/取消 |

### PrefsManager.kt

- `addRecentId(id)` / `getRecentIdsString()` — 最近播放 ID 列表（最多 50 条），逗号分隔
- `saveDownloadedSong(song)` / `getDownloadedSongs()` — 已下载歌曲（Gson 序列化存储）

---

## 导航体系

### Screen.kt — 路由定义

| 路由 | 标题 | 位置 |
|------|------|------|
| `home` | 首页 | 底部导航 Tab 1 |
| `recent` | 最近播放 | 底部导航 Tab 2 |
| `download` | 我的下载 | 底部导航 Tab 3（中部 FAB） |
| `category` | 音乐分类 | 底部导航 Tab 4 |
| `latest` | 最新歌曲 | 底部导航 Tab 5 |
| `artist` | 音乐歌手 | 侧边抽屉 |
| `album` | 音乐专辑 | 侧边抽屉 |
| `playlist` | 播放列表 | 侧边抽屉 |
| `mylist` | 我的列表 | 侧边抽屉 |
| `favorites` | 歌曲收藏 | 侧边抽屉 |
| `settings` | 设置中心 | 侧边抽屉底部 |

### AppNavigation.kt

- `ModalNavigationDrawer` 包裹整个 Scaffold
- Settings 页面隐藏抽屉手势、显示返回按钮
- 底部导航仅在 5 个 Tab 路由上显示
- `MiniPlayer` 始终显示（有歌曲在播放时）

---

## UI 组件

### MusicTopBar（TopBar.kt）

橙色背景顶栏。左侧：汉堡菜单（或返回按钮）；右侧：搜索图标。

### MusicBottomBar（BottomBar.kt）

5 个 Tab（首页 / 最近 / 下载 / 分类 / 最新），中间"我的下载"为突出 FAB 样式。

### DrawerContent（DrawerContent.kt）

`ModalDrawerSheet` 宽 280dp，顶部橙色渐变头图（音符图标 + App 名称），下方 6 个导航项 + 底部"设置"。

### MiniPlayer（MiniPlayer.kt）

底部固定条：圆形专辑封面 + 歌曲名/歌手 + 上一首/播放暂停/下一首。收集 `PlayerViewModel.currentSong` / `isPlaying`。

### SongListItem（SongListItem.kt）

水平行：缩略图（80×80）+ 标题/歌手 + 星级评分 + 播放量/下载量 + 更多菜单按钮。

### BannerCarousel（BannerCarousel.kt）

`HorizontalPager` + `LaunchedEffect` 每 3 秒自动翻页 + 底部圆点指示器 + 右下角播放按钮。

---

## 页面 & ViewModel

### 首页 — HomeScreen / HomeViewModel

- 加载 `home_new` 接口聚合数据
- 布局：BannerCarousel → 热门歌曲横向 LazyRow → 最新专辑横向 LazyRow (2 行网格) → 歌手列表

### 最近播放 — RecentScreen / RecentViewModel

- 从 `PrefsManager` 读取最近播放 ID 串，调用 `getRecentSongs` 获取详情
- 空态：灰色文字提示

### 我的下载 — DownloadScreen（无 ViewModel）

- 直接读取 `PrefsManager.getDownloadedSongs()`，静态展示

### 音乐分类 — CategoryScreen / CategoryViewModel

- 2 列 `LazyVerticalGrid`，无限分页（滚动至底部自动加载）
- 点击分类跳转（TODO：分类详情页）

### 最新歌曲 — LatestScreen / LatestViewModel

- 分页 `LazyColumn`，自动加载下一页

### 音乐歌手 — ArtistScreen / ArtistViewModel

- 2 列网格，圆形头像，分页加载

### 音乐专辑 — AlbumScreen / AlbumViewModel

- 2 列网格，封面 + 播放按钮悬浮，分页加载

### 播放列表 — PlaylistScreen / PlaylistViewModel

- 2 列网格，封面 + 播放按钮悬浮，分页加载

### 我的列表 — MyListScreen（无 ViewModel）

- "添加播放列表" 按钮 + 本地列表展示（2×2 音符马赛克封面）

### 歌曲收藏 — FavoritesScreen（无独立 ViewModel）

- 空态：放大镜图标 + 刷新按钮
- 有数据时：分页歌曲列表

### 设置 — SettingsScreen

- 主题切换开关
- 评价 App / 分享 / 隐私政策 / 关于 等列表行（带右箭头）

---

## 播放器

### PlayerViewModel（viewmodel/PlayerViewModel.kt）

全局单例（在 `MainActivity` 通过 `by viewModels()` 创建），贯穿所有 Composable。

| StateFlow | 类型 | 说明 |
|-----------|------|------|
| `currentSong` | `Song?` | 当前播放歌曲 |
| `isPlaying` | `Boolean` | 播放/暂停状态 |
| `queue` | `List<Song>` | 当前播放队列 |

| 方法 | 说明 |
|------|------|
| `playSong(song, queue)` | 设置队列并播放，同时写入 PrefsManager 最近记录 |
| `togglePlayPause()` | 切换播放/暂停 |
| `skipNext()` | 下一首 |
| `skipPrev()` | 上一首 |

`ExoPlayer` 监听 `onIsPlayingChanged` 和 `onMediaItemTransition` 更新 StateFlow。

### MusicPlayerService（player/MusicPlayerService.kt）

前台 Service，`foregroundServiceType = mediaPlayback`，用于后台持续播放。当前版本为骨架，正式播放逻辑在 `PlayerViewModel` 中。

---

## 主题 & 样式

### Color.kt

```kotlin
val OrangePrimary   = Color(0xFFE8441C)   // 主色调（橙红）
val OrangeLight     = Color(0xFFFF6B47)   // 浅橙
val OrangeDark      = Color(0xFFC23515)   // 深橙
val OrangeContainer = Color(0xFFFFE4DE)   // 容器背景
```

### Theme.kt（QQTMusicTheme）

- 关闭 Dynamic Color（固定橙色主题，不跟随系统壁纸颜色）
- Light / Dark 均使用 `orangeColorScheme`

---

## API 签名机制

后端要求所有接口通过 POST 请求，Body 包含：

```
data  = Base64(URLEncode(JSON参数字符串))
sign  = MD5("viaviweb" + data)
```

`ApiClient.buildData(action, params)` 封装了以上逻辑，所有 Repository 方法调用此函数生成 Body。

---

## 待办 / 后续优化

- [ ] **BASE_URL 配置**：部署后修改 `AppConfig.BASE_URL` 为正式服务器地址
- [ ] **分类详情页**：点击 Category 卡片后跳转歌曲列表（路由 + 传参）
- [ ] **专辑 / 歌手详情页**：点击进入详情（歌曲列表 + 简介）
- [ ] **搜索功能**：TopBar 搜索图标目前 `onSearchClick = {}`，需接入 SearchScreen
- [ ] **用户登录**：`PrefsManager` 中预留了 token 存储，登录页面尚未实现
- [ ] **收藏功能**：`toggleFavourite` 已在 Repository 实现，SongListItem 的 ⋮ 菜单需完善
- [ ] **MusicPlayerService 集成**：将 ExoPlayer 迁移到 Service，支持锁屏通知控制
- [ ] **下载功能**：`PrefsManager.saveDownloadedSong` 已就绪，需在 SongListItem 对接下载逻辑
- [ ] **分页通用化**：多个 ViewModel 有相同的分页逻辑，可抽取基类 `PagingViewModel`
- [ ] **错误处理**：当前网络错误仅打印日志，需统一 Snackbar / 重试 UI
- [ ] **歌词 (LRC) 支持**：后端 `lrc/` 目录有歌词文件，播放页可接入同步歌词显示

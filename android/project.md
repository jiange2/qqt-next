# Android（倾轻听 App）

> **何时阅读**：当你需要在 Android 客户端添加功能、修改页面、调整播放逻辑、修改 API 请求或本地存储时，首先阅读此文件。

## 模块职责

Android 端是用 Kotlin + Jetpack Compose 编写的音乐播放应用。从后端 API 获取歌曲、专辑、艺术家、分类、播放列表等数据，通过 ExoPlayer 播放，支持后台播放（前台服务 + MediaSession），以及本地下载和最近播放记录。

## 技术栈

- Kotlin 2.0.0 + Jetpack Compose BOM 2024.08.00
- ExoPlayer / Media3 1.4.0（音频播放 + 后台 MediaSession 服务）
- Retrofit2 2.11.0 + OkHttp Logging（HTTP 请求）
- Gson（JSON 解析 + 自定义 BooleanAdapter）
- Coil 2.7.0（图片异步加载）
- Navigation Compose 2.7.7（页面路由）
- ViewModel + StateFlow（状态管理，MVVM 架构）
- SharedPreferences（本地持久化）
- compileSdk 35，minSdk 26（Android 8.0+）

## 目录结构

```
app/src/main/java/com/qqt/music/
  AppConfig.kt              # 全局配置：BASE_URL、PACKAGE_NAME、SIGN_KEY
  MainActivity.kt           # 应用入口，初始化 PrefsManager，创建 PlayerViewModel，挂载 AppNavigation
  data/
    api/
      ApiClient.kt          # Retrofit 单例 + buildData()（签名编码：json  urlencode  base64）
      BooleanAdapter.kt     # Gson TypeAdapter：兼容后端返回 "0"/"1" 字符串作为 Boolean
      model/
        Song.kt             # 歌曲数据模型（id/title/artist/url/thumbnail/views/downloads/isFavourite/lrc）
        Models.kt           # 其他模型：Artist、Album、Playlist、Category、Banner、HomeData
    local/
      PrefsManager.kt       # SharedPreferences 封装：最近播放 ID 列表（50条）、已下载歌曲列表（Gson序列化）
    repository/
      MusicRepository.kt    # 所有 API 方法封装（getHome/getArtists/搜索/收藏切换等）
  player/
    MusicPlayer.kt          # ExoPlayer 简单封装（早期低级封装，已被 PlayerViewModel 替代）
    MusicPlayerService.kt   # 前台播放服务（MediaSessionService，foregroundServiceType=mediaPlayback）
  viewmodel/
    PlayerViewModel.kt      # 全局播放状态（currentSong/isPlaying/queue），playSong()/skipNext() 等
  ui/
    navigation/
      Screen.kt             # 所有页面路由与标题定义（bottomNavRoutes、drawerTopRoutes）
      AppNavigation.kt      # NavHost + ModalNavigationDrawer + Scaffold（TopBar/BottomBar/MiniPlayer）
    screens/
      home/                 # HomeScreen.kt + HomeViewModel.kt（Banner/趋势歌曲/专辑/艺术家）
      latest/               # 最新歌曲分页列表
      recent/               # 最近播放（PrefsManager 读 ID  API 查详情）
      download/             # 已下载（纯本地，PrefsManager.getDownloadedSongs()）
      category/             # 分类 2列网格，无限分页
      artist/               # 艺术家 2列网格，分页
      album/                # 专辑 2列网格，分页
      playlist/             # 播放列表 2列网格，分页
      mylist/               # 我的列表（本地，暂无后端支持）
      favorites/            # 收藏歌曲（需 user_id）
      settings/             # 设置（主题切换/关于/隐私政策）
    components/
      BannerCarousel.kt     # HorizontalPager 轮播（3秒自动翻页 + 圆点指示器）
      BottomBar.kt          # 底部导航栏（5 Tab，中间下载为 FAB 样式）
      DrawerContent.kt      # 侧边抽屉（橙色头图 + 6个导航项 + 底部设置）
      MiniPlayer.kt         # 底部迷你播放器（封面+歌曲名+上/播/下控制）
      SongListItem.kt       # 歌曲列表行（缩略图+标题+艺术家+评分+下载按钮）
      TopBar.kt             # 顶部导航栏（菜单/返回 + 标题 + 搜索按钮）
    theme/                  # Material3 主题（OrangePrimary=#E8441C，固定橙色不跟随系统Dynamic Color）
```

## 功能模块索引

| 功能 | 文档 | 说明 |
|------|------|------|
| API 请求与数据模型 | [`doc/api.md`](doc/api.md) | ApiClient、MusicRepository、数据模型 |
| 音乐播放器 | [`doc/player.md`](doc/player.md) | PlayerViewModel、MusicPlayerService、队列管理 |
| UI 导航与页面 | [`doc/ui.md`](doc/ui.md) | AppNavigation、Screen 路由、所有屏幕、公共组件 |
| 本地存储 | [`doc/storage.md`](doc/storage.md) | PrefsManager：最近播放、已下载歌曲 |

## 关键配置

`AppConfig.kt` 是唯一需要修改的配置文件：

```kotlin
object AppConfig {
    const val BASE_URL     = "http://47.111.25.157/"  // 后端服务器地址，末尾必须有斜杠
    const val PACKAGE_NAME = "com.vpapps.onlinemp3"   // 必须与后台 tbl_settings.package_name 一致
    const val SIGN_KEY     = "viaviweb"               // 签名密钥，必须与后端一致
}
```

## 与其他模块的接口

- **依赖后端**：所有内容数据从 `AppConfig.BASE_URL + "api.php"` 获取
- **对外无暴露**：纯客户端 App，不对外提供接口
- **权限**：`INTERNET`、`FOREGROUND_SERVICE`、`FOREGROUND_SERVICE_MEDIA_PLAYBACK`

## 开发指南

```bash
# 用 Android Studio 打开 android/ 目录（非根目录）
# 或命令行构建：
cd android
./gradlew assembleDebug          # 构建 debug APK
./gradlew installDebug           # 安装到连接的设备
```

修改服务器地址：编辑 `app/src/main/java/com/qqt/music/AppConfig.kt` 中的 `BASE_URL`。

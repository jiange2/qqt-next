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
  MainActivity.kt           # 应用入口：启动前台服务、初始化 PrefsManager、创建 PlayerViewModel
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
    MusicPlayerService.kt   # 前台服务：持有 ExoPlayer + MediaSession + WiFi 锁 + 缓存
    MediaControllerManager.kt # 单例：异步连接 MusicPlayerService，管理 MediaController
    AudioCache.kt           # LRU 缓存单例：占用不超过启动时刻缓存预算，自动驱逐最旧数据
    LastPlayedStore.kt      # SharedPreferences 封装：保存/加载播放进度（专辑/曲目/位置）
    MusicPlayer.kt          # ExoPlayer 简单封装（早期低级版本，已被 PlayerViewModel 替代）
  service/
    KeepAliveService.kt     # 独立前台服务：轻量级保活，与 MusicPlayerService 双层保活
  receiver/
    BootReceiver.kt         # BroadcastReceiver：BOOT_COMPLETED 后启动 KeepAliveService
    ManufacturerCompat.kt   # 工具类：获取小米/华为/OPPO/VIVO 自启动管理页面的 Intent
  viewmodel/
    PlayerViewModel.kt      # 全局播放状态：通过 MediaController 控制 Service 的 ExoPlayer，定期保存进度
  ui/
    navigation/
      Screen.kt             # 所有页面路由与标题定义（bottomNavRoutes、drawerTopRoutes）
      AppNavigation.kt      # ModalNavigationDrawer + Column（Scaffold + MiniPlayer），管理整体布局
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
      player/               # 播放器（全屏播放器 UI：专辑封面、进度条、播放控制）
    components/
      BannerCarousel.kt     # HorizontalPager 轮播（3秒自动翻页 + 圆点指示器）
      BottomBar.kt          # 底部导航栏（5 Tab，中间下载为 FAB 样式）
      DrawerContent.kt      # 侧边抽屉（橙色头图 + 6个导航项 + 底部设置）
      MiniPlayer.kt         # 底部迷你播放器：实时显示歌曲名、进度、播放控制
      SongListItem.kt       # 歌曲列表行（缩略图+标题+艺术家+评分+下载按钮）
      TopBar.kt             # 顶部导航栏（菜单/返回 + 标题 + 搜索按钮）
    theme/                  # Material3 主题（OrangePrimary=#E8441C，固定橙色不跟随系统Dynamic Color）
```

## 功能模块索引

| 功能 | 文档 | 说明 |
|------|------|------|
| API 请求与数据模型 | [`doc/api.md`](doc/api.md) | ApiClient、MusicRepository、数据模型 |
| 音乐播放器 | [`doc/player.md`](doc/player.md) | PlayerViewModel、MusicPlayerService、MediaController、缓存、进度保存 |
| 后台保活与自启动 | [`doc/background.md`](doc/background.md) | 双层前台服务、WiFi 锁、设备重启自启动、国产 ROM 兼容 |
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

# UI 导航与页面

> **何时阅读**：当你要新增页面、修改导航结构（底部导航/侧边抽屉）、调整公共组件（TopBar/MiniPlayer/SongListItem），或修改某个具体页面的 UI 逻辑时，阅读此文件。

## 功能概述

App 使用 Jetpack Compose Navigation 管理页面路由。整体布局由 `AppNavigation` 组装：`ModalNavigationDrawer`（侧边抽屉）包裹 `Column`（分为 Scaffold + MiniPlayer），`Scaffold` 包含 TopBar 和可选的 BottomBar，`MiniPlayer` 常驻显示在底部（无歌曲时显示占位符），5 个底部 Tab + 6 个抽屉项 + 1 个全屏播放器 + 2 个歌曲详情页（横幅歌曲/专辑歌曲）共 14 个页面目的地。

## 关键文件

| 文件 | 职责 |
|------|------|
| `ui/navigation/Screen.kt` | 所有路由枚举（route 字符串 + title），`bottomNavRoutes` / `drawerTopRoutes` 分组 |
| `ui/navigation/AppNavigation.kt` | 整体框架：ModalNavigationDrawer + Column（Scaffold + MiniPlayer）+ NavHost，控制 TopBar 和 MiniPlayer 显示逻辑 |
| `ui/components/TopBar.kt` | `MusicTopBar`：橙色顶栏，标题支持文本截断（`TextOverflow.Ellipsis`）和宽度限制防止溢出，左侧菜单/返回，右侧搜索 |
| `ui/components/BottomBar.kt` | `MusicBottomBar`：5个 Tab，中间「我的下载」为突出 FAB 样式 |
| `ui/components/DrawerContent.kt` | 侧边抽屉：橙色渐变头图 + 6个导航项 + 底部设置 |
| `ui/components/MiniPlayer.kt` | 迷你播放器（常驻显示于屏幕下方，有歌曲时显示播放控制，无歌曲时显示占位符，可点击打开全屏播放器） |
| `ui/screens/player/PlayerScreen.kt` | 全屏播放器：专辑封面旋转、进度条、播放控制、功能菜单 |
| `ui/components/SongListItem.kt` | 歌曲列表行（缩略图 + 标题 + 艺术家 + 评分 + 下载按钮） |
| `ui/components/BannerCarousel.kt` | 首页 Banner 横幅轮播（HorizontalPager + 自动翻页） |

## 路由结构

| 路由 | 标题 | 位置 | ViewModel |
|------|------|------|-----------|
| `home` | 首页 | 底部 Tab 1 | `HomeViewModel` |
| `recent` | 最近播放 | 底部 Tab 2 | `RecentViewModel` |
| `download` | 我的下载 | 底部 Tab 3（FAB） | 无（读 PrefsManager） |
| `category` | 音乐分类 | 底部 Tab 4 | `CategoryViewModel` |
| `latest` | 最新歌曲 | 底部 Tab 5 | `LatestViewModel` |
| `artist` | 音乐歌手 | 侧边抽屉 | `ArtistViewModel` |
| `album` | 音乐专辑 | 侧边抽屉 | `AlbumViewModel` |
| `playlist` | 播放列表 | 侧边抽屉 | `PlaylistViewModel` |
| `mylist` | 我的列表 | 侧边抽屉 | 无 |
| `favorites` | 歌曲收藏 | 侧边抽屉 | 无（待实现） |
| `settings` | 设置中心 | 侧边抽屉底部 | 无 |
| `player` | 播放器 | 全屏（无 TopBar/BottomBar） | 无（使用 PlayerViewModel） |
| `banner_songs/{bid}` | 歌曲（横幅标题） | 详情页：首页轮播点击 | 无（数据随首页接口内嵌，经 `BannerNav` 交接） |
| `album_songs/{aid}` | 专辑（专辑名） | 详情页：首页最新专辑/抽屉专辑列表点击 | `AlbumSongsViewModel` |

## AppNavigation 关键逻辑

```kotlin
// 播放器全屏页面：直接返回 PlayerScreen，不显示 Drawer/TopBar/BottomBar
if (isPlayerScreen) {
    PlayerScreen(playerViewModel, onBackClick = { navController.popBackStack() })
} else {
    // 常规页面：显示 Drawer + 布局列（顶部 Scaffold + 底部 MiniPlayer）
    ModalNavigationDrawer {
        Column(modifier = Modifier.fillMaxSize()) {
            // 顶部：Scaffold（TopBar + 可选 BottomBar + 页面内容）
            Scaffold(
                topBar = { MusicTopBar(...) },
                bottomBar = {
                    if (showBottomNav) MusicBottomBar(...)
                },
                modifier = Modifier.weight(1f),
            ) { innerPadding ->
                NavHost(...) { ... }
            }
            // 底部：MiniPlayer（常驻，不受路由影响）
            MiniPlayer(
                playerViewModel = playerViewModel,
                onPlayerClick = { navController.navigate(Screen.Player.route) },
            )
        }
    }
}

// 底部导航只在 5 个 Tab 路由显示
val showBottomNav = currentRoute in Screen.bottomNavRoutes

// Settings / 详情页：隐藏抽屉手势，显示返回按钮（而非菜单按钮）
gesturesEnabled = currentRoute != Screen.Settings.route && !isBannerSongs && !isAlbumSongs
showBackButton = currentRoute == Screen.Settings.route || isBannerSongs || isAlbumSongs
```

## 各页面说明

### HomeScreen / HomeViewModel
- `HomeViewModel.loadHome()` 调用 `MusicRepository.getHome()` 获取首页聚合数据（内部合并 `home` 与 `home_new` 两个接口，见 api.md）
- UI：Banner 轮播→热门歌曲横向滚动列表→最新专辑（横向 2行网格）→艺术家横向列表
- 点击最新专辑卡片 → `AlbumNav` 暂存专辑 → 跳转 `album_songs/{aid}`
- `HomeUiState`：`isLoading`, `banners`, `trendingSongs`, `latestAlbums`, `latestArtists`, `error`

### RecentScreen / RecentViewModel
- 从 `PrefsManager.getRecentIdsString()` 获取最近播放 ID 串，调用 `MusicRepository.getRecentSongs()` 查详情
- 空态显示提示文字

### DownloadScreen（无 ViewModel）
- 直接调用 `PrefsManager.getDownloadedSongs()` 读取本地缓存，`remember { }` 初始化一次（不响应实时变化）

### CategoryScreen / CategoryViewModel
- 2 列 `LazyVerticalGrid`，滚动到底部自动加载下一页（无限分页）

### LatestScreen / LatestViewModel
- 单列 `LazyColumn` 分页，滚动底部自动下一页

### ArtistScreen / AlbumScreen / PlaylistScreen
- 2 列网格，封面卡片样式，分页加载
- AlbumScreen 点击专辑卡片 → `AlbumNav` 暂存专辑 → 跳转 `album_songs/{aid}`（与首页最新专辑共用同一入口）

### AlbumSongsScreen / AlbumSongsViewModel（专辑歌曲页）
- 单列 `LazyColumn` 分页（后端每页固定 10 首），滚动到底自动加载下一页，行为同 LatestScreen
- 顶栏标题取 `AlbumNav.album.name`，显示返回键、禁用抽屉手势（同 BannerSongsScreen）
- 点击歌曲直接播放，播放队列 = 当前已加载的全部歌曲
- `AlbumNav.album` 为空时显示“内容已失效”；专辑无歌曲时显示空态提示

### SettingsScreen
- 主题 Switch（目前本地 state，未持久化到 PrefsManager）
- 评价/分享/隐私政策/关于（点击事件均为空，待实现）

### PlayerScreen（全屏播放器）
- 顶部栏：橙色 TopAppBar，显示歌曲标题和艺术家，左侧返回按钮、右侧收藏按钮和更多菜单
- 专辑封面：圆形，播放时平滑旋转（12秒一圈）
- 进度条：拖拽快进/快退，显示当前时间和总时长
- 第一行按钮：随机播放、上一首、播放/暂停（蓝色大圆形）、下一首、循环模式（支持无循环/全队列循环/单曲循环）
- 第二行按钮：添加到播放列表、分享、下载、评分、音量调节
- 通过点击 MiniPlayer 或导航到 `Screen.Player.route` 打开
- 返回按钮调用 `onBackClick()` 返回上一页（无菜单栏/底部栏干扰）

## 添加新页面的步骤

1. 在 `Screen.kt` 中添加新的 `object` 路由
2. 在 `AppNavigation.kt` 的 `NavHost` 中添加 `composable(Screen.Xxx.route) { ... }`
3. 如果是全屏页面（不显示 TopBar/BottomBar），在 `AppNavigation.kt` 中的 `if (isPlayerScreen)` 逻辑前添加特殊处理
4. 如需底部 Tab 显示，加入 `Screen.bottomNavRoutes`
5. 如需抽屉项，在 `DrawerContent.kt` 中添加导航项

## 注意事项

- `PlayerViewModel` 通过 `by viewModels()` 在 `MainActivity` 创建，然后通过参数逐层传递给页面 Composable（非 Hilt inject）
- Settings 页面与两个歌曲详情页的 `gesturesEnabled = false` 是为了防止侧滑手势与页面内部滑动冲突
- `Screen.titleOf(route)` 函数用于 `TopBar` 动态显示当前页面标题，新增路由时需同步更新此函数
- **[坑] TopBar 文本溢出处理**：使用 `TextOverflow.Ellipsis` 时，**必须导入** `androidx.compose.ui.text.style.TextOverflow`（注意包含 `.style.`），而不是 `androidx.compose.ui.text.TextOverflow`。后者不存在，会导致编译错误。参考 `SongListItem.kt`、`MiniPlayer.kt` 等其他组件的导入方式。
- **[坑] 播放器页面动画命名冲突**：自定义循环模式枚举不能用 `RepeatMode` 命名，因为 Compose 动画库已有同名枚举。应使用 `MusicRepeatMode` 等别名，避免冲突。

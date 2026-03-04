# UI 导航与页面

> **何时阅读**：当你要新增页面、修改导航结构（底部导航/侧边抽屉）、调整公共组件（TopBar/MiniPlayer/SongListItem），或修改某个具体页面的 UI 逻辑时，阅读此文件。

## 功能概述

App 使用 Jetpack Compose Navigation 管理页面路由。整体布局由 `AppNavigation` 组装：`ModalNavigationDrawer`（侧边抽屉）包裹 `Scaffold`（TopBar + BottomBar + 内容区），`MiniPlayer` 固定在 BottomBar 上方，5 个底部 Tab + 6 个抽屉项共 11 个页面目的地。

## 关键文件

| 文件 | 职责 |
|------|------|
| `ui/navigation/Screen.kt` | 所有路由枚举（route 字符串 + title），`bottomNavRoutes` / `drawerTopRoutes` 分组 |
| `ui/navigation/AppNavigation.kt` | 整体框架：ModalNavigationDrawer + Scaffold + NavHost，控制 TopBar 显示逻辑 |
| `ui/components/TopBar.kt` | `MusicTopBar`：橙色顶栏，左侧菜单/返回，右侧搜索 |
| `ui/components/BottomBar.kt` | `MusicBottomBar`：5个 Tab，中间「我的下载」为突出 FAB 样式 |
| `ui/components/DrawerContent.kt` | 侧边抽屉：橙色渐变头图 + 6个导航项 + 底部设置 |
| `ui/components/MiniPlayer.kt` | 迷你播放器（始终显示于 BottomBar 上方，有歌曲时可见） |
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

## AppNavigation 关键逻辑

```kotlin
// 底部导航只在 5 个 Tab 路由显示
val showBottomNav = currentRoute in Screen.bottomNavRoutes

// Settings 页面：隐藏抽屉手势，显示返回按钮（而非菜单按钮）
gesturesEnabled = currentRoute != Screen.Settings.route
showBackButton = currentRoute == Screen.Settings.route

// MiniPlayer 始终渲染在 BottomBar Column 内部（最上方）
Column {
    MiniPlayer(playerViewModel)
    if (showBottomNav) MusicBottomBar(...)
}
```

## 各页面说明

### HomeScreen / HomeViewModel
- `HomeViewModel.loadHome()` 调用 `MusicRepository.getHome()` 获取首页聚合数据
- UI：Banner 轮播→热门歌曲横向滚动列表→最新专辑（横向 2行网格）→艺术家横向列表
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

### SettingsScreen
- 主题 Switch（目前本地 state，未持久化到 PrefsManager）
- 评价/分享/隐私政策/关于（点击事件均为空，待实现）

## 添加新页面的步骤

1. 在 `Screen.kt` 中添加新的 `object` 路由
2. 在 `AppNavigation.kt` 的 `NavHost` 中添加 `composable(Screen.Xxx.route) { ... }`
3. 如需底部 Tab 显示，加入 `Screen.bottomNavRoutes`
4. 如需抽屉项，在 `DrawerContent.kt` 中添加导航项

## 注意事项

- `PlayerViewModel` 通过 `by viewModels()` 在 `MainActivity` 创建，然后通过参数逐层传递给页面 Composable（非 Hilt inject）
- Settings 页面的 `gesturesEnabled = false` 是为了防止侧滑手势与 Settings 内部滑动冲突
- `Screen.titleOf(route)` 函数用于 `TopBar` 动态显示当前页面标题，新增路由时需同步更新此函数

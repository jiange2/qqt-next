# UI 导航与页面

> **何时阅读**：当你要新增页面、修改导航结构（底部导航/侧边抽屉）、调整公共组件（TopBar/MiniPlayer/SongListItem），或修改某个具体页面的 UI 逻辑时，阅读此文件。

## 功能概述

App 使用 Jetpack Compose Navigation 管理页面路由。整体布局由 `AppNavigation` 组装：常驻下层为 `ModalNavigationDrawer`（侧边抽屉）包裹 `Column`（分为 Scaffold + MiniPlayer），`Scaffold` 包含 TopBar 和可选的 BottomBar，`MiniPlayer` 常驻显示在底部（无歌曲时显示占位符）；全屏播放器不走导航，以覆盖层状态 `playerOverlay` 叠加其上，下层页面原样保持，下拉收起整页滑出即时露出（无过渡动画）。5 个底部 Tab + 4 个抽屉项 + 7 个详情页（横幅歌曲/专辑歌曲/分类专辑/歌单详情/搜索/歌曲结果/艺术家歌曲）共 16 个页面目的地（全屏播放器为导航外覆盖层，非路由目的地）。

## 关键文件

| 文件 | 职责 |
|------|------|
| `ui/navigation/Screen.kt` | 所有路由枚举（route 字符串 + title），`bottomNavRoutes` / `drawerTopRoutes` 分组 |
| `ui/navigation/AppNavigation.kt` | 整体框架：ModalNavigationDrawer + Column（Scaffold + MiniPlayer）+ NavHost，控制 TopBar 和 MiniPlayer 显示逻辑 |
| `ui/components/TopBar.kt` | `MusicTopBar`：橙色顶栏，标题支持文本截断（`TextOverflow.Ellipsis`）和宽度限制防止溢出，左侧菜单/返回，右侧搜索 |
| `ui/components/BottomBar.kt` | `MusicBottomBar`：5个 Tab，中间「我的下载」为突出 FAB 样式 |
| `ui/components/DrawerContent.kt` | 侧边抽屉：橙色渐变头图 + 6个导航项 + 底部设置 |
| `ui/components/MiniPlayer.kt` | 迷你播放器（与底部导航连为一体的扁平长条，白底延伸至屏幕底部并自动避让系统手势区；有歌曲时显示播放控制，无歌曲时显示占位符，可点击打开全屏播放器） |
| `ui/screens/player/PlayerScreen.kt` | 全屏播放器：双页 Pager（歌词页左/歌曲页右）+ 封面主色动态背景 + 钉底主控行；下箭头顶栏 + 页签（真实导航）、标题/队列位置行、功能图标行、进度条；横滑切页与下拉返回手势；按压反馈为线性变淡/实心缩放（无波纹，见 PlayerScreen 小节） |
| `ui/screens/player/LyricsPanel.kt` | 歌词区组件：LRC 行渲染、当前行高亮与自动滚动居中、点击行跳播、手动滚动暂停跟随；`LyricsCache` 会话缓存与 `loadLyrics`（内嵌 lrcText 优先、外链 lrcUrl 兜底） |
| `ui/screens/player/LrcParser.kt` | LRC 歌词解析（`[mm:ss]`/`[mm:ss.xx]`/`[mm:ss.xxx]` 与一行多时间标签） |
| `ui/components/SongListItem.kt` | 歌曲列表行（缩略图 + 标题 + 艺术家 + 评分 + 下载按钮） |
| `ui/components/BannerCarousel.kt` | 首页 Banner 横幅轮播（HorizontalPager + 自动翻页） |
| `ui/screens/search/` | 搜索页组：`SearchScreen`（搜索框 + 组合结果分段单页：歌曲/专辑/艺术家，提交式触发）+ `SearchSongsScreen`（歌曲结果页，滚动自动分页）+ `ArtistSongsScreen`（艺术家歌曲页）；各配 ViewModel，交接经 `SearchNav`/`ArtistNav` |

## 路由结构

| 路由 | 标题 | 位置 | ViewModel |
|------|------|------|-----------|
| `home` | 首页 | 底部 Tab 1 | `HomeViewModel` |
| `recent` | 最近播放 | 底部 Tab 2 | `RecentViewModel` |
| `download` | 我的下载 | 底部 Tab 3（FAB） | 无（读 PrefsManager） |
| `category` | 音乐分类 | 底部 Tab 4 | `CategoryViewModel` |
| `latest` | 最新歌曲 | 底部 Tab 5 | `LatestViewModel` |
| `album` | 音乐专辑 | 侧边抽屉 | `AlbumViewModel` |
| `mylist` | 我的列表 | 侧边抽屉 | 无 |
| `favorites` | 歌曲收藏 | 侧边抽屉 | 无（待实现） |
| `settings` | 设置中心 | 侧边抽屉底部 | 无 |
| —（无路由） | 播放器：全屏覆盖层，`playerOverlay` 状态控制开关，不走 navigate/popBackStack | 全屏（无 TopBar/BottomBar） | 无（使用 PlayerViewModel） |
| `banner_songs/{bid}` | 歌曲（横幅标题） | 详情页：首页轮播点击 | 无（数据随首页接口内嵌，经 `BannerNav` 交接） |
| `album_songs/{aid}` | 专辑（专辑名） | 详情页：抽屉专辑列表/分类专辑页点击 | `AlbumSongsViewModel` |
| `category_albums/{cid}` | 分类（分类名） | 详情页：分类列表页点击（backend-next ADR 0009） | `CategoryAlbumsViewModel` |
| `search` | 搜索 | 详情页：TopBar 搜索图标点击 | `SearchViewModel` |
| `search_songs` | 搜索关键词 | 详情页：搜索页「查看更多歌曲」 | `SearchSongsViewModel` |
| `artist_songs` | 艺术家（艺术家名） | 详情页：搜索结果艺术家行点击 | `ArtistSongsViewModel` |

## AppNavigation 关键逻辑

```kotlin
// 常驻下层（抽屉 + 页面 + MiniPlayer）+ 播放器全屏覆盖层：下层页面原样保持，
// 播放器不走导航（playerOverlay 状态控制），下拉收起滑出即时露出下层页面
Box(modifier = Modifier.fillMaxSize()) {
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
                onPlayerClick = { playerOverlay = true },
            )
        }
    }

    // 播放器覆盖层：playerOverlay 为 true 时组合，叠在常驻下层之上
    if (playerOverlay) {
        PlayerScreen(playerViewModel, onBackClick = { playerOverlay = false })
    }
}

// 底部导航只在 5 个 Tab 路由显示
val showBottomNav = currentRoute in Screen.bottomNavRoutes

// Settings / 详情页（含搜索组三页）：隐藏抽屉手势，显示返回按钮（而非菜单按钮）
gesturesEnabled = ... && !isSearchSongs && !isArtistSongs && !isSearch
showBackButton = ... || isSearchSongs || isArtistSongs || isSearch
```

## 各页面说明

### HomeScreen / HomeViewModel
- `HomeViewModel.loadHome()` 调用 `MusicRepository.getHome()` 获取首页聚合数据（内部合并 `home` 与 `home_new` 两个接口，见 api.md）
- UI：整页 `LazyVerticalStaggeredGrid` 两列瀑布流；Banner 轮播/加载/错误区块以 `StaggeredGridItemSpan.FullLine` 横贯两列，热门歌曲卡片分列排布（BOM 2024.08.00/foundation 1.6.8 下为实验性 API，`@OptIn(ExperimentalFoundationApi::class)`）
- 瀑布流卡片：封面（按歌曲 id 哈希在 1:1 与 4:3 两档间取档，约六成高卡，确定性不随刷新跳动）+ 右下半透明播放按钮浮层 + 标题（≤2 行）+ 歌手副标题（`artist` 非空，11sp 单行省略）+ 曲风 chip（`categoryName` 非空，`ChipLabel` 样式）+ 热度行（耳机图标 + `totalViews` 万化：≥1万 → x.xw，≥1亿 → x.x亿，播放量为 0 不上）；不展示描述引言，评分不上卡
- 热度行不用心形：心形在既有 UI 语义中已被「喜欢/收藏」占用；歌手与热度保持行式文本（沿用旧卡样式），仅曲风为 chip
- 卡片点击 = 点播该曲并以整个热门榜单替换播放队列
- 「查看所有」按钮已移除：热门榜单条数由后端 `API_LATEST_LIMIT` 决定，无全量分页接口，撑不起全量页
- 最近播放区块已从首页移除（连带清理 `loadRecent()`、ON_RESUME 刷新、`recentSongs` 状态与导航接线）；「最近播放」仍由底部导航 tab 承载
- `HomeUiState`：`isLoading`, `banners`, `trendingSongs`, `error`

### RecentScreen / RecentViewModel
- 从 `PrefsManager.getRecentIds()` 获取最近播放 ID 列表，调用 `MusicRepository.getRecentSongs()` 查详情，按点播顺序重排（最新点播在前）
- 空态显示提示文字

### DownloadScreen（无 ViewModel）
- 直接调用 `PrefsManager.getDownloadedSongs()` 读取本地缓存，`remember { }` 初始化一次（不响应实时变化）

### CategoryScreen / CategoryViewModel
- 2 列 `LazyVerticalGrid`，滚动到底部自动加载下一页（无限分页）
- 点击分类卡片 → `CategoryNav` 暂存分类 → 跳转 `category_albums/{cid}`

### CategoryAlbumsScreen / CategoryAlbumsViewModel（分类专辑页）
- 2 列专辑网格（后端每页固定 10 张），滚动到底自动加载下一页；顶栏标题取 `CategoryNav.category.name`
- 点击专辑卡片 → `AlbumNav` 暂存专辑 → 跳转 `album_songs/{aid}`
- **单专辑直跳**：首页加载完成后若该分类仅 1 张专辑（按 `total_records` 判定，解析失败时首页恰 1 条也认定），经 `onAutoOpen` 直跳专辑歌曲页，并用 `popUpTo(category_albums, inclusive)` 替换当前栈条目——返回时直接回到分类列表页，不经过分类专辑页
- 空分类显示“该分类暂无专辑”；接口异常按空态处理，不会误触发直跳

### LatestScreen / LatestViewModel
- 单列 `LazyColumn` 分页，滚动底部自动下一页

### AlbumScreen
- 2 列网格，专辑卡片样式（专辑图在上、名称在图外下方，无卡片容器，共用 `ui/components/AlbumCard`，同分类专辑页），分页加载
- 点击专辑卡片 → `AlbumNav` 暂存专辑 → 跳转 `album_songs/{aid}`（与首页最新专辑共用同一入口）

### AlbumSongsScreen / AlbumSongsViewModel（专辑歌曲页）
- 单列 `LazyColumn` 分页（后端每页固定 10 首），滚动到底自动加载下一页，行为同 LatestScreen
- 顶栏标题取 `AlbumNav.album.name`，显示返回键、禁用抽屉手势（同 BannerSongsScreen）
- 点击歌曲直接播放，播放队列 = 当前已加载的全部歌曲
- `AlbumNav.album` 为空时显示“内容已失效”；专辑无歌曲时显示空态提示

### SearchScreen / SearchViewModel（搜索页）
- 顶部圆角搜索框（进入自动聚焦弹键盘；IME 搜索键/回车提交；提交式触发——输入过程不发请求，规避中文输入法 composing 误触发与后端 LIKE 全表扫描压力）
- 提交后调 `MusicRepository.searchAll()`（song_search 不带 search_type 走后端组合分支），一次返回三段
- 分段单页 LazyColumn：歌曲段（前 10 条 `SongListItem`）→ 专辑段（`chunked(2)` 两列 `AlbumCard`）→ 艺术家段（圆形头像行，尾随右箭头）；空段整段隐藏，三段全空显示 EmptyState「未找到相关歌曲」；接口异常按空态处理
- 歌曲段满页（≥10 条）时显示「查看更多歌曲」→ `SearchNav.query` 暂存 → 跳转 `search_songs`
- 点击歌曲行 = 点播该曲，播放队列 = 歌曲段当前已加载歌曲（不含专辑/艺术家段）；专辑卡片 → `AlbumNav` → `album_songs/{aid}`；艺术家行 → `ArtistNav` → `artist_songs`
- `SearchViewModel`：`onQueryChanged` 只改文本不发请求（清空时取消进行中请求并重置）；`submit()` 先取消旧 Job 再发新请求，防旧结果晚到覆盖

### SearchSongsScreen / SearchSongsViewModel（歌曲结果页）
- 单列 `LazyColumn`，`MusicRepository.searchSongs()`（search_type=songs）滚动到底自动分页（每页 10 条），行为同 LatestScreen
- 顶栏标题取 `SearchNav.query`；空结果显示 EmptyState「未找到相关歌曲」
- 点击歌曲直接播放，播放队列 = 当前已加载的全部歌曲

### ArtistSongsScreen / ArtistSongsViewModel（艺术家歌曲页）
- 单列 `LazyColumn`，`MusicRepository.getArtistSongs()`（artist_name_songs 按名字精确匹配）滚动到底自动分页（每页 10 条，id 倒序）
- 顶栏标题取 `ArtistNav.artist.name`；重名艺术家共用一页（旧契约，一期接受）
- 点击歌曲直接播放，播放队列 = 当前已加载的全部歌曲；空态「该艺术家暂无歌曲」

### SettingsScreen
- 主题 Switch（目前本地 state，未持久化到 PrefsManager）
- 评价/分享/隐私政策/关于（点击事件均为空，待实现）

### PlayerScreen（全屏播放器）
- 背景：封面主色亮色化垂直渐变（Palette 从 64px 封面小图提取主色，顶部主色浅版 → 底部近白，切歌颜色 400ms 平滑过渡）；主色按歌曲 ID 会话内缓存；提取中/失败/无可取色回退极光渐变背景图 + 半透明白蒙板（alpha 烘进渐变色实现淡入淡出）；亮色化保证藏青控件可读，状态栏图标始终深色，离开页面时还原
- 顶栏：下箭头收起 + 「歌曲 / 歌词」页签（真实导航：点击切页带翻页动画，选中程度取自 Pager 滑动进度连续过渡——字号/颜色插值、字重过半切换）+ 刷新图标（清当前歌歌词会话缓存并重取：内嵌重解析、外链重下载）
- 双页 Pager（横滑切页）：中部播放区首槽位为 HorizontalPager，歌词页在左（page 0）、歌曲页在右（page 1），默认落在歌曲页；歌曲页向右滑进歌词页、歌词页向左滑回歌曲页；标题区及以下区块在 Pager 外共享、不随翻页移动；槽位高度与封面一致（见高度自适应）。横滑手势由根级 scrollable(Horizontal) 直接驱动 pagerState（Pager 自带手势 userScrollEnabled=false），两页全域生效（含主控行、Slider 上方）；Slider 等子级水平控件优先消费不误触；拖动/fling 结束后自动 snap 回最近页（isScrollInProgress 监听）
- 歌词页（歌词区，LyricsPanel.kt）：居中歌词行（14sp→当前行 17sp 过渡），当前行品牌橙加粗、其余 PlayerIconGray；随播放进度自动滚动居中当前行（切歌/首次就绪瞬时定位，其后 200ms 动画）；用户手动滚动后 3 秒内暂停自动跟随，期满后若仍在拖动则等下次行变化恢复；点击任意行 seek 到该行起始时间；数据 `lrcText` 优先、为空下载 `lrcUrl` 解析，均无或失败显示「暂无歌词」占位；按歌曲 ID 会话内存缓存（LyricsCache，不落盘、不进缓存预算）
- 覆盖层进出（抽屉式）：打开时整页从屏幕底部滑入（200ms，FastOutSlowIn）；关闭路径（下拉返回/顶栏下箭头/系统返回）统一为整页向下滑出（同参数），动画结束才回调置 `playerOverlay=false`；系统返回由 PlayerScreen 内 BackHandler 接管（播放模式菜单开着时优先关菜单，其余一律触发收出）；下层页面全程原样保持
- 下拉返回：全页（两页全域）向下拖动，子级滚动容器（矮屏 verticalScroll、歌词 LazyColumn）滚到顶后剩余下拉量经 NestedScrollConnection 累计，位移达屏高约 22% 或松手甩动速度达 1200dp/s 即触发关闭（整页向下滑出，见覆盖层进出）；页面不跟手；收出动画期间系统返回被 BackHandler 拦截；正常屏无滚动容器，由恒消费 0 的 scrollable 充当 NestedScroll 事件源；手感参数在 `PlayerScreen.kt` 底部常量（`DismissDragFraction` / `DismissFlingVelocityDp` / `SlideAnimMs`）
- 按压反馈：全页可点控件无波纹、无按压投影/抬升；线性图标与文字类按下变淡（α 0.5，按下约 100ms、松手约 150ms），62dp 实心播放键改为按压缩放至 0.9 回弹（graphicsLayer 置于 shadow 前，投影随钮缩放）；手感参数集中在 `PlayerScreen.kt` 底部常量（`PressDimAlpha` / `PressScaleDown` / `PressInMs` / `PressOutMs`）；封面/播放键/滑钮的常驻投影是视觉层次，不属于反馈
- 歌曲封面（歌曲页槽位）：24dp 圆角方形 1:1，藏青阴影居中，不可点（无按压反馈）；切歌时旧图淡出、新图淡入并从 0.95 放大进场（AnimatedContent，400ms 与背景过渡同步）；暂停时饱和度 1→0.7 + 叠加 18% 深色 scrim（300ms），恢复播放还原；加载中/失败/无 URL 统一显示 72dp 音符占位图标（PlayerIconGray），图片加载成功后覆盖其上
- 标题区（左对齐）：歌名 / 歌手 / 队列位置（`当前序号 / 队列总数`）
- 功能图标行：喜欢 / 均衡器 / 下载 / 评论 / 更多；图标 28dp 居中于 48dp 触控框，行 padding 18dp = 28dp 内容线 − (48−28)/2，首末图标盒边缘与进度条两端对齐；喜欢为本地实心/描边心形切换；下载点击将当前歌曲入队下载，下载中图标位变 28dp 品牌橙环形进度 + 中心百分比数字（排队/总长未知时为不确定式旋转圈，点击无反应），已下载图标变 DownloadedGreen（#4CAF50）且点击无反应（取消/删除在下载列表页）；下载中/已下载均为不可操作态、无按压反馈（已下载由 `PlayerGlyph` `enabled = false` 实现，环形进度本身不可点）；均衡器 / 评论 / 更多点击暂为空实现
- 进度条：藏青 4dp 细轨道 + 16dp 小圆点滑钮（20dp slot 容器内居中，规避 M3 slot 贴左上问题）；M3 Slider 轨道两端各内缩半个 slot 宽（10dp），Slider 容器 padding 18dp（= 28 − 10）补偿，使轨道两端落在 28dp 内容线上（改滑钮/slot 尺寸需同步调整）；下方时间标签行左右 28dp
- 主控行（钉底常驻）：播放模式（三态：随机播放/顺序播放/单曲循环，ADR 0008，图标随模式换图形——随机=乱序箭头、顺序=循环环绕、单曲循环=环绕+1；顺序播放为默认态 PlayerIconGray，其余 BrandOrange；点击图标弹出白底圆角菜单（宽随最宽行整行内容右边不留白，IntrinsicSize.Max——不可用 Min：中文可逐字断行会窄到单字竖排；底边在图标顶边上方 20dp 防误触，行内 20dp 图标+14sp 文字，当前模式行 PlaceholderBg 高亮满行宽），菜单打开时再点图标按 随机→顺序→单曲循环 轮转且高亮跟随不关闭；状态持久化于 PlayerSettingsManager，Service collect 落到 ExoPlayer：随机=shuffleModeEnabled+REPEAT_ALL、顺序=REPEAT_ALL、单曲循环=REPEAT_ONE）/ 上一首 / 62dp 藏青大圆播放键 / 下一首 / 播放队列入口（点击暂为空实现），含 navigationBarsPadding 手势区避让
- 高度自适应（中部播放区正常屏固定高度、整体不可拖动）：外层 BoxWithConstraints 读取中部视口，首槽位（双页 Pager：歌词页/封面页）高度 = 封面边长 = min(0.76×宽, 剩余高度)（固定内容高度按 280dp×系统字体缩放保守估算、宁大勿小，估算误差全部由间距吸收），四段间距 = 最小值（30/16/10/18）+ weight 均摊剩余，内容总高恒等于视口；仅矮屏（视口放不下 0.5×宽下限封面 + 保守估算内容）退回整体滚动托底，主控行不随滚
- 通过点击 MiniPlayer 或导航到 `Screen.Player.route` 打开
- 返回按钮调用 `onBackClick()` 返回上一页（无菜单栏/底部栏干扰）；下拉返回与系统返回同效

## 添加新页面的步骤

1. 在 `Screen.kt` 中添加新的 `object` 路由
2. 在 `AppNavigation.kt` 的 `NavHost` 中添加 `composable(Screen.Xxx.route) { ... }`
3. 如果是全屏页面（不显示 TopBar/BottomBar），在 `AppNavigation.kt` 中的 `if (isPlayerScreen)` 逻辑前添加特殊处理
4. 如需底部 Tab 显示，加入 `Screen.bottomNavRoutes`
5. 如需抽屉项，在 `DrawerContent.kt` 中添加导航项

## 注意事项

- `PlayerViewModel` 通过 `by viewModels()` 在 `MainActivity` 创建，然后通过参数逐层传递给页面 Composable（非 Hilt inject）
- Settings 页面与各详情页（含搜索组三页）的 `gesturesEnabled = false` 是为了防止侧滑手势与页面内部滑动冲突
- `Screen.titleOf(route)` 函数用于 `TopBar` 动态显示当前页面标题，新增路由时需同步更新此函数
- **[坑] TopBar 文本溢出处理**：使用 `TextOverflow.Ellipsis` 时，**必须导入** `androidx.compose.ui.text.style.TextOverflow`（注意包含 `.style.`），而不是 `androidx.compose.ui.text.TextOverflow`。后者不存在，会导致编译错误。参考 `SongListItem.kt`、`MiniPlayer.kt` 等其他组件的导入方式。
- **[坑] 播放器页面动画命名冲突**：播放模式枚举不能用 `RepeatMode` 命名，因为 Compose 动画库已有同名枚举；现嵌套于 `PlayerSettingsManager` 内命名 `PlayMode`，图标映射集中在 `PlayerScreen.kt` 的 `playModeIcon()`（ADR 0008）。
- **[坑] M3 Slider 轨道两端内缩**：material3 的 `Slider` 会把轨道宽度减去一个 slot 宽并右移半个 slot 宽，轨道两端天然不贴容器边。当前 thumb slot 被 token 强制为 20dp，要贴 28dp 内容线，需给 Slider `padding(horizontal = 18.dp)`（= 28 − 10）补偿；slot/滑钮尺寸变化时同步调整（见 `PlayerScreen.kt` thumb 注释）。
- **[坑] Palette 提取封面主色**：Palette 不支持 hardware bitmap，提取用 `ImageRequest` 必须 `allowHardware(false)`（否则 `drawable as? BitmapDrawable` 得到 null）；用 64px 小图取色即可；主色按歌曲 ID 会话内缓存避免切回重复提取；近灰封面（饱和度 < 0.04）不抬饱和度，否则 HSL 钳制会把灰色染成淡红（见 `PlayerScreen.kt` `extractCoverPalette`）。

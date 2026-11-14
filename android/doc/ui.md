# UI 导航与页面

> **何时阅读**：当你要新增页面、修改导航结构（底部导航/侧边抽屉）、调整公共组件（TopBar/MiniPlayer/SongListItem），或修改某个具体页面的 UI 逻辑时，阅读此文件。

## 功能概述

App 使用 Jetpack Compose Navigation 管理页面路由。整体布局由 `AppNavigation` 组装：常驻下层为 `ModalNavigationDrawer`（侧边抽屉）包裹 `Column`（分为 Scaffold + MiniPlayer），`Scaffold` 包含 TopBar 和可选的 BottomBar，`MiniPlayer` 常驻显示在底部（无歌曲时显示占位符）；全屏播放器不走导航，以覆盖层状态 `playerOverlay` 叠加其上，下层页面原样保持，收起整页滑出即时露出（无过渡动画）。底部导航为 4 个 Tab（首页 / 本地音乐 / 音乐分类 / 最新歌曲），中央凸出「我的下载」圆钮；另有抽屉项与详情页（本地专辑 / 横幅歌曲 / 专辑歌曲 / 分类专辑 / 书单 / 阅读 / 歌单详情 / 搜索 / 歌曲结果 / 艺术家歌曲）。全屏播放器为导航外覆盖层，非路由目的地。

## 关键文件

| 文件 | 职责 |
|------|------|
| `ui/navigation/Screen.kt` | 所有路由枚举（route 字符串 + title），`bottomNavRoutes` / `drawerTopRoutes` 分组 |
| `ui/navigation/AppNavigation.kt` | 整体框架：ModalNavigationDrawer + Column（Scaffold + MiniPlayer）+ NavHost，控制 TopBar 和 MiniPlayer 显示逻辑 |
| `MainActivity.kt` | App 入口：宿主 `AppNavigation`；导入意图胶水——`singleTask` 接收音频 `VIEW`/`SEND` 意图（冷启动 `onCreate` / 在运行 `onNewIntent`），只「解析 URI + MIME/scheme 再校验 + 转交 `LocalMusicViewModel.importAudio`」，不在此弹 Toast |
| `ui/components/TopBar.kt` | `MusicTopBar`：橙色顶栏，标题支持文本截断（`TextOverflow.Ellipsis`）和宽度限制防止溢出，左侧菜单/返回；右侧默认搜索（仅无返回键页面），页面自带 `actions` 时接管右侧操作区（本地音乐 / 本地专辑菜单，带返回键也渲染） |
| `ui/components/BottomBar.kt` | `MusicBottomBar`：4 个 Tab（首页 / 本地音乐 / 音乐分类 / 最新歌曲），中间「我的下载」为突出 FAB 样式 |
| `ui/components/DrawerContent.kt` | 侧边抽屉：橙色渐变头图 + 6 个导航项 + 底部设置 |
| `ui/components/MiniPlayer.kt` | 迷你播放器（与底部导航连为一体的扁平长条，白底延伸至屏幕底部并自动避让系统手势区；有歌曲时显示播放控制，无歌曲时显示占位符，可点击打开全屏播放器） |
| `ui/screens/localmusic/` | 本地音乐页组：`LocalMusicScreen`（歌曲 / 专辑两视图、默认专辑、视图切换与批量管理在顶栏菜单、权限空态与灰显横幅、排除面板、歌行左滑删除、编辑模式勾选歌行与 `LocalMusicEditTopBar` / `LocalMusicBatchBar`）、`LocalMusicViewModel`（库快照与覆盖层、库刷新、权限核对、排除清单、删除接缝调用与结果落盘、导入编排、批量保存一次提交）、`LocalSongRow`（编辑态序号位换勾选框）、`LocalExcludedFoldersSheet`、`LocalMetadataEditSheet`、`LocalBatchEditSheet` |
| `ui/screens/localalbum/LocalAlbumScreen.kt` | 本地专辑页：某张本地专辑的歌曲列表，点歌以整张专辑为播放队列；歌行左滑删除（与本地音乐页同一条接缝），删空这张专辑时由导航层自动返回；编辑模式（`LocalAlbumTopBarMenu` ⋮ 菜单「批量管理」进入、勾选歌行，顶底栏/面板在导航层接线，全选改名整张搬走时页面跟随新专辑） |
| `ui/screens/player/PlayerScreen.kt` | 全屏播放器：双页 Pager（歌词页左/歌曲页右）+ 封面主色动态背景 + 钉底主控行；下箭头顶栏 + 页签（真实导航）、标题/队列位置行、功能图标行、进度条；横滑切页手势；按压反馈为线性变淡/实心缩放（无波纹，见 PlayerScreen 小节） |
| `ui/screens/player/LyricsPanel.kt` | 歌词区组件：LRC 行渲染、当前行高亮与自动滚动居中、点击行跳播、手动滚动暂停跟随；`LyricsCache` 会话缓存与 `loadLyrics`（内嵌 lrcText 优先、外链 lrcUrl 兜底） |
| `ui/screens/player/LrcParser.kt` | LRC 歌词解析（`[mm:ss]`/`[mm:ss.xx]`/`[mm:ss.xxx]` 与一行多时间标签） |
| `ui/components/SongListItem.kt` | 在线歌曲列表行（序号 + 标题 + 歌手与播放/下载计数 + 行尾封面）；`swipe` 非空时可左滑露出操作按钮 |
| `ui/components/RowSwipeReveal.kt` | 行内左滑露出操作按钮（ADR 0018）：`SwipeRevealRow` + `RowSwipeAction` + `RowSwipeRevealState`，见「行内左滑操作」 |
| `ui/components/BannerCarousel.kt` | 首页 Banner 横幅轮播（HorizontalPager + 自动翻页） |
| `ui/screens/search/` | 搜索页组：`SearchScreen`（搜索框 + 组合结果分段单页：歌曲/专辑/艺术家，提交式触发）+ `SearchSongsScreen`（歌曲结果页，滚动自动分页）+ `ArtistSongsScreen`（艺术家歌曲页）；各配 ViewModel，交接经 `SearchNav`/`ArtistNav` |

## 路由结构

| 路由 | 标题 | 位置 | ViewModel |
|------|------|------|-----------|
| `home` | 首页 | 底部 Tab 1 | `HomeViewModel` |
| `local_music` | 本地音乐 | 底部 Tab 2 | `LocalMusicViewModel`（提升在导航层） |
| `download` | 我的下载 | 底部导航中央 FAB | 无（读 `DownloadManager`） |
| `category` | 音乐分类 | 底部 Tab 3 | `CategoryViewModel` |
| `latest` | 最新歌曲 | 底部 Tab 4 | `LatestViewModel` |
| `album` | 音乐专辑 | 侧边抽屉 | `AlbumViewModel` |
| `mylist` | 我的歌单 | 侧边抽屉 | 无 |
| `favorites` | 歌曲收藏 | 侧边抽屉 | 无（待实现） |
| `settings` | 设置中心 | 侧边抽屉底部 | 无 |
| —（无路由） | 播放器：全屏覆盖层，`playerOverlay` 状态控制开关，不走 navigate/popBackStack | 全屏（无 TopBar/BottomBar） | 无（使用 PlayerViewModel） |
| `banner_songs/{bid}` | 歌曲（横幅标题） | 详情页：首页轮播点击 | 无（数据随首页接口内嵌，经 `BannerNav` 交接） |
| `album_songs/{aid}` | 专辑（专辑名） | 详情页：抽屉专辑列表/分类专辑页点击 | `AlbumSongsViewModel` |
| `local_album/{lid}` | 本地专辑（专辑名） | 详情页：本地音乐专辑视图卡片点击；`lid` 为 Base64 URL-safe 编码的专辑键 | 复用 `LocalMusicViewModel` |
| `category_albums/{cid}` | 分类（分类名） | 详情页：分类列表页点击（backend-next ADR 0009） | `CategoryAlbumsViewModel` |
| `search` | 搜索 | 详情页：TopBar 搜索图标点击 | `SearchViewModel` |
| `search_songs` | 搜索关键词 | 详情页：搜索页「查看更多歌曲」 | `SearchSongsViewModel` |
| `artist_songs` | 艺术家（艺术家名） | 详情页：搜索结果艺术家行点击 | `ArtistSongsViewModel` |

## AppNavigation 关键逻辑

```kotlin
// 常驻下层（抽屉 + 页面 + MiniPlayer）+ 播放器全屏覆盖层：下层页面原样保持，
// 播放器不走导航（playerOverlay 状态控制），收起滑出即时露出下层页面
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

// 底部导航只在底部 Tab 路由显示
val showBottomNav = currentRoute in Screen.bottomNavRoutes

// Settings / 详情页（含搜索组三页、本地专辑页）：隐藏抽屉手势，显示返回按钮（而非菜单按钮）
gesturesEnabled = ... && !isSearchSongs && !isArtistSongs && !isSearch && !isLocalAlbum
showBackButton = ... || isSearchSongs || isArtistSongs || isSearch || isLocalAlbum
```

本地音乐相关接线：

- `LocalMusicViewModel` 提升到 `AppNavigation`，页面、顶栏菜单与排除面板共用同一实例
- 本地音乐 Tab 的 TopBar 右侧不是搜索，而是 `LocalMusicTopBarMenu`：视图切换（第一项，文案随视图翻转）/ 批量管理（第二项，仅歌曲视图显示、未授权音频权限禁用）/ 重新扫描（完成后提示「新增 x、移除 y」）/「排除的文件夹」；编辑模式下整条顶栏被替换，此菜单隐藏
- 本地音乐视图状态（默认专辑）提升在 `AppNavigation`：`remember` 以 `local_music` 返回栈条目为键（初值 true = 专辑，离开本页键置空），页面与顶栏菜单共用。离开本页（含进本地专辑页，条目虽在栈上但已不是当前条目）键即置空、状态重建，返回必回专辑视图，「不记忆上次选择」由结构保证；播放器覆盖层不切 NavHost，条目不变、视图状态保留。视图变化即收起左滑展开态（页内 `LaunchedEffect(showAlbums)`）
- 本地音乐编辑模式状态提升在 `AppNavigation`：开关 `localMusicEditing`、勾选集合 `localMusicSelection`（`Set<LocalSong.id>`）与批量面板开关 `localMusicBatchPanelOpen`，都 `remember` 以 `local_music` 返回栈条目为键（与视图状态同键）。离开本页（含切底部 Tab / 进本地专辑页）键即置空、状态重建，「离开即退出、勾选不跨页保留」由结构保证；播放器覆盖层不切 NavHost，条目不变、编辑态保留。不放 `LocalMusicViewModel`——它是导航层单例、与专辑页共用，放进去会跨页泄漏。退出路径共用 `exitLocalMusicEditMode()`（清空勾选、关面板）：顶栏取消、系统返回、点保存、权限被收回（`LaunchedEffect(permissionGranted)`）；面板开着时系统返回先收面板、再按一次才退出编辑模式（`BackHandler` 不退出页面）
- 编辑态 `AppNavigation` 的 topBar lambda 把 `MusicTopBar` 整条换成 `LocalMusicEditTopBar`（左「取消」、标题「已选 n 首」、右「全选 / 取消全选」；`MusicTopBar` 标题是纯字符串、左侧固定菜单/返回键，无内部本地音乐分支，表达不了编辑态），⋮ 菜单隐藏；全选 / 已全选判据与批量保存门槛是纯规则（`data/local/LocalMusicEditRules.kt`）。批量底栏 `LocalMusicBatchBar` 只在编辑态渲染，放 Scaffold 内容区底部（NavHost 之下）——底部导航在 Scaffold bottomBar、MiniPlayer 在 Scaffold 之外下方，都不与本栏重叠；批量面板 `LocalBatchEditSheet` 在导航层渲染，保存按可见列表过滤勾选行调 `saveBatchMetadata` 后即退出编辑模式
- 本地专辑页编辑模式状态同样提升在 `AppNavigation`（票据04）：开关 `localAlbumEditing`、勾选集合 `localAlbumSelection` 与面板开关 `localAlbumBatchPanelOpen`，都 `remember` 以 `local_album` 返回栈条目为键（与本地音乐页那组各自独立、互不相通）。非编辑态顶栏右侧是 `LocalAlbumTopBarMenu`（⋮ 只含「批量管理」，未授权禁用；`MusicTopBar` 页面自带 actions 时带返回键也渲染）；编辑态复用同一套 `LocalMusicEditTopBar` / `LocalMusicBatchBar` / `LocalBatchEditSheet`，全选 = 本专辑全部可用行。退出路径共用 `exitLocalAlbumEditMode()`：顶栏取消、系统返回（专辑页自己的 `BackHandler`，面板先收再退编辑、不退出页面）、点保存、权限被收回（同一个 `LaunchedEffect(permissionGranted)` 两页一并退出）、切页（结构保证）
- 专辑页批量保存的去向在提交前同步推算（纯函数 `batchAlbumNewKeys`，`data/local/LocalMetadataEdit.kt`）：非全选（含灰显行未被选）或归并键不变 → 留在原页、列表自然变短；全选且新键唯一 → **页面跟随新专辑**——等 `albums` 派生追上提交（`first { 目标专辑含选中行 }`，1000ms 超时兜底 pop，等待期间校验返回栈条目身份防用户已离开）后交接 `LocalAlbumNav.albumName`、navigate 新 lid 并 `popUpTo(local_album, inclusive)` 替换条目（返回直接回本地音乐页；早跳会闪「专辑已不存在」——`albums` 派生异步重算，与删除链路同一坑）；全选但拆成多张新专辑（文件夹兜底专辑只填专辑名、成员专辑艺术家不同）→ 无唯一去向，`popBackStack()` 回本地音乐页
- 本地音乐域（`local_music` 与 `local_album`）**不显示离线横幅**：这两个页面不发起任何网络请求
- 播放失败产生的 `unavailableLocalSongId` 在导航层消费：把该行标记为不可用并轻提示「文件不可用」，不自动刷新；下次刷新后媒体库里已无此行，自然消失
- 本地歌曲删除的两个 launcher 同样注册在导航层：`StartIntentSenderForResult` 拉系统框（API 30+ 删除确认框 / API 29 `RecoverableSecurityException` 授权框，同一个契约），`RequestPermission` 拉 ≤28 的 `WRITE_EXTERNAL_STORAGE`。`LocalMusicViewModel` 产出「下一步动作」（`pendingDeleteAction`），导航层拉起后清空（与 `notice` 同范式）。**放常驻宿主而不是页面**：本地音乐页导航到专辑页时离开组合，页面级 `rememberLauncherForActivityResult` 随之注销，从专辑页发起删除、系统框返回时回调就丢了；导航层不随路由离开组合，两页共用一份。IntentSender 发送自身失败时契约回 `RESULT_CANCELED` + `EXTRA_SEND_INTENT_EXCEPTION`，launcher 检查该 extra 并标成拉起失败，ViewModel 据此弹「删除失败」——不会被当成用户取消而静默吞掉
- ≤28 的 App 内删除确认框也只在导航层渲染一份（`pendingDeleteConfirm` 非空即弹，两页共用）；API 29/30+ 系统框即唯一确认，此框永不出现
- 本地音乐域的一次性轻提示 `notice`（重扫数量 / 刷新失败 / 删除失败 / 批量保存「已修改 n 首」/ 导入成功「已导入到本地音乐」/ 导入失败「导入失败」）在导航层统一 Toast：专辑页没有页面级 collector，放这里两页都覆盖（本地音乐页不再自己消费，避免双弹）
- 导入不改导航接线（`AppNavigation` 零改动）：`MainActivity` 的意图胶水经 `by viewModels()` 访问 `LocalMusicViewModel`，与 `AppNavigation` 的 Compose `viewModel()` 命中**同一实例**（同 Activity ViewModelStore + 默认 factory + 默认 key，无 owner 覆写 / 自定义 factory，机制与前提见「注意事项」）；导入提示复用上一条的既有 `notice` collector，任何 Tab 下导入都会弹 Toast，StateFlow 保值、冷启动 `onCreate` 设的提示首帧组合后照样弹
- 删除成功且删空的是当前专辑 → 自动 `popBackStack()` 回本地音乐页：绑定在删除成功事件上（`deleteSucceeded` 带专辑键与删后剩余可见行数，剩余数在删除提交时同步算好——`albums` 派生异步重算，消费时读它会拿到旧值），并校验当前路由的专辑键一致；不做「专辑消失就 pop」的通用观察，进程被杀恢复到本就不存在的专辑时仍显示既有空态
- 音频权限统一走 `ActivityResultContracts.RequestPermission()`，权限名按系统版本取用（`audioReadPermission()`：Android 13+ `READ_MEDIA_AUDIO`、12 及以下 `READ_EXTERNAL_STORAGE`）；授权成功自动静默刷新，拒绝过的入口升级为「重试 + 去系统设置」

## 行内左滑操作（SwipeRevealRow）

行内操作不占常驻图标位，一律由左滑手势承载（ADR 0018）。共享机制在 `ui/components/RowSwipeReveal.kt`：`SwipeRevealRow`（包裹行内容）+ `RowSwipeAction`（按钮文案与行为）+ `RowSwipeRevealState`（同页互斥展开状态）。

- **手势**：手指从右向左拖动，行内容整体左移露出行尾按钮，松手位移过半即停住、不足一半回弹（180ms 动画）；横向拖动越过触摸阈值才进入滑动，纵向滚动不受影响，整行点歌照常
- **按钮**：88dp 宽、`DangerRed` 底、白色 14sp 文案，文案即操作名；样式写死在组件里，调用点只给 label 与 `onAction`
- **互斥**：同一个 `RowSwipeRevealState` 下同一时刻只有一行展开，滑开新的一行自动收起上一行；状态由页面级 `remember` 持有（不放进列表分支内部，否则分支切换会重置）
- **收起**：列表滚动即收起（页面 `rememberLazyListState()` + `LaunchedEffect(listState.isScrollInProgress)`）；点露出的按钮先收起再执行操作；展开态下点行内其它区域只收起、不触发点歌
- **行内容须不透明**：白底由组件加在内容 Box 上，行自身不要再画背景（否则露出的按钮会从下方透出）；`swipe = null` 时内容原样渲染、左滑无反应
- **启用范围**（有移除语义的列表）：下载页已完成行「删除」（App 确认框）、下载中行「取消」（直接执行）、歌单详情行「移出歌单」（直接执行）、本地音乐页与本地专辑页歌行「删除」（分版本确认，见各页小节）；其余列表不传 `swipe`
- 本地两页的歌行还带长按（元数据编辑）：`LocalSongRow` 的 `combinedClickable` 与外层左滑共存，灰显只关行内点击、不影响左滑；两页编辑模式下左滑收起（`swipe = null`）、长按改为切换勾选（见 LocalMusicScreen / LocalAlbumScreen 小节）

## 各页面说明

### HomeScreen / HomeViewModel
- `HomeViewModel.loadHome()` 调用 `MusicRepository.getHome()` 获取首页聚合数据（内部合并 `home` 与 `home_new` 两个接口，见 api.md）
- UI：整页 `LazyVerticalStaggeredGrid` 两列瀑布流；Banner 轮播/加载/错误区块以 `StaggeredGridItemSpan.FullLine` 横贯两列，热门歌曲卡片分列排布（BOM 2024.08.00/foundation 1.6.8 下为实验性 API，`@OptIn(ExperimentalFoundationApi::class)`）
- 瀑布流卡片：封面（按歌曲 id 哈希在 1:1 与 4:3 两档间取档，约六成高卡，确定性不随刷新跳动）+ 右下半透明播放按钮浮层 + 标题（≤2 行）+ 歌手副标题（`artist` 非空，11sp 单行省略）+ 曲风 chip（`categoryName` 非空，`ChipLabel` 样式）+ 热度行（耳机图标 + `totalViews` 万化：≥1万 → x.xw，≥1亿 → x.x亿，播放量为 0 不上）；不展示描述引言，评分不上卡
- 热度行不用心形：心形在既有 UI 语义中已被「喜欢/收藏」占用；歌手与热度保持行式文本（沿用旧卡样式），仅曲风为 chip
- 卡片点击 = 点播该曲并以整个热门榜单替换播放队列
- 「查看所有」按钮已移除：热门榜单条数由后端 `API_LATEST_LIMIT` 决定，无全量分页接口，撑不起全量页
- `HomeUiState`：`isLoading`, `banners`, `trendingSongs`, `error`

### LocalMusicScreen / LocalMusicViewModel（本地音乐）
- 页面有「歌曲 / 专辑」两视图，进入默认专辑视图，不记忆上次选择；视图切换在顶栏 ⋮ 菜单第一项，文案随当前视图翻转（专辑视图时「切换到歌曲视图」，歌曲视图时「切换到专辑视图」），点击立即切换并收起菜单，始终显示、始终可点（含空库、未授权、加载中）；两视图都完全来自本机库快照与系统媒体库刷新，不发起任何网络请求
- **进入与授权**：首次进入未授权 → 说明空态 + 「授权音乐访问」（系统标准音频权限弹窗）；拒绝过 → 「重试 + 去系统设置」双入口（勾了不再询问后系统不再弹窗，去设置是兜底）；授权成功立即（毫秒级整库查询）出全库。进页面与回前台（`ON_RESUME`）各核对一次权限；进页面授予时静默刷新一次库，回前台仅在授权状态有变化时动作（重新开启 → 静默刷新恢复灰显的行，又被关闭 → 灰显与横幅立即出现），无变化不刷新（ADR 0022：进入页面与授权成功后各静默刷新一次）
- **歌曲视图**：按合并展示标题升序（`Collator(Locale.CHINA)`）列出 `LocalSongRow`（序号 / 标题 / 艺术家 / 时长）；点击以当前完整歌曲列表为播放队列，来源记为 `QueueSource.LOCAL`
- **专辑视图**：两列卡片墙（系统专辑图 + 专辑名 + 艺术家 · 歌曲数）；点击进入本地专辑页
- **顶栏菜单**：视图切换 / 批量管理 / 重新扫描 / 排除的文件夹（视图切换始终可点；批量管理仅歌曲视图显示、未授权音频权限时禁用、空库不额外禁用；重新扫描在未授权音频权限时禁用）
- **编辑模式（批量管理）**：本页入口仅歌曲视图，从顶栏 ⋮ 菜单「批量管理」进入（点击进入并收起菜单与左滑展开态；本地专辑页有独立入口，见 LocalAlbumScreen 小节）；开关、勾选集合与面板开关都在导航层（见 AppNavigation 接线），离开本页即退出、勾选不记忆
  - **勾选交互**：歌行序号位换成前置勾选框（勾选框只展示不接事件，切换统一走整行点击 / 长按，避免点框与点行双重切换）；点行 / 长按 = 切换勾选，不跳播、不弹单首面板；左滑删除收起（`swipe = null`）；灰显行（`available = false`）不可勾选
  - **顶栏 / 底栏**：顶栏整条换成 `LocalMusicEditTopBar`——左「取消」、标题「已选 n 首」、右「全选 / 取消全选」（已全选时文案翻转、再点清空勾选），⋮ 菜单隐藏；全选 = 可见列表（排除过滤后）全部可用行，灰显行跳过；底栏 `LocalMusicBatchBar` 只有一个「编辑信息」按钮，0 选禁用，放 Scaffold 内容区底部（NavHost 之下），不与底部导航 / MiniPlayer 重叠
  - **批量面板**：「编辑信息」打开 `LocalBatchEditSheet`——仅专辑 / 专辑艺术家两个自由文本字段，初始为空、不预填、无「恢复默认」，留空 = 不改（与单首面板相反），两字段 trim 皆空时保存禁用（详见 LocalMetadataEditSheet 小节）
  - **保存与退出**：保存调 `LocalMusicViewModel.saveBatchMetadata` 一次提交（覆盖层整表一次原子写，见 [storage.md](storage.md)），点保存即退出编辑模式、清空勾选、关闭面板，提交完成后 Toast「已修改 n 首」（n = 勾选提交数）经 `notice` 弹出；专辑分组 / 计数 / 封面随列表重算，歌曲按新专辑 / 专辑艺术家换组；取消、系统返回（面板开着先收面板、再按一次退出）、切页 / 进其他页面、编辑中权限被收回都退出并清空勾选，未保存输入随面板离组合丢弃；全程纯本地、零网络
- **刷新**：一次整库查询，无进度条、无取消；静默刷新不提示，顶栏「重新扫描」完成后提示「新增 x、移除 y」（数量按排除过滤后的可见库统计——被排除文件夹里的增删列表看不见，不计入）；刷新期间旧列表照常可用、播放不打断；查询失败（卷不可用等）保留旧快照不动，不放空列表假装没歌
- **空态与失效**：
  - 权限拒绝 → 解释空态：「重试」（再拉系统弹窗）/「去系统设置」（到本 App 的设置页手动开启）
  - 已授权但媒体库无音乐 → 「系统媒体库里还没有音乐」+ 重新扫描
  - 权限被系统关闭（进页面或回前台复查发现）→ 旧列表整体灰显（含专辑卡片）+ 重新授权横幅（重试 / 去系统设置），不放空列表假装没歌；此时歌行不露左滑删除（整库灰显是权限问题，不是文件失效）；重新授权后自动刷新，灰显的行恢复可播
  - 播放中文件被删 → 该行灰显并轻提示「文件不可用」，下次刷新后自然消失（媒体库里已无此行）
- **排除文件夹**：顶栏「排除的文件夹」打开底部面板（`LocalExcludedFoldersSheet`），自动列出库中含音乐的文件夹并带开关；排除含其全部子文件夹，切换即时生效（纯内存过滤）、不需要重扫，状态随快照落盘、重启保留
- **元数据编辑**：长按歌曲行打开 `LocalMetadataEditSheet`；编辑模式下长按改为切换勾选，不弹此面板
- **删除（左滑）**：歌行左滑露出红色「删除」按钮，点按钮才执行（见「行内左滑操作」；展开态在列表滚动与歌曲/专辑视图切换时收起）。删的是设备存储里的音频文件本身，删后其他应用也不再可见，与「排除文件夹」（仅本 App 不展示、文件保留）是两回事。删除经 `data/local/LocalMusicDeleter` 分版本接缝，UI 与 ViewModel 不直接碰 MediaStore 删除 API；全程纯本地、零网络请求，飞行模式可用
- **删除确认按系统版本分档**：Android 11+（API 30+）`createDeleteRequest` 的系统删除框即唯一确认，允许后由系统执行删除；Android 10（API 29）`resolver.delete` 捕获 `RecoverableSecurityException` → 系统授权框 → 授权后重试删除（App 自有文件可能直接删成功、不弹框）；Android 9 及以下（≤28）没有系统框，先弹导航层的 App 确认框（标题 `删除本地歌曲`，正文 `「歌名」将从设备存储删除，其他应用也不再可见。`——歌名取覆盖后的展示标题，按钮 `删除` / `取消`，两个 Text 走 M3 默认色），确认后进接缝，API 23+ 且未授予写入权限时再弹 `WRITE_EXTERNAL_STORAGE` 权限框（API 21–22 安装即授予、无权限框；已授予则不再弹）
- **删除结果**：成功 → 该行即时从列表消失、专辑分组与卡片计数随之重算（纯派生）、整库快照原子落盘，不等下次扫描；失败 → 一律轻提示「删除失败」，仅当确认媒体库行已不在（delete 返回 0 行——文件已被外部删除 / URI 失效）才把该行标为不可用（灰显并落盘）；拒绝写入权限、provider 异常、异常框结果码、IntentSender 发送失败都保持该行可播（它们不证明文件不可播，真不可播的文件走既有「文件不可用」链路、下次刷新自然消失）；取消 → 一行不动、无提示。灰显（文件不可用）的行照样可左滑删除；删除正在播放的歌不做特殊处理（不主动停止、不跳歌），真播不了时走既有「文件不可用」链路
- **导入（入口在来源 App，qqt 内无按钮）**：qqt 够不到来源 App 私有目录（`Android/data/`），界面内不设导入按钮、不做 SAF 选择器——入口在来源 App 一侧：微信/QQ 等任意 App 里点音频 →「用其他应用打开」或「分享」→ 选 qqt（不止微信/QQ）。`MainActivity` 注册两个 intent-filter：`VIEW` 带 `content` scheme + `audio/*`；`SEND` 仅 `audio/*` **不带 scheme**——SEND 意图只带 MIME、无 data URI，`IntentFilter.matchData` 在 filter 声明 scheme 时对 SEND 的空 scheme 必不匹配，带 scheme 会令分享入口失效，`content` scheme 改由胶水层对 `EXTRA_STREAM` 防御性校验。`singleTask` 保证在运行复用实例：冷启动 `onCreate` 末尾与 `onNewIntent` 共用同一解析（VIEW 取 `data`、SEND 取 `EXTRA_STREAM` 单文件，`SEND_MULTIPLE` 静默忽略；非 content 拒收，MIME 取 `intent.type`、拿不到退回 `resolver.getType(uri)`，非 `audio/*` 拒收），胶水只「解析 URI + 转交 `LocalMusicViewModel.importAudio`」，不含复制/命名/刷新逻辑、不在此弹 Toast。导入经 `data/local/LocalMusicImporter` 唯一写侧接缝，UI 与 ViewModel 不直接碰 MediaStore 写入 API；全程纯本地、零网络请求，飞行模式可用
- **导入落库按系统版本分档**：Android 10+（API 29+）`MediaStore.Audio.Media` 插入自有行（`RELATIVE_PATH = "Music/导入/"`，`IS_PENDING = 1` 期间写入字节、写完置 0 发布，任何失败清理自有 pending 行不留半截），App 拥有自己创建的行、不需要新权限；Android 9 及以下（≤28）直写公共 `Music/导入/`（临时文件 + rename）后 `MediaScannerConnection.scanFile` 触发扫描，API 23+ 未授予 `WRITE_EXTERNAL_STORAGE` 直接失败、不弹申请框（单步接缝取舍，见 [storage.md](storage.md)）。目标目录重名自动加后缀 `name (2).ext` 不覆盖旧文件；重复导入各自成行、不去重（ADR 0022）
- **导入结果**：成功 → 轻提示「已导入到本地音乐」+ 静默刷新（复用整库刷新管线），回到本地音乐页新歌自动可见、不用手动重扫，随快照落盘重启仍在；失败 → 轻提示「导入失败」，不留半截文件。导入只落库：不自动播放、不重建播放队列、不打断当前播放。未授音频读取权限时导入照样落库成功（来源 URI 读权限 + 写自有行），刷新在权限门槛静默跳过；进本地音乐页走现有权限引导、授权后即可见。导入的歌 = 普通本地歌曲：播放、元数据编辑、批量管理、左滑删除全复用现有机制；专辑归并按现有规则（有专辑标签按「专辑名 + 专辑艺术家」，无标签按文件夹名兜底聚成「导入」专辑），`Music/导入/` 可被「排除文件夹」照常排除
- 库快照与覆盖层见 [storage.md](storage.md)；来源与权限口径见 `LocalMusicLibrarySource`（`IS_MUSIC != 0`、`IS_PENDING` / `IS_TRASHED` 版本条件、多存储卷合并、行 ID 身份）

### LocalAlbumScreen（本地专辑页）
- 路由 `local_album/{lid}`，`lid` 是 Base64 URL-safe 编码的专辑键；顶栏标题取专辑名
- 专辑头：封面（系统专辑图，无图占位）+ 专辑名 + 艺术家 + 歌曲数；歌曲按音轨号升序（无音轨号按标题）
- 点歌以整张专辑为播放队列，来源仍是 `QueueSource.LOCAL`（与本地音乐页共用同一份播放模式记忆）
- 长按歌曲行同样打开元数据编辑面板；专辑因刷新或改专辑名而不存在时显示「专辑已不存在」
- **编辑模式（批量管理，票据04）**：顶栏右侧 ⋮ 菜单（`LocalAlbumTopBarMenu`，与返回键共存）只含「批量管理」一项，未授权音频权限禁用、专辑不存在 / 加载中不额外禁用；点击进入编辑模式并收起菜单与左滑展开态。行交互 / 顶栏 / 底栏 / 批量面板与本地音乐页完全同构（同一套 `LocalSongRow` 勾选态、`LocalMusicEditTopBar`、`LocalMusicBatchBar`、`LocalBatchEditSheet`，状态在导航层、见 AppNavigation 接线）：点行 / 长按 = 切换勾选（不跳播、不弹单首面板）、左滑收起、灰显行不可选；全选 = 本专辑全部可用行。与本地音乐页勾选互不相通，离开本页即退出、勾选不记忆
  - **保存去向**：部分勾选搬出 → 留在原页，列表与专辑头计数 / 封面随派生更新；全选改名把整张专辑搬走（新键唯一）→ 页面跟随新专辑（标题 / 封面 / 列表更新，返回栈条目被替换，系统返回直接回本地音乐页，不闪「专辑已不存在」）；全选但拆成多张新专辑（文件夹兜底专辑只填专辑名、成员专辑艺术家不同）→ 自动返回本地音乐页。去向由 `batchAlbumNewKeys` 提交前同步推算，跟随导航等 `albums` 派生就绪（见 AppNavigation 接线）
  - **退出路径**：取消、系统返回（面板开着先收面板、再按一次退出，不退出页面）、切页 / 进其他页面、点保存、编辑中权限被收回都退出并清空勾选；Toast「已修改 n 首」经导航层 `notice` 弹出
- **删除（左滑）**：歌行左滑「删除」与本地音乐页共用同一行组件、同一条接缝与同一套分版本确认口径（30+ 系统删除框 / 29 系统授权框 / ≤28 App 确认框），结果口径也一致：成功即移除该行、整库快照原子落盘，专辑头歌曲数与列表随之更新；失败一律轻提示「删除失败」，仅在确认文件已不在时才灰显该行；取消不动。删掉这张专辑的最后一首歌 → 导航层自动返回本地音乐页，不闪「专辑已不存在」空态
- **权限核对**：进页面核对一次音频权限（`onAlbumPageEntered()`，只核对、不刷新——刷新仍由本地音乐页入口触发），回前台（`ON_RESUME`）再核对一次。进程被杀后直接恢复到专辑页不经过本地音乐页的 `onPageEntered`，不核对则权限状态停在初值 false，正常已授权的库会被误判成权限被关（整库灰显、左滑删除露不出）；权限被关时歌行不露左滑删除
- 删除失败等一次性轻提示由导航层统一 Toast，本页当场可见（不再静默挂着等回到本地音乐页才迟到弹出）

### LocalMetadataEditSheet（元数据编辑面板）
- 底部弹层，字段：标题、艺术家、专辑、专辑艺术家、音轨号、年份 + 「恢复默认」
- 入口仅列表长按：本地音乐页歌曲视图 / 本地专辑页长按歌曲行
- 音轨号只接受正整数、年份只接受四位数字，首尾空白统一裁剪；留空或与媒体库标签相同不算「改过」
- 保存只写覆盖层，不回写音频文件；保存后歌曲列表、专辑分组与封面立即刷新，并同步播放器上的标题 / 艺术家展示值（不打断播放）
- **批量变体 `LocalBatchEditSheet`**：本地音乐页 / 本地专辑页编辑模式底栏「编辑信息」打开（已选 ≥1 首），与单首面板同款 ModalBottomSheet，但仅专辑 / 专辑艺术家两个自由文本字段，初始为空、不预填、无「恢复默认」按钮；**留空 = 不改**（与单首面板「留空 = 恢复文件原值」相反），两字段 trim 皆空时保存禁用；标题「批量管理」，副标题显示已选数与「只改 App 里的显示，不会修改音频文件本身」；字段标签用规范词「专辑」「专辑艺术家」（CONTEXT.md，弃用「专辑作者」）
- 批量保存走 `LocalMusicViewModel.saveBatchMetadata` 一次提交（与单首共用 `editLock`、覆盖层整表一次原子写，见 [storage.md](storage.md)）；点保存即关面板 + 退出编辑模式 + 清空勾选，Toast「已修改 n 首」（n = 提交数）经导航层 `notice` 弹出；批量只改专辑字段，播放器只显示标题 + 艺术家，不做播放器同步

### DownloadScreen（无 ViewModel）
- 直接从 `DownloadManager.downloadedSongs` / `activeDownloads` 收集下载完成与进行中的列表（实时响应）
- 点歌以当前下载列表为播放队列，来源记为 `QueueSource.DOWNLOADS`；已完成行左滑「删除」走 `DownloadManager.deleteDownload()`（删的是 App 私有文件、没有系统框，故保留 App 确认框），下载中行左滑「取消」直接执行——与本地歌曲删除（系统媒体库文件、分版本系统框）是两条独立链路

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
- 双页 Pager（横滑切页）：中部播放区首槽位为 HorizontalPager，歌曲页在左（page 0）、歌词页在右（page 1），默认落在歌曲页；歌曲页向左滑进歌词页、歌词页向右滑回歌曲页；标题区及以下区块在 Pager 外共享、不随翻页移动；槽位高度与封面一致（见高度自适应）。横滑手势由根级 scrollable(Horizontal) 经翻转包裹（pagerDragState）驱动 pagerState——scrollable 与 Pager 的滚动量符号相反，直驱会内容反向跟手（Pager 自带手势 userScrollEnabled=false），两页全域生效（含主控行、Slider 上方）；Slider 等子级水平控件优先消费不误触；拖动/fling 结束后自动 snap 回最近页（isScrollInProgress 监听）
- 歌词页（歌词区，LyricsPanel.kt）：居中歌词行（14sp→当前行 17sp 过渡），当前行品牌橙加粗、其余 PlayerIconGray；随播放进度自动滚动居中当前行（切歌/首次就绪瞬时定位，其后 200ms 动画）；用户手动滚动后 3 秒内暂停自动跟随，期满后若仍在拖动则等下次行变化恢复；点击任意行 seek 到该行起始时间；数据 `lrcText` 优先、为空下载 `lrcUrl` 解析，均无或失败显示「暂无歌词」占位；按歌曲 ID 会话内存缓存（LyricsCache，不落盘、不进缓存预算）
- 覆盖层进出（抽屉式）：打开时整页从屏幕底部滑入（200ms，FastOutSlowIn）；关闭路径（顶栏下箭头/系统返回）统一为整页向下滑出（同参数），动画结束才回调置 `playerOverlay=false`；系统返回由 PlayerScreen 内 BackHandler 接管（播放模式菜单开着时优先关菜单，其余一律触发收出）；下层页面全程原样保持
- 按压反馈：全页可点控件无波纹、无按压投影/抬升；线性图标与文字类按下变淡（α 0.5，按下约 100ms、松手约 150ms），62dp 实心播放键改为按压缩放至 0.9 回弹（graphicsLayer 置于 shadow 前，投影随钮缩放）；手感参数集中在 `PlayerScreen.kt` 底部常量（`PressDimAlpha` / `PressScaleDown` / `PressInMs` / `PressOutMs`）；封面/播放键/滑钮的常驻投影是视觉层次，不属于反馈
- 歌曲封面（歌曲页槽位）：24dp 圆角方形 1:1，藏青阴影居中，不可点（无按压反馈）；切歌时旧图淡出、新图淡入并从 0.95 放大进场（AnimatedContent，400ms 与背景过渡同步）；暂停时饱和度 1→0.7 + 叠加 18% 深色 scrim（300ms），恢复播放还原；加载中/失败/无 URL 统一显示 72dp 音符占位图标（PlayerIconGray），图片加载成功后覆盖其上
- 标题区（左对齐）：歌名 / 歌手 / 队列位置（`当前序号 / 队列总数`）
- 功能图标行（仅在线歌曲；本地歌曲整行不出现）：喜欢 / 播放设置 / 下载 / 加入歌单 / 更多；图标 28dp 居中于 48dp 触控框，行 padding 18dp = 28dp 内容线 − (48−28)/2，首末图标盒边缘与进度条两端对齐；喜欢为本地实心/描边心形切换；下载点击将当前歌曲入队下载，下载中图标位变 28dp 品牌橙环形进度 + 中心百分比数字（排队/总长未知时为不确定式旋转圈，点击无反应），已下载图标变 DownloadedGreen（#4CAF50）且点击无反应（取消/删除在下载列表页）；下载中/已下载均为不可操作态、无按压反馈（已下载由 `PlayerGlyph` `enabled = false` 实现，环形进度本身不可点）；播放设置 / 加入歌单 / 更多点击打开对应底部浮层（播放设置=定时关闭，加入歌单=勾选我的歌单/新建，更多=当前歌曲扩展操作）；定时生效期间播放设置图标右上角小圆点角标
- 本地歌曲差异（`Song.isLocalSong()`，ID 走 `local:` 命名空间或 URL 是 `content://`）：
  - 功能图标行整行不出现（纯播放形态）：收藏 / 下载 / 加入歌单对本地歌曲无意义或必然失败，播放设置与更多也一并收起
  - 页签只剩「歌曲」，没有歌词页与刷新入口，播放期间不发 `song_info` 请求；切回在线歌曲后全部恢复
  - 元数据编辑只走列表长按入口（本地音乐页歌曲视图 / 本地专辑页）；已设定时本地播放期间仍全局生效，不可见、不可取消，切回在线歌曲恢复可见可操作
  - 队列来源为 `QueueSource.LOCAL`，播放模式按该来源单独记忆
- 进度条：藏青 4dp 细轨道 + 16dp 小圆点滑钮（20dp slot 容器内居中，规避 M3 slot 贴左上问题）；M3 Slider 轨道两端各内缩半个 slot 宽（10dp），Slider 容器 padding 18dp（= 28 − 10）补偿，使轨道两端落在 28dp 内容线上（改滑钮/slot 尺寸需同步调整）；下方时间标签行左右 28dp
- 主控行（钉底常驻）：播放模式（三态：随机播放/顺序播放/单曲循环，ADR 0008，图标随模式换图形——随机=乱序箭头、顺序=循环环绕、单曲循环=环绕+1；顺序播放为默认态 PlayerIconGray，其余 BrandOrange；点击图标弹出白底圆角菜单（宽随最宽行整行内容右边不留白，IntrinsicSize.Max——不可用 Min：中文可逐字断行会窄到单字竖排；底边在图标顶边上方 20dp 防误触，行内 20dp 图标+14sp 文字，当前模式行 PlaceholderBg 高亮满行宽），菜单打开时再点图标按 随机→顺序→单曲循环 轮转且高亮跟随不关闭；状态持久化于 PlayerSettingsManager，Service collect 落到 ExoPlayer：随机=shuffleModeEnabled+REPEAT_ALL、顺序=REPEAT_ALL、单曲循环=REPEAT_ONE）/ 上一首 / 62dp 藏青大圆播放键 / 下一首 / 播放队列入口（打开队列浮层，点选为队列内跳播），含 navigationBarsPadding 手势区避让
- 高度自适应（中部播放区正常屏固定高度、整体不可拖动）：外层 BoxWithConstraints 读取中部视口，首槽位（双页 Pager：歌词页/封面页）高度 = 封面边长 = min(0.76×宽, 剩余高度)（固定内容高度按 280dp×系统字体缩放保守估算、宁大勿小，估算误差全部由间距吸收），四段间距 = 最小值（30/16/10/18）+ weight 均摊剩余，内容总高恒等于视口；仅矮屏（视口放不下 0.5×宽下限封面 + 保守估算内容）退回整体滚动托底，主控行不随滚
- 通过点击 MiniPlayer 打开（`playerOverlay` 覆盖层，非路由）
- 返回按钮调用 `onBackClick()` 返回上一页（无菜单栏/底部栏干扰）；顶栏下箭头与系统返回同效

## 添加新页面的步骤

1. 在 `Screen.kt` 中添加新的 `object` 路由
2. 在 `AppNavigation.kt` 的 `NavHost` 中添加 `composable(Screen.Xxx.route) { ... }`
3. 如果是全屏页面（不显示 TopBar/BottomBar），在 `AppNavigation.kt` 中的 `if (isPlayerScreen)` 逻辑前添加特殊处理
4. 如需底部 Tab 显示，加入 `Screen.bottomNavRoutes`
5. 如需抽屉项，在 `DrawerContent.kt` 中添加导航项

## 注意事项

- `PlayerViewModel` 通过 `by viewModels()` 在 `MainActivity` 创建，然后通过参数逐层传递给页面 Composable（非 Hilt inject）
- `LocalMusicViewModel` 的所有权是**隐式共享**：实例由 `AppNavigation` 的 Compose `viewModel()` 构造，`MainActivity` 的导入胶水经 `by viewModels()` 访问到的是同一实例——两者都解析到宿主 Activity 的同一个 ViewModelStore + 默认 factory + 默认 key（类全名）。前提：全仓无 `LocalViewModelStoreOwner` 覆写、无自定义 `ViewModelProvider.Factory`、无自定义 key（`PlayerViewModel` 先例同机制）。将来若引入 owner 覆写或自定义 factory/key，此共享即失效，导入胶水需改为显式传参（照 `PlayerViewModel` 范式）
- Settings 页面与各详情页（含搜索组三页）的 `gesturesEnabled = false` 是为了防止侧滑手势与页面内部滑动冲突
- `Screen.titleOf(route)` 函数用于 `TopBar` 动态显示当前页面标题，新增路由时需同步更新此函数
- **[坑] TopBar 文本溢出处理**：使用 `TextOverflow.Ellipsis` 时，**必须导入** `androidx.compose.ui.text.style.TextOverflow`（注意包含 `.style.`），而不是 `androidx.compose.ui.text.TextOverflow`。后者不存在，会导致编译错误。参考 `SongListItem.kt`、`MiniPlayer.kt` 等其他组件的导入方式。
- **[坑] 播放器页面动画命名冲突**：播放模式枚举不能用 `RepeatMode` 命名，因为 Compose 动画库已有同名枚举；现嵌套于 `PlayerSettingsManager` 内命名 `PlayMode`，图标映射集中在 `PlayerScreen.kt` 的 `playModeIcon()`（ADR 0008）。
- **[坑] M3 Slider 轨道两端内缩**：material3 的 `Slider` 会把轨道宽度减去一个 slot 宽并右移半个 slot 宽，轨道两端天然不贴容器边。当前 thumb slot 被 token 强制为 20dp，要贴 28dp 内容线，需给 Slider `padding(horizontal = 18.dp)`（= 28 − 10）补偿；slot/滑钮尺寸变化时同步调整（见 `PlayerScreen.kt` thumb 注释）。
- **[坑] Palette 提取封面主色**：Palette 不支持 hardware bitmap，提取用 `ImageRequest` 必须 `allowHardware(false)`（否则 `drawable as? BitmapDrawable` 得到 null）；用 64px 小图取色即可；主色按歌曲 ID 会话内缓存避免切回重复提取；近灰封面（饱和度 < 0.04）不抬饱和度，否则 HSL 钳制会把灰色染成淡红（见 `PlayerScreen.kt` `extractCoverPalette`）。

package com.qqt.music.ui.navigation

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import androidx.lifecycle.viewmodel.compose.viewModel
import com.qqt.music.data.local.LocalDeleteAction
import com.qqt.music.data.local.decodeLocalAlbumKey
import com.qqt.music.data.local.encodeLocalAlbumKey
import com.qqt.music.data.local.isAllSelected
import com.qqt.music.data.local.toggleSelectAll
import com.qqt.music.ui.components.DrawerContent
import com.qqt.music.ui.components.MiniPlayer
import com.qqt.music.ui.components.MusicBottomBar
import com.qqt.music.ui.components.MusicTopBar
import com.qqt.music.ui.components.OfflineBanner
import com.qqt.music.ui.components.navigateSingle
import com.qqt.music.ui.screens.album.AlbumScreen
import com.qqt.music.ui.screens.albumsongs.AlbumSongsScreen
import com.qqt.music.ui.screens.bannersongs.BannerSongsScreen
import com.qqt.music.ui.screens.category.CategoryScreen
import com.qqt.music.ui.screens.categoryalbums.CategoryAlbumsScreen
import com.qqt.music.ui.screens.booklist.BookListScreen
import com.qqt.music.ui.screens.reader.ReaderScreen
import com.qqt.music.ui.screens.download.DownloadScreen
import com.qqt.music.ui.screens.favorites.FavoritesScreen
import com.qqt.music.ui.screens.home.HomeScreen
import com.qqt.music.ui.screens.latest.LatestScreen
import com.qqt.music.ui.screens.localalbum.LocalAlbumScreen
import com.qqt.music.ui.screens.localmusic.LocalBatchEditSheet
import com.qqt.music.ui.screens.localmusic.LocalMusicBatchBar
import com.qqt.music.ui.screens.localmusic.LocalMusicEditTopBar
import com.qqt.music.ui.screens.localmusic.LocalMusicScreen
import com.qqt.music.ui.screens.localmusic.LocalMusicTopBarMenu
import com.qqt.music.ui.screens.localmusic.LocalMusicViewModel
import com.qqt.music.ui.screens.mylist.MyListScreen
import com.qqt.music.ui.screens.mylist.PlaylistDetailScreen
import com.qqt.music.ui.screens.player.PlayerScreen
import com.qqt.music.ui.screens.search.ArtistSongsScreen
import com.qqt.music.ui.screens.search.SearchScreen
import com.qqt.music.ui.screens.search.SearchSongsScreen
import com.qqt.music.viewmodel.PlayerViewModel
import kotlinx.coroutines.launch

@Composable
fun AppNavigation(playerViewModel: PlayerViewModel) {
    val navController = rememberNavController()
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route

    val showBottomNav = currentRoute in Screen.bottomNavRoutes
    val isBannerSongs = currentRoute?.startsWith("banner_songs") == true
    val isAlbumSongs = currentRoute?.startsWith("album_songs") == true
    val isCategoryAlbums = currentRoute?.startsWith("category_albums") == true
    val isBookList = currentRoute?.startsWith("book_list") == true
    val isReader = currentRoute?.startsWith("reader") == true
    val isMyPlaylistDetail = currentRoute?.startsWith("mylist_detail") == true
    val isSearchSongs = currentRoute?.startsWith("search_songs") == true
    val isArtistSongs = currentRoute?.startsWith("artist_songs") == true
    val isLocalAlbum = currentRoute?.startsWith("local_album") == true
    val isSearch = currentRoute == Screen.Search.route
    val showBackButton = isBannerSongs || isAlbumSongs || isCategoryAlbums || isBookList || isReader || isMyPlaylistDetail || isSearchSongs || isArtistSongs || isSearch || isLocalAlbum

    // 本地音乐：权限、库列表与刷新提升到导航层，页面与顶栏菜单共用一份实例
    val localMusicViewModel: LocalMusicViewModel = viewModel()
    val localMusicPermissionGranted by localMusicViewModel.permissionGranted.collectAsState()
    // 本地歌曲播放失败（文件被删、授权被回收或损坏）：提示「文件不可用」并把该行标为不可用。
    // 放在导航层而不是本地音乐页：在播放器 / 专辑页触发时同样要提示与落状态；不自动重扫、不重试
    val unavailableLocalSongId by playerViewModel.unavailableLocalSongId.collectAsState()
    LaunchedEffect(unavailableLocalSongId) {
        val songId = unavailableLocalSongId ?: return@LaunchedEffect
        localMusicViewModel.markUnavailable(songId)
        Toast.makeText(context, "文件不可用", Toast.LENGTH_SHORT).show()
        playerViewModel.consumeUnavailableLocalSong()
    }
    // 本地歌曲删除（票据02）：系统删除/授权框与写入权限框的 launcher 都注册在导航层（宿主常驻）——
    // 从专辑页发起删除时本地音乐页已离开组合，页面级 launcher 会随之注销丢回调
    val systemDeleteLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        // IntentSender 发送自身失败时契约回 RESULT_CANCELED + EXTRA_SEND_INTENT_EXCEPTION：
        // 不区分就会被接缝当成用户取消而静默，这里显式标成拉起失败，让用户看到「删除失败」
        localMusicViewModel.onSystemDeleteDialogResult(
            resultCode = result.resultCode,
            launchFailed = result.data?.hasExtra(
                ActivityResultContracts.StartIntentSenderForResult.EXTRA_SEND_INTENT_EXCEPTION
            ) == true,
        )
    }
    val writePermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> localMusicViewModel.onWritePermissionResult(granted) }
    // ViewModel 产出「下一步动作」（系统框 / 权限框），这里拉起后清空（与 notice 同范式）
    val pendingDeleteAction by localMusicViewModel.pendingDeleteAction.collectAsState()
    LaunchedEffect(pendingDeleteAction) {
        when (val action = pendingDeleteAction) {
            is LocalDeleteAction.LaunchSystemDialog -> systemDeleteLauncher.launch(action.request)
            is LocalDeleteAction.RequestWritePermission -> writePermissionLauncher.launch(action.permission)
            else -> return@LaunchedEffect
        }
        localMusicViewModel.consumePendingDeleteAction()
    }
    // 删除成功且删空的是当前专辑 → 自动返回本地音乐页：绑定在删除成功动作上，
    // 不做「专辑消失就 pop」的通用观察——进程被杀恢复到本就不存在的专辑时仍显示既有空态
    val deleteSucceeded by localMusicViewModel.deleteSucceeded.collectAsState()
    LaunchedEffect(deleteSucceeded) {
        val event = deleteSucceeded ?: return@LaunchedEffect
        localMusicViewModel.consumeDeleteSucceeded()
        if (!isLocalAlbum || event.remainingSongs != 0) return@LaunchedEffect
        val currentAlbumKey = backStackEntry?.arguments?.getString("lid")?.let(::decodeLocalAlbumKey)
        if (currentAlbumKey == event.albumKey) navController.popBackStack()
    }
    // 本地音乐域的一次性轻提示（重扫数量 / 刷新失败 / 删除失败）：导航层统一 Toast——
    // 专辑页没有页面级 collector，放这里两页都覆盖（本地音乐页原消费点已删，避免双弹）
    val notice by localMusicViewModel.notice.collectAsState()
    LaunchedEffect(notice) {
        val text = notice ?: return@LaunchedEffect
        Toast.makeText(context, text, Toast.LENGTH_SHORT).show()
        localMusicViewModel.consumeNotice()
    }
    val isLocalMusic = currentRoute == Screen.LocalMusic.route
    // 本地音乐视图状态（默认专辑）提升到导航层，页面与顶栏菜单共用：remember 以 local_music 的
    // 返回栈条目为键——离开本页（含进本地专辑页，此时条目虽在栈上但已不是当前条目）键即置空、
    // 状态重建，返回时总是专辑视图，「不记忆上次选择」由结构保证；播放器覆盖层不切 NavHost，
    // 条目不变，视图状态保留
    val localMusicEntry = if (isLocalMusic) backStackEntry else null
    var localMusicShowAlbums by remember(localMusicEntry) { mutableStateOf(true) }
    // 编辑模式开关与勾选集合（spec「编辑模式状态与选择态」，票据02）：与视图状态同键——
    // 离开本页（含切底部 Tab / 进本地专辑页）键即置空、状态重建，「离开即退出、勾选不跨页保留」
    // 由结构保证；不放 LocalMusicViewModel——它是导航层单例、与专辑页共用，放进去会跨页泄漏
    var localMusicEditing by remember(localMusicEntry) { mutableStateOf(false) }
    var localMusicSelection by remember(localMusicEntry) { mutableStateOf(emptySet<String>()) }
    var localMusicBatchPanelOpen by remember(localMusicEntry) { mutableStateOf(false) }
    // 可见列表（已按排除清单过滤；权限被收回时整库灰显）：全选候选集与批量保存的提交集都用它
    val localMusicSongs by localMusicViewModel.songs.collectAsState()

    // 退出编辑模式：清空勾选、关批量面板（取消 / 系统返回 / 保存成功 / 权限被收回共用）
    fun exitLocalMusicEditMode() {
        localMusicEditing = false
        localMusicSelection = emptySet()
        localMusicBatchPanelOpen = false
    }
    // 编辑中权限被收回：退出编辑模式，整库灰显沿用页面既有口径（spec 用户故事 11）
    LaunchedEffect(localMusicPermissionGranted) {
        if (!localMusicPermissionGranted) exitLocalMusicEditMode()
    }
    // 系统返回只退出编辑模式、不退出页面；面板打开时先收面板（未保存输入丢弃），再按一次才退出
    BackHandler(enabled = isLocalMusic && localMusicEditing) {
        if (localMusicBatchPanelOpen) {
            localMusicBatchPanelOpen = false
        } else {
            exitLocalMusicEditMode()
        }
    }
    // 本地音乐域（歌曲/专辑视图与本地专辑页）不发任何请求：断网/服务不可达横幅对它没有意义（spec 用户故事 38）
    val isLocalMusicArea = isLocalMusic || isLocalAlbum
    // 播放器不走导航：纯覆盖层状态控制开关。下层 NavHost 永不切页，
    // 关闭时下层原样即时露出（无任何过渡动画），滚动位置等页面状态全程保留。
    // 系统返回由 PlayerScreen 内部 BackHandler 接管（统一走整页下滑收出），顶层无需重复拦截
    var playerOverlay by remember { mutableStateOf(false) }

    // 常驻下层（抽屉 + 页面 + MiniPlayer）+ 播放器全屏覆盖层：下层页面始终原样保持，
    // 收起整页滑出时露出的就是它本身，关闭即显、无过渡
    Box(modifier = Modifier.fillMaxSize()) {
        ModalNavigationDrawer(
            drawerState = drawerState,
            drawerContent = {
                DrawerContent(
                    navController = navController,
                    drawerState = drawerState,
                    currentRoute = currentRoute,
                )
            },
            gesturesEnabled = !isBannerSongs && !isAlbumSongs && !isCategoryAlbums && !isBookList && !isReader && !isMyPlaylistDetail && !isSearchSongs && !isArtistSongs && !isSearch && !isLocalAlbum,
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                Scaffold(
                    topBar = {
                        if (isLocalMusic && localMusicEditing) {
                            // 编辑态顶栏：取消 / 全选·取消全选 / 已选 n 首，⋮ 菜单隐藏（spec「顶栏与底栏」）；
                            // 全选 = 可见列表全部可用行，已全选再点清空（纯规则见 LocalMusicEditRules）
                            LocalMusicEditTopBar(
                                selectedCount = localMusicSelection.size,
                                allSelected = isAllSelected(localMusicSongs, localMusicSelection),
                                onCancel = { exitLocalMusicEditMode() },
                                onToggleSelectAll = {
                                    localMusicSelection = toggleSelectAll(localMusicSongs, localMusicSelection)
                                },
                            )
                        } else {
                            MusicTopBar(
                                title = Screen.titleOf(currentRoute),
                                onMenuClick = { scope.launch { drawerState.open() } },
                                onSearchClick = { navController.navigate(Screen.Search.route) },
                                showBackButton = showBackButton,
                                onBackClick = { navController.popBackStack() },
                                // 本地音乐不兼搜索：右侧换成该页自己的菜单（切换视图 / 批量管理 / 重新扫描 / 排除的文件夹）
                                actions = if (isLocalMusic) {
                                    {
                                        LocalMusicTopBarMenu(
                                            viewModel = localMusicViewModel,
                                            showAlbums = localMusicShowAlbums,
                                            onToggleView = { localMusicShowAlbums = !localMusicShowAlbums },
                                            editEnabled = localMusicPermissionGranted,
                                            onEnterEdit = { localMusicEditing = true },
                                            rescanEnabled = localMusicPermissionGranted,
                                            onRescan = { localMusicViewModel.rescan() },
                                        )
                                    }
                                } else null,
                            )
                        }
                    },
                    bottomBar = {
                        if (showBottomNav) {
                            MusicBottomBar(navController = navController, currentRoute = currentRoute)
                        }
                    },
                    // 系统导航栏空档不由 Scaffold 垫入内容：无底部导航的页面若被垫高，
                    // 内容与外层 MiniPlayer 之间会出现页面底色的空隙；
                    // 系统栏区域由 MiniPlayer 底部的白底 spacer 统一负责
                    contentWindowInsets = WindowInsets(0, 0, 0, 0),
                    modifier = Modifier.weight(1f),
                ) { innerPadding ->
                    // 断网横幅（ADR 0012）置于内容区顶部：TopBar 下方，出现时页面内容整体下移；
                    // 全屏播放器为覆盖层，覆盖期间自然遮住横幅（播放场景无需离线提示）
                    Column(modifier = Modifier.padding(innerPadding)) {
                        if (!isLocalMusicArea) OfflineBanner()
                        NavHost(
                            navController = navController,
                            startDestination = Screen.Home.route,
                            modifier = Modifier.weight(1f),
                            // 页面过渡：轻微滑动 + 淡入淡出，避免生硬跳变
                            enterTransition = {
                                fadeIn(tween(220)) + slideInHorizontally(tween(220)) { it / 14 }
                            },
                            exitTransition = { fadeOut(tween(160)) },
                            popEnterTransition = { fadeIn(tween(220)) },
                            popExitTransition = {
                                fadeOut(tween(180)) + slideOutHorizontally(tween(180)) { it / 14 }
                            },
                            ) {
                            composable(Screen.Home.route) {
                                HomeScreen(
                                    playerViewModel = playerViewModel,
                                    onBannerClick = { banner ->
                                        when {
                                            banner.songs.isNotEmpty() -> {
                                                BannerNav.banner = banner
                                                navController.navigate("banner_songs/${banner.id}")
                                            }
                                            banner.link.isNotBlank() -> {
                                                runCatching {
                                                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(banner.link)))
                                                }
                                            }
                                        }
                                    },
                                )
                            }
                            composable(Screen.LocalMusic.route) {
                                LocalMusicScreen(
                                    viewModel = localMusicViewModel,
                                    playerViewModel = playerViewModel,
                                    showAlbums = localMusicShowAlbums,
                                    editing = localMusicEditing,
                                    selection = localMusicSelection,
                                    // 点行 / 长按切换勾选：灰显行已被行内 enabled 挡住，这里只动集合
                                    onToggleSelect = { song ->
                                        localMusicSelection = if (song.id in localMusicSelection) {
                                            localMusicSelection - song.id
                                        } else {
                                            localMusicSelection + song.id
                                        }
                                    },
                                    onAlbumClick = { album ->
                                        // 专辑键按 URL 安全编码后作为路由参数；专辑名给顶栏标题
                                        LocalAlbumNav.albumName = album.name
                                        navController.navigate("local_album/${encodeLocalAlbumKey(album.key)}")
                                    },
                                )
                            }
                            composable(
                                route = Screen.LocalAlbum.route,
                                arguments = listOf(navArgument("lid") { type = NavType.StringType }),
                            ) { entry ->
                                // 页面自己从路由参数解出专辑键：进程被杀后重建也能定位回同一张专辑
                                val albumKey = entry.arguments?.getString("lid")?.let(::decodeLocalAlbumKey)
                                LocalAlbumScreen(
                                    albumKey = albumKey,
                                    viewModel = localMusicViewModel,
                                    playerViewModel = playerViewModel,
                                )
                            }
                            composable(Screen.Download.route) {
                                DownloadScreen(playerViewModel = playerViewModel)
                            }
                            composable(Screen.Category.route) {
                                CategoryScreen(
                                    onCategoryClick = { cat ->
                                        // 分类分流（书籍阅读域 ADR 0011）：书籍分类进书单页，音乐分类走既有专辑页
                                        CategoryNav.category = cat
                                        if (cat.isBook) navController.navigate("book_list/${cat.id}")
                                        else navController.navigate("category_albums/${cat.id}")
                                    },
                                )
                            }
                            composable(Screen.Latest.route) {
                                LatestScreen(playerViewModel = playerViewModel)
                            }
                            composable(Screen.Album.route) {
                                AlbumScreen(
                                    onAlbumClick = { album ->
                                        AlbumNav.album = album
                                        navController.navigate("album_songs/${album.id}")
                                    },
                                )
                            }
                            composable(Screen.MyList.route) {
                                MyListScreen(
                                    onPlaylistClick = { playlist ->
                                        MyListNav.playlistId = playlist.id
                                        MyListNav.playlistName = playlist.name
                                        navController.navigate("mylist_detail/${playlist.id}")
                                    },
                                )
                            }
                            composable(Screen.MyPlaylistDetail.route) {
                                PlaylistDetailScreen(playerViewModel = playerViewModel)
                            }
                            composable(Screen.Favorites.route) {
                                FavoritesScreen(playerViewModel = playerViewModel)
                            }
                            composable(Screen.BannerSongs.route) {
                                BannerSongsScreen(playerViewModel = playerViewModel)
                            }
                            composable(Screen.AlbumSongs.route) {
                                AlbumSongsScreen(playerViewModel = playerViewModel)
                            }
                            composable(Screen.Search.route) {
                                SearchScreen(
                                    playerViewModel = playerViewModel,
                                    onAlbumClick = { album ->
                                        AlbumNav.album = album
                                        navController.navigate("album_songs/${album.id}")
                                    },
                                    onArtistClick = { artist ->
                                        ArtistNav.artist = artist
                                        navController.navigate(Screen.ArtistSongs.route)
                                    },
                                    onMoreSongs = { query ->
                                        SearchNav.query = query
                                        navController.navigate(Screen.SearchSongs.route)
                                    },
                                )
                            }
                            composable(Screen.SearchSongs.route) {
                                SearchSongsScreen(playerViewModel = playerViewModel)
                            }
                            composable(Screen.ArtistSongs.route) {
                                ArtistSongsScreen(playerViewModel = playerViewModel)
                            }
                            composable(Screen.BookList.route) {
                                BookListScreen(
                                    onBookClick = { book ->
                                        ReaderNav.book = book
                                        navController.navigate("reader/${book.id}")
                                    },
                                )
                            }
                            composable(Screen.Reader.route) {
                                ReaderScreen()
                            }
                            composable(Screen.CategoryAlbums.route) {
                                CategoryAlbumsScreen(
                                    onAlbumClick = { album ->
                                        AlbumNav.album = album
                                        navController.navigate("album_songs/${album.id}")
                                    },
                                    onAutoOpen = { album ->
                                        // 分类下仅一张专辑：直跳专辑歌曲页并替换当前栈条目，返回时直接回到分类列表页
                                        AlbumNav.album = album
                                        navController.navigate("album_songs/${album.id}") {
                                            popUpTo(Screen.CategoryAlbums.route) { inclusive = true }
                                        }
                                    },
                                )
                        }
                    }
                    // 编辑模式底栏（票据02）：只在编辑态渲染，位于 Scaffold 内容区底部——
                    // 底部导航在 Scaffold bottomBar、MiniPlayer 在 Scaffold 之外下方，都不与本栏重叠
                    if (isLocalMusic && localMusicEditing) {
                        LocalMusicBatchBar(
                            selectedCount = localMusicSelection.size,
                            onEditInfo = { localMusicBatchPanelOpen = true },
                        )
                    }
                }
            }
                // Mini player below the scaffold：与底部导航连为一体的扁平长条；
                // 书籍域两页（书单/阅读）不挂——阅读场景与音乐控件互不干扰（ADR 0011）
                if (!isBookList && !isReader) {
                    MiniPlayer(
                        playerViewModel = playerViewModel,
                        onPlayerClick = { playerOverlay = true },
                        showTopDivider = !showBottomNav,
                    )
                }
            }
        }

        // 全屏播放器覆盖层：playerOverlay 为 true 时组合，叠在常驻下层之上；收起整页滑出即露出下层页面。
        // 播放器不接 LocalMusicViewModel：本地歌曲功能图标行整行不出现，元数据编辑只走列表长按入口
        if (playerOverlay) {
            PlayerScreen(
                playerViewModel = playerViewModel,
                onBackClick = { playerOverlay = false },
            )
        }

        // 批量管理面板（票据02，spec「批量面板」）：编辑模式底栏「编辑信息」打开。
        // 保存接票据01 的一次提交入口（整表一次原子落盘），成功即关面板 + 退出编辑模式 + 清空勾选；
        // Toast「已修改 n 首」走上方既有 notice 链路自动弹出，这里不另弹、不做播放器同步
        if (localMusicEditing && localMusicBatchPanelOpen) {
            LocalBatchEditSheet(
                selectedCount = localMusicSelection.size,
                onSave = { albumInput, albumArtistInput ->
                    val selected = localMusicSongs.filter { it.id in localMusicSelection }
                    localMusicViewModel.saveBatchMetadata(selected, albumInput, albumArtistInput)
                    exitLocalMusicEditMode()
                },
                onDismiss = { localMusicBatchPanelOpen = false },
            )
        }

        // ≤28 的 App 内删除确认框（票据02）：29/30+ 系统框即唯一确认，此框永不出现。
        // 导航层渲染、两页共用；文案沿用下载页句式、语义改对本地歌曲（不设 color，走 M3 默认）
        val pendingDeleteConfirm by localMusicViewModel.pendingDeleteConfirm.collectAsState()
        pendingDeleteConfirm?.let { song ->
            AlertDialog(
                onDismissRequest = { localMusicViewModel.cancelDeleteConfirm() },
                title = { Text("删除本地歌曲") },
                text = { Text("「${song.displayTitle()}」将从设备存储删除，其他应用也不再可见。") },
                confirmButton = {
                    TextButton(onClick = { localMusicViewModel.confirmDelete() }) { Text("删除") }
                },
                dismissButton = {
                    TextButton(onClick = { localMusicViewModel.cancelDeleteConfirm() }) { Text("取消") }
                },
            )
        }
    }
}

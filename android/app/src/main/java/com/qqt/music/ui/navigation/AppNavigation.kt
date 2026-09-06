package com.qqt.music.ui.navigation

import android.content.Intent
import android.net.Uri
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
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.Scaffold
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.qqt.music.ui.components.DrawerContent
import com.qqt.music.ui.components.MiniPlayer
import com.qqt.music.ui.components.MusicBottomBar
import com.qqt.music.ui.components.MusicTopBar
import com.qqt.music.ui.components.navigateSingle
import com.qqt.music.ui.screens.album.AlbumScreen
import com.qqt.music.ui.screens.albumsongs.AlbumSongsScreen
import com.qqt.music.ui.screens.bannersongs.BannerSongsScreen
import com.qqt.music.ui.screens.category.CategoryScreen
import com.qqt.music.ui.screens.categoryalbums.CategoryAlbumsScreen
import com.qqt.music.ui.screens.download.DownloadScreen
import com.qqt.music.ui.screens.favorites.FavoritesScreen
import com.qqt.music.ui.screens.home.HomeScreen
import com.qqt.music.ui.screens.latest.LatestScreen
import com.qqt.music.ui.screens.mylist.MyListScreen
import com.qqt.music.ui.screens.mylist.PlaylistDetailScreen
import com.qqt.music.ui.screens.player.PlayerScreen
import com.qqt.music.ui.screens.recent.RecentScreen
import com.qqt.music.ui.screens.settings.SettingsScreen
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
    val isMyPlaylistDetail = currentRoute?.startsWith("mylist_detail") == true
    val showBackButton = currentRoute == Screen.Settings.route || isBannerSongs || isAlbumSongs || isCategoryAlbums || isMyPlaylistDetail
    // 播放器不走导航：纯覆盖层状态控制开关。下层 NavHost 永不切页，
    // 关闭时下层原样即时露出（无任何过渡动画），滚动位置等页面状态全程保留。
    // 系统返回由 PlayerScreen 内部 BackHandler 接管（统一走整页下滑收出），顶层无需重复拦截
    var playerOverlay by remember { mutableStateOf(false) }

    // 常驻下层（抽屉 + 页面 + MiniPlayer）+ 播放器全屏覆盖层：下层页面始终原样保持，
    // 下拉收起整页滑出时露出的就是它本身，关闭即显、无过渡
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
            gesturesEnabled = currentRoute != Screen.Settings.route && !isBannerSongs && !isAlbumSongs && !isCategoryAlbums && !isMyPlaylistDetail,
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                Scaffold(
                    topBar = {
                        MusicTopBar(
                            title = Screen.titleOf(currentRoute),
                            onMenuClick = { scope.launch { drawerState.open() } },
                            onSearchClick = {},
                            showBackButton = showBackButton,
                            onBackClick = { navController.popBackStack() },
                        )
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
                    NavHost(
                        navController = navController,
                        startDestination = Screen.Home.route,
                        modifier = Modifier.padding(innerPadding),
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
                    composable(Screen.Recent.route) {
                        RecentScreen(playerViewModel = playerViewModel)
                    }
                    composable(Screen.Download.route) {
                        DownloadScreen(playerViewModel = playerViewModel)
                    }
                    composable(Screen.Category.route) {
                        CategoryScreen(
                            onCategoryClick = { cat ->
                                CategoryNav.category = cat
                                navController.navigate("category_albums/${cat.id}")
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
                    composable(Screen.Settings.route) {
                        SettingsScreen()
                    }
                    composable(Screen.BannerSongs.route) {
                        BannerSongsScreen(playerViewModel = playerViewModel)
                    }
                    composable(Screen.AlbumSongs.route) {
                        AlbumSongsScreen(playerViewModel = playerViewModel)
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
                }
                // Mini player below the scaffold：与底部导航连为一体的扁平长条
                MiniPlayer(
                    playerViewModel = playerViewModel,
                    onPlayerClick = { playerOverlay = true },
                    showTopDivider = !showBottomNav,
                )
            }
        }

        // 全屏播放器覆盖层：playerOverlay 为 true 时组合，叠在常驻下层之上；下拉收起整页滑出即露出下层页面
        if (playerOverlay) {
            PlayerScreen(
                playerViewModel = playerViewModel,
                onBackClick = { playerOverlay = false },
            )
        }
    }
}

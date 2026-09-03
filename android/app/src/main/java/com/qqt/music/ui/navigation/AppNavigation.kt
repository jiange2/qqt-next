package com.qqt.music.ui.navigation

import android.content.Intent
import android.net.Uri
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.layout.Column
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
import com.qqt.music.ui.screens.album.AlbumScreen
import com.qqt.music.ui.screens.albumsongs.AlbumSongsScreen
import com.qqt.music.ui.screens.artist.ArtistScreen
import com.qqt.music.ui.screens.bannersongs.BannerSongsScreen
import com.qqt.music.ui.screens.category.CategoryScreen
import com.qqt.music.ui.screens.categoryalbums.CategoryAlbumsScreen
import com.qqt.music.ui.screens.download.DownloadScreen
import com.qqt.music.ui.screens.favorites.FavoritesScreen
import com.qqt.music.ui.screens.home.HomeScreen
import com.qqt.music.ui.screens.latest.LatestScreen
import com.qqt.music.ui.screens.mylist.MyListScreen
import com.qqt.music.ui.screens.player.PlayerScreen
import com.qqt.music.ui.screens.playlist.PlaylistScreen
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
    val showBackButton = currentRoute == Screen.Settings.route || isBannerSongs || isAlbumSongs || isCategoryAlbums
    val isPlayerScreen = currentRoute == Screen.Player.route

    // If player screen, show it fullscreen without drawer/topbar/bottombar
    if (isPlayerScreen) {
        PlayerScreen(
            playerViewModel = playerViewModel,
            onBackClick = { navController.popBackStack() },
        )
    } else {
        ModalNavigationDrawer(
            drawerState = drawerState,
            drawerContent = {
                DrawerContent(
                    navController = navController,
                    drawerState = drawerState,
                    currentRoute = currentRoute,
                )
            },
            gesturesEnabled = currentRoute != Screen.Settings.route && !isBannerSongs && !isAlbumSongs && !isCategoryAlbums,
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
                            onAlbumClick = { album ->
                                AlbumNav.album = album
                                navController.navigate("album_songs/${album.id}")
                            },
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
                    composable(Screen.Artist.route) {
                        ArtistScreen()
                    }
                    composable(Screen.Album.route) {
                        AlbumScreen(
                            onAlbumClick = { album ->
                                AlbumNav.album = album
                                navController.navigate("album_songs/${album.id}")
                            },
                        )
                    }
                    composable(Screen.Playlist.route) {
                        PlaylistScreen()
                    }
                    composable(Screen.MyList.route) {
                        MyListScreen()
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
                    composable(Screen.Player.route) {
                        PlayerScreen(
                            playerViewModel = playerViewModel,
                            onBackClick = { navController.popBackStack() },
                        )
                    }
                }
                }
                // Mini player below the scaffold
                MiniPlayer(
                    playerViewModel = playerViewModel,
                    onPlayerClick = { navController.navigate(Screen.Player.route) },
                )
            }
        }
    }
}

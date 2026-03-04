package com.qqt.music.ui.navigation

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.Scaffold
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.qqt.music.ui.components.DrawerContent
import com.qqt.music.ui.components.MiniPlayer
import com.qqt.music.ui.components.MusicBottomBar
import com.qqt.music.ui.components.MusicTopBar
import com.qqt.music.ui.screens.album.AlbumScreen
import com.qqt.music.ui.screens.artist.ArtistScreen
import com.qqt.music.ui.screens.category.CategoryScreen
import com.qqt.music.ui.screens.download.DownloadScreen
import com.qqt.music.ui.screens.favorites.FavoritesScreen
import com.qqt.music.ui.screens.home.HomeScreen
import com.qqt.music.ui.screens.latest.LatestScreen
import com.qqt.music.ui.screens.mylist.MyListScreen
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
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route

    val showBottomNav = currentRoute in Screen.bottomNavRoutes
    val showBackButton = currentRoute == Screen.Settings.route

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            DrawerContent(
                navController = navController,
                drawerState = drawerState,
                currentRoute = currentRoute,
            )
        },
        gesturesEnabled = currentRoute != Screen.Settings.route,
    ) {
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
                Column {
                    MiniPlayer(playerViewModel)
                    if (showBottomNav) {
                        MusicBottomBar(navController = navController, currentRoute = currentRoute)
                    }
                }
            },
        ) { innerPadding ->
            NavHost(
                navController = navController,
                startDestination = Screen.Home.route,
                modifier = Modifier.padding(innerPadding),
            ) {
                composable(Screen.Home.route) {
                    HomeScreen(playerViewModel = playerViewModel)
                }
                composable(Screen.Recent.route) {
                    RecentScreen(playerViewModel = playerViewModel)
                }
                composable(Screen.Download.route) {
                    DownloadScreen(playerViewModel = playerViewModel)
                }
                composable(Screen.Category.route) {
                    CategoryScreen()
                }
                composable(Screen.Latest.route) {
                    LatestScreen(playerViewModel = playerViewModel)
                }
                composable(Screen.Artist.route) {
                    ArtistScreen()
                }
                composable(Screen.Album.route) {
                    AlbumScreen()
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
            }
        }
    }
}

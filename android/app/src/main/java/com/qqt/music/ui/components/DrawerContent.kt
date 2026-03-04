package com.qqt.music.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.qqt.music.ui.navigation.Screen
import com.qqt.music.ui.theme.OrangePrimary
import kotlinx.coroutines.launch

@Composable
fun DrawerContent(
    navController: NavController,
    drawerState: DrawerState,
    currentRoute: String?,
) {
    val scope = rememberCoroutineScope()

    fun navigate(route: String) {
        scope.launch { drawerState.close() }
        navController.navigate(route) {
            popUpTo(Screen.Home.route)
            launchSingleTop = true
        }
    }

    ModalDrawerSheet(
        drawerContainerColor = Color.White,
        modifier = Modifier.width(280.dp),
    ) {
        // Header
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(160.dp)
                .background(
                    Brush.horizontalGradient(
                        listOf(OrangePrimary, Color(0xFFFF7955))
                    )
                )
                .padding(20.dp),
            contentAlignment = Alignment.BottomStart,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(60.dp)
                        .clip(CircleShape)
                        .background(Color.White),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.Default.MusicNote,
                        contentDescription = null,
                        tint = OrangePrimary,
                        modifier = Modifier.size(36.dp),
                    )
                }
                Spacer(Modifier.width(12.dp))
                Column {
                    Text("倾轻听", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Color.White)
                    Text("播放你收藏的歌曲", fontSize = 13.sp, color = Color.White.copy(alpha = 0.85f))
                }
            }
        }

        Spacer(Modifier.height(8.dp))

        val items = listOf(
            Triple(Icons.Default.Home, "首页", Screen.Home.route),
            Triple(Icons.Default.Person, "音乐歌手", Screen.Artist.route),
            Triple(Icons.Default.Album, "音乐专辑", Screen.Album.route),
            Triple(Icons.Default.QueueMusic, "所有歌曲", Screen.Latest.route),
            Triple(Icons.Default.PlaylistPlay, "播放列表", Screen.Playlist.route),
            Triple(Icons.Default.PlaylistAdd, "我的列表", Screen.MyList.route),
            Triple(Icons.Default.CloudDownload, "我的下载", Screen.Download.route),
            Triple(Icons.Default.Favorite, "歌曲收藏", Screen.Favorites.route),
            Triple(Icons.Default.LibraryMusic, "建议歌曲", Screen.Latest.route),
            Triple(Icons.Default.Settings, "设置中心", Screen.Settings.route),
            Triple(Icons.Default.Login, "登录", Screen.Home.route),
        )

        items.forEach { (icon, label, route) ->
            val selected = currentRoute == route && route != Screen.Home.route
            NavigationDrawerItem(
                icon = {
                    Icon(
                        icon, contentDescription = null,
                        tint = if (selected) OrangePrimary else Color(0xFF888888),
                    )
                },
                label = {
                    Text(
                        label,
                        fontSize = 15.sp,
                        color = if (selected) OrangePrimary else Color(0xFF333333),
                    )
                },
                selected = selected,
                onClick = { navigate(route) },
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                colors = NavigationDrawerItemDefaults.colors(
                    selectedContainerColor = OrangePrimary.copy(alpha = 0.1f),
                    unselectedContainerColor = Color.Transparent,
                ),
            )
        }
    }
}

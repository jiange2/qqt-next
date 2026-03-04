package com.qqt.music.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.qqt.music.ui.navigation.Screen
import com.qqt.music.ui.theme.OrangePrimary

@Composable
fun MusicBottomBar(
    navController: NavController,
    currentRoute: String?,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .wrapContentHeight()
    ) {
        Surface(
            color = Color.White,
            shadowElevation = 8.dp,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(60.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                BottomNavItem(
                    icon = Icons.Default.Home,
                    label = "首页",
                    selected = currentRoute == Screen.Home.route,
                    onClick = { navController.navigateSingle(Screen.Home.route) },
                    modifier = Modifier.weight(1f),
                )
                BottomNavItem(
                    icon = Icons.Default.AccessTime,
                    label = "最近播放",
                    selected = currentRoute == Screen.Recent.route,
                    onClick = { navController.navigateSingle(Screen.Recent.route) },
                    modifier = Modifier.weight(1f),
                )
                // Center placeholder for FAB
                Box(modifier = Modifier.weight(1f))
                BottomNavItem(
                    icon = Icons.Default.Category,
                    label = "音乐分类",
                    selected = currentRoute == Screen.Category.route,
                    onClick = { navController.navigateSingle(Screen.Category.route) },
                    modifier = Modifier.weight(1f),
                )
                BottomNavItem(
                    icon = Icons.Default.Notifications,
                    label = "最新歌曲",
                    selected = currentRoute == Screen.Latest.route,
                    onClick = { navController.navigateSingle(Screen.Latest.route) },
                    modifier = Modifier.weight(1f),
                )
            }
        }

        // Center FAB
        FloatingActionButton(
            onClick = { navController.navigateSingle(Screen.Download.route) },
            modifier = Modifier
                .align(Alignment.TopCenter)
                .offset(y = (-16).dp)
                .size(52.dp),
            containerColor = OrangePrimary,
            contentColor = Color.White,
            elevation = FloatingActionButtonDefaults.elevation(4.dp),
        ) {
            Icon(Icons.Default.LibraryMusic, contentDescription = "我的下载", modifier = Modifier.size(26.dp))
        }
    }
}

@Composable
private fun BottomNavItem(
    icon: ImageVector,
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .clickable(onClick = onClick)
            .padding(vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = if (selected) OrangePrimary else Color(0xFF888888),
            modifier = Modifier.size(22.dp),
        )
        Spacer(Modifier.height(2.dp))
        Text(
            text = label,
            fontSize = 10.sp,
            color = if (selected) OrangePrimary else Color(0xFF888888),
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            maxLines = 1,
        )
    }
}

private fun NavController.navigateSingle(route: String) {
    navigate(route) {
        popUpTo(Screen.Home.route) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}

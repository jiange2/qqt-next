package com.qqt.music.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.qqt.music.ui.navigation.Screen
import com.qqt.music.ui.theme.BrandOrange
import com.qqt.music.ui.theme.BrandOrangeDeep
import com.qqt.music.ui.theme.Hairline
import com.qqt.music.ui.theme.InkFaint
import com.qqt.music.ui.theme.brandBrush

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
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column {
                HorizontalDivider(thickness = 0.5.dp, color = Hairline)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(58.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    BottomNavItem(
                        selectedIcon = Icons.Filled.Home,
                        unselectedIcon = Icons.Outlined.Home,
                        label = "首页",
                        selected = currentRoute == Screen.Home.route,
                        onClick = { navController.navigateSingle(Screen.Home.route) },
                        modifier = Modifier.weight(1f),
                    )
                    BottomNavItem(
                        selectedIcon = Icons.Filled.AccessTime,
                        unselectedIcon = Icons.Outlined.AccessTime,
                        label = "最近播放",
                        selected = currentRoute == Screen.Recent.route,
                        onClick = { navController.navigateSingle(Screen.Recent.route) },
                        modifier = Modifier.weight(1f),
                    )
                    // Center placeholder for FAB
                    Box(modifier = Modifier.weight(1f))
                    BottomNavItem(
                        selectedIcon = Icons.Filled.Category,
                        unselectedIcon = Icons.Outlined.Category,
                        label = "音乐分类",
                        selected = currentRoute == Screen.Category.route,
                        onClick = { navController.navigateSingle(Screen.Category.route) },
                        modifier = Modifier.weight(1f),
                    )
                    BottomNavItem(
                        selectedIcon = Icons.Filled.Notifications,
                        unselectedIcon = Icons.Outlined.Notifications,
                        label = "最新歌曲",
                        selected = currentRoute == Screen.Latest.route,
                        onClick = { navController.navigateSingle(Screen.Latest.route) },
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }

        // Center FAB：品牌渐变 + 柔光阴影，承载「我的下载」
        Box(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .offset(y = (-18).dp)
                .size(54.dp)
                .shadow(
                    elevation = 10.dp,
                    shape = CircleShape,
                    ambientColor = BrandOrangeDeep.copy(alpha = 0.4f),
                    spotColor = BrandOrangeDeep.copy(alpha = 0.4f),
                )
                .clip(CircleShape)
                .background(brandBrush())
                .clickable { navController.navigateSingle(Screen.Download.route) },
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Default.LibraryMusic,
                contentDescription = "我的下载",
                tint = Color.White,
                modifier = Modifier.size(26.dp),
            )
        }
    }
}

@Composable
private fun BottomNavItem(
    selectedIcon: ImageVector,
    unselectedIcon: ImageVector,
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val tint by animateColorAsState(
        targetValue = if (selected) BrandOrange else InkFaint,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "navTint",
    )
    val iconSize by animateDpAsState(
        targetValue = if (selected) 24.dp else 22.dp,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "navIconSize",
    )
    Column(
        modifier = modifier
            // 去水波纹：切换选中态时只有颜色/尺寸动画，更干净
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            )
            .padding(vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            imageVector = if (selected) selectedIcon else unselectedIcon,
            contentDescription = label,
            tint = tint,
            modifier = Modifier.size(iconSize),
        )
        Spacer(Modifier.height(3.dp))
        Text(
            text = label,
            fontSize = 10.sp,
            color = tint,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
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

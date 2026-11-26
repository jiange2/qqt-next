package com.cdndash.app.ui.navigation

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Dashboard
import androidx.compose.material.icons.outlined.Payments
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.cdndash.app.data.AccountStore
import com.cdndash.app.data.aliyun.AliyunApi
import com.cdndash.app.ui.dashboard.DashboardScreen
import com.cdndash.app.ui.packages.PackageDetailScreen
import com.cdndash.app.ui.settings.SettingsScreen
import com.cdndash.app.ui.spend.SpendScreen

/** 底部导航的三个顶层入口；看板是启动页，也是返回键的兜底。 */
enum class TopLevelDestination(
    val route: String,
    val label: String,
    val icon: ImageVector,
) {
    Dashboard("dashboard", "看板", Icons.Outlined.Dashboard),
    Spend("spend", "花费", Icons.Outlined.Payments),
    Settings("settings", "设置", Icons.Outlined.Settings),
}

/** 悬浮玻璃顶栏/底部胶囊各自占用的高度（含间距）；页面滚动内容按这两个值留白。 */
val TopBarContentPadding = 80.dp
val PillNavContentPadding = 96.dp

@Composable
fun CdnDashApp(accountStore: AccountStore, aliyunApi: AliyunApi) {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = backStackEntry?.destination
    val selectedDestination = TopLevelDestination.entries.firstOrNull { candidate ->
        currentDestination?.hierarchy?.any { it.route == candidate.route } == true
    }
    // 资源包明细是看板的下钻页，不属于底部导航的三个顶层；顶栏换成返回 + 固定标题。
    val isPackageDetail = currentDestination?.route == PACKAGE_DETAIL_ROUTE
    val title = if (isPackageDetail) {
        "资源包明细"
    } else {
        selectedDestination?.label ?: TopLevelDestination.Dashboard.label
    }

    Scaffold { innerPadding ->
        // 顶栏与底部胶囊都悬浮在内容之上：NavHost 不为它们预留高度，页面内容滚到玻璃
        // 容器后方透出磨砂质感；各页按 [TopBarContentPadding]/[PillNavContentPadding] 留白。
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            NavHost(
                navController = navController,
                startDestination = TopLevelDestination.Dashboard.route,
                modifier = Modifier.fillMaxSize(),
            ) {
                composable(TopLevelDestination.Dashboard.route) {
                    DashboardScreen(
                        accountStore = accountStore,
                        aliyunApi = aliyunApi,
                        onOpenSettings = {
                            navController.navigateToTopLevel(TopLevelDestination.Settings)
                        },
                        onOpenPackages = { account ->
                            navController.navigate(packageDetailRoute(account.id))
                        },
                    )
                }
                composable(
                    route = PACKAGE_DETAIL_ROUTE,
                    arguments = listOf(
                        navArgument(PACKAGE_DETAIL_ACCOUNT_ARG) { type = NavType.StringType },
                    ),
                ) { entry ->
                    PackageDetailScreen(
                        accountStore = accountStore,
                        aliyunApi = aliyunApi,
                        accountId = entry.arguments?.getString(PACKAGE_DETAIL_ACCOUNT_ARG).orEmpty(),
                    )
                }
                composable(TopLevelDestination.Spend.route) {
                    SpendScreen(accountStore = accountStore, aliyunApi = aliyunApi)
                }
                composable(TopLevelDestination.Settings.route) {
                    SettingsScreen(accountStore = accountStore)
                }
            }
            GlassTopBar(
                title = title,
                onBack = if (isPackageDetail) {
                    { navController.popBackStack() }
                } else {
                    null
                },
                modifier = Modifier.align(Alignment.TopCenter),
            )
            GlassPillNavigationBar(
                selected = selectedDestination,
                onSelect = { navController.navigateToTopLevel(it) },
                modifier = Modifier.align(Alignment.BottomCenter),
            )
        }
    }
}

/**
 * 玻璃容器的统一质感：半透明底 + 顶部高光 + 细描边 + 投影。
 *
 * 真 backdrop blur 在单 Activity Compose 里没有稳定实现（需 API 31+ RenderEffect 截取底层内容，
 * 或引入第三方库），用这组图层模拟；内容从容器后滚过时即呈磨砂观感。
 * 顶栏与底部胶囊共用，保证两处玻璃完全一致。
 */
@Composable
private fun GlassContainer(
    shape: Shape,
    elevation: Dp,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val containerColor = MaterialTheme.colorScheme.surfaceContainerLow.copy(alpha = 0.86f)
    val borderColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)
    val highlight = Brush.verticalGradient(
        colors = listOf(Color.White.copy(alpha = 0.12f), Color.Transparent),
    )
    Box(
        modifier = modifier
            .shadow(elevation = elevation, shape = shape)
            .clip(shape)
            .background(containerColor)
            .background(highlight)
            .border(width = 1.dp, color = borderColor, shape = shape),
    ) {
        content()
    }
}

/** 玻璃顶栏：与底部胶囊同质感的大圆角悬浮条，左对齐标题，下钻页带返回键。 */
@Composable
private fun GlassTopBar(
    title: String,
    onBack: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    GlassContainer(
        shape = RoundedCornerShape(28.dp),
        elevation = 8.dp,
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (onBack != null) {
                IconButton(onClick = onBack, modifier = Modifier.padding(start = 4.dp)) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                        contentDescription = "返回看板",
                    )
                }
            }
            Text(
                text = title,
                modifier = Modifier.padding(
                    start = if (onBack != null) 4.dp else 20.dp,
                    end = 20.dp,
                ),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
            )
        }
    }
}

/**
 * 玻璃胶囊导航：居中悬浮的半透明药丸，宽度随内容自适应。
 * 文字禁换行，避免窄屏下「设置」被压成竖排；选中项是实心药丸，颜色切换带过渡动画。
 */
@Composable
private fun GlassPillNavigationBar(
    selected: TopLevelDestination?,
    onSelect: (TopLevelDestination) -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 16.dp),
        contentAlignment = Alignment.Center,
    ) {
        GlassContainer(
            shape = RoundedCornerShape(percent = 50),
            elevation = 16.dp,
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 6.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TopLevelDestination.entries.forEach { destination ->
                    PillNavItem(
                        destination = destination,
                        isSelected = destination == selected,
                        onClick = { onSelect(destination) },
                    )
                }
            }
        }
    }
}

@Composable
private fun PillNavItem(
    destination: TopLevelDestination,
    isSelected: Boolean,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(percent = 50)
    val containerColor by animateColorAsState(
        targetValue = if (isSelected) {
            MaterialTheme.colorScheme.primaryContainer
        } else {
            Color.Transparent
        },
        label = "pill-nav-container",
    )
    val contentColor by animateColorAsState(
        targetValue = if (isSelected) {
            MaterialTheme.colorScheme.onPrimaryContainer
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant
        },
        label = "pill-nav-content",
    )
    Row(
        modifier = Modifier
            .clip(shape)
            .background(containerColor)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = destination.icon,
            contentDescription = destination.label,
            modifier = Modifier.size(22.dp),
            tint = contentColor,
        )
        Spacer(Modifier.width(6.dp))
        Text(
            text = destination.label,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium,
            color = contentColor,
            maxLines = 1,
            softWrap = false,
        )
    }
}

/**
 * 顶层切换保留各自栈状态，且始终把启动页留在栈底：从花费/设置返回时回到看板，
 * 与常见底部导航的返回键行为一致。
 */
private fun NavHostController.navigateToTopLevel(destination: TopLevelDestination) {
    navigate(destination.route) {
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}

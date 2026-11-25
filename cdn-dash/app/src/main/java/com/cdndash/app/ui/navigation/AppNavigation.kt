package com.cdndash.app.ui.navigation

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Dashboard
import androidx.compose.material.icons.outlined.Payments
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.cdndash.app.data.AccountStore
import com.cdndash.app.data.aliyun.AliyunApi
import com.cdndash.app.ui.dashboard.DashboardScreen
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CdnDashApp(accountStore: AccountStore, aliyunApi: AliyunApi) {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = backStackEntry?.destination
    val selectedDestination = TopLevelDestination.entries.firstOrNull { candidate ->
        currentDestination?.hierarchy?.any { it.route == candidate.route } == true
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(selectedDestination?.label ?: TopLevelDestination.Dashboard.label) },
            )
        },
        bottomBar = {
            NavigationBar {
                TopLevelDestination.entries.forEach { destination ->
                    NavigationBarItem(
                        selected = destination == selectedDestination,
                        onClick = { navController.navigateToTopLevel(destination) },
                        icon = { Icon(destination.icon, contentDescription = destination.label) },
                        label = { Text(destination.label) },
                    )
                }
            }
        },
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = TopLevelDestination.Dashboard.route,
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            composable(TopLevelDestination.Dashboard.route) {
                DashboardScreen(
                    accountStore = accountStore,
                    aliyunApi = aliyunApi,
                    onOpenSettings = {
                        navController.navigateToTopLevel(TopLevelDestination.Settings)
                    },
                )
            }
            composable(TopLevelDestination.Spend.route) {
                SpendScreen(accountStore = accountStore, aliyunApi = aliyunApi)
            }
            composable(TopLevelDestination.Settings.route) {
                SettingsScreen(accountStore = accountStore)
            }
        }
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

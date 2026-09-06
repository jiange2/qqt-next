package com.qqt.music.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
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
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.qqt.music.ui.navigation.Screen
import com.qqt.music.ui.theme.BrandOrange
import com.qqt.music.ui.theme.BrandOrangeDeep
import com.qqt.music.ui.theme.Hairline
import com.qqt.music.ui.theme.InkNav
import com.qqt.music.ui.theme.brandBrush
import kotlin.math.cos
import kotlin.math.sin

@Composable
fun MusicBottomBar(
    navController: NavController,
    currentRoute: String?,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            // 固定槽高：圆钮向上超界绘制，否则圆钮测量高度会撑大 bottomBar，
            // 栏底与 MiniPlayer 之间露出透明缝隙
            .height(NavHeight)
    ) {
        Surface(
            color = Color.White,
            // 凸包轮廓：栏顶边在中央向上拱起圆弧鼓包，将中央圆钮兜在其内
            shape = RaisedNavShape(),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(NavHeight),
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
                        selectedIcon = Icons.Filled.History,
                        unselectedIcon = Icons.Outlined.History,
                        label = "最近播放",
                        selected = currentRoute == Screen.Recent.route,
                        onClick = { navController.navigateSingle(Screen.Recent.route) },
                        modifier = Modifier.weight(1f),
                    )
                    // Center placeholder for FAB
                    Box(modifier = Modifier.weight(1f))
                    BottomNavItem(
                        selectedIcon = Icons.Filled.Folder,
                        unselectedIcon = Icons.Outlined.Folder,
                        label = "音乐分类",
                        selected = currentRoute == Screen.Category.route,
                        onClick = { navController.navigateSingle(Screen.Category.route) },
                        modifier = Modifier.weight(1f),
                    )
                    BottomNavItem(
                        selectedIcon = Icons.Filled.WorkspacePremium,
                        unselectedIcon = Icons.Outlined.WorkspacePremium,
                        label = "最新歌曲",
                        selected = currentRoute == Screen.Latest.route,
                        onClick = { navController.navigateSingle(Screen.Latest.route) },
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }

        // 栏顶外框：细线渐起 → 凸包弧 → 渐落 → 细线，整条骑线描边连续
        Canvas(modifier = Modifier.matchParentSize()) {
            val hw = BulgeHalfWidth.toPx()
            val cx = size.width / 2f
            val frame = Path()
            frame.moveTo(0f, 0f)
            frame.lineTo(cx - hw, 0f)
            frame.addTopFrameMid(cx, this)
            frame.lineTo(size.width, 0f)
            // 描边宽 = 0.5dp 基础上再加 3 物理像素，与 MiniPlayer 细线同步加粗
            drawPath(frame, Hairline, style = Stroke(width = 0.5.dp.toPx() + 3f))
        }

        // Center FAB：品牌渐变 + 柔光阴影（投在凸包白底上），承载「我的下载」；
        // 底部与各 Tab 内容底对齐、顶部凸出栏外，被栏顶的圆弧凸包整个兜住
        Box(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .offset(y = -FabOverhang)
                // requiredSize 突破槽高约束：圆钮按自身尺寸测量绘制
                .requiredSize(FabSize)
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
                modifier = Modifier.size(27.dp),
            )
        }
    }
}

/** 导航栏高度（bottomBar 槽高，MiniPlayer 紧贴其下） */
private val NavHeight = 64.dp
/** 中央圆钮直径 */
private val FabSize = 54.dp
/** 中央圆钮凸出栏顶的高度（圆钮底沿 = FabSize − FabOverhang，近似对齐 Tab 内容底） */
private val FabOverhang = 4.dp
/** 凸包半径：圆钮半径 27dp + 约 6dp 白边（轮廓裁剪与外框描边共用） */
private val BulgeRadius = 33.dp
/** 凸包圆心（= 圆钮中心）距栏顶的高度 */
private val BulgeCenterY = FabSize / 2 - FabOverhang
/** 凸包渐起点半宽：边框未到圆钮侧边就开始上斜，弧不突兀 */
private val BulgeHalfWidth = 45.dp

/** 外框中段：cubic 渐起 → 凸包圆弧（240°→300°，仅顶部小帽）→ cubic 渐落，切线连续且曲率渐进（G1） */
private fun Path.addTopFrameMid(cx: Float, density: Density) {
    with(density) {
        val r = BulgeRadius.toPx()
        val cy = BulgeCenterY.toPx()
        val hw = BulgeHalfWidth.toPx()
        val inset = 16.dp.toPx()
        // 圆弧相接点半径角 240°（cos=−0.5、sin=−0.866），白边恰为 6dp
        val phi = Math.toRadians(240.0)
        val jx = (r * cos(phi)).toFloat()
        val jy = cy + (r * sin(phi)).toFloat()
        // 过渡段末端控制点沿圆弧切向 (0.866, −0.5) 回退 10dp，令曲率渐进贴合弧、消除突变
        val tx = 10.dp.toPx() * 0.8660254f
        val ty = 10.dp.toPx() * 0.5f
        cubicTo(cx - hw + inset, 0f, cx + jx - tx, jy + ty, cx + jx, jy)
        arcTo(
            rect = Rect(cx - r, cy - r, cx + r, cy + r),
            startAngleDegrees = 240f,
            sweepAngleDegrees = 60f,
            forceMoveTo = false,
        )
        cubicTo(cx - jx + tx, jy + ty, cx + hw - inset, 0f, cx + hw, 0f)
    }
}

/** 底部导航栏形状：栏顶边在中央向上拱起圆弧鼓包（渐起渐落），将中央圆钮兜在其内 */
private class RaisedNavShape : Shape {
    override fun createOutline(
        size: Size,
        layoutDirection: LayoutDirection,
        density: Density,
    ): Outline {
        val path = Path()
        with(density) {
            // 凸包几何：中段以圆钮中心为圆心的圆弧贴住圆钮（白边 6dp），
            // 两端 cubic 渐起/渐落与栏顶直线相切，白边由 6dp 渐宽至约 18dp
            val hw = BulgeHalfWidth.toPx()
            val cx = size.width / 2f
            path.moveTo(0f, 0f)
            path.lineTo(cx - hw, 0f)
            path.addTopFrameMid(cx, density)
            path.lineTo(size.width, 0f)
            path.lineTo(size.width, size.height)
            path.lineTo(0f, size.height)
            path.close()
        }
        return Outline.Generic(path)
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
        targetValue = if (selected) BrandOrange else InkNav,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "navTint",
    )
    val iconSize by animateDpAsState(
        targetValue = if (selected) 27.dp else 25.dp,
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
            // 字号取自主题 labelSmall（其注释即「小标签 / 底部导航」），不再硬编码
            style = MaterialTheme.typography.labelSmall,
            color = tint,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
            maxLines = 1,
        )
    }
}

internal fun NavController.navigateSingle(route: String) {
    navigate(route) {
        popUpTo(Screen.Home.route) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}

package com.qqt.music.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.qqt.music.ui.theme.BrandOrange
import com.qqt.music.ui.theme.InkPrimary
import com.qqt.music.ui.theme.WarmBackground

/**
 * 顶栏：暖白底与页面融为一体，深色标题 + 品牌色点缀，
 * 告别大面积橙色平铺，让内容区的橙色元素成为视觉焦点。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MusicTopBar(
    title: String,
    onMenuClick: () -> Unit,
    onSearchClick: () -> Unit,
    showBackButton: Boolean = false,
    onBackClick: () -> Unit = {},
) {
    TopAppBar(
        title = {
            Text(
                text = title,
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp,
                color = InkPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.fillMaxWidth(0.7f),
            )
        },
        navigationIcon = {
            IconButton(onClick = if (showBackButton) onBackClick else onMenuClick) {
                Icon(
                    imageVector = if (showBackButton) Icons.AutoMirrored.Filled.ArrowBack else Icons.Default.Menu,
                    contentDescription = if (showBackButton) "Back" else "Menu",
                    tint = InkPrimary,
                )
            }
        },
        actions = {
            if (!showBackButton) {
                IconButton(onClick = onSearchClick) {
                    Icon(Icons.Default.Search, contentDescription = "Search", tint = BrandOrange)
                }
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = WarmBackground,
            titleContentColor = InkPrimary,
        ),
    )
}

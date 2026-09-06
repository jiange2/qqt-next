package com.qqt.music.ui.screens.categoryalbums

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Album
import androidx.compose.material.icons.outlined.BrokenImage
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.qqt.music.data.api.model.Album
import com.qqt.music.ui.components.AlbumCard
import com.qqt.music.ui.components.EmptyState
import com.qqt.music.ui.navigation.CategoryNav
import com.qqt.music.ui.theme.BrandOrange
import com.qqt.music.ui.theme.WarmBackground

/**
 * 分类专辑页（分类-专辑层级，backend-next ADR 0009）：
 * 点击分类后展示该分类下的专辑网格，点击专辑进入既有专辑歌曲页；
 * 分类下仅 1 张专辑时不展示网格，加载完成后经 onAutoOpen 直跳专辑歌曲页（返回时回到分类列表页）。
 */
@Composable
fun CategoryAlbumsScreen(
    onAlbumClick: (Album) -> Unit = {},
    onAutoOpen: (Album) -> Unit = {},
    viewModel: CategoryAlbumsViewModel = viewModel(),
) {
    val category = CategoryNav.category
    if (category == null) {
        EmptyState(icon = Icons.Outlined.BrokenImage, title = "内容已失效")
        return
    }

    val albums by viewModel.albums.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val autoOpen by viewModel.autoOpen.collectAsState()
    LaunchedEffect(autoOpen) { autoOpen?.let(onAutoOpen) }
    val gridState = rememberLazyGridState()

    val reachedEnd by remember {
        derivedStateOf {
            val info = gridState.layoutInfo
            val total = info.totalItemsCount
            val last = info.visibleItemsInfo.lastOrNull()?.index ?: 0
            total > 0 && last >= total - 4
        }
    }
    LaunchedEffect(reachedEnd) { if (reachedEnd) viewModel.loadMore() }

    if (isLoading && albums.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = BrandOrange)
        }
        return
    }

    if (!isLoading && albums.isEmpty()) {
        EmptyState(icon = Icons.Outlined.Album, title = "该分类暂无专辑")
        return
    }

    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        state = gridState,
        contentPadding = PaddingValues(12.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier.fillMaxSize().background(WarmBackground),
    ) {
        items(albums) { album ->
            AlbumCard(album = album, onClick = { onAlbumClick(album) })
        }
        if (isLoading) {
            item(span = { GridItemSpan(2) }) {
                Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = BrandOrange, modifier = Modifier.size(32.dp))
                }
            }
        }
    }
}

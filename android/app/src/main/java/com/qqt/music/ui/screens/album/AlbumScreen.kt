package com.qqt.music.ui.screens.album

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.qqt.music.data.api.model.Album
import com.qqt.music.ui.components.AlbumCard
import com.qqt.music.ui.theme.BrandOrange
import com.qqt.music.ui.theme.WarmBackground

@Composable
fun AlbumScreen(
    onAlbumClick: (Album) -> Unit = {},
    viewModel: AlbumViewModel = viewModel(),
) {
    val albums by viewModel.albums.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
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

package com.qqt.music.ui.screens.category

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.outlined.Category
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.qqt.music.data.api.model.Category
import com.qqt.music.ui.components.EmptyState
import com.qqt.music.ui.theme.BrandOrange
import com.qqt.music.ui.theme.InkFaint
import com.qqt.music.ui.theme.InkPrimary
import com.qqt.music.ui.theme.PlaceholderBg
import com.qqt.music.ui.theme.WarmBackground

@Composable
fun CategoryScreen(
    onCategoryClick: (Category) -> Unit,
    viewModel: CategoryViewModel = viewModel(),
) {
    val categories by viewModel.categories.collectAsState()
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

    if (!isLoading && categories.isEmpty()) {
        EmptyState(icon = Icons.Outlined.Category, title = "暂无分类")
        return
    }

    // 一排 3 个裸圆入口：无卡片容器，直接浮于暖色底；交互链路与分页逻辑不变
    LazyVerticalGrid(
        columns = GridCells.Fixed(3),
        state = gridState,
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
        modifier = Modifier.fillMaxSize().background(WarmBackground),
    ) {
        items(categories) { cat ->
            CategoryCircleItem(category = cat, onClick = { onCategoryClick(cat) })
        }
        if (isLoading) {
            item(span = { GridItemSpan(3) }) {
                Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = BrandOrange, modifier = Modifier.size(32.dp))
                }
            }
        }
    }
}

/** 分类圆形入口：圆内文字由后台上传的分类图素材自带，App 不叠字；无图分类以纯色圆加淡音符占位。
 *  按压反馈：圆图与标签整体变淡（α 0.5），无波纹无阴影。 */
@Composable
private fun CategoryCircleItem(category: Category, onClick: () -> Unit) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .alpha(if (pressed) 0.5f else 1f)
            .clickable(interactionSource = interactionSource, indication = null, onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .clip(CircleShape)
                .background(PlaceholderBg),
            contentAlignment = Alignment.Center,
        ) {
            AsyncImage(
                // 圆径约百 dp，thumb(300x300) 优先省流量，原图兜底
                model = category.imageThumb.ifBlank { category.image },
                contentDescription = category.name,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
            if (category.imageThumb.isBlank() && category.image.isBlank()) {
                Icon(
                    Icons.Default.MusicNote,
                    contentDescription = null,
                    tint = InkFaint,
                    modifier = Modifier.fillMaxSize(0.35f),
                )
            }
        }
        Text(
            text = category.name,
            fontSize = 12.sp,
            color = InkPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 8.dp),
        )
    }
}

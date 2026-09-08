package com.qqt.music.ui.screens.booklist

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.outlined.BrokenImage
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.qqt.music.data.api.model.Book
import com.qqt.music.ui.components.EmptyState
import com.qqt.music.ui.navigation.CategoryNav
import com.qqt.music.ui.theme.BrandOrange
import com.qqt.music.ui.theme.InkFaint
import com.qqt.music.ui.theme.InkPrimary
import com.qqt.music.ui.theme.InkSecondary
import com.qqt.music.ui.theme.PlaceholderBg
import com.qqt.music.ui.theme.WarmBackground

/**
 * 书单页（书籍阅读域 ADR 0011）：书籍分类下的书以 2 列封面卡网格展示，
 * 点击直进阅读页（无专辑单卡直跳逻辑——书籍列表本身就是目标页）。
 */
@Composable
fun BookListScreen(
    onBookClick: (Book) -> Unit = {},
    viewModel: BookListViewModel = viewModel(),
) {
    val category = CategoryNav.category
    if (category == null) {
        EmptyState(icon = Icons.Outlined.BrokenImage, title = "内容已失效")
        return
    }

    val books by viewModel.books.collectAsState()
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

    if (isLoading && books.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = BrandOrange)
        }
        return
    }

    if (!isLoading && books.isEmpty()) {
        EmptyState(icon = Icons.AutoMirrored.Outlined.MenuBook, title = "该分类暂无书籍")
        return
    }

    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        state = gridState,
        contentPadding = PaddingValues(12.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        // navigationBarsPadding：无迷你播放器后，系统导航栏空档由内容区自行让位（背景延伸至底）
        modifier = Modifier.fillMaxSize().background(WarmBackground).navigationBarsPadding(),
    ) {
        items(books) { book ->
            BookCard(book = book, onClick = { onBookClick(book) })
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

/** 书籍封面卡（书籍阅读域 ADR 0011）：封面在上（3:4、四角圆角、无播放钮），书名/作者在图下；
 *  无封面出纯色底 + 书名居中占位卡。thumb 优先原图兜底；按压整卡变淡，与专辑卡规范一致。 */
@Composable
private fun BookCard(book: Book, onClick: () -> Unit) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .alpha(if (pressed) 0.5f else 1f)
            .clickable(interactionSource = interactionSource, indication = null, onClick = onClick),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(3f / 4f)
                .clip(RoundedCornerShape(12.dp))
                .background(PlaceholderBg),
            contentAlignment = Alignment.Center,
        ) {
            if (book.coverThumb.isBlank() && book.cover.isBlank()) {
                Text(
                    text = book.name,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium,
                    color = InkSecondary,
                    textAlign = TextAlign.Center,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.fillMaxSize().padding(12.dp).wrapContentHeight(Alignment.CenterVertically),
                )
            } else {
                // thumb(720x，书籍大卡按 3x 屏显示宽定尺寸) 优先省流量，原图兑底
                AsyncImage(
                    model = book.coverThumb.ifBlank { book.cover },
                    contentDescription = book.name,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
        Text(
            text = book.name,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
            color = InkPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 8.dp),
        )
        if (book.author.isNotBlank()) {
            Text(
                text = book.author,
                fontSize = 11.sp,
                color = InkFaint,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
    }
}

package com.qqt.music.ui.screens.reader

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.qqt.music.data.api.model.BookChapter
import com.qqt.music.data.local.ReadingProgressStore
import com.qqt.music.ui.components.EmptyState
import com.qqt.music.ui.theme.BrandOrange
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.drop

/**
 * 阅读页（书籍阅读域 ADR 0011）：
 * - 全局顶栏承担返回/书名；内容区顶部常驻操作行 = 章节标题 + 目录 + 字号（Aa）
 * - 正文按 \n 分段 LazyColumn 渲染；章首「上一章」、章尾「下一章」footer
 * - 目录浮层（ModalBottomSheet）：滚动列章、当前章高亮、点选即跳浮层收起
 * - 阅读设置浮层：字号五档 + 背景主题（护眼色系，默认护眼绿）单选（全局一份偏好）
 * - 配色：阅读主题驱动正文区背景/字色（与系统明暗解耦）；书单/阅读页不显示迷你播放器
 * - 进度写入时机 = 章切换（VM 内）/ 滚动停止约 2s / 退出阅读页（onDispose）
 */
@Composable
fun ReaderScreen(viewModel: ReaderViewModel = viewModel()) {
    val state by viewModel.state.collectAsState()
    val chapters by viewModel.chapters.collectAsState()
    val fontSp by ReadingProgressStore.fontSizeSp.collectAsState()
    val theme by ReadingProgressStore.theme.collectAsState()

    var showToc by remember { mutableStateOf(false) }
    var showSettings by remember { mutableStateOf(false) }

    when (val s = state) {
        is ReaderState.Loading -> Box(
            modifier = Modifier.fillMaxSize().background(Color(theme.bg)),
            contentAlignment = Alignment.Center,
        ) {
            CircularProgressIndicator(color = BrandOrange)
        }
        is ReaderState.Error -> EmptyState(
            icon = Icons.AutoMirrored.Outlined.MenuBook,
            title = s.message,
            actionText = if (s.retryable) "重试" else null,
            onAction = if (s.retryable) ({ viewModel.openInitial() }) else null,
        )
        is ReaderState.Reading -> ReaderContent(
            state = s,
            chapterCount = chapters.size,
            fontSp = fontSp,
            theme = theme,
            onPrev = viewModel::openPrev,
            onNext = viewModel::openNext,
            onSaveProgress = viewModel::saveProgress,
            onConsumeRestore = viewModel::consumeRestore,
            onShowToc = { showToc = true },
            onShowSettings = { showSettings = true },
        )
    }

    if (showToc) {
        TocSheet(
            chapters = chapters,
            currentIndex = (state as? ReaderState.Reading)?.chapterIndex ?: 0,
            onOpen = { index ->
                viewModel.openChapter(index)
                showToc = false
            },
            onDismiss = { showToc = false },
        )
    }
    if (showSettings) {
        ReaderSettingsSheet(
            currentSp = fontSp,
            currentTheme = theme,
            onSelectSize = { ReadingProgressStore.setFontSizeSp(it) },
            onSelectTheme = { ReadingProgressStore.setTheme(it) },
            onDismiss = { showSettings = false },
        )
    }
}

@Composable
private fun ReaderContent(
    state: ReaderState.Reading,
    chapterCount: Int,
    fontSp: Int,
    theme: ReadingProgressStore.ReadingTheme,
    onPrev: () -> Unit,
    onNext: () -> Unit,
    onSaveProgress: (Float) -> Unit,
    onConsumeRestore: () -> Unit,
    onShowToc: () -> Unit,
    onShowSettings: () -> Unit,
) {
    val listState = rememberLazyListState()
    // 阅读主题配色：与系统明暗解耦，护眼色系由阅读设置中选择
    val bg = Color(theme.bg)
    val onBg = Color(theme.onBg)
    // 正文段落之前的 item 数：「上一章」footer（首章无）+ 章标题行
    val headerOffset = (if (state.chapterIndex > 0) 1 else 0) + 1

    /** 章内已读比例：可视首段落序号 / 总段数 */
    fun currentRatio(): Float {
        if (state.paragraphs.isEmpty()) return 0f
        val paraIndex = (listState.firstVisibleItemIndex - headerOffset)
            .coerceIn(0, state.paragraphs.size - 1)
        return paraIndex.toFloat() / state.paragraphs.size
    }

    // 恢复/换章定位：进章按 restoreRatio 换算段落索引 scrollToItem（消费一次防重组重复）；无恢复落章首
    LaunchedEffect(state.chapterId) {
        if (state.restoreRatio > 0f && state.paragraphs.isNotEmpty()) {
            val target = (state.restoreRatio * state.paragraphs.size).toInt()
                .coerceIn(0, state.paragraphs.size - 1)
            listState.scrollToItem(target + headerOffset)
            onConsumeRestore()
        } else {
            listState.scrollToItem(0)
        }
    }

    // 滚动节流写入：滚动停止约 2s 后按比例落盘（连续滚动被 collectLatest 取代，即节流）
    LaunchedEffect(state.chapterId) {
        snapshotFlow { listState.firstVisibleItemIndex }
            .drop(1)
            .collectLatest {
                delay(2000)
                onSaveProgress(currentRatio())
            }
    }

    // 退出阅读页（或章切换卸载旧章）时保存位置
    DisposableEffect(state.chapterId) {
        onDispose { onSaveProgress(currentRatio()) }
    }

    Column(modifier = Modifier.fillMaxSize().background(bg)) {
        // 常驻操作行：章节标题 + 目录 + 字号（返回/书名由全局顶栏承担）
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = state.chapterTitle,
                fontSize = 14.sp,
                color = onBg.copy(alpha = 0.7f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f).padding(start = 8.dp),
            )
            IconButton(onClick = onShowToc) {
                Icon(Icons.AutoMirrored.Filled.List, contentDescription = "目录", tint = onBg)
            }
            IconButton(onClick = onShowSettings) {
                Text("Aa", fontSize = 15.sp, fontWeight = FontWeight.Medium, color = onBg)
            }
        }
        // navigationBarsPadding：无迷你播放器后，系统导航栏空档由内容区自行让位（背景延伸至底）
        LazyColumn(state = listState, modifier = Modifier.fillMaxSize().navigationBarsPadding()) {
            if (state.chapterIndex > 0) {
                item(key = "prev") {
                    ChapterNavLink("上一章", onBg = onBg, onClick = onPrev)
                }
            }
            item(key = "title") {
                Text(
                    text = state.chapterTitle,
                    fontSize = (fontSp + 4).sp,
                    fontWeight = FontWeight.Bold,
                    color = onBg,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp),
                )
            }
            itemsIndexed(state.paragraphs) { _, para ->
                // 空行段落以空格占位，保持原文段距
                Text(
                    text = para.ifBlank { " " },
                    fontSize = fontSp.sp,
                    lineHeight = (fontSp * 1.7f).sp,
                    color = onBg,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 5.dp),
                )
            }
            if (state.chapterIndex < chapterCount - 1) {
                item(key = "next") {
                    ChapterNavLink("下一章", onBg = onBg, onClick = onNext)
                }
            } else {
                item(key = "end") {
                    Text(
                        "— 全书完 —",
                        fontSize = 13.sp,
                        color = onBg.copy(alpha = 0.4f),
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp),
                    )
                }
            }
        }
    }
}

/** 章首/章尾翻章链接：整行点击，品牌色文案 */
@Composable
private fun ChapterNavLink(label: String, onBg: Color, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 16.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, fontSize = 14.sp, color = MaterialTheme.colorScheme.primary)
    }
}

/** 目录浮层：滚动列章、当前章高亮、点选即跳；打开时定位到当前章 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TocSheet(
    chapters: List<BookChapter>,
    currentIndex: Int,
    onOpen: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    val listState = rememberLazyListState()
    LaunchedEffect(Unit) {
        if (currentIndex in chapters.indices) listState.scrollToItem(currentIndex)
    }
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Text(
                "目录（${chapters.size} 章）",
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
            )
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxWidth().heightIn(max = 460.dp),
            ) {
                itemsIndexed(chapters) { index, chapter ->
                    Text(
                        text = chapter.title,
                        fontSize = 14.sp,
                        fontWeight = if (index == currentIndex) FontWeight.Bold else FontWeight.Normal,
                        color = if (index == currentIndex) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurface
                        },
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onOpen(index) }
                            .padding(horizontal = 20.dp, vertical = 12.dp),
                    )
                }
            }
        }
    }
}

/** 阅读设置浮层：字号五档（标签即预览）+ 背景主题色块单选（全局一份偏好） */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun ReaderSettingsSheet(
    currentSp: Int,
    currentTheme: ReadingProgressStore.ReadingTheme,
    onSelectSize: (Int) -> Unit,
    onSelectTheme: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 24.dp),
        ) {
            Text(
                "字号",
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.height(12.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                ReadingProgressStore.FONT_SIZES_SP.forEach { size ->
                    FilterChip(
                        selected = currentSp == size,
                        onClick = { onSelectSize(size) },
                        label = { Text("${size}", fontSize = size.sp) },
                    )
                }
            }
            Spacer(Modifier.height(20.dp))
            Text(
                "背景",
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                ReadingProgressStore.READER_THEMES.forEach { t ->
                    val selected = t.id == currentTheme.id
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(Color(t.bg))
                            .border(
                                width = if (selected) 2.dp else 1.dp,
                                color = if (selected) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.outlineVariant
                                },
                                shape = CircleShape,
                            )
                            .clickable { onSelectTheme(t.id) },
                        contentAlignment = Alignment.Center,
                    ) {
                        if (selected) {
                            Icon(
                                Icons.Default.Check,
                                contentDescription = t.label,
                                tint = Color(t.onBg),
                            )
                        }
                    }
                }
            }
        }
    }
}

package com.qqt.music.ui.screens.player

import android.app.Activity
import android.content.Context
import android.graphics.drawable.BitmapDrawable
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.LocalOverscrollConfiguration
import androidx.compose.foundation.MutatePriority
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.ScrollScope
import androidx.compose.foundation.gestures.ScrollableState
import androidx.compose.foundation.gestures.scrollable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.lerp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import androidx.core.graphics.ColorUtils
import androidx.core.view.WindowCompat
import androidx.palette.graphics.Palette
import coil.compose.AsyncImage
import coil.imageLoader
import coil.request.ImageRequest
import com.qqt.music.data.local.FavoriteStore
import com.qqt.music.data.local.LocalPlaylistStore
import com.qqt.music.data.local.RatingStore
import com.qqt.music.data.repository.MusicRepository
import com.qqt.music.data.repository.RatingOutcome
import com.qqt.music.download.DownloadManager
import com.qqt.music.player.PlayerSettingsManager
import com.qqt.music.ui.components.NewPlaylistDialog
import com.qqt.music.ui.components.PlayerIcons
import com.qqt.music.ui.theme.BrandOrange
import com.qqt.music.ui.theme.CachedGold
import com.qqt.music.ui.theme.DownloadedGreen
import com.qqt.music.ui.theme.InkSecondary
import com.qqt.music.ui.theme.PlaceholderBg
import com.qqt.music.ui.theme.PlayerIconGray
import com.qqt.music.ui.theme.PlayerNavy
import com.qqt.music.ui.theme.PlayerTrack
import com.qqt.music.ui.theme.StarGold
import com.qqt.music.ui.theme.WarmBackground
import com.qqt.music.viewmodel.PlayerViewModel
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 全屏播放器：背景为封面主色亮色化垂直渐变（Palette 提取、400ms 过渡；提取中/失败回退默认暖白底，ADR 0005）。
 * 顶部下箭头 + 「歌曲/歌词」页签 + 循环箭头；圆角方形歌曲封面居中大图（高度自适应：min(0.76×宽, 中部剩余高度)，
 * 矮屏以 0.5×宽托底；切歌淡入 + 0.95→1.0 缩放进场、暂停压暗降饱和、加载/失败/无 URL 统一音符占位）；
 * 左对齐标题 / 歌手 / 队列位置；中部五个功能图标（喜欢 / 播放设置 / 下载 / 加入歌单 / 更多菜单：评分）；藏青细进度条；
 * 主控行（播放模式 / 上一首 / 藏青大圆播放键 / 下一首 / 队列入口）钉在页面底部常驻。
 * 中部播放区首槽位为双页横向 Pager（歌曲页在左、歌词页在右，CONTEXT.md「横滑切页」），横滑由根级水平 scrollable 全域驱动（含主控行上方），标题及以下共享不随翻页移动；
 * 覆盖层进出（抽屉式）：打开整页从屏幕底部滑入，关闭（下箭头/系统返回）统一整页向下滑出，动画结束才关覆盖层；
 * 正常屏中部播放区固定高度、整体不可拖动（四段间距 = 最小值 + weight 均摊剩余，估算误差由间距吸收）；
 * 矮屏内容整体滚动托底。状态栏图标在本页保持深色，离开时还原；歌词页可见且播放进行中（含装载与缓冲）时屏幕常亮，滑回歌曲页、暂停或关闭本页即恢复系统熄屏（CONTEXT.md「屏幕常亮」）。
 */
@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun PlayerScreen(
    playerViewModel: PlayerViewModel,
    onBackClick: () -> Unit = {},
) {
    val currentSong by playerViewModel.currentSong.collectAsState()
    val isPlaying by playerViewModel.isPlaying.collectAsState()
    val isBuffering by playerViewModel.isBuffering.collectAsState()
    val currentPosition by playerViewModel.currentPosition.collectAsState()
    val duration by playerViewModel.duration.collectAsState()
    val cacheVisual by playerViewModel.cacheVisual.collectAsState()
    val queue by playerViewModel.queue.collectAsState()
    val currentIndex by playerViewModel.currentIndex.collectAsState()

    // 下载按钮状态：下载列表与活动下载实时来自 DownloadManager，切歌/完成时自动刷新
    val downloadedSongs by DownloadManager.downloadedSongs.collectAsState()
    val activeDownloads by DownloadManager.activeDownloads.collectAsState()

    val songId = currentSong?.id

    // 收藏选中态实时来自 FavoriteStore，切歌自动跟随（本地收藏，ADR 0005）
    val favouriteIds by FavoriteStore.favouriteIds.collectAsState()
    val isFavorite = songId != null && songId in favouriteIds

    // 双页 Pager：page 0 = 歌曲页（左）、page 1 = 歌词页（右），默认落在歌曲页；页签选中态由滑动进度连续派生
    val pagerState = rememberPagerState(initialPage = 0) { 2 }
    // 根级 scrollable 的手势量与 Pager 的滚动量符号相反，直驱会内容反向跟手（真机实证：手往左滑、内容往右跑），
    // 包一层把送入 pagerState 的量取反使内容跟手；scroll 仍转发进 pagerState 自己的 scroll 块，
    // isScrollInProgress / 打断翻页动画的语义不变（松手吸附监听照旧挂 pagerState）
    val pagerDragState = remember(pagerState) {
        object : ScrollableState {
            // 包裹层的「前/后」与 Pager 相反（正量被取反送入），可滚标记也随符号翻转
            override val isScrollInProgress: Boolean get() = pagerState.isScrollInProgress
            override val canScrollForward: Boolean get() = pagerState.canScrollBackward
            override val canScrollBackward: Boolean get() = pagerState.canScrollForward
            override fun dispatchRawDelta(delta: Float): Float = -pagerState.dispatchRawDelta(-delta)
            override suspend fun scroll(
                scrollPriority: MutatePriority,
                block: suspend ScrollScope.() -> Unit,
            ) = pagerState.scroll(scrollPriority) {
                val pagerScope = this
                object : ScrollScope {
                    override fun scrollBy(pixels: Float): Float = -pagerScope.scrollBy(-pixels)
                }.block()
            }
        }
    }
    val tabSelection = (pagerState.currentPage + pagerState.currentPageOffsetFraction).coerceIn(0f, 1f)
    // 全域横滑松手后的落页校正：拖动/fling 结束（isScrollInProgress 变 false）若停在两页之间，动画到最近页；
    // 页签点击的 animateScrollToPage 结束时已在整页位置，此处为空操作
    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.isScrollInProgress }.collect { scrolling ->
            if (!scrolling) {
                val target = (pagerState.currentPage + pagerState.currentPageOffsetFraction)
                    .roundToInt().coerceIn(0, pagerState.pageCount - 1)
                if (target != pagerState.currentPage || pagerState.currentPageOffsetFraction != 0f) {
                    pagerState.animateScrollToPage(target)
                }
            }
        }
    }

    // 歌词加载（ADR 0014）：歌词字段只在 song_info 详情接口下发，滑到歌词页才请求——详情请求在后端计一次
    // 播放，播放器打开即取会给播放量灌水；会话缓存避免切回已播歌重复请求；顶栏「刷新」递增 lyricsReload 强制重取
    var lyricsState by remember { mutableStateOf<LyricsState>(LyricsState.Loading) }
    var lyricsReload by remember { mutableStateOf(0) }
    val lyricsPageVisible = pagerState.currentPage == 1
    LaunchedEffect(lyricsPageVisible, songId, lyricsReload) {
        if (!lyricsPageVisible) return@LaunchedEffect
        if (songId == null) {
            lyricsState = LyricsState.Empty
            return@LaunchedEffect
        }
        if (lyricsReload > 0) LyricsCache.clear(songId)
        val cached = LyricsCache.get(songId)
        if (cached != null) {
            lyricsState = LyricsState.Ready(cached)
            return@LaunchedEffect
        }
        lyricsState = LyricsState.Loading
        when (val outcome = loadLyrics(songId)) {
            is LyricsOutcome.Ready -> {
                LyricsCache.put(songId, outcome.lines)
                lyricsState = LyricsState.Ready(outcome.lines)
            }
            LyricsOutcome.NoData -> lyricsState = LyricsState.Empty
            LyricsOutcome.Failed -> lyricsState = LyricsState.Error
        }
    }

    // 覆盖层进出（抽屉式）：进场 settling=false→true，整页从屏幕底部滑入；关闭（下箭头/系统返回）统一
    // 置 dismissing，整页向下滑出，动画结束才回调 onBackClick() 关闭覆盖层（下层页面全程原样保持）
    var settling by remember { mutableStateOf(false) }
    var dismissing by remember { mutableStateOf(false) }
    val slideProgress by animateFloatAsState(
        targetValue = if (dismissing || !settling) 1f else 0f,
        animationSpec = tween(SlideAnimMs, easing = FastOutSlowInEasing),
        label = "playerSlide",
        finishedListener = { if (dismissing && it >= 1f) onBackClick() },
    )
    LaunchedEffect(Unit) { settling = true }

    // 按钮四态：未下载可点发起下载；下载中变环形进度+百分比；已下载变绿——两者为不可操作态
    // （enabled=false：点击无反应且无按压反馈；取消/删除在下载列表页）
    val isCurrentDownloaded = songId != null && downloadedSongs.any { it.id == songId }
    val isCurrentDownloading = songId != null && activeDownloads.containsKey(songId)

    // 播放设置面板（PlayerSettingsManager 单例：定时关闭）
    val sleepTimer by PlayerSettingsManager.sleepTimer.collectAsState()
    val sleepRemainingSeconds by PlayerSettingsManager.sleepRemainingSeconds.collectAsState()
    var showSettingsSheet by remember { mutableStateOf(false) }
    var showPlaylistSheet by remember { mutableStateOf(false) }
    var showMoreSheet by remember { mutableStateOf(false) }
    var showQueueSheet by remember { mutableStateOf(false) }

    // 播放模式（ADR 0008）：持久化于 PlayerSettingsManager，Service 收集后落到 ExoPlayer
    val playMode by PlayerSettingsManager.playMode.collectAsState()
    var showModeMenu by remember { mutableStateOf(false) }

    // 播放模式菜单开着时系统返回先关菜单（本处 BackHandler 晚于覆盖层注册，优先接管）
    BackHandler(enabled = showModeMenu) { showModeMenu = false }

    // 系统返回 = 统一走整页下滑收出（与下箭头同路）；收出动画期间再按返回无操作
    BackHandler(enabled = !showModeMenu) {
        if (!dismissing) dismissing = true
    }

    // 已评态实时来自 RatingStore（本机已评记录，ADR 0007），评分对话框据此进入只读展示
    val ratedSongs by RatingStore.ratedSongs.collectAsState()
    val scope = rememberCoroutineScope()

    // 封面主色提取：歌曲 ID → 亮色化渐变色，会话内缓存避免切回已播放歌重复提取（ADR 0005）；
    // 无条目（提取中）或值为 null（提取失败/无可取色）时背景回退默认暖白底
    val context = LocalContext.current
    val coverPalettes = remember { mutableStateMapOf<String, CoverPalette?>() }
    val songCover = currentSong?.thumbnailBig.orEmpty()
    LaunchedEffect(songId, songCover) {
        if (songId != null && songCover.isNotBlank() && songId !in coverPalettes) {
            coverPalettes[songId] = extractCoverPalette(context, songCover)
        }
    }
    val songPalette = songId?.let { coverPalettes[it] }
    // 背景过渡：渐变色 400ms 平滑跟随主色；提取中/失败目标值为暖白底，
    // 切歌时旧色→暖白→新色全程由动画引擎连续插值，无回退层切换
    val gradientTop by animateColorAsState(
        targetValue = songPalette?.top ?: WarmBackground,
        animationSpec = tween(400),
        label = "bgTop",
    )
    val gradientBottom by animateColorAsState(
        targetValue = songPalette?.bottom ?: WarmBackground,
        animationSpec = tween(400),
        label = "bgBottom",
    )

    // 亮色页面上状态栏图标切为深色，离开时还原
    val view = LocalView.current
    if (!view.isInEditMode) {
        DisposableEffect(Unit) {
            val window = (view.context as Activity).window
            val controller = WindowCompat.getInsetsController(window, view)
            val previous = controller.isAppearanceLightStatusBars
            controller.isAppearanceLightStatusBars = true
            onDispose { controller.isAppearanceLightStatusBars = previous }
        }
    }

    // 屏幕常亮（CONTEXT.md「屏幕常亮」）：歌词页可见且播放进行中（含装载与缓冲）时豁免系统熄屏，
    // 滑回歌曲页、暂停或收出覆盖层即恢复；窗口级 FLAG_KEEP_SCREEN_ON，离开组合时由 onDispose 清除
    val keepScreenOn = lyricsPageVisible && (isPlaying || isBuffering)
    if (!view.isInEditMode) {
        DisposableEffect(keepScreenOn) {
            val window = (view.context as Activity).window
            if (keepScreenOn) window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            onDispose { window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            // 覆盖层进出动画：进场整页从屏幕底部滑入、关闭整页向下滑出（SlideAnimMs），
            // 下层页面全程原样保持；滑出结束后 onBackClick 关闭覆盖层
            .graphicsLayer { translationY = slideProgress * size.height }
            // 横滑切页（CONTEXT.md「横滑切页」）：根级 scrollable(Horizontal) 经 pagerDragState 翻转包裹驱动 pagerState
            // （两套滚动量符号相反，直驱会内容反向跟手），两页全域（含主控行、Slider 上方）均可横滑跟手；
            // Slider 等子级水平控件优先消费不误触；Pager 自带手势关闭（userScrollEnabled=false）统一手势入口，
            // 收起动画中禁用
            .scrollable(orientation = Orientation.Horizontal, state = pagerDragState, enabled = !dismissing)
            // 背景：默认暖白底；封面主色亮色化垂直渐变（顶部主色浅版 → 底部近白）400ms 跟随，
            // 提取中/失败渐变目标值即暖白，与底色融为一体（ADR 0005）
            .background(WarmBackground)
            .background(
                Brush.verticalGradient(
                    listOf(gradientTop, gradientBottom),
                ),
            ),
    ) {
        // ── 顶栏：下箭头 / 页签 / 循环箭头 ─────────────────
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .height(56.dp)
                .padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            PlayerGlyph(Icons.Default.KeyboardArrowDown, "收起", 28.dp, PlayerNavy, touchSize = 48.dp) {
                dismissing = true // 与系统返回同路：统一整页下滑收出
            }
            Row(
                modifier = Modifier.weight(1f),
                horizontalArrangement = Arrangement.Center,
            ) {
                // 页签 = 真实导航：文案序「歌曲=左页 / 歌词=右页」，选中程度取自 Pager 滑动进度，滑动中连续过渡
                listOf("歌曲", "歌词").forEachIndexed { index, label ->
                    val selection = if (index == 0) 1f - tabSelection else tabSelection
                    PlayerTab(label = label, selection = selection) {
                        scope.launch { pagerState.animateScrollToPage(if (index == 0) 0 else 1) }
                    }
                }
            }
            // 刷新：清当前歌歌词会话缓存并重取（内嵌重解析、外链重下载）
            PlayerGlyph(PlayerIcons.Refresh, "刷新", 20.dp, PlayerNavy) {
                if (songId != null) lyricsReload++
            }
            Spacer(Modifier.width(14.dp))
        }

        // ── 中部播放区：正常屏固定高度、整体不可拖。首槽位（双页 Pager：歌词页/封面页）高度随封面弹性（上限 0.76×宽、下限 0.5×宽），
        //    四段间距 = 最小值 + weight 均摊剩余，内容总高恒等于视口；仅矮屏（放不下下限封面）退回整体滚动，主控行不随滚 ──
        BoxWithConstraints(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
        ) {
            // 固定内容高度保守估算：12 顶部间距 + 标题块(~76) + 功能图标行(48) + 进度条(48)
            // + 时间行(13) + 四段最小间距(30+16+10+18=74)。随系统字体缩放放大（sp 文本会实际变高），
            // 宁大勿小：估算只决定封面尺寸与矮屏判定，正常屏的估算误差全部由 weight 间距吸收，不会出现微滚动。
            val fixedContentHeight = 280.dp * LocalDensity.current.fontScale
            // 封面边长：min(0.76×宽, 中部剩余高度)，矮屏以 0.5×宽托底
            val coverSize = (maxHeight - fixedContentHeight).coerceIn(maxWidth * 0.5f, maxWidth * 0.76f)
            // 矮屏判定：视口连 0.5×宽下限封面 + 保守估算内容都放不下 → 退回整体滚动托底
            val isShortScreen = maxHeight - fixedContentHeight < maxWidth * 0.5f

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .then(if (isShortScreen) Modifier.verticalScroll(rememberScrollState()) else Modifier),
            ) {
                Spacer(Modifier.height(12.dp))

                // ── 首槽位：双页横向 Pager（歌曲页在左、歌词页在右，CONTEXT.md「横滑切页」）；
                //    标题及以下区块在 Pager 外共享，不随翻页移动 ──
                HorizontalPager(
                    state = pagerState,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(coverSize),
                    userScrollEnabled = false, // 横滑统一由根级 scrollable(Horizontal) 全域驱动（见根 modifier 链）
                ) { page ->
                    if (page == 1) {
                        // 歌词页（page 1）：歌词区占满槽位（内部 LazyColumn 自滚）
                        LyricsPanel(
                            state = lyricsState,
                            positionMs = currentPosition,
                            onSeekTo = { playerViewModel.seekTo(it) },
                            onRetry = { if (songId != null) lyricsReload++ },
                            modifier = Modifier.fillMaxSize(),
                        )
                    } else {
                        // 歌曲页（page 0）：歌曲封面（24dp 圆角方形），统一占位：加载中/失败/无 URL 都显示音符（ADR 0005）
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center,
                        ) {
                            val coverShape = RoundedCornerShape(24.dp)
                            Box(
                                modifier = Modifier
                                    .size(coverSize)
                                    .shadow(
                                        elevation = 20.dp,
                                        shape = coverShape,
                                        ambientColor = PlayerNavy.copy(alpha = 0.20f),
                                        spotColor = PlayerNavy.copy(alpha = 0.25f),
                                    )
                                    .clip(coverShape)
                                    .background(PlaceholderBg),
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(
                                    imageVector = Icons.Default.MusicNote,
                                    contentDescription = null,
                                    tint = PlayerIconGray,
                                    modifier = Modifier.size(72.dp),
                                )
                                if (songCover.isNotBlank()) {
                                    // 切歌动效：旧图淡出，新图淡入并从 0.95 放大进场（与背景过渡同为 400ms）
                                    AnimatedContent(
                                        targetState = songId to songCover,
                                        transitionSpec = {
                                            (
                                                fadeIn(tween(400)) +
                                                    scaleIn(initialScale = 0.95f, animationSpec = tween(400))
                                                ).togetherWith(fadeOut(tween(400)))
                                        },
                                        label = "coverSwitch",
                                    ) { (_, cover) ->
                                        // 暂停态反馈：饱和度 1→0.7 + 18% 深色 scrim，恢复播放即还原
                                        val coverSaturation by animateFloatAsState(
                                            targetValue = if (isPlaying) 1f else 0.7f,
                                            animationSpec = tween(300),
                                            label = "coverSaturation",
                                        )
                                        val coverDim by animateFloatAsState(
                                            targetValue = if (isPlaying) 0f else 0.18f,
                                            animationSpec = tween(300),
                                            label = "coverDim",
                                        )
                                        Box(modifier = Modifier.fillMaxSize()) {
                                            AsyncImage(
                                                model = cover,
                                                contentDescription = currentSong?.title,
                                                contentScale = ContentScale.Crop,
                                                colorFilter = ColorFilter.colorMatrix(
                                                    ColorMatrix().apply { setToSaturation(coverSaturation) },
                                                ),
                                                modifier = Modifier.fillMaxSize(),
                                            )
                                            Box(
                                                modifier = Modifier
                                                    .fillMaxSize()
                                                    .background(Color.Black.copy(alpha = coverDim)),
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // 四段间距均为「最小值 + weight 均摊剩余」：正常屏内容恒等于视口、整体不可拖动；矮屏滚动时只保留最小值
                Spacer(Modifier.height(30.dp))
                if (!isShortScreen) Spacer(Modifier.weight(1f))

                // ── 标题 / 歌手 / 队列位置（左对齐）───────────────
                Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 28.dp)) {
                    Text(
                        text = currentSong?.title ?: "暂无播放歌曲",
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold,
                        color = PlayerNavy,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = currentSong?.artist?.ifBlank { "佚名" } ?: "选择歌曲开始播放",
                        fontSize = 13.sp,
                        color = PlayerIconGray,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(Modifier.height(14.dp))
                    Text(
                        text = if (currentSong == null) "" else "${currentIndex + 1} / ${queue.size}",
                        fontSize = 13.sp,
                        color = PlayerIconGray,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }

                // 触控框占位 48dp：26→16，使字形顶部与歌词行的间距不变
                Spacer(Modifier.height(16.dp))
                if (!isShortScreen) Spacer(Modifier.weight(1f))

                // ── 功能图标行：喜欢 / 播放设置 / 下载 / 加入歌单 / 更多 ──
                // 图标 28dp 居中于 48dp 触控框；行 padding 18dp = 28dp 内容线 − (48−28)/2，首末图标盒边缘贴进度条两端
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 18.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    PlayerGlyph(
                        icon = if (isFavorite) PlayerIcons.LikeFilled else PlayerIcons.Like,
                        desc = "喜欢",
                        size = 28.dp,
                        tint = if (isFavorite) BrandOrange else PlayerNavy,
                        touchSize = 48.dp,
                    ) { songId?.let { FavoriteStore.toggle(it) } }
                    // 播放设置入口：定时生效期间图标右上角小圆点角标（倒计时是不可见状态，需要常驻可见性）
                    Box {
                        PlayerGlyph(PlayerIcons.Equalizer, "播放设置", 28.dp, PlayerNavy, touchSize = 48.dp) {
                            showSettingsSheet = true
                        }
                        if (sleepTimer?.isActive == true) {
                            Box(
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .padding(top = 11.dp, end = 11.dp)
                                    .size(7.dp)
                                    .clip(CircleShape)
                                    .background(BrandOrange),
                            )
                        }
                    }
                    if (isCurrentDownloading) {
                        // 下载中：环形进度 + 中心百分比（排队/总长未知时为不确定式旋转圈），点击无反应
                        val state = activeDownloads[songId]?.state
                        PlayerDownloadProgress(
                            progress = (state as? DownloadManager.ActiveState.Downloading)?.progress,
                        )
                    } else {
                        PlayerGlyph(
                            icon = PlayerIcons.Download,
                            desc = "下载",
                            size = 28.dp,
                            tint = if (isCurrentDownloaded) DownloadedGreen else PlayerNavy,
                            touchSize = 48.dp,
                            enabled = !isCurrentDownloaded,
                        ) {
                            currentSong?.let { DownloadManager.enqueue(it) }
                        }
                    }
                    PlayerGlyph(PlayerIcons.PlaylistAdd, "加入歌单", 28.dp, PlayerNavy, touchSize = 48.dp) {
                        showPlaylistSheet = true
                    }
                    PlayerGlyph(PlayerIcons.More, "更多", 28.dp, PlayerNavy, touchSize = 48.dp) {
                        showMoreSheet = true
                    }
                }

                // 24→10：抵消触控框增高（24→48），使进度条视觉位置不变
                Spacer(Modifier.height(10.dp))
                if (!isShortScreen) Spacer(Modifier.weight(1f))

                // ── 进度条：藏青细轨道 + 小圆点滑钮 ─────────────
                // M3 Slider 会把轨道两端各内缩半个 thumb slot 宽。slot 被 requiredSizeIn 强制为 20dp
                // （SliderTokens.HandleWidth/Height，与视觉滑钮尺寸无关），内缩 10dp；容器 padding 18dp
                // （= 28dp 内容线 − 10dp）把轨道两端推回内容线，与功能图标行、标题区同缘。
                // 仅当 thumb 容器尺寸变化时需同步调整此 padding
                Column(modifier = Modifier.fillMaxWidth()) {
                    Slider(
                        value = if (duration > 0) currentPosition.toFloat().coerceIn(0f, duration.toFloat()) else 0f,
                        onValueChange = { playerViewModel.seekTo(it.toLong()) },
                        valueRange = 0f..maxOf(duration.toFloat(), 1f),
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp),
                        thumb = {
                            // M3 1.2.1 的 thumb slot 被 requiredSizeIn 强制为 20dp（SliderTokens.HandleWidth/Height），
                            // slot 内容小于 20dp 时会贴左上放置导致滑钮脱轨；用等尺寸容器让 M3 居中容器，
                            // 滑钮再在容器内居中。升级 material3 版本时需复查该 token 值。
                            Box(
                                modifier = Modifier.size(20.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(16.dp)
                                        .shadow(1.dp, CircleShape)
                                        .clip(CircleShape)
                                        .background(PlayerNavy)
                                        .border(2.dp, Color.White, CircleShape),
                                )
                            }
                        },
                        track = { sliderState ->
                            val range = sliderState.valueRange
                            val fraction = if (range.endInclusive > range.start) {
                                ((sliderState.value - range.start) / (range.endInclusive - range.start))
                                    .coerceIn(0f, 1f)
                            } else {
                                0f
                            }
                            // 缓存染色（ADR 0013）：整曲本地可得整槽浅金；未命中显缓存前缀浅藏青条，
                            // 随播放实时生长、长满即翻金；已播放段实心藏青永不染色
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(4.dp)
                                    .clip(RoundedCornerShape(2.dp))
                                    .background(
                                        if (cacheVisual.fullyCached) CachedGold.copy(alpha = 0.30f) else PlayerTrack,
                                    ),
                            ) {
                                if (!cacheVisual.fullyCached && cacheVisual.prefixFraction > 0f) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth(cacheVisual.prefixFraction)
                                            .fillMaxHeight()
                                            .background(PlayerNavy.copy(alpha = 0.30f)),
                                    )
                                }
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth(fraction)
                                        .fillMaxHeight()
                                        .background(PlayerNavy),
                                )
                            }
                        },
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 28.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(formatTime(currentPosition), fontSize = 11.sp, color = PlayerIconGray)
                        Text(formatTime(duration), fontSize = 11.sp, color = PlayerIconGray)
                    }
                }

                Spacer(Modifier.height(18.dp))
                if (!isShortScreen) Spacer(Modifier.weight(1f))
            }
        }

        // ── 主控行：播放模式 / 上一首 / 播放 / 下一首 / 队列入口（钉底常驻，不随内容滚动）──
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 32.dp)
                .navigationBarsPadding()
                .padding(bottom = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // 播放模式（ADR 0008）：图标随模式换图形，默认态「顺序播放」灰、随机/单曲循环橙。
            // Q3 交互：点击图标开菜单；菜单开着时再点图标轮转（菜单不关、高亮跟随）；
            // 点选项选中并关菜单；点菜单以外区域仅关菜单；系统返回先关菜单
            var modeAnchor by remember { mutableStateOf(IntOffset.Zero) }
            Box(
                modifier = Modifier.onGloballyPositioned {
                    modeAnchor = IntOffset(it.positionInWindow().x.roundToInt(), it.positionInWindow().y.roundToInt())
                },
            ) {
                PlayerGlyph(
                    icon = playModeIcon(playMode),
                    desc = "播放模式",
                    size = 22.dp,
                    tint = if (playMode == PlayerSettingsManager.PlayMode.SEQUENTIAL) PlayerIconGray else BrandOrange,
                ) {
                    showModeMenu = true
                }
                if (showModeMenu) {
                    PlayModeMenuOverlay(
                        anchor = modeAnchor,
                        current = playMode,
                        onSelect = { mode ->
                            showModeMenu = false
                            playerViewModel.setPlayMode(mode)
                        },
                        onDismiss = { showModeMenu = false },
                        onCycle = { playerViewModel.cyclePlayMode() },
                    )
                }
            }

            PlayerGlyph(PlayerIcons.Previous, "上一首", 28.dp, PlayerNavy) { playerViewModel.skipPrev() }

            // 播放键：藏青大圆钮（按素材 pause.png 的样式用代码绘制，保证清晰度与暂停态切换）
            // 按压反馈为实心块专用缩放：graphicsLayer 置于 shadow 之前，投影随圆钮一同缩放
            val playInteractionSource = remember { MutableInteractionSource() }
            val playPressed by playInteractionSource.collectIsPressedAsState()
            val playScale = animateFloatAsState(
                targetValue = if (playPressed) PressScaleDown else 1f,
                animationSpec = tween(durationMillis = if (playPressed) PressInMs else PressOutMs),
                label = "playPressScale",
            )
            Box(
                modifier = Modifier
                    .size(62.dp)
                    .graphicsLayer {
                        val scale = playScale.value
                        scaleX = scale
                        scaleY = scale
                    }
                    .shadow(
                        elevation = 12.dp,
                        shape = CircleShape,
                        ambientColor = PlayerNavy.copy(alpha = 0.40f),
                        spotColor = PlayerNavy.copy(alpha = 0.40f),
                    )
                    .clip(CircleShape)
                    .background(PlayerNavy)
                    .clickable(
                        interactionSource = playInteractionSource,
                        indication = null,
                    ) { playerViewModel.togglePlayPause() },
                contentAlignment = Alignment.Center,
            ) {
                Crossfade(targetState = isPlaying, label = "playPause") { playing ->
                    Icon(
                        imageVector = if (playing) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = "播放/暂停",
                        tint = Color.White,
                        modifier = Modifier.size(28.dp),
                    )
                }
            }

            PlayerGlyph(PlayerIcons.Next, "下一首", 28.dp, PlayerNavy) { playerViewModel.skipNext() }

            PlayerGlyph(PlayerIcons.Playlist, "播放队列", 22.dp, PlayerIconGray) { showQueueSheet = true }
        }

        // ── 播放设置浮层：定时关闭 ─────────────
        if (showSettingsSheet) {
            ModalBottomSheet(
                onDismissRequest = { showSettingsSheet = false },
                containerColor = Color.White,
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp)
                        .padding(bottom = 28.dp),
                ) {
                    Text(
                        text = "播放设置",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = PlayerNavy,
                    )
                    Spacer(Modifier.height(16.dp))

                    // 定时关闭：到点暂停（不杀服务、不清队列）；播完本曲 = 当前歌曲自然放完时暂停
                    SettingSectionLabel("定时关闭")
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(30, 60, 90, 120, 240).forEach { minutes ->
                            SettingChip(label = "$minutes 分钟", selected = sleepTimer?.minutes == minutes) {
                                playerViewModel.startSleepTimer(minutes)
                            }
                        }
                        SettingChip(label = "播完本曲", selected = sleepTimer?.endOfTrack == true) {
                            playerViewModel.startSleepTimerEndOfTrack()
                        }
                        SettingChip(label = "关闭", selected = sleepTimer == null) {
                            playerViewModel.clearSleepTimer()
                        }
                    }
                    // 分钟模式显示剩余倒计时（播完本曲没有确定性时刻可显示）
                    val remaining = sleepRemainingSeconds
                    if (remaining != null && sleepTimer?.endOfTrack == false) {
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = "${formatTime(remaining * 1000)} 后暂停",
                            fontSize = 12.sp,
                            color = BrandOrange,
                        )
                    }
                }
            }
        }

        // ── 加入歌单浮层：勾选本地歌单即时生效（再点移出）/ 新建歌单 ──
        if (showPlaylistSheet) {
            val playlists by LocalPlaylistStore.playlists.collectAsState()
            var showCreateDialog by remember { mutableStateOf(false) }
            ModalBottomSheet(
                onDismissRequest = { showPlaylistSheet = false },
                containerColor = Color.White,
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp)
                        .padding(bottom = 28.dp),
                ) {
                    Text(
                        text = "加入歌单",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = PlayerNavy,
                    )
                    Spacer(Modifier.height(12.dp))

                    // 新建歌单入口：建后新歌单出现在列表最前，可直接勾选
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .clickable { showCreateDialog = true }
                            .padding(horizontal = 4.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null, tint = BrandOrange, modifier = Modifier.size(22.dp))
                        Spacer(Modifier.width(10.dp))
                        Text("新建歌单", fontSize = 14.sp, fontWeight = FontWeight.Medium, color = BrandOrange)
                    }
                    Spacer(Modifier.height(4.dp))

                    if (playlists.isEmpty()) {
                        Text(
                            text = "还没有歌单，先新建一个",
                            fontSize = 13.sp,
                            color = PlayerIconGray,
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 12.dp),
                        )
                    } else {
                        Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                            playlists.forEach { playlist ->
                                val added = songId != null && songId in playlist.songIds
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(12.dp))
                                        .clickable { songId?.let { LocalPlaylistStore.toggleSong(playlist.id, it) } }
                                        .padding(horizontal = 4.dp, vertical = 12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Text(
                                        text = playlist.name,
                                        fontSize = 15.sp,
                                        color = PlayerNavy,
                                        modifier = Modifier.weight(1f),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                    Text(
                                        text = "${playlist.songIds.size} 首",
                                        fontSize = 12.sp,
                                        color = PlayerIconGray,
                                    )
                                    if (added) {
                                        Spacer(Modifier.width(10.dp))
                                        Icon(
                                            Icons.Default.Check,
                                            contentDescription = "已加入",
                                            tint = BrandOrange,
                                            modifier = Modifier.size(20.dp),
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
            if (showCreateDialog) {
                NewPlaylistDialog(
                    onDismiss = { showCreateDialog = false },
                    onCreate = { name ->
                        LocalPlaylistStore.create(name)
                        showCreateDialog = false
                    },
                )
            }
        }

        // ── 更多菜单浮层：当前歌曲的扩展操作（现仅评分，按可扩展列表实现，ADR 0007）──
        if (showMoreSheet) {
            var showRatingDialog by remember { mutableStateOf(false) }
            ModalBottomSheet(
                onDismissRequest = { showMoreSheet = false },
                containerColor = Color.White,
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp)
                        .padding(bottom = 28.dp),
                ) {
                    Text(
                        text = "更多",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = PlayerNavy,
                    )
                    Spacer(Modifier.height(12.dp))

                    // 评分入口：无播放歌曲时为不可操作态（不提供按压反馈，与 PlayerGlyph enabled 语义一致）
                    val canRate = currentSong != null
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .graphicsLayer { alpha = if (canRate) 1f else 0.4f }
                            .clip(RoundedCornerShape(12.dp))
                            .clickable(enabled = canRate) { showRatingDialog = true }
                            .padding(horizontal = 4.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(PlayerIcons.Star, contentDescription = null, tint = PlayerNavy, modifier = Modifier.size(22.dp))
                        Spacer(Modifier.width(10.dp))
                        Text("评分", fontSize = 14.sp, fontWeight = FontWeight.Medium, color = PlayerNavy)
                    }
                }
            }

            // 评分对话框（二级，与「新建歌单」先例同为浮层内组合）
            if (showRatingDialog && currentSong != null) {
                val song = currentSong!!
                RatingDialog(
                    songTitle = song.title,
                    alreadyRatedStars = ratedSongs[song.id],
                    onSubmit = { stars ->
                        scope.launch {
                            when (MusicRepository.submitRating(song.id, stars)) {
                                RatingOutcome.SUCCESS -> {
                                    RatingStore.record(song.id, stars)
                                    Toast.makeText(context, "评分成功", Toast.LENGTH_SHORT).show()
                                    showRatingDialog = false
                                }
                                // 本地已评记录丢失（卸载重装）的兜底：后端拒绝，按本次所选补记（ADR 0007）
                                RatingOutcome.ALREADY_RATED -> {
                                    RatingStore.record(song.id, stars)
                                    Toast.makeText(context, "已评分过", Toast.LENGTH_SHORT).show()
                                    showRatingDialog = false
                                }
                                RatingOutcome.FAILED ->
                                    Toast.makeText(context, "评分失败，请重试", Toast.LENGTH_SHORT).show()
                            }
                        }
                    },
                    onDismiss = { showRatingDialog = false },
                )
            }
        }

        // ── 播放队列浮层：主控行队列入口打开，展示当前会话队列；点选为队列内跳播，不重建队列 ──
        if (showQueueSheet) {
            ModalBottomSheet(
                onDismissRequest = { showQueueSheet = false },
                containerColor = Color.White,
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp)
                        .padding(bottom = 28.dp),
                ) {
                    Text(
                        text = "播放队列",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = PlayerNavy,
                    )
                    Spacer(Modifier.height(12.dp))

                    // 队列只在点播时产生，空队列属正常态：展示空态文案引导去点歌
                    if (queue.isEmpty()) {
                        Text(
                            text = "还没有播放队列，先去点一首歌吧",
                            fontSize = 13.sp,
                            color = PlayerIconGray,
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 12.dp),
                        )
                    } else {
                        // 队列可能来自整张分类歌曲列表：定高 LazyColumn 滚动，不撑爆浮层；
                        // 关闭系统过滚动光效（列表拖到底的蓝紫色发光是系统默认色，与本页配色无关）
                        CompositionLocalProvider(LocalOverscrollConfiguration provides null) {
                            LazyColumn(modifier = Modifier.heightIn(max = 460.dp)) {
                                itemsIndexed(queue) { index, song ->
                                    val isCurrent = song.id == currentSong?.id
                                    // 行按压反馈用播放器统一的线性变淡，不用默认波纹
                                    val interactionSource = remember { MutableInteractionSource() }
                                    val pressAlpha = rememberPressDimAlpha(interactionSource)
                                    // 行样式：纯白底无行底色，当前曲仅以橙色序号+标题区分
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .graphicsLayer { alpha = pressAlpha.value }
                                            .clickable(
                                                interactionSource = interactionSource,
                                                indication = null,
                                            ) { playerViewModel.skipTo(index) }
                                            .padding(horizontal = 12.dp, vertical = 14.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        // 行首队列序号：固宽左对齐；当前曲橙色
                                        Text(
                                            text = "${index + 1}",
                                            fontSize = 14.sp,
                                            fontWeight = if (isCurrent) FontWeight.SemiBold else FontWeight.Normal,
                                            color = if (isCurrent) BrandOrange else PlayerIconGray,
                                            modifier = Modifier.width(32.dp),
                                        )
                                        Spacer(Modifier.width(12.dp))
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = song.title,
                                                fontSize = 16.sp,
                                                fontWeight = if (isCurrent) FontWeight.SemiBold else FontWeight.Normal,
                                                color = if (isCurrent) BrandOrange else PlayerNavy,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                            )
                                            Text(
                                                text = song.artist.ifBlank { song.categoryName.ifBlank { "佚名" } },
                                                fontSize = 12.sp,
                                                color = PlayerIconGray,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/** 播放器矢量图标按钮：图标居中于触控框内；按压反馈为线性图标专用变淡（见 rememberPressDimAlpha），
 *  无波纹无阴影；touchSize 只扩大点击热区，不改变视觉尺寸；enabled=false 用于不可操作态，无按压反馈 */
@Composable
private fun PlayerGlyph(
    icon: ImageVector,
    desc: String,
    size: Dp,
    tint: Color,
    touchSize: Dp = size,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressAlpha = rememberPressDimAlpha(interactionSource)
    Box(
        modifier = Modifier
            .size(touchSize)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                enabled = enabled,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = desc,
            tint = tint,
            modifier = Modifier
                .size(size)
                .graphicsLayer { alpha = pressAlpha.value },
        )
    }
}

/** 「歌曲 / 歌词」页签：selection 0..1 跟随横滑连续过渡（字号/颜色插值、字重过半切换）；按压反馈同为线性变淡，无波纹 */
@Composable
private fun PlayerTab(
    label: String,
    selection: Float,
    onClick: () -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressAlpha = rememberPressDimAlpha(interactionSource)
    Text(
        text = label,
        fontSize = lerp(14.sp, 16.sp, selection),
        fontWeight = if (selection > 0.5f) FontWeight.SemiBold else FontWeight.Normal,
        color = lerp(PlayerIconGray, PlayerNavy, selection),
        modifier = Modifier
            .graphicsLayer { alpha = pressAlpha.value }
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick,
            )
            .padding(horizontal = 14.dp, vertical = 6.dp),
    )
}

/** 播放设置面板分区标题 */
@Composable
private fun SettingSectionLabel(text: String) {
    Text(
        text = text,
        fontSize = 13.sp,
        fontWeight = FontWeight.SemiBold,
        color = PlayerIconGray,
        modifier = Modifier.padding(bottom = 8.dp),
    )
}

/** 播放设置面板的选项胶囊：选中橙底白字，未选白底描边；无波纹，按压反馈与播放器一致变淡 */
@Composable
private fun SettingChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressAlpha = rememberPressDimAlpha(interactionSource)
    Box(
        modifier = Modifier
            .graphicsLayer { alpha = pressAlpha.value }
            .clip(RoundedCornerShape(50))
            .background(if (selected) BrandOrange else Color.Transparent)
            .border(
                1.dp,
                if (selected) BrandOrange else PlayerIconGray.copy(alpha = 0.45f),
                RoundedCornerShape(50),
            )
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick,
            )
            .padding(horizontal = 14.dp, vertical = 7.dp),
    ) {
        Text(
            text = label,
            fontSize = 13.sp,
            color = if (selected) Color.White else PlayerNavy,
        )
    }
}

/** 评分对话框：未评为 1–5 星输入（默认未选，选星后提交可用）；已评为只读星级展示（ADR 0007）。
 *  星为实心图标，按压反馈用实心缩放分型（与播放键一致），无波纹；点击热区不随缩放变化 */
@Composable
private fun RatingDialog(
    songTitle: String,
    alreadyRatedStars: Int?,
    onSubmit: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    var selected by remember { mutableStateOf(0) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("评分", fontWeight = FontWeight.SemiBold) },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = songTitle,
                    fontSize = 13.sp,
                    color = PlayerIconGray,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(14.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    (1..5).forEach { star ->
                        val interactive = alreadyRatedStars == null
                        val interactionSource = remember { MutableInteractionSource() }
                        val pressed by interactionSource.collectIsPressedAsState()
                        val scale = animateFloatAsState(
                            targetValue = if (pressed) PressScaleDown else 1f,
                            animationSpec = tween(durationMillis = if (pressed) PressInMs else PressOutMs),
                            label = "starPressScale",
                        )
                        Icon(
                            imageVector = Icons.Default.Star,
                            contentDescription = if (interactive) "评 $star 星" else null,
                            tint = if (star <= (alreadyRatedStars ?: selected)) StarGold else UnratedStar,
                            modifier = Modifier
                                .size(36.dp)
                                .graphicsLayer {
                                    scaleX = scale.value
                                    scaleY = scale.value
                                }
                                .clickable(
                                    interactionSource = interactionSource,
                                    indication = null,
                                    enabled = interactive,
                                ) { selected = star },
                        )
                    }
                }
            }
        },
        confirmButton = {
            if (alreadyRatedStars == null) {
                TextButton(onClick = { onSubmit(selected) }, enabled = selected > 0) {
                    Text("提交", color = BrandOrange)
                }
            } else {
                TextButton(onClick = onDismiss) { Text("知道了", color = BrandOrange) }
            }
        },
        dismissButton = {
            if (alreadyRatedStars == null) {
                TextButton(onClick = onDismiss) { Text("取消", color = InkSecondary) }
            }
        },
    )
}

/** 按压变淡反馈：按下 α 降至 [PressDimAlpha]（约 PressInMs），松手约 PressOutMs 恢复；返回 State 供 graphicsLayer 内延迟读取。
 *  internal：同包 LyricsPanel.kt 的歌词行复用同一手感 */
@Composable
internal fun rememberPressDimAlpha(interactionSource: MutableInteractionSource): State<Float> {
    val pressed by interactionSource.collectIsPressedAsState()
    return animateFloatAsState(
        targetValue = if (pressed) PressDimAlpha else 1f,
        animationSpec = tween(durationMillis = if (pressed) PressInMs else PressOutMs),
        label = "pressDim",
    )
}

// 按压反馈手感参数（播放器页统一在此微调）：线性变淡透明度 / 实心块缩放比 / 按下与松手时长
private const val PressDimAlpha = 0.5f
private const val PressScaleDown = 0.9f
private const val PressInMs = 100
private const val PressOutMs = 150

// 覆盖层手感参数（与按压反馈同区微调）：进出动画时长
private const val SlideAnimMs = 200

// 评分对话框未选星颜色（与各列表未选星一致）
private val UnratedStar = Color(0xFFE8E2DB)

/** 封面主色亮色化结果：背景渐变的顶部浅主色与底部近白（ADR 0005） */
private data class CoverPalette(val top: Color, val bottom: Color)

/** 从歌曲封面提取主色并亮色化为背景渐变色；加载失败或无可取色返回 null（背景回退极光图）。
 *  64px 小图足够取色；allowHardware(false) 因 Palette 不支持 hardware bitmap。
 *  亮色化：亮度抬进 0.80~0.92 浅色区保证藏青控件可读；饱和度钳制 0.14~0.40，
 *  近灰图（S < 0.04）不抬饱和，避免 HSL 钳制把灰色染成淡红 */
private suspend fun extractCoverPalette(context: Context, coverUrl: String): CoverPalette? =
    withContext(Dispatchers.Default) {
        val request = ImageRequest.Builder(context)
            .data(coverUrl)
            .allowHardware(false)
            .size(64)
            .build()
        val bitmap = (context.imageLoader.execute(request).drawable as? BitmapDrawable)?.bitmap
            ?: return@withContext null
        val swatch = Palette.from(bitmap)
            .maximumColorCount(16)
            .generate()
            .let { it.vibrantSwatch ?: it.dominantSwatch ?: it.mutedSwatch }
            ?: return@withContext null
        val hsl = FloatArray(3)
        ColorUtils.colorToHSL(swatch.rgb, hsl)
        hsl[2] = hsl[2].coerceIn(0.80f, 0.92f)
        if (hsl[1] >= 0.04f) hsl[1] = hsl[1].coerceIn(0.14f, 0.40f)
        val top = Color(ColorUtils.HSLToColor(hsl))
        CoverPalette(top = top, bottom = lerp(top, Color.White, 0.85f))
    }

/** 下载中的按钮形态：28dp 环形进度 + 中心百分比数字；排队/总长未知（progress < 0）为不确定式旋转圈。仅展示，点击无反应 */
@Composable
private fun PlayerDownloadProgress(progress: Float?) {
    Box(
        modifier = Modifier.size(48.dp),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier.size(28.dp),
            contentAlignment = Alignment.Center,
        ) {
            if (progress == null || progress < 0f) {
                CircularProgressIndicator(
                    modifier = Modifier.size(28.dp),
                    color = BrandOrange,
                    strokeWidth = 2.5.dp,
                )
            } else {
                CircularProgressIndicator(
                    progress = { progress },
                    modifier = Modifier.size(28.dp),
                    color = BrandOrange,
                    trackColor = PlayerTrack,
                    strokeWidth = 2.5.dp,
                )
                Text(
                    text = "${(progress * 100).toInt()}%",
                    fontSize = 9.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = BrandOrange,
                )
            }
        }
    }
}

// Helper function to format time in mm:ss format
private fun formatTime(millis: Long): String {
    val totalSeconds = abs(millis / 1000)
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "%02d:%02d".format(minutes, seconds)
}

/** 播放模式对应图标（ADR 0008）：随机=乱序箭头、顺序=循环环绕、单曲循环=环绕+1 */
private fun playModeIcon(mode: PlayerSettingsManager.PlayMode): ImageVector = when (mode) {
    PlayerSettingsManager.PlayMode.SHUFFLE -> PlayerIcons.Shuffle
    PlayerSettingsManager.PlayMode.SEQUENTIAL -> PlayerIcons.Repeat
    PlayerSettingsManager.PlayMode.REPEAT_ONE -> PlayerIcons.RepeatOne
}

/** 播放模式弹出菜单浮层（ADR 0008，Q3 交互）：三只非焦点 Popup 叠层——
 *  全屏透明遮罩（点菜单与图标以外仅关菜单）、图标热区替身（菜单开着点图标仍轮转、菜单不关）、
 *  锚定图标正上方的白底圆角卡片（当前模式行浅灰高亮）；系统返回由 BackHandler 先关菜单 */
@Composable
private fun PlayModeMenuOverlay(
    anchor: IntOffset,
    current: PlayerSettingsManager.PlayMode,
    onSelect: (PlayerSettingsManager.PlayMode) -> Unit,
    onDismiss: () -> Unit,
    onCycle: () -> Unit,
) {
    // 1. 全屏透明遮罩：负偏移锚点坐标，使遮罩从屏幕原点铺满
    Popup(
        alignment = Alignment.TopStart,
        offset = IntOffset(-anchor.x, -anchor.y),
        properties = PopupProperties(focusable = false),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onDismiss,
                ),
        )
    }

    // 2. 图标热区替身：与本体图标同位，热区扩至 48dp 更易点准、不易滑进菜单；接管菜单开着期间的图标点击（轮转）
    Popup(
        alignment = Alignment.Center,
        properties = PopupProperties(focusable = false),
    ) {
        PlayerGlyph(
            icon = playModeIcon(current),
            desc = "",
            size = 22.dp,
            tint = if (current == PlayerSettingsManager.PlayMode.SEQUENTIAL) PlayerIconGray else BrandOrange,
            touchSize = 48.dp,
        ) { onCycle() }
    }

    // 3. 菜单卡片：底边钉在图标顶边上方 20dp（锚盒 22dp + 间隙 20dp），拉开与图标距离防误触，向上生长；
    //    宽度取最宽行的整行宽（IntrinsicSize.Max：中文可逐字断行，Min 会窄到单字导致竖排），右边不留白；
    //    行内不用 fillMaxWidth（其 intrinsic 会报可用最大宽把卡片顶满屏），列宽定后行背景自然满宽
    val menuLift = with(LocalDensity.current) { (22.dp + 20.dp).roundToPx() }
    Popup(
        alignment = Alignment.BottomStart,
        offset = IntOffset(0, -menuLift),
        properties = PopupProperties(focusable = false),
    ) {
        Column(
            modifier = Modifier
                .width(IntrinsicSize.Max)
                .shadow(6.dp, RoundedCornerShape(12.dp))
                .clip(RoundedCornerShape(12.dp))
                .background(Color.White),
        ) {
            PlayerSettingsManager.PlayMode.values().forEach { mode ->
                PlayModeMenuRow(
                    mode = mode,
                    selected = mode == current,
                    onClick = { onSelect(mode) },
                )
            }
        }
    }
}

/** 播放模式菜单行：20dp 图标 + 14sp 文字，当前模式行浅灰底；按压反馈线性变淡、无波纹 */
@Composable
private fun PlayModeMenuRow(
    mode: PlayerSettingsManager.PlayMode,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressAlpha = rememberPressDimAlpha(interactionSource)
    Row(
        modifier = Modifier
            .graphicsLayer { alpha = pressAlpha.value }
            .clickable(interactionSource = interactionSource, indication = null, onClick = onClick)
            .background(if (selected) PlaceholderBg else Color.Transparent)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = playModeIcon(mode),
            contentDescription = null,
            tint = PlayerNavy,
            modifier = Modifier.size(20.dp),
        )
        Spacer(Modifier.width(10.dp))
        Text(mode.label, fontSize = 14.sp, color = PlayerNavy)
    }
}

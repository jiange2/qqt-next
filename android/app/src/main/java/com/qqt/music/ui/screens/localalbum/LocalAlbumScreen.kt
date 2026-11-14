package com.qqt.music.ui.screens.localalbum

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.outlined.Album
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.qqt.music.data.local.LocalAlbum
import com.qqt.music.data.local.LocalSong
import com.qqt.music.player.QueueSource
import com.qqt.music.ui.components.EmptyState
import com.qqt.music.ui.components.RowSwipeAction
import com.qqt.music.ui.components.RowSwipeRevealState
import com.qqt.music.ui.screens.localmusic.LocalAlbumCover
import com.qqt.music.ui.screens.localmusic.LocalMetadataEditSheet
import com.qqt.music.ui.screens.localmusic.LocalMusicViewModel
import com.qqt.music.ui.screens.localmusic.LocalSongRow
import com.qqt.music.ui.theme.BrandOrange
import com.qqt.music.ui.theme.InkFaint
import com.qqt.music.ui.theme.InkPrimary
import com.qqt.music.ui.theme.InkSecondary
import com.qqt.music.viewmodel.PlayerViewModel

/**
 * 本地专辑页：某张本地专辑的歌曲列表，页面标题由顶栏取自专辑名。
 *
 * 歌曲按音轨号升序（音轨号为空按标题）——顺序在分组时就算好了（[com.qqt.music.data.local.groupIntoAlbums]）；
 * 点歌以整张专辑为播放队列，来源仍是本地音乐（与本地音乐页共用同一份来源记忆）。
 * 长按歌曲行同样可以打开元数据编辑面板（与歌曲视图同一个面板）。
 * 歌行左滑删除与本地音乐页同一条接缝；删空这张专辑时由导航层自动返回本地音乐页。
 * 编辑模式（[editing]，开关与勾选集合在导航层，票据04）：顶栏 ⋮ 菜单「批量管理」进入，
 * 点行 / 长按切换勾选（不跳播、不弹单首面板）、左滑删除收起；顶栏 / 底栏 / 批量面板由导航层接线，
 * 全选改名让整张专辑换键时页面跟随新专辑（去向推算 [com.qqt.music.data.local.batchAlbumNewKeys]）。
 * 数据全部来自扫描索引，页面不发起任何网络请求。
 */
@Composable
fun LocalAlbumScreen(
    albumKey: String?,
    viewModel: LocalMusicViewModel,
    playerViewModel: PlayerViewModel,
    editing: Boolean,
    selection: Set<String>,
    onToggleSelect: (LocalSong) -> Unit,
) {
    val albums by viewModel.albums.collectAsState()
    val loading by viewModel.loading.collectAsState()
    val permissionGranted by viewModel.permissionGranted.collectAsState()
    val album = albums.firstOrNull { it.key == albumKey }
    // 正在编辑元数据的歌曲：非空即打开编辑面板（长按歌曲行打开）
    var editingSong by remember { mutableStateOf<LocalSong?>(null) }
    val swipeState = remember { RowSwipeRevealState() }
    val listState = rememberLazyListState()
    // 列表滚动即收起左滑展开态，避免行停在半开位置
    LaunchedEffect(listState.isScrollInProgress) {
        if (listState.isScrollInProgress) swipeState.close()
    }
    // 进入 / 退出编辑模式同样收起：编辑态左滑删除收起（swipe = null），退出时不残留展开位
    LaunchedEffect(editing) { swipeState.close() }

    // 进专辑页核对音频权限（不刷新）：进程被杀后直接恢复到专辑页不走本地音乐页的 onPageEntered，
    // 不核对则 permissionGranted 停在初值 false，正常已授权的库被误判成权限被关（左滑删除露不出）
    LaunchedEffect(Unit) { viewModel.onAlbumPageEntered() }

    // 回到前台重新核对权限（与本地音乐页同口径）：系统设置里被关掉时灰显与删除门槛立即生效
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) viewModel.onAppResumed()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    when {
        // 索引还没读回来：先别把「还没读出来」当成「专辑没有了」
        album == null && loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = BrandOrange, modifier = Modifier.size(28.dp))
        }

        // 专辑可能因为重扫、移除目录或改专辑名而不再存在
        album == null -> EmptyState(
            icon = Icons.Outlined.Album,
            title = "专辑已不存在",
            subtitle = "重新扫描后再试",
        )

        else -> Column(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.White),
        ) {
            LocalAlbumHeader(album = album)
            val queue = album.songs.map { it.toSong() }
            LazyColumn(
                modifier = Modifier.weight(1f),
                state = listState,
                contentPadding = PaddingValues(bottom = 6.dp),
            ) {
                itemsIndexed(album.songs, key = { _, song -> song.id }) { index, song ->
                    // 显式声明类型：if 分支里直接写 lambda 字面量会掉进可空函数类型的推断坑
                    // （onLongClick 可空，见 LocalMusicScreen 同款注释）
                    val rowClick: () -> Unit = if (editing) {
                        // 编辑模式：点行 = 切换勾选（不跳播）
                        { onToggleSelect(song) }
                    } else {
                        // 点歌以整张专辑为队列（spec：点击本地专辑以整张专辑顺序聆听）
                        {
                            playerViewModel.playSong(
                                song = song.toSong(),
                                queue = queue,
                                sourceDescriptor = QueueSource.LOCAL,
                            )
                        }
                    }
                    val rowLongClick: () -> Unit = if (editing) {
                        // 编辑模式：长按同样切换勾选，不弹单首面板；灰显行由行内 enabled 挡住
                        { onToggleSelect(song) }
                    } else {
                        { editingSong = song }
                    }
                    LocalSongRow(
                        index = index + 1,
                        song = song,
                        playerViewModel = playerViewModel,
                        onClick = rowClick,
                        onLongClick = rowLongClick,
                        // 权限被关时不露左滑删除（整库灰显是权限问题，不是文件失效）；文件真不可用的行照常可删；
                        // 编辑模式收起左滑删除：勾选交互不与行内手势打架
                        swipe = if (!editing && permissionGranted) {
                            RowSwipeAction(swipeState, "删除") { viewModel.requestDelete(song) }
                        } else {
                            null
                        },
                        editing = editing,
                        selected = song.id in selection,
                    )
                }
            }
        }
    }

    // 元数据编辑面板：长按歌曲行打开。改专辑名后歌曲可能当场换组，这张专辑的列表随之刷新
    editingSong?.let { song ->
        LocalMetadataEditSheet(
            song = song,
            viewModel = viewModel,
            playerViewModel = playerViewModel,
            onDismiss = { editingSong = null },
        )
    }
}

/** 专辑头：封面 + 专辑名 + 艺术家 + 歌曲数 */
@Composable
private fun LocalAlbumHeader(album: LocalAlbum) {
    val albumName = album.name
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        LocalAlbumCover(
            albumArtUri = album.albumArtUri,
            albumName = albumName,
            modifier = Modifier.size(88.dp),
        )
        Spacer(Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = albumName,
                fontSize = 17.sp,
                fontWeight = FontWeight.SemiBold,
                color = InkPrimary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = album.artist.ifBlank { "未知艺术家" },
                fontSize = 13.sp,
                color = InkSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(2.dp))
            Text(text = "${album.songs.size} 首", fontSize = 12.sp, color = InkFaint)
        }
    }
}

/**
 * 本地专辑页的顶栏菜单（票据04）：只有「批量管理」一项，点击进入本页编辑模式并收起菜单。
 * 未授权音频权限时禁用（沿用本地音乐页「批量管理」的门槛范式）；专辑不存在 / 加载中不额外禁用
 * （进入后无行可选，全选为 no-op，与本地音乐页空库口径一致）。
 */
@Composable
fun LocalAlbumTopBarMenu(editEnabled: Boolean, onEnterEdit: () -> Unit) {
    var menuExpanded by remember { mutableStateOf(false) }

    Box {
        IconButton(onClick = { menuExpanded = true }) {
            Icon(Icons.Default.MoreVert, contentDescription = "本地专辑菜单", tint = InkPrimary)
        }
        DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
            DropdownMenuItem(
                text = { Text("批量管理") },
                enabled = editEnabled,
                onClick = {
                    menuExpanded = false
                    onEnterEdit()
                },
            )
        }
    }
}

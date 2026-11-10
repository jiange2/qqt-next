package com.qqt.music.ui.screens.localmusic

import android.Manifest
import android.net.Uri
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.outlined.Album
import androidx.compose.material.icons.outlined.MusicNote
import androidx.compose.material.icons.outlined.MusicOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.qqt.music.data.local.LocalAlbum
import com.qqt.music.data.local.LocalSong
import com.qqt.music.player.QueueSource
import com.qqt.music.ui.components.EmptyState
import com.qqt.music.ui.theme.BrandOrange
import com.qqt.music.ui.theme.BrandOrangeSoft
import com.qqt.music.ui.theme.InkFaint
import com.qqt.music.ui.theme.InkPrimary
import com.qqt.music.ui.theme.InkSecondary
import com.qqt.music.ui.theme.PlaceholderBg
import com.qqt.music.ui.theme.WarmBackground
import com.qqt.music.viewmodel.PlayerViewModel

/**
 * 本地音乐页（歌曲 / 专辑两视图）：来源是系统媒体库（spec，推翻 ADR 0019）。
 *
 * 首次进入（未授权且没有库快照）给一句说明与「授权音乐访问」按钮，拉起系统标准的音频权限弹窗；
 * 授权后立刻（秒级）按标题升序（中文排序）列出全机音乐；进入页面时静默刷新一次库，
 * 没有进度条、没有取消（spec：扫描只是一次查询）。
 * 权限被系统关闭后进页面：快照保留并整体灰显 + 重新授权横幅，不放空列表假装没歌。
 * 专辑视图把同一份列表按专辑归并成卡片墙，点开进本地专辑页。
 * 长按歌曲行打开元数据编辑面板（与全屏播放器「更多菜单 → 编辑信息」同一个面板）。
 * 顶栏菜单：「重新扫描」（完成后提示新增 / 移除数量）与「排除的文件夹」（票据 03）。
 * 两个视图都不发起任何网络请求。
 */
@Composable
fun LocalMusicScreen(
    viewModel: LocalMusicViewModel,
    playerViewModel: PlayerViewModel,
    onAlbumClick: (LocalAlbum) -> Unit = {},
) {
    val loading by viewModel.loading.collectAsState()
    val permissionGranted by viewModel.permissionGranted.collectAsState()
    val songs by viewModel.songs.collectAsState()
    val albums by viewModel.albums.collectAsState()
    // 视图选择只活在本页组合内：进去页面默认歌曲视图，不记忆上次的选择（spec「导航与页面」）
    var showAlbums by remember { mutableStateOf(false) }
    // 正在编辑元数据的歌曲：非空即打开编辑面板（长按歌曲行打开）
    var editingSong by remember { mutableStateOf<LocalSong?>(null) }

    val context = LocalContext.current
    val notice by viewModel.notice.collectAsState()
    // 重扫完成的一次性轻提示：与全 App 的 Toast 轻提示口径一致
    LaunchedEffect(notice) {
        val text = notice ?: return@LaunchedEffect
        Toast.makeText(context, text, Toast.LENGTH_SHORT).show()
        viewModel.consumeNotice()
    }

    // 音频权限请求：Android 13+ 是音乐粒度的 READ_MEDIA_AUDIO，12 及以下 READ_EXTERNAL_STORAGE
    //（清单标 maxSdkVersion 32，spec「扫描来源与权限」）
    val audioPermission = if (Build.VERSION.SDK_INT >= 33) {
        Manifest.permission.READ_MEDIA_AUDIO
    } else {
        Manifest.permission.READ_EXTERNAL_STORAGE
    }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> viewModel.onPermissionResult(granted) }

    // 进入本地音乐页：核对权限并静默刷新一次库（同一次进入含从权限弹窗返回的场景）
    LaunchedEffect(Unit) { viewModel.onPageEntered() }

    when {
        // 快照还在读：先给加载态，避免闪过一帧「还没有内容」的引导空态
        loading -> LoadingState()

        // 未授权且没有快照：首次进入的说明空态，授权后立刻出全库
        songs.isEmpty() && !permissionGranted -> EmptyState(
            icon = Icons.Outlined.MusicNote,
            title = "这里是你手机里的音乐",
            subtitle = "授权音乐访问后，App 直接读取系统媒体库，立刻列出全部本地歌曲，不需要选择任何文件夹",
            actionText = "授权音乐访问",
            onAction = { permissionLauncher.launch(audioPermission) },
        )

        // 已授权但库是空的：不是权限问题，媒体库里确实没有音乐
        songs.isEmpty() -> EmptyState(
            icon = Icons.Outlined.MusicOff,
            title = "没有找到可播放的音乐",
            subtitle = "系统媒体库里还没有音乐；把音乐文件放进手机存储后点「重新扫描」",
            actionText = "重新扫描",
            onAction = viewModel::rescan,
        )

        // 有快照但权限没了：旧列表灰显 + 重新授权横幅（歌没丢，只是权限没了）
        !permissionGranted -> Column(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.White),
        ) {
            PermissionRevokedBanner(onGrant = { permissionLauncher.launch(audioPermission) })
            LibraryArea(
                showAlbums = showAlbums,
                onSelectView = { showAlbums = it },
                songs = songs,
                albums = albums,
                playerViewModel = playerViewModel,
                onSongClick = { editingSong = it },
                onAlbumClick = onAlbumClick,
            )
        }

        else -> Column(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.White),
        ) {
            LibraryArea(
                showAlbums = showAlbums,
                onSelectView = { showAlbums = it },
                songs = songs,
                albums = albums,
                playerViewModel = playerViewModel,
                onSongClick = { editingSong = it },
                onAlbumClick = onAlbumClick,
            )
        }
    }

    // 元数据编辑面板：长按歌曲行打开。保存只写覆盖层，列表与专辑分组立即跟着变
    editingSong?.let { song ->
        LocalMetadataEditSheet(
            song = song,
            viewModel = viewModel,
            playerViewModel = playerViewModel,
            onDismiss = { editingSong = null },
        )
    }
}

/** 歌曲视图与专辑视图的公共区域：视图切换 + 列表/卡片墙（歌曲视图点歌以当前完整列表为队列） */
@Composable
private fun LibraryArea(
    showAlbums: Boolean,
    onSelectView: (Boolean) -> Unit,
    songs: List<LocalSong>,
    albums: List<LocalAlbum>,
    playerViewModel: PlayerViewModel,
    onSongClick: (LocalSong) -> Unit,
    onAlbumClick: (LocalAlbum) -> Unit,
) {
    LibraryViewSwitch(albumSelected = showAlbums, onSelect = onSelectView)
    if (showAlbums) {
        AlbumGrid(albums = albums, onAlbumClick = onAlbumClick, modifier = Modifier.weight(1f))
    } else {
        LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(vertical = 6.dp),
        ) {
            itemsIndexed(songs, key = { _, song -> song.id }) { index, song ->
                LocalSongRow(
                    index = index + 1,
                    song = song,
                    playerViewModel = playerViewModel,
                    // 点歌以当前完整歌曲列表为队列，来源记为本地音乐（播放模式按该来源记忆）
                    onClick = {
                        playerViewModel.playSong(
                            song = song.toSong(),
                            queue = songs.map { it.toSong() },
                            sourceDescriptor = QueueSource.LOCAL,
                        )
                    },
                    onLongClick = { onSongClick(song) },
                )
            }
        }
    }
}

/**
 * 「歌曲 / 专辑」切换：分段控件，选中项白底橙字。
 * 视图选择不落盘、不记忆，进页面就是歌曲视图（spec「导航与页面」）。
 */
@Composable
private fun LibraryViewSwitch(albumSelected: Boolean, onSelect: (Boolean) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 8.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(PlaceholderBg)
            .padding(3.dp),
    ) {
        ViewSwitchSegment("歌曲", selected = !albumSelected, onClick = { onSelect(false) }, modifier = Modifier.weight(1f))
        ViewSwitchSegment("专辑", selected = albumSelected, onClick = { onSelect(true) }, modifier = Modifier.weight(1f))
    }
}

@Composable
private fun ViewSwitchSegment(text: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(15.dp))
            .background(if (selected) Color.White else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(vertical = 6.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            fontSize = 13.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            color = if (selected) BrandOrange else InkSecondary,
        )
    }
}

/** 专辑卡片墙：两列，与在线「音乐专辑」页同一套卡片尺寸（1:1 封面 + 名称在下） */
@Composable
private fun AlbumGrid(
    albums: List<LocalAlbum>,
    onAlbumClick: (LocalAlbum) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 2.dp, bottom = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        modifier = modifier,
    ) {
        items(albums, key = { it.key }) { album ->
            LocalAlbumCard(album = album, onClick = { onAlbumClick(album) })
        }
    }
}

/** 专辑卡片：封面（无封面时占位图）+ 专辑名 + 艺术家 · 歌曲数 */
@Composable
private fun LocalAlbumCard(album: LocalAlbum, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
    ) {
        LocalAlbumCover(
            albumArtUri = album.albumArtUri,
            albumName = album.name,
            modifier = Modifier.fillMaxWidth().aspectRatio(1f),
        )
        Text(
            text = album.name,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
            color = InkPrimary,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 8.dp),
        )
        Spacer(Modifier.height(2.dp))
        Text(
            text = "${album.artist.ifBlank { "未知艺术家" }} · ${album.songs.size} 首",
            fontSize = 11.sp,
            color = InkSecondary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/**
 * 专辑封面：系统媒体库预抽取的专辑图（按成员行的媒体库专辑 ID 构造的 albumart URI，
 * 见 [LocalAlbum.albumArtUri]），由 Coil 直接加载并缓存（内存 + 磁盘）——content:// 本地读取，
 * 不发起任何网络请求。加载中与加载失败（含系统没有该专辑的预抽取图）都露出底层占位图，
 * 与在线专辑卡片的占位风格一致。
 */
@Composable
internal fun LocalAlbumCover(albumArtUri: Uri, albumName: String, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(PlaceholderBg),
        contentAlignment = Alignment.Center,
    ) {
        // 占位图垫底：加载中 / 加载失败（无专辑图）时露出，封面加载成功后被盖住
        Icon(
            Icons.Outlined.Album,
            contentDescription = null,
            tint = InkFaint,
            modifier = Modifier.size(32.dp),
        )
        AsyncImage(
            model = albumArtUri,
            contentDescription = albumName,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
        )
    }
}

/** 首帧/启动读快照：只做“App 正在把上次的列表拿回来”的提示，不阻塞页面 */
@Composable
private fun LoadingState() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(WarmBackground),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        CircularProgressIndicator(
            modifier = Modifier.size(22.dp),
            color = BrandOrange,
            strokeWidth = 2.dp,
        )
        Spacer(Modifier.height(10.dp))
        Text("正在加载本地音乐", color = InkFaint, fontSize = 13.sp)
    }
}

/**
 * 权限被系统关闭后的顶部提示条：说明为什么列表灰显，右侧按钮直接再次拉起授权
 * （重新授权成功后自动刷新，灰显的行恢复可播；spec 用户故事 6）。
 */
@Composable
private fun PermissionRevokedBanner(onGrant: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(BrandOrangeSoft)
            .padding(start = 16.dp, end = 8.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "音乐访问权限已被关闭，重新授权后即可播放",
            color = InkPrimary,
            fontSize = 13.sp,
            modifier = Modifier.weight(1f),
        )
        TextButton(
            onClick = onGrant,
            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
        ) {
            Text("授权音乐访问", color = BrandOrange, fontSize = 13.sp)
        }
    }
}

/**
 * 本地音乐页的顶栏菜单（替换默认搜索入口）：
 * - 「重新扫描」：整库一次查询，完成后提示新增 / 移除数量；未授权音频权限时不可用
 * - 「排除的文件夹」：打开排除面板（票据 03）——自动列出库中含音乐的文件夹并带开关，
 *   切换即时生效、不需要重新扫描，飞行模式下照常可用（纯本地过滤），故始终可点
 */
@Composable
fun LocalMusicTopBarMenu(
    viewModel: LocalMusicViewModel,
    rescanEnabled: Boolean,
    onRescan: () -> Unit,
) {
    var menuExpanded by remember { mutableStateOf(false) }
    var showExcludedFolders by remember { mutableStateOf(false) }

    Box {
        IconButton(onClick = { menuExpanded = true }) {
            Icon(Icons.Default.MoreVert, contentDescription = "本地音乐菜单", tint = InkPrimary)
        }
        DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
            DropdownMenuItem(
                text = { Text("重新扫描") },
                enabled = rescanEnabled,
                onClick = {
                    menuExpanded = false
                    onRescan()
                },
            )
            DropdownMenuItem(
                text = { Text("排除的文件夹") },
                onClick = {
                    menuExpanded = false
                    showExcludedFolders = true
                },
            )
        }
    }

    if (showExcludedFolders) {
        LocalExcludedFoldersSheet(viewModel = viewModel, onDismiss = { showExcludedFolders = false })
    }
}

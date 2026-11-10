package com.qqt.music.ui.screens.localmusic

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
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
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import coil.compose.AsyncImage
import com.qqt.music.data.local.LocalAlbum
import com.qqt.music.data.local.LocalSong
import com.qqt.music.data.local.audioReadPermission
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
 * 拒绝过则升级为「重试 + 去系统设置」双入口（重复拒绝后系统可能不再弹窗，去设置是兜底）。
 * 授权后立刻（秒级）按标题升序（中文排序）列出全机音乐；进入页面时静默刷新一次库，
 * 没有进度条、没有取消（spec：扫描只是一次查询）。
 * 权限被系统关闭后进页面（或回前台时复查发现被关）：快照保留并整体灰显 + 重新授权横幅，
 * 不放空列表假装没歌；重新授权成功（弹窗或系统设置任一路径）自动刷新，灰显的行恢复可播。
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
    val permissionDeniedOnce by viewModel.permissionDeniedOnce.collectAsState()
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

    // 音频权限请求：权限名按系统版本取用（13+ 音乐粒度 READ_MEDIA_AUDIO / 12 及以下
    // READ_EXTERNAL_STORAGE，清单标 maxSdkVersion 32，spec「扫描来源与权限」）。
    // launch 是一次真实的重新请求：拒绝过后再点「重试」仍会拉起系统弹窗——
    // 若用户已勾选「不再询问」，系统会直接回绝不给弹窗，此时走「去系统设置」入口（票据02）
    val audioPermission = audioReadPermission(Build.VERSION.SDK_INT)
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> viewModel.onPermissionResult(granted) }

    // 「去系统设置」：打开本 App 的系统设置页，音频权限开关在那里；打开失败静默忽略（个别桌面形态没有该页）
    fun openAppSettings() {
        runCatching {
            context.startActivity(
                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                    .setData(Uri.fromParts("package", context.packageName, null)),
            )
        }
    }

    // 进入本地音乐页：核对权限并静默刷新一次库（同一次进入含从权限弹窗返回的场景）
    LaunchedEffect(Unit) { viewModel.onPageEntered() }

    // 回到前台（从系统设置页返回等时机）重新核对权限：设置里重新开启 → 自动刷新恢复，
    // 灰显的行恢复可播；又被关闭 → 灰显与重新授权横幅立即出现（票据02）
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) viewModel.onAppResumed()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    when {
        // 快照还在读：先给加载态，避免闪过一帧「还没有内容」的引导空态
        loading -> LoadingState()

        // 未授权且没有快照：说明空态——空列表是权限问题而不是没有歌（spec 用户故事 5）。
        // 拒绝过 → 「重试 + 去系统设置」双入口；没拒绝过 → 单一授权入口
        songs.isEmpty() && !permissionGranted ->
            if (permissionDeniedOnce) {
                PermissionDeniedState(
                    onRetry = { permissionLauncher.launch(audioPermission) },
                    onOpenSettings = { openAppSettings() },
                )
            } else {
                EmptyState(
                    icon = Icons.Outlined.MusicNote,
                    title = "这里是你手机里的音乐",
                    subtitle = "授权音乐访问后，App 直接读取系统媒体库，立刻列出全部本地歌曲，不需要选择任何文件夹",
                    actionText = "授权音乐访问",
                    onAction = { permissionLauncher.launch(audioPermission) },
                )
            }

        // 已授权但库是空的：不是权限问题，媒体库里确实没有音乐
        songs.isEmpty() -> EmptyState(
            icon = Icons.Outlined.MusicOff,
            title = "没有找到可播放的音乐",
            subtitle = "系统媒体库里还没有音乐；把音乐文件放进手机存储后点「重新扫描」",
            actionText = "重新扫描",
            onAction = viewModel::rescan,
        )

        // 有快照但权限没了：旧列表灰显 + 重新授权横幅（歌没丢，只是权限没了）；专辑卡片同样不可点
        !permissionGranted -> Column(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.White),
        ) {
            PermissionRevokedBanner(
                onRetry = { permissionLauncher.launch(audioPermission) },
                onOpenSettings = { openAppSettings() },
            )
            LibraryArea(
                showAlbums = showAlbums,
                onSelectView = { showAlbums = it },
                songs = songs,
                albums = albums,
                playerViewModel = playerViewModel,
                onSongClick = { editingSong = it },
                onAlbumClick = onAlbumClick,
                albumsEnabled = false,
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

/** 歌曲视图与专辑视图的公共区域：视图切换 + 列表/卡片墙（歌曲视图点歌以当前完整列表为队列）。
 *  [albumsEnabled] 为 false（权限被关）时专辑卡片灰显不可点：点进去只能看到一页不可用的行 */
@Composable
private fun LibraryArea(
    showAlbums: Boolean,
    onSelectView: (Boolean) -> Unit,
    songs: List<LocalSong>,
    albums: List<LocalAlbum>,
    playerViewModel: PlayerViewModel,
    onSongClick: (LocalSong) -> Unit,
    onAlbumClick: (LocalAlbum) -> Unit,
    albumsEnabled: Boolean = true,
) {
    LibraryViewSwitch(albumSelected = showAlbums, onSelect = onSelectView)
    if (showAlbums) {
        AlbumGrid(
            albums = albums,
            onAlbumClick = onAlbumClick,
            enabled = albumsEnabled,
            modifier = Modifier.weight(1f),
        )
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
    enabled: Boolean = true,
) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 2.dp, bottom = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        modifier = modifier,
    ) {
        items(albums, key = { it.key }) { album ->
            LocalAlbumCard(album = album, enabled = enabled, onClick = { onAlbumClick(album) })
        }
    }
}

/** 专辑卡片：封面（无封面时占位图）+ 专辑名 + 艺术家 · 歌曲数；[enabled] 为 false 时灰显不可点 */
@Composable
private fun LocalAlbumCard(
    album: LocalAlbum,
    onClick: () -> Unit,
    enabled: Boolean = true,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled, onClick = onClick),
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
            color = if (enabled) InkPrimary else InkFaint,
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
 * 拒绝授权后的说明空态：解释空列表是权限问题而不是没有歌（spec 用户故事 5），
 * 「重试」再次拉起系统授权弹窗，「去系统设置」打开本 App 的设置页手动开启。
 * 视觉沿用共享 [EmptyState] 的语言（浅橙圆底图标 + 主/副文案），但需要两个操作，
 * 共享组件不为单页扩参，故单独成组件（票据02）。
 */
@Composable
private fun PermissionDeniedState(onRetry: () -> Unit, onOpenSettings: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(WarmBackground)
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(
            modifier = Modifier
                .size(88.dp)
                .clip(CircleShape)
                .background(BrandOrangeSoft),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Outlined.MusicNote, contentDescription = null, tint = BrandOrange, modifier = Modifier.size(40.dp))
        }
        Spacer(Modifier.height(18.dp))
        Text(
            "还没有音乐访问权限",
            color = InkSecondary,
            fontSize = 15.sp,
            fontWeight = FontWeight.Medium,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            "没有权限就读取不了系统媒体库；可以重试授权，或到系统设置里手动开启",
            color = InkFaint,
            fontSize = 13.sp,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(16.dp))
        Button(
            onClick = onRetry,
            colors = ButtonDefaults.buttonColors(containerColor = BrandOrange),
            shape = RoundedCornerShape(20.dp),
            modifier = Modifier.width(140.dp),
        ) { Text("重试") }
        TextButton(onClick = onOpenSettings) {
            Text("去系统设置", color = InkSecondary, fontSize = 13.sp)
        }
    }
}

/**
 * 权限被系统关闭后的顶部提示条：说明为什么列表灰显（歌没丢，只是权限没了），右侧
 * 「重试」再次拉起授权弹窗、「去系统设置」跳设置页手动开启——重新授权成功后自动刷新，
 * 灰显的行恢复可播（spec 用户故事 6）。重复拒绝（或勾了不再询问）后系统可能不再弹窗，
 * 「去系统设置」是始终可用的兜底路径（票据02）。
 */
@Composable
private fun PermissionRevokedBanner(onRetry: () -> Unit, onOpenSettings: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(BrandOrangeSoft)
            .padding(start = 16.dp, end = 4.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "音乐访问权限已被关闭，重新授权后即可播放",
            color = InkPrimary,
            fontSize = 13.sp,
            modifier = Modifier.weight(1f),
        )
        TextButton(
            onClick = onRetry,
            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
        ) {
            Text("重试", color = BrandOrange, fontSize = 13.sp)
        }
        TextButton(
            onClick = onOpenSettings,
            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
        ) {
            Text("去系统设置", color = BrandOrange, fontSize = 13.sp)
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

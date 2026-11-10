package com.qqt.music.ui.screens.localmusic

import android.widget.Toast
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
import com.qqt.music.data.local.LocalMusicRoot
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
import java.io.File

/**
 * 本地音乐页（歌曲 / 专辑两视图）：未授权文件夹时引导选目录；授权后自动扫描，
 * 扫描中展示进度并可取消，完成后按标题升序（中文排序）列出可播放的本地歌曲，
 * 并以轻提示报告本次的新增 / 移除数量；重扫期间旧列表照常可用，失败不破坏旧索引。
 * 列表来自扫描索引（跨重启保留），启动时读盘未完成前不把空当空态。
 * 专辑视图把同一份索引按专辑归并成卡片墙，点开进本地专辑页。
 * 长按歌曲行打开元数据编辑面板（与全屏播放器「更多菜单 → 编辑信息」同一个面板）。
 * 进入页面时异步核对根目录的持久化授权：全部失效给重新选择的空态，部分失效给顶部提示与管理入口，
 * 对应歌曲灰显；文件被删 / 读不到的行同样灰显且不可点。两个视图都不发起任何网络请求。
 */
@Composable
fun LocalMusicScreen(
    viewModel: LocalMusicViewModel,
    playerViewModel: PlayerViewModel,
    onSelectFolder: () -> Unit,
    onManageFolders: () -> Unit = {},
    onAlbumClick: (LocalAlbum) -> Unit = {},
) {
    val loading by viewModel.loading.collectAsState()
    val roots by viewModel.roots.collectAsState()
    val songs by viewModel.songs.collectAsState()
    val albums by viewModel.albums.collectAsState()
    val scan by viewModel.scan.collectAsState()
    val indexUnreadable by viewModel.indexUnreadable.collectAsState()
    val revokedTrees by viewModel.revokedTrees.collectAsState()
    val allRootsRevoked by viewModel.allRootsRevoked.collectAsState()
    // 视图选择只活在本页组合内：进去页面默认歌曲视图，不记忆上次的选择（spec「导航与页面」）
    var showAlbums by remember { mutableStateOf(false) }
    // 正在编辑元数据的歌曲：非空即打开编辑面板（长按歌曲行打开）
    var editingSong by remember { mutableStateOf<LocalSong?>(null) }

    val context = LocalContext.current
    val notice by viewModel.notice.collectAsState()
    // 扫描完成 / 失败的一次性轻提示：与全 App 的 Toast 轻提示口径一致
    LaunchedEffect(notice) {
        val text = notice ?: return@LaunchedEffect
        Toast.makeText(context, text, Toast.LENGTH_SHORT).show()
        viewModel.consumeNotice()
    }

    // 进入本地音乐页时核对持久化授权：异步进行，不阻塞首帧（spec「权限与失效」）。
    // 同一次进入含从目录选择器返回的场景，重新授权后由 addRoot 再核对一次
    LaunchedEffect(Unit) { viewModel.checkRootPermissions() }

    when {
        // 索引还在读：先给加载态，避免闪过一帧「还没有内容」的引导空态
        loading -> LoadingState()

        // 所有根目录的持久化授权都被系统回收：不是歌丢了，而是权限没了——引导重新选择
        allRootsRevoked -> EmptyState(
            icon = Icons.Outlined.MusicOff,
            title = "音乐文件夹访问权限已失效",
            subtitle = "系统回收了对已添加文件夹的访问权限。重新选择同一个文件夹即可恢复，歌曲和你的修改都还在",
            actionText = "重新选择文件夹",
            onAction = onSelectFolder,
        )

        // 索引坏了且连根目录都没能重建：只能让用户重新选一次目录（授权也没了）
        indexUnreadable && roots.isEmpty() -> EmptyState(
            icon = Icons.Outlined.MusicOff,
            title = "本地索引无法读取",
            subtitle = "索引文件已损坏，重新选择音乐文件夹后 App 会重新建立列表",
            actionText = "选择音乐文件夹",
            onAction = onSelectFolder,
        )

        roots.isEmpty() -> EmptyState(
            icon = Icons.Outlined.MusicNote,
            title = "本地音乐还没有内容",
            subtitle = "选择一个音乐文件夹，App 只扫描你授权的目录，不需要任何存储权限",
            actionText = "选择音乐文件夹",
            onAction = onSelectFolder,
        )

        songs.isEmpty() && scan.running -> ScanningState(scan, onCancel = viewModel::cancelScan)

        // 首次扫描就取消：没有上一次结果可展示，不能当成“目录里没有音乐”
        songs.isEmpty() && scan.cancelled -> EmptyState(
            icon = Icons.Outlined.MusicNote,
            title = "扫描已取消",
            subtitle = "这次扫描被中断，还没有可播放的音乐",
            actionText = "重新扫描",
            onAction = viewModel::startScan,
        )

        // 首次扫描就失败：没有上一次结果可展示，给出失败提示与重试入口
        songs.isEmpty() && scan.failed -> EmptyState(
            icon = Icons.Outlined.MusicOff,
            title = "扫描失败",
            subtitle = "读取你授权的音乐文件夹时出错，请稍后重新扫描",
            actionText = "重新扫描",
            onAction = viewModel::startScan,
        )

        // 索引无法识别：根目录已从系统授权重建，重新扫描即重建列表
        indexUnreadable -> EmptyState(
            icon = Icons.Outlined.MusicOff,
            title = "本地索引无法读取",
            subtitle = "索引文件已损坏，重新扫描你授权过的音乐文件夹即可恢复列表",
            actionText = "重新扫描",
            onAction = viewModel::startScan,
        )

        songs.isEmpty() -> EmptyState(
            icon = Icons.Outlined.MusicOff,
            title = "没有找到可播放的音乐",
            subtitle = "目录里可能没有支持的音频格式（mp3 / m4a / aac / flac / ogg / opus / wav）",
            actionText = "重新扫描",
            onAction = viewModel::startScan,
        )

        else -> Column(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.White),
        ) {
            // 部分根目录授权失效：提示原因并提供管理入口，来自这些目录的歌曲已在列表里灰显
            if (revokedTrees.isNotEmpty() && !allRootsRevoked) {
                RevokedPermissionBanner(
                    count = revokedTrees.size,
                    onManage = onManageFolders,
                )
            }
            // 重扫期间旧列表照常可用，进度条只占顶部一条
            if (scan.running) ScanProgressBanner(scan, onCancel = viewModel::cancelScan)
            LibraryViewSwitch(albumSelected = showAlbums, onSelect = { showAlbums = it })
            if (showAlbums) {
                AlbumGrid(albums = albums, viewModel = viewModel, onAlbumClick = onAlbumClick, modifier = Modifier.weight(1f))
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
                            onLongClick = { editingSong = song },
                        )
                    }
                }
            }
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
    viewModel: LocalMusicViewModel,
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
            LocalAlbumCard(album = album, viewModel = viewModel, onClick = { onAlbumClick(album) })
        }
    }
}

/** 专辑卡片：封面（无封面时占位图）+ 专辑名 + 艺术家 · 歌曲数 */
@Composable
private fun LocalAlbumCard(album: LocalAlbum, viewModel: LocalMusicViewModel, onClick: () -> Unit) {
    val generation by viewModel.coverGeneration.collectAsState()
    // 键上带专辑本身：编辑元数据改了分组成员或标题后重新取图（重扫清缓存则靠代次）
    val cover by produceState<File?>(null, album, generation) {
        value = viewModel.albumCover(album)
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
    ) {
        LocalAlbumCover(cover = cover, albumName = album.name, modifier = Modifier.fillMaxWidth().aspectRatio(1f))
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
 * 专辑封面：封面文件来自 App 私有封面缓存（[LocalMusicViewModel.albumCover]），
 * 没有封面时显示占位图。model 是本地 File，AsyncImage 不会发起网络请求。
 */
@Composable
internal fun LocalAlbumCover(cover: File?, albumName: String, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(PlaceholderBg),
        contentAlignment = Alignment.Center,
    ) {
        if (cover != null) {
            AsyncImage(
                model = cover,
                contentDescription = albumName,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            Icon(
                Icons.Outlined.Album,
                contentDescription = null,
                tint = InkFaint,
                modifier = Modifier.size(32.dp),
            )
        }
    }
}

/** 首帧/启动读索引：只做“App 正在把上次的列表拿回来”的提示，不阻塞页面 */
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

/** 首次扫描时的整页进度：没有旧列表可展示，给出进度、进度条与取消入口 */
@Composable
private fun ScanningState(scan: LocalScanState, onCancel: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(WarmBackground)
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text("正在扫描音乐", color = InkSecondary, fontSize = 15.sp, fontWeight = FontWeight.Medium)
        Spacer(Modifier.height(8.dp))
        Text(
            text = "已处理 ${scan.scannedFiles} 个文件，已找到 ${scan.foundSongs} 首",
            color = InkFaint,
            fontSize = 13.sp,
            textAlign = TextAlign.Center,
        )
        if (scan.currentName.isNotBlank()) {
            Spacer(Modifier.height(4.dp))
            Text(
                text = scan.currentName,
                color = InkFaint,
                fontSize = 12.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.height(18.dp))
        LinearProgressIndicator(
            modifier = Modifier
                .width(160.dp)
                .height(3.dp)
                .clip(RoundedCornerShape(2.dp)),
            color = BrandOrange,
            trackColor = PlaceholderBg,
        )
        Spacer(Modifier.height(10.dp))
        TextButton(onClick = onCancel) {
            Text("取消扫描", color = BrandOrange, fontSize = 13.sp)
        }
    }
}

/** 列表顶部的扫描进度条：旧列表仍可点播，进度只做提示 */
@Composable
private fun ScanProgressBanner(scan: LocalScanState, onCancel: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(BrandOrangeSoft)
            .padding(start = 16.dp, end = 8.dp, top = 6.dp, bottom = 8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "正在扫描 · 已找到 ${scan.foundSongs} 首",
                color = InkPrimary,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.weight(1f),
            )
            TextButton(
                onClick = onCancel,
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
            ) {
                Text("取消", color = BrandOrange, fontSize = 13.sp)
            }
        }
        LinearProgressIndicator(
            modifier = Modifier
                .fillMaxWidth()
                .height(3.dp)
                .clip(RoundedCornerShape(2.dp)),
            color = BrandOrange,
            trackColor = PlaceholderBg,
        )
        if (scan.currentName.isNotBlank()) {
            Spacer(Modifier.height(4.dp))
            Text(
                text = scan.currentName,
                color = InkFaint,
                fontSize = 11.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(end = 8.dp),
            )
        }
    }
}

/**
 * 部分根目录授权失效的顶部提示条：说明哪些歌曲为什么灰显，右侧「管理」直接打开文件夹管理弹层
 * 去移除失效目录或重新添加（spec「权限与失效」）。
 */
@Composable
private fun RevokedPermissionBanner(count: Int, onManage: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(BrandOrangeSoft)
            .padding(start = 16.dp, end = 8.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "有 $count 个音乐文件夹的访问权限已失效，其中的歌曲暂不可用",
            color = InkPrimary,
            fontSize = 13.sp,
            modifier = Modifier.weight(1f),
        )
        TextButton(
            onClick = onManage,
            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
        ) {
            Text("管理", color = BrandOrange, fontSize = 13.sp)
        }
    }
}

/**
 * 本地音乐页的顶栏菜单（替换默认搜索入口）：重新扫描、添加音乐文件夹、管理音乐文件夹。
 * 扫描中「重新扫描」换成「取消扫描」；未授权任何目录时后两项不可用。
 */
@Composable
fun LocalMusicTopBarMenu(
    roots: List<LocalMusicRoot>,
    scanning: Boolean,
    onRescan: () -> Unit,
    onCancelScan: () -> Unit,
    onAddFolder: () -> Unit,
    onManageFolders: () -> Unit,
) {
    var menuExpanded by remember { mutableStateOf(false) }
    val hasRoots = roots.isNotEmpty()

    Box {
        IconButton(onClick = { menuExpanded = true }) {
            Icon(Icons.Default.MoreVert, contentDescription = "本地音乐菜单", tint = InkPrimary)
        }
        DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
            DropdownMenuItem(
                text = { Text(if (scanning) "取消扫描" else "重新扫描") },
                enabled = hasRoots,
                onClick = {
                    menuExpanded = false
                    if (scanning) onCancelScan() else onRescan()
                },
            )
            DropdownMenuItem(
                text = { Text("添加音乐文件夹") },
                onClick = {
                    menuExpanded = false
                    onAddFolder()
                },
            )
            DropdownMenuItem(
                text = { Text("管理音乐文件夹") },
                enabled = hasRoots,
                onClick = {
                    menuExpanded = false
                    onManageFolders()
                },
            )
        }
    }
}

/**
 * 「管理音乐文件夹」：列出已授权的根目录，可逐个移除。
 * 移除立即生效、不必重扫：该目录下的歌曲直接从列表消失，用户改过的元数据按身份保留
 * （重新添加同一目录即恢复）。授权已被系统回收的目录带一行说明，重新添加同一目录即可恢复。
 */
@Composable
fun AuthorizedFoldersDialog(
    roots: List<LocalMusicRoot>,
    onRemove: (LocalMusicRoot) -> Unit,
    onDismiss: () -> Unit,
    revokedTrees: Set<String> = emptySet(),
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("音乐文件夹") },
        text = {
            Column {
                Text(
                    text = if (roots.isEmpty()) {
                        "还没有添加音乐文件夹"
                    } else {
                        "App 只扫描这些目录。移除后其中的歌曲会从列表消失，你改过的信息会保留。"
                    },
                    color = InkSecondary,
                    fontSize = 13.sp,
                )
                Spacer(Modifier.height(4.dp))
                roots.forEach { root ->
                    val revoked = root.treeUri.toString() in revokedTrees
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .padding(vertical = 6.dp),
                        ) {
                            Text(
                                text = root.name,
                                color = if (revoked) InkFaint else InkPrimary,
                                fontSize = 15.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            if (revoked) {
                                Text(
                                    text = "访问权限已失效，重新添加同一个文件夹即可恢复",
                                    color = BrandOrange,
                                    fontSize = 11.sp,
                                )
                            }
                        }
                        TextButton(
                            onClick = { onRemove(root) },
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                        ) {
                            Text("移除", color = BrandOrange, fontSize = 13.sp)
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("关闭") }
        },
    )
}

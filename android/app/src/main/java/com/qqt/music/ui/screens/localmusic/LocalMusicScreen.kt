package com.qqt.music.ui.screens.localmusic

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.outlined.MusicNote
import androidx.compose.material.icons.outlined.MusicOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.qqt.music.data.local.LocalMusicRoot
import com.qqt.music.data.local.LocalSong
import com.qqt.music.player.QueueSource
import com.qqt.music.ui.components.EmptyState
import com.qqt.music.ui.components.songIndexFontSize
import com.qqt.music.ui.theme.BrandOrange
import com.qqt.music.ui.theme.BrandOrangeSoft
import com.qqt.music.ui.theme.InkFaint
import com.qqt.music.ui.theme.InkPrimary
import com.qqt.music.ui.theme.InkSecondary
import com.qqt.music.ui.theme.PlaceholderBg
import com.qqt.music.ui.theme.WarmBackground
import com.qqt.music.viewmodel.PlayerViewModel

/**
 * 本地音乐页（歌曲视图）：未授权文件夹时引导选目录；授权后自动扫描，
 * 扫描中展示进度并可取消，完成后按标题升序（中文排序）列出可播放的本地歌曲。
 * 列表来自扫描索引（跨重启保留），启动时读盘未完成前不把空当空态。
 * 专辑视图与专辑页属票据 08。
 */
@Composable
fun LocalMusicScreen(
    viewModel: LocalMusicViewModel,
    playerViewModel: PlayerViewModel,
    onSelectFolder: () -> Unit,
) {
    val loading by viewModel.loading.collectAsState()
    val roots by viewModel.roots.collectAsState()
    val songs by viewModel.songs.collectAsState()
    val scan by viewModel.scan.collectAsState()
    val indexUnreadable by viewModel.indexUnreadable.collectAsState()

    when {
        // 索引还在读：先给加载态，避免闪过一帧「还没有内容」的引导空态
        loading -> LoadingState()

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
            // 重扫期间旧列表照常可用，进度条只占顶部一条
            if (scan.running) ScanProgressBanner(scan, onCancel = viewModel::cancelScan)
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
                    )
                }
            }
        }
    }
}

/** 本地歌曲行：序号 / 标题 / 艺术家 / 时长；当前曲目序号与标题转橙 */
@Composable
private fun LocalSongRow(
    index: Int,
    song: LocalSong,
    playerViewModel: PlayerViewModel,
    onClick: () -> Unit,
) {
    val currentSong by playerViewModel.currentSong.collectAsState()
    val isCurrent = currentSong?.id == song.id
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            // 左留白 8dp = 序号格与标题之间的间距，使序号在「行左缘 ↔ 标题」之间左右居中
            .padding(start = 8.dp, end = 16.dp, top = 9.dp, bottom = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "$index",
            fontSize = songIndexFontSize("$index".length),
            fontWeight = if (isCurrent) FontWeight.SemiBold else FontWeight.Normal,
            textAlign = TextAlign.Center,
            color = if (isCurrent) BrandOrange else InkSecondary,
            maxLines = 1,
            softWrap = false,
            modifier = Modifier.width(26.dp),
        )
        Spacer(Modifier.width(8.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = song.displayTitle(),
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                color = if (isCurrent) BrandOrange else InkPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(1.dp))
            Text(
                text = song.displayArtist().ifBlank { "未知艺术家" },
                fontSize = 12.sp,
                color = InkSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.width(12.dp))
        Text(
            text = formatDuration(song.durationMs),
            fontSize = 12.sp,
            color = InkFaint,
            maxLines = 1,
        )
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

/** mm:ss；时长未知（标签读不到）显示 --:-- */
private fun formatDuration(millis: Long): String {
    if (millis <= 0L) return "--:--"
    val totalSeconds = millis / 1000
    return "%d:%02d".format(totalSeconds / 60, totalSeconds % 60)
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

/** 已授权目录列表（管理入口）：移除单个根目录在后续票据接入 */
@Composable
fun AuthorizedFoldersDialog(
    roots: List<LocalMusicRoot>,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("音乐文件夹") },
        text = {
            Column {
                roots.forEach { root ->
                    Text(
                        text = root.name,
                        color = InkPrimary,
                        fontSize = 15.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(vertical = 6.dp),
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("关闭") }
        },
    )
}

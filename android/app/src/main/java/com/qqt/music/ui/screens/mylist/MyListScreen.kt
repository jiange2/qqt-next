package com.qqt.music.ui.screens.mylist

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.outlined.QueueMusic
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import com.qqt.music.data.local.LocalPlaylist
import com.qqt.music.data.local.LocalPlaylistStore
import com.qqt.music.ui.components.EmptyState
import com.qqt.music.ui.components.NewPlaylistDialog
import com.qqt.music.ui.components.RenamePlaylistDialog
import com.qqt.music.ui.theme.BrandOrange
import com.qqt.music.ui.theme.InkFaint
import com.qqt.music.ui.theme.InkPrimary
import com.qqt.music.ui.theme.InkSecondary
import com.qqt.music.ui.theme.PlaceholderBg
import com.qqt.music.ui.theme.WarmBackground
import kotlinx.coroutines.flow.StateFlow

class MyListViewModel : ViewModel() {
    /** 本地歌单实时来自 LocalPlaylistStore（进程内事实来源，ADR 0006），无需加载 */
    val playlists: StateFlow<List<LocalPlaylist>> = LocalPlaylistStore.playlists
}

@Composable
fun MyListScreen(
    onPlaylistClick: (LocalPlaylist) -> Unit,
    viewModel: MyListViewModel = viewModel(),
) {
    val playlists by viewModel.playlists.collectAsState()
    var showCreateDialog by remember { mutableStateOf(false) }
    var renameTarget by remember { mutableStateOf<LocalPlaylist?>(null) }
    var deleteTarget by remember { mutableStateOf<LocalPlaylist?>(null) }
    var menuOpenForId by remember { mutableStateOf<String?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(WarmBackground),
    ) {
        // 新建歌单按钮
        Button(
            onClick = { showCreateDialog = true },
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth(0.55f)
                .align(Alignment.CenterHorizontally),
            colors = ButtonDefaults.buttonColors(containerColor = BrandOrange),
            shape = RoundedCornerShape(24.dp),
            elevation = ButtonDefaults.buttonElevation(defaultElevation = 2.dp),
        ) {
            Icon(Icons.Default.Add, null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(4.dp))
            Text("新建歌单", fontSize = 14.sp, fontWeight = FontWeight.Medium)
        }

        if (playlists.isEmpty()) {
            EmptyState(
                icon = Icons.Outlined.QueueMusic,
                title = "还没有歌单",
                subtitle = "在播放器点「加入歌单」，或点上方按钮新建",
            )
        } else {
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items(playlists, key = { it.id }) { playlist ->
                    MyPlaylistCard(
                        playlist = playlist,
                        onClick = { onPlaylistClick(playlist) },
                        menuExpanded = menuOpenForId == playlist.id,
                        onMenuExpandChange = { menuOpenForId = if (it) playlist.id else null },
                        onRename = { renameTarget = playlist },
                        onDelete = { deleteTarget = playlist },
                    )
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
    renameTarget?.let { playlist ->
        RenamePlaylistDialog(
            initialName = playlist.name,
            onDismiss = { renameTarget = null },
            onRename = { name ->
                LocalPlaylistStore.rename(playlist.id, name)
                renameTarget = null
            },
        )
    }
    deleteTarget?.let { playlist ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text("删除歌单") },
            text = { Text("删除「${playlist.name}」？歌单内 ${playlist.songIds.size} 首歌不会被删除。") },
            confirmButton = {
                TextButton(onClick = {
                    LocalPlaylistStore.remove(playlist.id)
                    deleteTarget = null
                }) { Text("删除", color = Color(0xFFD32F2F)) }
            },
            dismissButton = {
                TextButton(onClick = { deleteTarget = null }) { Text("取消", color = InkSecondary) }
            },
        )
    }
}

@Composable
private fun MyPlaylistCard(
    playlist: LocalPlaylist,
    onClick: () -> Unit,
    menuExpanded: Boolean,
    onMenuExpandChange: (Boolean) -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit,
) {
    Card(
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        modifier = Modifier.clickable(onClick = onClick),
    ) {
        Column {
            // 本地歌单只存歌曲 ID 不换详情，无缩略图可拼，统一音符占位
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f)
                    .background(PlaceholderBg),
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    modifier = Modifier
                        .size(64.dp)
                        .clip(CircleShape)
                        .background(Color(0xFFE3DCD4)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Default.MusicNote, null, tint = Color.White, modifier = Modifier.size(30.dp))
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 10.dp, end = 2.dp, top = 6.dp, bottom = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = playlist.name,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        color = InkPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = "${playlist.songIds.size} 首",
                        fontSize = 11.sp,
                        color = InkFaint,
                    )
                }
                Box {
                    IconButton(onClick = { onMenuExpandChange(true) }, modifier = Modifier.size(28.dp)) {
                        Icon(Icons.Default.MoreVert, contentDescription = "更多", tint = InkFaint, modifier = Modifier.size(18.dp))
                    }
                    DropdownMenu(
                        expanded = menuExpanded,
                        onDismissRequest = { onMenuExpandChange(false) },
                    ) {
                        DropdownMenuItem(
                            text = { Text("重命名") },
                            onClick = {
                                onMenuExpandChange(false)
                                onRename()
                            },
                        )
                        DropdownMenuItem(
                            text = { Text("删除") },
                            onClick = {
                                onMenuExpandChange(false)
                                onDelete()
                            },
                        )
                    }
                }
            }
        }
    }
}

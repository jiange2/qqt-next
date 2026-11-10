package com.qqt.music.ui.screens.localmusic

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.MusicNote
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.qqt.music.ui.components.EmptyState
import com.qqt.music.ui.theme.BrandOrange
import com.qqt.music.ui.theme.InkPrimary
import com.qqt.music.ui.theme.InkSecondary

/**
 * 本地音乐页：未授权任何文件夹时给出引导空态与「选择音乐文件夹」主按钮；
 * 授权后列出已授权的目录树。歌曲/专辑视图与扫描进度在后续票据接入。
 */
@Composable
fun LocalMusicScreen(
    viewModel: LocalMusicViewModel,
    onSelectFolder: () -> Unit,
) {
    val roots by viewModel.roots.collectAsState()

    if (roots.isEmpty()) {
        EmptyState(
            icon = Icons.Outlined.MusicNote,
            title = "本地音乐还没有内容",
            subtitle = "选择一个音乐文件夹，App 只扫描你授权的目录，不需要任何存储权限",
            actionText = "选择音乐文件夹",
            onAction = onSelectFolder,
        )
    } else {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.White),
        ) {
            Text(
                text = "已授权的音乐文件夹",
                color = InkSecondary,
                fontSize = 13.sp,
                modifier = Modifier.padding(start = 16.dp, top = 14.dp, end = 16.dp, bottom = 4.dp),
            )
            roots.forEach { root ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                        .padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Folder,
                        contentDescription = null,
                        tint = BrandOrange,
                        modifier = Modifier.size(20.dp),
                    )
                    Spacer(Modifier.width(12.dp))
                    Text(
                        text = root.name,
                        color = InkPrimary,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

/**
 * 本地音乐页的顶栏菜单（替换默认搜索入口）：重新扫描、添加音乐文件夹、管理音乐文件夹。
 * 未授权任何目录时，「重新扫描」「管理音乐文件夹」不可用；重新扫描在扫描管线接入后启用。
 */
@Composable
fun LocalMusicTopBarMenu(
    roots: List<LocalMusicRoot>,
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
            // 扫描管线尚未接入：无扫描结果可刷新前保持不可用，避免出现点了没反应的入口
            DropdownMenuItem(
                text = { Text("重新扫描") },
                enabled = false,
                onClick = {},
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

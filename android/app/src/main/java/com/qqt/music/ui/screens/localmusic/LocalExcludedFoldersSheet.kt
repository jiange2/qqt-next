package com.qqt.music.ui.screens.localmusic

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.qqt.music.data.local.LocalFolder
import com.qqt.music.ui.theme.BrandOrange
import com.qqt.music.ui.theme.InkFaint
import com.qqt.music.ui.theme.InkPrimary

/**
 * 「排除的文件夹」面板（spec「文件夹黑名单」，票据 03）：底部弹层，自动列出当前库中
 * 所有含音乐的文件夹（从库行路径派生，无需手输路径），每个带一个开关。
 *
 * 排除某文件夹时它的子文件夹一并从列表消失；开关切换即时生效（纯内存过滤，不重扫、
 * 不发任何网络请求，飞行模式下照常可用），排除状态随库快照落盘、重启后保留。
 * 文件夹清单派生自整库——已被排除的文件夹也在清单里，否则无法取消排除。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LocalExcludedFoldersSheet(
    viewModel: LocalMusicViewModel,
    onDismiss: () -> Unit,
) {
    val folders by viewModel.libraryFolders.collectAsState()
    val excludedFolders by viewModel.excludedFolders.collectAsState()

    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = Color.White) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 28.dp),
        ) {
            Text("排除的文件夹", fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = InkPrimary)
            Spacer(Modifier.height(4.dp))
            Text(
                "排除后，该文件夹及其子文件夹都不会出现在列表里；打开开关立即恢复，不需要重新扫描",
                fontSize = 12.sp,
                color = InkFaint,
            )
            Spacer(Modifier.height(8.dp))

            if (folders.isEmpty()) {
                Text(
                    "库里还没有音乐；扫描到音乐后，这里会自动列出所有文件夹",
                    fontSize = 13.sp,
                    color = InkFaint,
                    modifier = Modifier.padding(vertical = 24.dp),
                )
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 420.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    items(folders, key = { it.path }) { folder ->
                        FolderRow(
                            folder = folder,
                            excluded = folder.path in excludedFolders,
                            onToggle = { viewModel.setFolderExcluded(folder.path, it) },
                        )
                    }
                }
            }
        }
    }
}

/** 单个文件夹行：目录名 + 完整路径（与目录名相同时不重复展示）+ 排除开关 */
@Composable
private fun FolderRow(
    folder: LocalFolder,
    excluded: Boolean,
    onToggle: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = folder.name,
                fontSize = 14.sp,
                color = InkPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (folder.path != folder.name) {
                Text(
                    text = folder.path,
                    fontSize = 11.sp,
                    color = InkFaint,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Switch(
            checked = excluded,
            onCheckedChange = onToggle,
            colors = SwitchDefaults.colors(
                checkedTrackColor = BrandOrange,
                checkedThumbColor = Color.White,
            ),
        )
    }
}

package com.qqt.music.ui.screens.localmusic

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.qqt.music.data.local.LocalMetadataDraft
import com.qqt.music.data.local.MetadataSuggestion
import com.qqt.music.data.local.batchPanelCanSave
import com.qqt.music.ui.theme.BrandOrange
import com.qqt.music.ui.theme.InkFaint
import com.qqt.music.ui.theme.InkPrimary
import com.qqt.music.ui.theme.InkSecondary

/**
 * 批量管理面板（票据02，spec「批量面板」）：编辑模式勾选 ≥1 首后从底栏「编辑信息」打开。
 * 与单首面板 [LocalMetadataEditSheet] 同款 ModalBottomSheet，但只有专辑 / 专辑艺术家两个
 * 字段（自由输入 + 本地库编辑候选，spec「编辑候选」），初始为空、不预填、无「恢复默认」；
 * 「留空 = 不改」（与单首面板「留空 = 恢复文件原值」相反）。两字段 trim 皆空时保存禁用
 * （[batchPanelCanSave]）。候选列表由导航层从可见歌曲列表派生传入，面板保持无 ViewModel。
 * 保存接 [LocalMusicViewModel.saveBatchMetadata]（票据01 一次提交链路）：成功关面板 +
 * 退出编辑模式 + 清空勾选由导航层接线，Toast「已修改 n 首」走既有 notice 链路，这里不另弹；
 * 批量只改专辑字段，播放器只显示标题 + 艺术家，无需播放器同步。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LocalBatchEditSheet(
    selectedCount: Int,
    albumSuggestions: List<MetadataSuggestion>,
    albumArtistSuggestions: List<MetadataSuggestion>,
    onSave: (albumInput: String, albumArtistInput: String) -> Unit,
    onDismiss: () -> Unit,
) {
    // 初始为空、不预填：批量是「把散落的歌归并进一张专辑」，预填某一首的值反而会误导；
    // 面板关闭即离组合，重新打开又是空草稿（未保存输入丢弃）
    var draft by remember { mutableStateOf(LocalMetadataDraft()) }
    val canSave = batchPanelCanSave(draft.album, draft.albumArtist)

    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = Color.White) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                // 两个字段加键盘：矮屏也能滚到最下面的按钮
                .verticalScroll(rememberScrollState())
                .imePadding()
                .padding(horizontal = 20.dp)
                .padding(bottom = 28.dp),
        ) {
            Text("批量管理", fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = InkPrimary)
            Spacer(Modifier.height(4.dp))
            Text(
                text = "已选 $selectedCount 首 · 只改 App 里的显示，不会修改音频文件本身",
                fontSize = 12.sp,
                color = InkFaint,
            )
            Spacer(Modifier.height(12.dp))

            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                MetadataSuggestionField(
                    label = "专辑",
                    value = draft.album,
                    onValueChange = { draft = draft.copy(album = it) },
                    suggestions = albumSuggestions,
                    // 点选专辑候选连带回填专辑艺术家（spec「专辑候选联动」）：两框一起填，
                    // 选中行才按同一「专辑名 + 专辑艺术家」并进一张；清空本框 = 不改（既有语义）
                    onSuggestionClick = { draft = draft.copy(album = it.value, albumArtist = it.supporting.orEmpty()) },
                )
                MetadataSuggestionField(
                    label = "专辑艺术家",
                    value = draft.albumArtist,
                    onValueChange = { draft = draft.copy(albumArtist = it) },
                    suggestions = albumArtistSuggestions,
                )
                Text(
                    text = "留空的字段保持原样",
                    fontSize = 11.sp,
                    color = InkFaint,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }

            Spacer(Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Spacer(Modifier.weight(1f))
                TextButton(onClick = onDismiss) {
                    Text("取消", color = InkSecondary)
                }
                TextButton(
                    onClick = { onSave(draft.album, draft.albumArtist) },
                    enabled = canSave,
                ) {
                    Text("保存", color = if (canSave) BrandOrange else InkFaint)
                }
            }
        }
    }
}
package com.qqt.music.ui.screens.localmusic

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.qqt.music.data.local.LocalNumberInput
import com.qqt.music.data.local.LocalSong
import com.qqt.music.data.local.metadataDraft
import com.qqt.music.data.local.trackNoInput
import com.qqt.music.data.local.yearInput
import com.qqt.music.ui.theme.BrandOrange
import com.qqt.music.ui.theme.InkFaint
import com.qqt.music.ui.theme.InkPrimary
import com.qqt.music.ui.theme.InkSecondary
import com.qqt.music.viewmodel.PlayerViewModel

/**
 * 本地歌曲的元数据编辑面板（spec「元数据编辑」，ADR 0020）：底部弹层，六个字段 + 「恢复默认」。
 *
 * 两个入口共用这一个面板：歌曲视图 / 本地专辑页长按歌曲行；全屏播放器「更多菜单 → 编辑信息」。
 * 只写覆盖层——音频文件的标签一个字节都不改；保存后歌曲列表、专辑分组与专辑封面立即刷新
 * （展示值全部从覆盖层派生），而重扫、移动文件、改名之后改动仍然生效（覆盖层按内容身份存储）。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LocalMetadataEditSheet(
    song: LocalSong,
    viewModel: LocalMusicViewModel,
    playerViewModel: PlayerViewModel,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    // 草稿按歌曲重置：换一首歌编辑时面板里显示的是那首歌的值
    var draft by remember(song.id) { mutableStateOf(song.metadataDraft()) }
    // 音轨号与年份边输入边校验：非法即时红框并给出提示，保存不可点（spec：非法输入被拒绝并提示）；
    // 没改动的字段不校验（[trackNoInput] / [yearInput]）——标签里带的老值不该把整张面板锁死
    val trackNoError = (song.trackNoInput(draft.trackNo) as? LocalNumberInput.Invalid)?.message
    val yearError = (song.yearInput(draft.year) as? LocalNumberInput.Invalid)?.message
    val canSave = trackNoError == null && yearError == null
    // 没有覆盖项的歌本来就在用文件标签值：恢复默认无事可做，入口置灰
    val canRestore = song.overlay != null

    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = Color.White) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                // 六个字段加键盘：矮屏也能滚到最下面的按钮
                .verticalScroll(rememberScrollState())
                .imePadding()
                .padding(horizontal = 20.dp)
                .padding(bottom = 28.dp),
        ) {
            Text("编辑歌曲信息", fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = InkPrimary)
            Spacer(Modifier.height(4.dp))
            Text("只改 App 里的显示，不会修改音频文件本身", fontSize = 12.sp, color = InkFaint)
            Spacer(Modifier.height(12.dp))

            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                MetadataField(
                    label = "标题",
                    value = draft.title,
                    onValueChange = { draft = draft.copy(title = it) },
                )
                MetadataField(
                    label = "艺术家",
                    value = draft.artist,
                    onValueChange = { draft = draft.copy(artist = it) },
                )
                MetadataField(
                    label = "专辑",
                    value = draft.album,
                    onValueChange = { draft = draft.copy(album = it) },
                )
                MetadataField(
                    label = "专辑艺术家",
                    value = draft.albumArtist,
                    onValueChange = { draft = draft.copy(albumArtist = it) },
                )
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    MetadataField(
                        label = "音轨号",
                        value = draft.trackNo,
                        onValueChange = { draft = draft.copy(trackNo = it) },
                        error = trackNoError,
                        numeric = true,
                        modifier = Modifier.weight(1f),
                    )
                    MetadataField(
                        label = "年份",
                        value = draft.year,
                        onValueChange = { draft = draft.copy(year = it) },
                        error = yearError,
                        numeric = true,
                        modifier = Modifier.weight(1f),
                    )
                }
                Text(
                    // 专辑留空时卡片上看到的是文件夹名（[com.qqt.music.data.local.groupIntoAlbums] 的兜底），
                    // 说清楚这句，免得用户以为面板漏填了一个屏上明明有值的字段
                    text = "留空即回到文件标签值；专辑留空时按所在文件夹名显示",
                    fontSize = 11.sp,
                    color = InkFaint,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }

            Spacer(Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(
                    // 恢复默认：删掉这首歌的整条覆盖，全部回到文件标签值（ADR 0020）
                    onClick = {
                        val restored = viewModel.restoreDefaultMetadata(song)
                        syncPlayerDisplay(playerViewModel, restored)
                        Toast.makeText(context, "已恢复文件标签值", Toast.LENGTH_SHORT).show()
                        onDismiss()
                    },
                    enabled = canRestore,
                    contentPadding = PaddingValues(horizontal = 4.dp),
                ) {
                    Text("恢复默认", color = if (canRestore) BrandOrange else InkFaint, fontSize = 13.sp)
                }
                Spacer(Modifier.weight(1f))
                TextButton(onClick = onDismiss) {
                    Text("取消", color = InkSecondary)
                }
                TextButton(
                    onClick = {
                        when (val result = viewModel.saveMetadata(song, draft)) {
                            is LocalMetadataSaveResult.Saved -> {
                                syncPlayerDisplay(playerViewModel, result.song)
                                onDismiss()
                            }
                            // 保存按钮已按校验结果置灰，这里只是并发改稿的兜底：提示落在字段下方
                            is LocalMetadataSaveResult.Invalid ->
                                Toast.makeText(context, "音轨号或年份不合法，请检查", Toast.LENGTH_SHORT).show()
                        }
                    },
                    enabled = canSave,
                ) {
                    Text("保存", color = if (canSave) BrandOrange else InkFaint)
                }
            }
        }
    }
}

/** 编辑结果同步到播放器上的展示值：正在听的那首歌，标题与艺术家立即换成新值 */
private fun syncPlayerDisplay(playerViewModel: PlayerViewModel, song: LocalSong) {
    playerViewModel.refreshLocalSongDisplay(
        id = song.id,
        title = song.displayTitle(),
        artist = song.displayArtist(),
    )
}

/**
 * 单个字段：M3 描边输入框。数值字段用数字键盘，非法时红框 + 字段下方提示。
 * 不给占位文案——面板初值就是合并后的展示值，用户看到的就是当前生效的值。
 */
@Composable
private fun MetadataField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    error: String? = null,
    numeric: Boolean = false,
) {
    // 提示区显式声明类型：if 分支里直接写 lambda 字面量会掉进可空函数类型的推断坑
    val errorHint: (@Composable () -> Unit)? = error?.let { message -> { Text(message, fontSize = 11.sp) } }
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label, fontSize = 13.sp) },
        singleLine = true,
        isError = error != null,
        supportingText = errorHint,
        keyboardOptions = if (numeric) {
            KeyboardOptions(keyboardType = KeyboardType.Number)
        } else {
            KeyboardOptions.Default
        },
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = BrandOrange,
            focusedLabelColor = BrandOrange,
            cursorColor = BrandOrange,
        ),
        modifier = modifier.fillMaxWidth(),
    )
}

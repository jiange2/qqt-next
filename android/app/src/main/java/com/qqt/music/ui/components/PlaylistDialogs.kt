package com.qqt.music.ui.components

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import com.qqt.music.ui.theme.BrandOrange
import com.qqt.music.ui.theme.InkSecondary

/** 新建歌单对话框：输入名字即建（本地歌单，ADR 0006）；成功回调后由调用方关闭对话框 */
@Composable
fun NewPlaylistDialog(
    onDismiss: () -> Unit,
    onCreate: (String) -> Unit,
) {
    var name by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("新建歌单", fontWeight = FontWeight.SemiBold) },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                placeholder = { Text("歌单名称") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = {
            TextButton(
                onClick = { onCreate(name) },
                enabled = name.isNotBlank(),
            ) { Text("创建", color = BrandOrange) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消", color = InkSecondary) }
        },
    )
}

/** 重命名歌单对话框：名字未变更时不可保存 */
@Composable
fun RenamePlaylistDialog(
    initialName: String,
    onDismiss: () -> Unit,
    onRename: (String) -> Unit,
) {
    var name by remember { mutableStateOf(initialName) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("重命名歌单", fontWeight = FontWeight.SemiBold) },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                placeholder = { Text("歌单名称") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = {
            TextButton(
                onClick = { onRename(name) },
                enabled = name.isNotBlank() && name.trim() != initialName,
            ) { Text("保存", color = BrandOrange) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消", color = InkSecondary) }
        },
    )
}

package com.qqt.music.ui.components

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable

/**
 * 白名单引导弹窗（ADR 0014）：app 打开时检查电池优化白名单，未放行则引导放行。
 */
@Composable
fun WhitelistGuidanceDialog(
    onBatteryWhitelist: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("保障后台播放") },
        text = {
            Text("为避免省电策略中断后台播放，建议将本 App 加入电池优化白名单。")
        },
        confirmButton = {
            TextButton(onClick = onBatteryWhitelist) { Text("去设置") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        },
    )
}

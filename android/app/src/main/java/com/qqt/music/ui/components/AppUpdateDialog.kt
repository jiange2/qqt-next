package com.qqt.music.ui.components

import android.text.Html
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import com.qqt.music.data.api.model.AppUpdateInfo

/**
 * App 更新弹窗：展示后台配置的更新描述，点击"立即更新"跳转下载地址。
 * forceUpdate = true（后台关闭"允许取消更新"）时不可关闭，只能点更新。
 */
@Composable
fun AppUpdateDialog(
    info: AppUpdateInfo,
    forceUpdate: Boolean,
    onUpdate: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = { if (!forceUpdate) onDismiss() },
        title = { Text("发现新版本 ${info.newVersion}") },
        text = { Text(htmlToText(info.updateDesc).ifBlank { "新版本已发布，请尽快更新。" }) },
        confirmButton = {
            TextButton(onClick = onUpdate) { Text("立即更新") }
        },
        dismissButton = if (!forceUpdate) {
            {
                TextButton(onClick = onDismiss) { Text("稍后再说") }
            }
        } else {
            null
        },
    )
}

/** 后台更新描述来自 CKEditor，是 HTML 片段；转纯文本展示 */
private fun htmlToText(html: String): String {
    if (html.isBlank()) return ""
    return try {
        Html.fromHtml(html, Html.FROM_HTML_MODE_LEGACY).toString().trim()
    } catch (e: Exception) {
        html
    }
}

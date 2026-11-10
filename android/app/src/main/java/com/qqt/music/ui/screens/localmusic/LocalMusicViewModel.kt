package com.qqt.music.ui.screens.localmusic

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** 已授权的音乐文件夹：SAF 树 URI + 展示用目录名（ADR 0019 以用户选中的树为扫描范围） */
data class LocalMusicRoot(val treeUri: Uri, val name: String)

/**
 * 本地音乐页的授权目录状态。跨重启的授权由系统侧保管（takePersistableUriPermission），
 * 这里只保留本进程内的树列表；扫描与索引持久化由后续票据接入。
 */
class LocalMusicViewModel : ViewModel() {
    private val _roots = MutableStateFlow<List<LocalMusicRoot>>(emptyList())
    val roots: StateFlow<List<LocalMusicRoot>> = _roots.asStateFlow()

    /** 收录用户选中的目录树；同一棵树重复选择只记一次 */
    fun addRoot(context: Context, treeUri: Uri) {
        if (_roots.value.any { it.treeUri == treeUri }) return
        // 选择目录即是唯一一次授权：从此本机可长期读取该树，无任何存储权限弹窗
        runCatching {
            context.contentResolver.takePersistableUriPermission(
                treeUri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION,
            )
        }
        val name = queryTreeName(context, treeUri) ?: treeUri.lastPathSegment ?: "音乐文件夹"
        _roots.value = _roots.value + LocalMusicRoot(treeUri, name)
    }

    /** SAF 只给树 URI，目录名需要查询树根 document 的 DISPLAY_NAME */
    private fun queryTreeName(context: Context, treeUri: Uri): String? = runCatching {
        val rootUri = DocumentsContract.buildDocumentUriUsingTree(
            treeUri,
            DocumentsContract.getTreeDocumentId(treeUri),
        )
        context.contentResolver.query(
            rootUri,
            arrayOf(DocumentsContract.Document.COLUMN_DISPLAY_NAME),
            null,
            null,
            null,
        )?.use { cursor -> if (cursor.moveToFirst()) cursor.getString(0) else null }
    }.getOrNull()
}
